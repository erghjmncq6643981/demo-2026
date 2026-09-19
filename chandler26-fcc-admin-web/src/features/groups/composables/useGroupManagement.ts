import { buildTree } from '../model/groupTree';
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { type OrgNode } from '../../../views/OrgTreeItem.vue';
import {
  agentApi,
  type AgentGroupVO,
  type AgentGroupMemberVO,
  type AgentVO
} from '../../../api/agentApi';
import { toast, confirmAction, errorText } from '../../../utils/feedback';

/** Owns groups queries, mutations and view state for one mounted page. */
export function useGroupManagement() {
  // ==================== 1. 组织架构树与真实数据库状态 ====================
  const searchOrg = ref('');
  const rawGroups = ref<AgentGroupVO[]>([]);
  const treeData = ref<OrgNode[]>([]);
  const selectedNodeId = ref<string>('');
  const selectedDept = ref('');
  const selectedDeptPath = ref('');
  const selectedDeptCode = ref('');
  const selectedDeptType = ref('');
  const selectedDeptDesc = ref('');

  // 话务策略与配置
  const strategy = ref('ROUND_ROBIN');
  const enableGroupPickup = ref(false); // 是否开启同组代答
  const exclusiveNumberPool = ref(false); // 是否独占号码池
  const selectedCarrier = ref('全部'); // 运营商筛选: 全部 / 电信 / 移动 / 联通
  const searchMemberQuery = ref(''); // 组员姓名/工号搜索

  // Toast 提示统一走全局反馈层 (应用内 toast)
  const triggerToast = (msg: string) => toast(msg, 'success');

  // 成员数据源 (真实从数据库拉取)
  const currentMembers = ref<AgentGroupMemberVO[]>([]);
  const loadingMembers = ref(false);

  // 系统已有坐席库 (用于“绑定坐席”下拉选择)
  const systemAgents = ref<AgentVO[]>([]);


  // 加载组织架构全量分组
  const loadOrgTree = async (targetSelectId?: string) => {
    try {
      const list = await agentApi.listGroups();
      rawGroups.value = list || [];
      treeData.value = buildTree(rawGroups.value);

      // 默认选中节点逻辑
      const toSelect = targetSelectId || selectedNodeId.value;
      const found = rawGroups.value.find(g => g.id === toSelect);
      if (found) {
        handleSelectGroup(found);
      } else if (rawGroups.value.length > 0) {
        handleSelectGroup(rawGroups.value[0]);
      }
    } catch (err: any) {
      console.error('加载组织架构失败:', err);
      triggerToast('加载组织架构数据失败: ' + (err.message || '网络异常'));
    }
  };

  // 计算节点完整路径 (面包屑)
  const computeGroupPath = (group: AgentGroupVO): string => {
    const parts: string[] = [group.groupName];
    let cur = group;
    while (cur.parentId) {
      const parent = rawGroups.value.find(g => g.id === cur.parentId);
      if (parent) {
        parts.unshift(parent.groupName);
        cur = parent;
      } else {
        break;
      }
    }
    return parts.join(' / ');
  };

  // 切换选中的组
  const handleSelectGroup = (group: AgentGroupVO) => {
    selectedNodeId.value = group.id;
    selectedDept.value = group.groupName;
    selectedDeptCode.value = group.groupCode;
    selectedDeptType.value = group.groupType || 'SKILL';
    strategy.value = group.routingStrategy || 'ROUND_ROBIN';
    selectedDeptPath.value = computeGroupPath(group);
    selectedDeptDesc.value = group.groupType === 'COMPANY'
      ? '企业顶级根节点，负责全集团话务路由调度与组织架构总控'
      : `负责业务承接与坐席话务转接 (编码: ${group.groupCode})`;

    loadMembers(group.id);
  };

  // 点击左侧树节点联动
  const handleSelectNode = (node: OrgNode) => {
    const group = rawGroups.value.find(g => g.id === node.id);
    if (group) {
      handleSelectGroup(group);
    }
  };

  // 加载指定组的成员列表
  const loadMembers = async (groupId: string) => {
    loadingMembers.value = true;
    try {
      const list = await agentApi.listGroupMembers(groupId);
      currentMembers.value = list || [];

      // 同步更新树节点人数徽章
      const updateCountRecursive = (nodes: OrgNode[]) => {
        for (const n of nodes) {
          if (n.id === groupId) {
            n.count = currentMembers.value.length;
            return true;
          }
          if (n.children && updateCountRecursive(n.children)) return true;
        }
        return false;
      };
      updateCountRecursive(treeData.value);
    } catch (err) {
      console.error('加载成员失败:', err);
      currentMembers.value = [];
    } finally {
      loadingMembers.value = false;
    }
  };

  // ==================== 2. 组织架构右键菜单与操作弹窗 ====================
  const contextMenu = ref({
    visible: false,
    x: 0,
    y: 0,
    node: null as OrgNode | null
  });

  const handleTreeContextMenu = (e: MouseEvent, node: OrgNode) => {
    contextMenu.value = {
      visible: true,
      x: e.clientX,
      y: e.clientY,
      node: node
    };
  };

  const closeContextMenu = () => {
    contextMenu.value.visible = false;
  };

  // 弹窗状态：新增子部门
  const showAddDeptModal = ref(false);
  const newDeptName = ref('');
  const newDeptCode = ref('');
  const newDeptType = ref('SKILL');
  const newDeptStrategy = ref('ROUND_ROBIN');

  // 弹窗状态：修改部门
  const showEditDeptModal = ref(false);
  const editDeptId = ref<string>('');
  const editDeptName = ref('');
  const editDeptCode = ref('');
  const editDeptType = ref('SKILL');
  const editDeptStrategy = ref('ROUND_ROBIN');

  // 弹窗状态：删除确认
  const showDeleteDeptModal = ref(false);
  const deletingDeptNode = ref<OrgNode | null>(null);

  const openAddDept = () => {
    newDeptName.value = '';
    newDeptCode.value = `Q-GRP-${Date.now().toString().slice(-4)}`;
    newDeptType.value = 'SKILL';
    newDeptStrategy.value = 'ROUND_ROBIN';
    showAddDeptModal.value = true;
    closeContextMenu();
  };

  const openEditDept = () => {
    const targetNode = contextMenu.value.node || treeData.value.find(n => n.id === selectedNodeId.value);
    if (!targetNode) return;
    const group = rawGroups.value.find(g => String(g.id) === targetNode.id);
    if (group) {
      editDeptId.value = group.id;
      editDeptName.value = group.groupName;
      editDeptCode.value = group.groupCode;
      editDeptType.value = group.groupType || 'SKILL';
      editDeptStrategy.value = group.routingStrategy || 'ROUND_ROBIN';
    }
    showEditDeptModal.value = true;
    closeContextMenu();
  };

  const openDeleteDept = () => {
    const targetNode = contextMenu.value.node;
    if (!targetNode) return;
    if (targetNode.level === 0) {
      toast('顶级企业根节点受系统保护，不可删除！', 'warning');
      closeContextMenu();
      return;
    }
    deletingDeptNode.value = targetNode;
    showDeleteDeptModal.value = true;
    closeContextMenu();
  };

  // 确认新增子部门
  const handleConfirmAddDept = async () => {
    if (!newDeptName.value.trim() || !newDeptCode.value.trim()) {
      toast('请填写部门名称和唯一编码！', 'warning');
      return;
    }
    const parentNode = contextMenu.value.node || treeData.value.find(n => n.id === selectedNodeId.value);
    if (!parentNode) {
      toast('请先选择上级部门', 'warning');
      return;
    }
    const parentId = parentNode.id;

    try {
      const newId = await agentApi.createGroup({
        parentId: parentId,
        groupName: newDeptName.value.trim(),
        groupCode: newDeptCode.value.trim().toUpperCase(),
        groupType: newDeptType.value,
        routingStrategy: newDeptStrategy.value
      });
      showAddDeptModal.value = false;
      triggerToast(`成功在【${parentNode?.name || '根节点'}】下新增部门【${newDeptName.value}】！`);
      await loadOrgTree(newId);
    } catch (err: any) {
      toast('创建失败: ' + (err.message || '请检查编码是否冲突'), 'error');
    }
  };

  // 确认修改部门
  const handleConfirmEditDept = async () => {
    if (!editDeptName.value.trim() || !editDeptCode.value.trim()) {
      toast('部门名称和编码不能为空！', 'warning');
      return;
    }

    try {
      await agentApi.updateGroup({
        id: editDeptId.value,
        groupName: editDeptName.value.trim(),
        groupCode: editDeptCode.value.trim().toUpperCase(),
        groupType: editDeptType.value,
        routingStrategy: editDeptStrategy.value
      });
      showEditDeptModal.value = false;
      triggerToast(`部门【${editDeptName.value}】配置已成功保存！`);
      await loadOrgTree(editDeptId.value);
    } catch (err: any) {
      toast('修改失败: ' + (err.message || '网络错误'), 'error');
    }
  };

  // 确认删除部门
  const handleConfirmDeleteDept = async () => {
    if (!deletingDeptNode.value) return;
    const targetId = deletingDeptNode.value.id;
    const targetName = deletingDeptNode.value.name;

    try {
      await agentApi.deleteGroup(targetId);
      showDeleteDeptModal.value = false;
      triggerToast(`部门【${targetName}】已删除！`);
      if (selectedNodeId.value === targetId) {
        selectedNodeId.value = '';
      }
      await loadOrgTree(selectedNodeId.value);
    } catch (err: any) {
      toast('删除失败: ' + (err.message || '系统繁忙'), 'error');
    }
  };

  // 保存当前选中的组的话务配置
  const handleSaveGroupConfig = async () => {
    try {
      await agentApi.updateGroup({
        id: selectedNodeId.value,
        groupName: selectedDept.value,
        groupCode: selectedDeptCode.value,
        groupType: selectedDeptType.value,
        routingStrategy: strategy.value
      });
      triggerToast(`技能组【${selectedDept.value}】话务路由策略已更新为【${strategyDesc.value}】！`);
      await loadOrgTree(selectedNodeId.value);
    } catch (err: any) {
      toast('保存失败: ' + (err.message || '请重试'), 'error');
    }
  };

  onMounted(() => {
    window.addEventListener('click', closeContextMenu);
    loadOrgTree();
  });

  onUnmounted(() => {
    window.removeEventListener('click', closeContextMenu);
  });

  // ==================== 3. 坐席成员管理 (新增坐席 vs 绑定坐席) ====================
  const memberPage = ref(1);
  const memberPageSize = ref(10);

  const filteredMembers = computed(() => {
    if (!searchMemberQuery.value.trim()) return currentMembers.value;
    const q = searchMemberQuery.value.trim().toLowerCase();
    return currentMembers.value.filter(m =>
      m.agentName.toLowerCase().includes(q) ||
      m.workNo.includes(q) ||
      (m.phoneNumber && m.phoneNumber.includes(q))
    );
  });

  const pagedMembers = computed(() => {
    const start = (memberPage.value - 1) * memberPageSize.value;
    return filteredMembers.value.slice(start, start + memberPageSize.value);
  });

  const totalMemberPages = computed(() => {
    return Math.ceil(filteredMembers.value.length / memberPageSize.value) || 1;
  });

  // 弹窗：➕ 新增坐席 (新建档案并入组)
  const showAddAgentModal = ref(false);
  const newAgentName = ref('');
  const newAgentWorkNo = ref('');
  const newAgentPhone = ref('');
  const newAgentRoleCode = ref('AGENT');
  const newAgentMemberRole = ref('MEMBER');
  const newAgentPriority = ref(0);

  const openAddAgentModal = () => {
    newAgentName.value = '';
    newAgentWorkNo.value = '';
    newAgentPhone.value = '';
    newAgentRoleCode.value = 'AGENT';
    newAgentMemberRole.value = 'MEMBER';
    newAgentPriority.value = 0;
    showAddAgentModal.value = true;
  };

  const handleConfirmCreateAgent = async () => {
    if (!newAgentName.value.trim() || !newAgentWorkNo.value.trim()) {
      toast('坐席姓名和工号为必填项！', 'warning');
      return;
    }
    try {
      await agentApi.createAndBindAgent(selectedNodeId.value, {
        agentName: newAgentName.value.trim(),
        workNo: newAgentWorkNo.value.trim(),
        phoneNumber: newAgentPhone.value.trim() || undefined,
        roleCode: newAgentRoleCode.value,
        memberRole: newAgentMemberRole.value,
        priority: newAgentPriority.value
      });
      showAddAgentModal.value = false;
      triggerToast(`坐席 ${newAgentName.value} (${newAgentWorkNo.value}) 创建并加入【${selectedDept.value}】成功！`);
      await loadMembers(selectedNodeId.value);
    } catch (err: any) {
      toast('创建坐席失败: ' + (err.message || '请检查工号是否已被占用'), 'error');
    }
  };

  // 弹窗：🔗 绑定已有坐席
  const showBindAgentModal = ref(false);
  const selectedBindAgentId = ref<string | null>(null);
  const bindMemberRole = ref('MEMBER');
  const bindPriority = ref(0);
  const loadingSystemAgents = ref(false);

  const openBindAgentModal = async () => {
    loadingSystemAgents.value = true;
    showBindAgentModal.value = true;
    selectedBindAgentId.value = null;
    bindMemberRole.value = 'MEMBER';
    bindPriority.value = 0;

    try {
      const res = await agentApi.list({ pageNum: 1, pageSize: 200 });
      systemAgents.value = res.list || [];
    } catch (err) {
      console.error('加载系统坐席失败:', err);
    } finally {
      loadingSystemAgents.value = false;
    }
  };

  // 过滤掉已经在当前技能组内的坐席
  const availableAgentsToBind = computed(() => {
    const currentAgentIds = new Set(currentMembers.value.map(m => m.agentId));
    return systemAgents.value.filter(a => !currentAgentIds.has(a.id));
  });

  const handleConfirmBindAgent = async () => {
    if (!selectedBindAgentId.value) {
      toast('请选择要绑定的坐席！', 'warning');
      return;
    }
    try {
      await agentApi.addMemberToGroup({
        groupId: selectedNodeId.value,
        agentId: selectedBindAgentId.value,
        memberRole: bindMemberRole.value,
        priority: bindPriority.value
      });
      showBindAgentModal.value = false;
      triggerToast(`坐席已成功绑定至【${selectedDept.value}】！`);
      await loadMembers(selectedNodeId.value);
    } catch (err: any) {
      toast('绑定失败: ' + (err.message || '网络异常'), 'error');
    }
  };

  // 弹窗：✏️ 修改组员在组内的身份或优先级
  const showEditMemberModal = ref(false);
  const editingMember = ref<AgentGroupMemberVO | null>(null);
  const editMemberRole = ref('MEMBER');
  const editMemberPriority = ref(0);

  const handleOpenEditMember = (mem: AgentGroupMemberVO) => {
    editingMember.value = mem;
    editMemberRole.value = mem.memberRole || 'MEMBER';
    editMemberPriority.value = mem.priority || 0;
    showEditMemberModal.value = true;
  };

  const handleConfirmEditMember = async () => {
    if (!editingMember.value) return;
    try {
      await agentApi.updateGroupMember(
        selectedNodeId.value,
        editingMember.value.agentId,
        {
          memberRole: editMemberRole.value,
          priority: editMemberPriority.value
        }
      );
      showEditMemberModal.value = false;
      triggerToast(`组员 ${editingMember.value.agentName} 权限已更新！`);
      await loadMembers(selectedNodeId.value);
    } catch (err: any) {
      toast('修改失败: ' + (err.message || '网络异常'), 'error');
    }
  };

  // 解绑坐席 (从当前组移出，保留账号)
  const handleUnbindMember = async (mem: AgentGroupMemberVO) => {
    const ok = await confirmAction(`确认将坐席 ${mem.agentName} (${mem.workNo}) 从【${selectedDept.value}】解绑移出吗？\n（注：坐席人员账号依然完好保留在系统中）`, { title: '解绑坐席' });
    if (!ok) {
      return;
    }
    try {
      await agentApi.removeMemberFromGroup(selectedNodeId.value, mem.agentId);
      toast(`坐席 ${mem.agentName} 已成功从技能组解绑！`, 'success');
      await loadMembers(selectedNodeId.value);
    } catch (err: any) {
      toast('解绑失败: ' + (err.message || '网络错误'), 'error');
    }
  };

  // 彻底删除坐席档案
  const handleDeleteMemberAccount = async (mem: AgentGroupMemberVO) => {
    const ok = await confirmAction(`确认彻底注销并删除坐席【${mem.agentName} (${mem.workNo})】的档案吗？`, { title: '删除坐席档案', danger: true, confirmText: '确认删除' });
    if (!ok) {
      return;
    }
    try {
      await agentApi.delete(mem.agentId);
      toast(`坐席档案【${mem.agentName}】已删除！`, 'success');
      await loadMembers(selectedNodeId.value);
    } catch (err: any) {
      toast('删除失败: ' + (err.message || '系统繁忙'), 'error');
    }
  };

  // 策略描述计算
  const strategyDesc = computed(() => {
    switch (strategy.value) {
      case 'ROUND_ROBIN': return '轮询均摊 (Round-Robin)：呼入按坐席次序循环转接，实现全队接待量均匀平摊。';
      case 'LONGEST_IDLE': return '最长空闲优先 (Longest Idle)：优先路由给空闲等待时间最长的坐席，保障人效均衡。';
      case 'PRIORITY': return '优先级分发 (Priority)：严格按组内坐席优先级分发，同级按空闲时长分配。';
      default: return '轮询均摊路由调度。';
    }
  });

  return {
    searchOrg,
    treeData,
    selectedNodeId,
    selectedDept,
    selectedDeptPath,
    selectedDeptCode,
    selectedDeptType,
    selectedDeptDesc,
    strategy,
    enableGroupPickup,
    exclusiveNumberPool,
    searchMemberQuery,
    triggerToast,
    currentMembers,
    handleSelectNode,
    contextMenu,
    handleTreeContextMenu,
    showAddDeptModal,
    newDeptName,
    newDeptCode,
    newDeptType,
    newDeptStrategy,
    showEditDeptModal,
    editDeptName,
    editDeptCode,
    editDeptType,
    editDeptStrategy,
    showDeleteDeptModal,
    deletingDeptNode,
    openAddDept,
    openEditDept,
    openDeleteDept,
    handleConfirmAddDept,
    handleConfirmEditDept,
    handleConfirmDeleteDept,
    handleSaveGroupConfig,
    memberPage,
    memberPageSize,
    filteredMembers,
    pagedMembers,
    totalMemberPages,
    showAddAgentModal,
    newAgentName,
    newAgentWorkNo,
    newAgentPhone,
    newAgentRoleCode,
    newAgentMemberRole,
    newAgentPriority,
    openAddAgentModal,
    handleConfirmCreateAgent,
    showBindAgentModal,
    selectedBindAgentId,
    bindMemberRole,
    bindPriority,
    loadingSystemAgents,
    openBindAgentModal,
    availableAgentsToBind,
    handleConfirmBindAgent,
    showEditMemberModal,
    editingMember,
    editMemberRole,
    editMemberPriority,
    handleOpenEditMember,
    handleConfirmEditMember,
    handleUnbindMember,
    handleDeleteMemberAccount,
    strategyDesc
  };
}
