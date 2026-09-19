<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue';
import OrgTreeItem, { type OrgNode } from './OrgTreeItem.vue';
import {
  agentApi,
  type AgentGroupVO,
  type AgentGroupMemberVO,
  type AgentVO
} from '../api/agentApi';

// ==================== 1. 组织架构树与真实数据库状态 ====================
const searchOrg = ref('');
const rawGroups = ref<AgentGroupVO[]>([]);
const treeData = ref<OrgNode[]>([]);
const selectedNodeId = ref<string>('1');
const selectedDept = ref('箱箱物流科技');
const selectedDeptPath = ref('箱箱物流科技');
const selectedDeptCode = ref('ROOT_ORG');
const selectedDeptType = ref('COMPANY');
const selectedDeptDesc = ref('集团语音调度与多级客服组织根节点');

// 话务策略与配置
const strategy = ref('ROUND_ROBIN');
const enableGroupPickup = ref(false); // 是否开启同组代答
const exclusiveNumberPool = ref(false); // 是否独占号码池
const selectedCarrier = ref('全部'); // 运营商筛选: 全部 / 电信 / 移动 / 联通
const searchMemberQuery = ref(''); // 组员姓名/工号搜索

// Toast 提示
const toastMsg = ref('');
const triggerToast = (msg: string) => {
  toastMsg.value = msg;
  setTimeout(() => { toastMsg.value = ''; }, 3200);
};

// 成员数据源 (真实从数据库拉取)
const currentMembers = ref<AgentGroupMemberVO[]>([]);
const loadingMembers = ref(false);

// 系统已有坐席库 (用于“绑定坐席”下拉选择)
const systemAgents = ref<AgentVO[]>([]);

// 构建树形结构算法
const buildTree = (groups: AgentGroupVO[]): OrgNode[] => {
  if (!groups || groups.length === 0) return [];
  const map = new Map<number, OrgNode>();
  
  // 第一轮：初始化所有节点
  groups.forEach(g => {
    let iconType: OrgNode['icon'] = 'group';
    if (g.parentId === 0 || !g.parentId || g.id === 1 || g.groupType === 'COMPANY') {
      iconType = 'company';
    } else if (g.groupType === 'CENTER') {
      iconType = 'center';
    }

    map.set(g.id, {
      id: String(g.id),
      name: g.groupName,
      level: 0,
      icon: iconType,
      count: g.memberCount || 0,
      expanded: true,
      children: []
    });
  });

  const roots: OrgNode[] = [];

  // 第二轮：组装父子层级
  groups.forEach(g => {
    const node = map.get(g.id)!;
    if (g.parentId && g.parentId !== 0 && map.has(g.parentId)) {
      const parentNode = map.get(g.parentId)!;
      node.level = parentNode.level + 1;
      if (!parentNode.children) parentNode.children = [];
      parentNode.children.push(node);
      if (parentNode.icon === 'group') {
        parentNode.icon = 'center';
      }
    } else {
      roots.push(node);
    }
  });

  return roots;
};

// 加载组织架构全量分组
const loadOrgTree = async (targetSelectId?: string) => {
  try {
    const list = await agentApi.listGroups();
    rawGroups.value = list || [];
    treeData.value = buildTree(rawGroups.value);

    // 默认选中节点逻辑
    const toSelect = targetSelectId || selectedNodeId.value;
    const found = rawGroups.value.find(g => String(g.id) === String(toSelect));
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
  while (cur.parentId && cur.parentId !== 0) {
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
  selectedNodeId.value = String(group.id);
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
  const group = rawGroups.value.find(g => String(g.id) === node.id);
  if (group) {
    handleSelectGroup(group);
  }
};

// 加载指定组的成员列表
const loadMembers = async (groupId: number) => {
  loadingMembers.value = true;
  try {
    const list = await agentApi.listGroupMembers(groupId);
    currentMembers.value = list || [];
    
    // 同步更新树节点人数徽章
    const updateCountRecursive = (nodes: OrgNode[]) => {
      for (const n of nodes) {
        if (n.id === String(groupId)) {
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
const editDeptId = ref<number>(0);
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
  if (targetNode.id === '1' || targetNode.level === 0) {
    alert('顶级企业根节点受系统保护，不可删除！');
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
    alert('请填写部门名称和唯一编码！');
    return;
  }
  const parentNode = contextMenu.value.node || treeData.value.find(n => n.id === selectedNodeId.value);
  const parentId = parentNode ? Number(parentNode.id) : 1;

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
    await loadOrgTree(String(newId));
  } catch (err: any) {
    alert('创建失败: ' + (err.message || '请检查编码是否冲突'));
  }
};

// 确认修改部门
const handleConfirmEditDept = async () => {
  if (!editDeptName.value.trim() || !editDeptCode.value.trim()) {
    alert('部门名称和编码不能为空！');
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
    await loadOrgTree(String(editDeptId.value));
  } catch (err: any) {
    alert('修改失败: ' + (err.message || '网络错误'));
  }
};

// 确认删除部门
const handleConfirmDeleteDept = async () => {
  if (!deletingDeptNode.value) return;
  const targetId = Number(deletingDeptNode.value.id);
  const targetName = deletingDeptNode.value.name;

  try {
    await agentApi.deleteGroup(targetId);
    showDeleteDeptModal.value = false;
    triggerToast(`部门【${targetName}】已删除！`);
    if (selectedNodeId.value === String(targetId)) {
      selectedNodeId.value = '1';
    }
    await loadOrgTree(selectedNodeId.value);
  } catch (err: any) {
    alert('删除失败: ' + (err.message || '系统繁忙'));
  }
};

// 保存当前选中的组的话务配置
const handleSaveGroupConfig = async () => {
  try {
    await agentApi.updateGroup({
      id: Number(selectedNodeId.value),
      groupName: selectedDept.value,
      groupCode: selectedDeptCode.value,
      groupType: selectedDeptType.value,
      routingStrategy: strategy.value
    });
    triggerToast(`技能组【${selectedDept.value}】话务路由策略已更新为【${strategyDesc.value}】！`);
    await loadOrgTree(selectedNodeId.value);
  } catch (err: any) {
    alert('保存失败: ' + (err.message || '请重试'));
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
    alert('坐席姓名和工号为必填项！');
    return;
  }
  try {
    await agentApi.createAndBindAgent(Number(selectedNodeId.value), {
      agentName: newAgentName.value.trim(),
      workNo: newAgentWorkNo.value.trim(),
      phoneNumber: newAgentPhone.value.trim() || undefined,
      roleCode: newAgentRoleCode.value,
      memberRole: newAgentMemberRole.value,
      priority: newAgentPriority.value
    });
    showAddAgentModal.value = false;
    triggerToast(`坐席 ${newAgentName.value} (${newAgentWorkNo.value}) 创建并加入【${selectedDept.value}】成功！`);
    await loadMembers(Number(selectedNodeId.value));
  } catch (err: any) {
    alert('创建坐席失败: ' + (err.message || '请检查工号是否已被占用'));
  }
};

// 弹窗：🔗 绑定已有坐席
const showBindAgentModal = ref(false);
const selectedBindAgentId = ref<number | null>(null);
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
    alert('请选择要绑定的坐席！');
    return;
  }
  try {
    await agentApi.addMemberToGroup({
      groupId: Number(selectedNodeId.value),
      agentId: selectedBindAgentId.value,
      memberRole: bindMemberRole.value,
      priority: bindPriority.value
    });
    showBindAgentModal.value = false;
    triggerToast(`坐席已成功绑定至【${selectedDept.value}】！`);
    await loadMembers(Number(selectedNodeId.value));
  } catch (err: any) {
    alert('绑定失败: ' + (err.message || '网络异常'));
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
      Number(selectedNodeId.value),
      editingMember.value.agentId,
      {
        memberRole: editMemberRole.value,
        priority: editMemberPriority.value
      }
    );
    showEditMemberModal.value = false;
    triggerToast(`组员 ${editingMember.value.agentName} 权限已更新！`);
    await loadMembers(Number(selectedNodeId.value));
  } catch (err: any) {
    alert('修改失败: ' + (err.message || '网络异常'));
  }
};

// 解绑坐席 (从当前组移出，保留账号)
const handleUnbindMember = async (mem: AgentGroupMemberVO) => {
  if (!confirm(`确认将坐席 ${mem.agentName} (${mem.workNo}) 从【${selectedDept.value}】解绑移出吗？\n（注：坐席人员账号依然完好保留在系统中）`)) {
    return;
  }
  try {
    await agentApi.removeMemberFromGroup(Number(selectedNodeId.value), mem.agentId);
    triggerToast(`坐席 ${mem.agentName} 已成功从技能组解绑！`);
    await loadMembers(Number(selectedNodeId.value));
  } catch (err: any) {
    alert('解绑失败: ' + (err.message || '网络错误'));
  }
};

// 彻底删除坐席档案
const handleDeleteMemberAccount = async (mem: AgentGroupMemberVO) => {
  if (!confirm(`⚠️ 危险操作：确认彻底注销并删除坐席【${mem.agentName} (${mem.workNo})】的档案吗？`)) {
    return;
  }
  try {
    await agentApi.delete(mem.agentId);
    triggerToast(`坐席档案【${mem.agentName}】已删除！`);
    await loadMembers(Number(selectedNodeId.value));
  } catch (err: any) {
    alert('删除失败: ' + (err.message || '系统繁忙'));
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
</script>

<template>
  <div class="h-full flex-1 flex gap-5 overflow-hidden">
    
    <!-- 顶部全局 Toast 浮动提示 -->
    <div v-if="toastMsg" class="fixed top-6 right-8 z-50 bg-slate-900/95 text-white px-5 py-3 rounded-2xl shadow-2xl text-sm font-bold flex items-center gap-2.5 animate-bounce border border-slate-700 backdrop-blur-md">
      <span class="text-blue-400">🔔</span>
      <span>{{ toastMsg }}</span>
    </div>

    <!-- ========================================================================= -->
    <!-- 1. 左侧企业组织架构树 (真实数据库驱动，单一顶级根节点初始) -->
    <!-- ========================================================================= -->
    <div class="w-80 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-5 flex flex-col shrink-0">
      
      <!-- 顶栏：标题与快捷新增 -->
      <div class="flex items-center justify-between pb-3 border-b border-slate-100">
        <div class="flex items-center gap-2">
          <span class="text-lg">🏢</span>
          <h2 class="text-sm font-black text-slate-900 tracking-tight">组织与技能组</h2>
        </div>
        <button
          @click="openAddDept"
          class="px-2.5 py-1 bg-blue-50 hover:bg-blue-100 text-[#1677ff] rounded-lg text-xs font-bold transition flex items-center gap-1 cursor-pointer"
          title="在选中部门下创建子部门或技能组"
        >
          <span>➕</span>
          <span>新建分组</span>
        </button>
      </div>

      <!-- 搜索栏 -->
      <div class="relative my-3">
        <input
          v-model="searchOrg"
          type="text"
          placeholder="搜索部门或技能组..."
          class="w-full bg-[#f8fafc] border border-slate-200 rounded-xl pl-3 pr-8 py-2 text-xs text-slate-700 placeholder-slate-400 focus:outline-none focus:border-[#1677ff] transition"
        />
        <span class="absolute right-2.5 top-2.5 text-slate-400 text-xs">🔍</span>
      </div>

      <!-- 组织架构树主体 -->
      <div class="flex-1 overflow-y-auto space-y-1 pr-1 custom-scrollbar">
        <OrgTreeItem
          v-for="node in treeData"
          :key="node.id"
          :node="node"
          :selected-id="selectedNodeId"
          :search="searchOrg"
          @select="handleSelectNode"
          @contextmenu="handleTreeContextMenu"
        />
        <div v-if="treeData.length === 0" class="py-12 text-center text-xs text-slate-400">
          正在加载组织架构...
        </div>
      </div>

      <!-- 底部右键提示条 -->
      <div class="pt-3 mt-2 border-t border-slate-100 flex items-center justify-between text-xs text-slate-500">
        <div class="flex items-center gap-1.5">
          <span class="text-blue-500 font-bold">💡</span>
          <span>右键节点增/删/改</span>
        </div>
        <span class="font-mono text-slate-600 font-bold truncate max-w-[140px]" :title="selectedDept">
          {{ selectedDept }}
        </span>
      </div>
    </div>

    <!-- ========================================================================= -->
    <!-- 2. 右侧管理主区域 (卡片化、真实数据库驱动) -->
    <!-- ========================================================================= -->
    <div class="flex-1 bg-white rounded-2xl border border-slate-200/80 shadow-xs p-6 flex flex-col overflow-y-auto space-y-6">
      
      <!-- 2.1 顶部部门信息与话务路由配置卡片 -->
      <div class="bg-gradient-to-r from-slate-50 via-white to-blue-50/30 rounded-2xl border border-slate-200/90 p-5 shadow-2xs space-y-4">
        <!-- 头部面包屑与技能组标题 -->
        <div class="flex items-center justify-between flex-wrap gap-3 pb-3 border-b border-slate-200/60">
          <div>
            <div class="flex items-center gap-2 text-xs text-slate-400 font-medium mb-1">
              <svg class="w-3.5 h-3.5 text-slate-400" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" /></svg>
              <span>{{ selectedDeptPath }}</span>
            </div>
            <div class="flex items-center gap-3">
              <h1 class="text-lg font-black text-slate-900 tracking-tight">{{ selectedDept }}</h1>
              <span class="px-2.5 py-0.5 rounded-full bg-blue-100/70 text-[#1677ff] font-mono font-bold text-xs">
                {{ selectedDeptCode }}
              </span>
              <span class="px-2 py-0.5 rounded-md bg-emerald-50 text-emerald-700 text-xs font-bold border border-emerald-200">
                ● 运行就绪
              </span>
            </div>
          </div>
          <div class="flex items-center gap-2">
            <button
              @click="openEditDept"
              class="px-3.5 py-2 bg-white border border-slate-200 hover:bg-slate-50 text-slate-700 rounded-xl text-xs font-bold shadow-2xs transition cursor-pointer"
            >
              ✏️ 编辑部门
            </button>
            <button
              @click="handleSaveGroupConfig"
              class="px-4 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-md shadow-blue-500/20 flex items-center gap-1.5 transition cursor-pointer"
            >
              <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path stroke-linecap="round" stroke-linejoin="round" stroke-width="2.5" d="M5 13l4 4L19 7"/></svg>
              <span>保存策略</span>
            </button>
          </div>
        </div>

        <!-- 部门简述 -->
        <p class="text-xs text-slate-500 font-medium leading-relaxed">
          {{ selectedDeptDesc }}
        </p>

        <!-- 核心话务属性与规则网格 -->
        <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 pt-1">
          <!-- 卡片 1: 坐席分配策略 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-1.5">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-blue-500">⚡</span>
                <span>坐席分配策略</span>
              </div>
              <span class="text-[10px] text-slate-400">ACD 规则</span>
            </div>
            <select
              v-model="strategy"
              class="w-full bg-[#f8fafc] border border-slate-200 rounded-lg px-2.5 py-1.5 text-xs text-slate-900 font-bold focus:outline-none focus:border-[#1677ff] cursor-pointer"
            >
              <option value="ROUND_ROBIN">轮询均摊 (Round-Robin)</option>
              <option value="LONGEST_IDLE">最长空闲优先 (Longest Idle)</option>
              <option value="PRIORITY">坐席优先级 (Priority)</option>
            </select>
            <div class="text-[10px] text-slate-400 leading-tight">
              {{ strategyDesc }}
            </div>
          </div>

          <!-- 卡片 2: 同组代答机制 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-2">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-purple-500">📞</span>
                <span>同组代答机制</span>
              </div>
              <span
                class="text-[10px] font-bold px-1.5 py-0.5 rounded"
                :class="enableGroupPickup ? 'bg-blue-50 text-[#1677ff]' : 'bg-slate-100 text-slate-400'"
              >
                {{ enableGroupPickup ? '已开启' : '已关闭' }}
              </span>
            </div>
            <div class="flex items-center justify-between pt-0.5">
              <span class="text-xs font-semibold text-slate-800">
                {{ enableGroupPickup ? '支持跨工位快速代答' : '仅目标分机振铃' }}
              </span>
              <button
                type="button"
                @click="enableGroupPickup = !enableGroupPickup; triggerToast(enableGroupPickup ? '已开启同组代答' : '已关闭同组代答')"
                class="relative inline-flex h-6 w-12 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none"
                :class="enableGroupPickup ? 'bg-[#1677ff]' : 'bg-[#cbd5e1]'"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out mt-0.5 ml-0.5"
                  :class="enableGroupPickup ? 'translate-x-6' : 'translate-x-0'"
                />
              </button>
            </div>
          </div>

          <!-- 卡片 3: 号码池模式 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-2">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-emerald-500">🌐</span>
                <span>外呼线路池</span>
              </div>
              <span
                class="text-[10px] font-bold px-1.5 py-0.5 rounded"
                :class="exclusiveNumberPool ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-400'"
              >
                {{ exclusiveNumberPool ? '独立号码池' : '共享号码池' }}
              </span>
            </div>
            <div class="flex items-center justify-between pt-0.5">
              <span class="text-xs font-semibold text-slate-800">
                {{ exclusiveNumberPool ? '组内专属外呼中继' : '共享公共网关中继' }}
              </span>
              <button
                type="button"
                @click="exclusiveNumberPool = !exclusiveNumberPool; triggerToast(exclusiveNumberPool ? '已开启独立号码池' : '已恢复共享号码池')"
                class="relative inline-flex h-6 w-12 shrink-0 cursor-pointer rounded-full transition-colors duration-200 ease-in-out focus:outline-none"
                :class="exclusiveNumberPool ? 'bg-[#1677ff]' : 'bg-[#cbd5e1]'"
              >
                <span
                  class="pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow-sm ring-0 transition duration-200 ease-in-out mt-0.5 ml-0.5"
                  :class="exclusiveNumberPool ? 'translate-x-6' : 'translate-x-0'"
                />
              </button>
            </div>
          </div>

          <!-- 卡片 4: 节点统计概览 -->
          <div class="bg-white p-3.5 rounded-xl border border-slate-200 shadow-2xs space-y-1.5">
            <div class="flex items-center justify-between text-xs text-slate-500 font-bold">
              <div class="flex items-center gap-1.5">
                <span class="text-indigo-500">👥</span>
                <span>在册坐席人数</span>
              </div>
              <span class="text-[10px] text-slate-400">实时统计</span>
            </div>
            <div class="flex items-center justify-between pt-1">
              <div class="text-2xl font-black text-slate-900 font-mono">
                {{ currentMembers.length }} <span class="text-xs font-bold text-slate-400">人</span>
              </div>
              <span class="px-2 py-0.5 rounded bg-blue-50 text-blue-700 text-xs font-bold">
                {{ selectedDeptType }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- ======================================================================= -->
      <!-- 2.2 技能组坐席成员列表 (真实数据库绑定，新增坐席 + 绑定已有坐席) -->
      <!-- ======================================================================= -->
      <div class="bg-white rounded-2xl border border-slate-200/80 shadow-2xs p-5 space-y-4">
        <!-- 顶栏：标题、快速检索与新增/绑定按钮 -->
        <div class="flex items-center justify-between flex-wrap gap-4">
          <div class="flex items-center gap-3">
            <div class="w-2.5 h-5 bg-indigo-500 rounded-full"></div>
            <h3 class="text-sm font-black text-slate-900">技能组坐席成员</h3>
            <span class="px-2 py-0.5 rounded-full bg-indigo-50 text-indigo-700 font-mono text-xs font-bold">
              共 {{ filteredMembers.length }} 名成员
            </span>
          </div>

          <div class="flex items-center gap-2.5 text-xs">
            <!-- 坐席检索框 -->
            <div class="relative">
              <input
                v-model="searchMemberQuery"
                type="text"
                placeholder="搜索姓名/工号/手机..."
                class="w-48 bg-[#f8fafc] border border-slate-200 rounded-xl pl-3 pr-7 py-1.5 text-xs text-slate-700 placeholder-slate-400 focus:outline-none focus:border-[#1677ff]"
              />
              <span class="absolute right-2 top-2 text-slate-400 text-xs">🔍</span>
            </div>

            <!-- 按钮 1: ➕ 新增坐席 (新建档案并入组) -->
            <button
              @click="openAddAgentModal"
              class="px-3.5 py-1.5 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold shadow-xs transition flex items-center gap-1 cursor-pointer"
            >
              <span>➕</span>
              <span>新增坐席</span>
            </button>

            <!-- 按钮 2: 🔗 绑定坐席 (从系统已有坐席中选取) -->
            <button
              @click="openBindAgentModal"
              class="px-3.5 py-1.5 bg-indigo-50 hover:bg-indigo-100 text-indigo-700 border border-indigo-200 rounded-xl text-xs font-bold shadow-xs transition flex items-center gap-1 cursor-pointer"
            >
              <span>🔗</span>
              <span>绑定坐席</span>
            </button>
          </div>
        </div>

        <!-- 坐席成员表格 (真实数据库字段) -->
        <div class="border border-slate-200/90 rounded-xl overflow-hidden">
          <table class="w-full text-xs text-center">
            <thead class="bg-[#f8fafc] text-slate-600 border-b border-slate-200 font-bold">
              <tr>
                <th class="py-3 px-4 text-left">坐席姓名</th>
                <th class="py-3 px-4">工号</th>
                <th class="py-3 px-4">联系电话</th>
                <th class="py-3 px-4">组内身份</th>
                <th class="py-3 px-4">调度优先级</th>
                <th class="py-3 px-4">系统角色</th>
                <th class="py-3 px-4">入组时间</th>
                <th class="py-3 px-4 text-right">操作</th>
              </tr>
            </thead>
            <tbody class="divide-y divide-slate-100 bg-white">
              <tr v-for="mem in pagedMembers" :key="mem.id" class="hover:bg-blue-50/30 transition-colors">
                <!-- 姓名 + 头像 -->
                <td class="py-3 px-4 text-left">
                  <div class="flex items-center gap-2.5">
                    <div class="w-7 h-7 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-500 text-white font-bold flex items-center justify-center text-xs shadow-2xs">
                      {{ mem.agentName ? mem.agentName.slice(0, 1) : '坐' }}
                    </div>
                    <div>
                      <div class="font-bold text-slate-900">{{ mem.agentName }}</div>
                      <div class="text-[10px] text-slate-400 font-mono">ID: {{ mem.agentId }}</div>
                    </div>
                  </div>
                </td>
                <!-- 工号 -->
                <td class="py-3 px-4 font-mono font-bold text-slate-700">
                  <span class="px-2 py-0.5 rounded bg-slate-100 text-slate-700">
                    {{ mem.workNo }}
                  </span>
                </td>
                <!-- 手机号 -->
                <td class="py-3 px-4 font-mono text-slate-700">
                  {{ mem.phoneNumber || '-' }}
                </td>
                <!-- 组内身份 (LEADER / MEMBER) -->
                <td class="py-3 px-4">
                  <span
                    class="px-2.5 py-0.5 rounded-full text-[11px] font-bold"
                    :class="mem.memberRole === 'LEADER' ? 'bg-purple-100 text-purple-700 border border-purple-200' : 'bg-blue-50 text-blue-700'"
                  >
                    {{ mem.memberRole === 'LEADER' ? '👑 班长席 / 组长' : '👤 普通坐席' }}
                  </span>
                </td>
                <!-- 优先级 -->
                <td class="py-3 px-4 font-mono font-bold text-slate-700">
                  <span class="px-2 py-0.5 rounded bg-amber-50 text-amber-700 border border-amber-200 text-[11px]">
                    优先级 {{ mem.priority ?? 0 }}
                  </span>
                </td>
                <!-- 系统角色 -->
                <td class="py-3 px-4">
                  <span class="text-xs text-slate-600 font-medium">
                    {{ mem.roleCode === 'SUPERVISOR' ? '主管' : '坐席' }}
                  </span>
                </td>
                <!-- 加入时间 -->
                <td class="py-3 px-4 font-mono text-slate-400 text-[11px]">
                  {{ mem.createdAt ? mem.createdAt.replace('T', ' ').slice(0, 19) : '-' }}
                </td>
                <!-- 操作 -->
                <td class="py-3 px-4 text-right">
                  <div class="inline-flex items-center gap-1.5">
                    <button
                      @click="handleOpenEditMember(mem)"
                      class="text-[#1677ff] hover:text-blue-700 hover:bg-blue-50 px-2 py-1 rounded transition font-bold cursor-pointer"
                    >
                      修改
                    </button>
                    <span class="text-slate-200">|</span>
                    <button
                      @click="handleUnbindMember(mem)"
                      class="text-amber-600 hover:text-amber-800 hover:bg-amber-50 px-2 py-1 rounded transition font-bold cursor-pointer"
                      title="仅将该坐席移出当前技能组，保留坐席账号"
                    >
                      解绑
                    </button>
                    <span class="text-slate-200">|</span>
                    <button
                      @click="handleDeleteMemberAccount(mem)"
                      class="text-rose-600 hover:text-rose-800 hover:bg-rose-50 px-2 py-1 rounded transition font-bold cursor-pointer"
                      title="彻底注销并删除该坐席账号"
                    >
                      删除
                    </button>
                  </div>
                </td>
              </tr>
              <tr v-if="pagedMembers.length === 0">
                <td colspan="8" class="py-12 text-center text-slate-400">
                  <div class="text-2xl mb-1">📭</div>
                  <div>当前技能组暂无坐席成员，请点击右上角「新增坐席」或「绑定坐席」</div>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 成员表分页器 -->
        <div class="flex items-center justify-between text-xs text-slate-500 pt-2">
          <span>共 {{ filteredMembers.length }} 名成员 ({{ currentMembers.length }} 条数据库记录)</span>
          <div class="flex items-center gap-3">
            <div class="flex items-center gap-1">
              <button
                :disabled="memberPage <= 1"
                @click="memberPage--"
                class="w-6 h-6 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer"
              >
                &lt;
              </button>
              <button
                v-for="p in totalMemberPages"
                :key="p"
                @click="memberPage = p"
                class="w-6 h-6 rounded flex items-center justify-center text-xs cursor-pointer font-bold"
                :class="memberPage === p ? 'bg-[#1677ff] text-white' : 'border border-slate-200 hover:bg-slate-50 text-slate-700'"
              >
                {{ p }}
              </button>
              <button
                :disabled="memberPage >= totalMemberPages"
                @click="memberPage++"
                class="w-6 h-6 border border-slate-200 rounded flex items-center justify-center hover:bg-slate-50 disabled:opacity-30 cursor-pointer"
              >
                &gt;
              </button>
            </div>
            <div class="relative">
              <select
                v-model="memberPageSize"
                class="border border-slate-200 rounded px-2 py-0.5 text-xs text-slate-600 bg-white focus:outline-none cursor-pointer pr-5 appearance-none"
              >
                <option :value="10">10 条/页</option>
                <option :value="20">20 条/页</option>
              </select>
              <span class="absolute right-1.5 top-1 text-[10px] text-slate-400 pointer-events-none">⌄</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ==================== 右键悬浮上下文菜单 ==================== -->
    <div
      v-if="contextMenu.visible"
      :style="{ top: `${contextMenu.y}px`, left: `${contextMenu.x}px` }"
      class="fixed z-50 bg-white border border-slate-200 rounded-xl shadow-2xl py-1.5 w-48 text-xs select-none backdrop-blur-md"
      @click.stop
    >
      <div class="px-3 py-1.5 text-[11px] text-slate-400 border-b border-slate-100 font-bold truncate">
        📂 {{ contextMenu.node?.name }}
      </div>
      <button
        @click="openAddDept"
        class="w-full text-left px-3 py-2 text-slate-700 hover:bg-blue-50 hover:text-[#1677ff] flex items-center gap-2 cursor-pointer transition-colors"
      >
        <span>➕</span>
        <span class="font-medium">新增子部门/技能组</span>
      </button>
      <button
        @click="openEditDept"
        class="w-full text-left px-3 py-2 text-slate-700 hover:bg-blue-50 hover:text-[#1677ff] flex items-center gap-2 cursor-pointer transition-colors"
      >
        <span>✏️</span>
        <span class="font-medium">编辑此部门</span>
      </button>
      <div class="h-px bg-slate-100 my-1"></div>
      <button
        @click="openDeleteDept"
        class="w-full text-left px-3 py-2 text-rose-600 hover:bg-rose-50 flex items-center gap-2 cursor-pointer transition-colors"
      >
        <span>🗑️</span>
        <span class="font-medium">删除该部门节点</span>
      </button>
    </div>

    <!-- ==================== 弹窗 1: 新增子部门 / 技能组 ==================== -->
    <div v-if="showAddDeptModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">新增子部门 / 技能组</span>
          <button @click="showAddDeptModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div class="text-slate-500">
            上级部门: <strong class="text-slate-800">{{ selectedDept }}</strong>
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">部门/技能组名称 *</label>
            <input
              v-model="newDeptName"
              type="text"
              placeholder="例如：白班一组 / 售前咨询中心"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">唯一编码 (英文大写) *</label>
            <input
              v-model="newDeptCode"
              type="text"
              placeholder="例如：Q-HOTLINE-01"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono uppercase text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组织类型</label>
              <select v-model="newDeptType" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="SKILL">话务技能组</option>
                <option value="CENTER">业务管理中心</option>
                <option value="COMPANY">子公司</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">路由分发策略</label>
              <select v-model="newDeptStrategy" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="ROUND_ROBIN">轮询均摊</option>
                <option value="LONGEST_IDLE">最长空闲优先</option>
                <option value="PRIORITY">优先级分发</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddDeptModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmAddDept" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            确认新增
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 2: 编辑部门/技能组 ==================== -->
    <div v-if="showEditDeptModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">编辑部门/技能组</span>
          <button @click="showEditDeptModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-bold">部门名称 *</label>
            <input
              v-model="editDeptName"
              type="text"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">唯一编码 *</label>
            <input
              v-model="editDeptCode"
              type="text"
              class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono uppercase text-slate-800 focus:outline-none focus:border-[#1677ff]"
            />
          </div>
          <div class="grid grid-cols-2 gap-2">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组织类型</label>
              <select v-model="editDeptType" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="COMPANY">顶级集团/公司</option>
                <option value="CENTER">业务管理中心</option>
                <option value="SKILL">话务技能组</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">路由分发策略</label>
              <select v-model="editDeptStrategy" class="w-full border border-slate-200 rounded-xl px-2.5 py-1.5 text-xs text-slate-800 bg-white">
                <option value="ROUND_ROBIN">轮询均摊</option>
                <option value="LONGEST_IDLE">最长空闲优先</option>
                <option value="PRIORITY">优先级分发</option>
              </select>
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showEditDeptModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmEditDept" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            保存修改
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 3: 删除部门节点确认 ==================== -->
    <div v-if="showDeleteDeptModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <span class="font-bold text-sm text-slate-900">确认删除部门</span>
          <button @click="showDeleteDeptModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="text-xs text-slate-600 leading-relaxed">
          确定要删除部门【<strong class="text-slate-900">{{ deletingDeptNode?.name }}</strong>】吗？<br />
          <span class="text-rose-500 font-medium">注意：该部门及其下级关联的所有配置将从数据库移除。</span>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showDeleteDeptModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmDeleteDept" class="px-5 py-2 bg-rose-600 hover:bg-rose-700 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            确认删除
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 4: ➕ 新增坐席并加入组 ==================== -->
    <div v-if="showAddAgentModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div>
            <span class="font-bold text-sm text-slate-900">新增坐席并加入技能组</span>
            <div class="text-[11px] text-slate-400">将创建坐席人员档案，并直接绑定到【{{ selectedDept }}】</div>
          </div>
          <button @click="showAddAgentModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">坐席姓名 *</label>
              <input v-model="newAgentName" type="text" placeholder="如 舒欣" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">坐席工号 *</label>
              <input v-model="newAgentWorkNo" type="text" placeholder="如 90105" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">联系手机</label>
              <input v-model="newAgentPhone" type="text" placeholder="如 13800138000" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组内身份</label>
              <select v-model="newAgentMemberRole" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="MEMBER">👤 普通坐席</option>
                <option value="LEADER">👑 班长席 / 组长</option>
              </select>
            </div>
          </div>
          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">系统角色权限</label>
              <select v-model="newAgentRoleCode" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="AGENT">普通坐席</option>
                <option value="SUPERVISOR">主管席</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">调度优先级 (数值越小越先分发)</label>
              <input v-model.number="newAgentPriority" type="number" min="0" max="100" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showAddAgentModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmCreateAgent" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            确认创建并入组
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 5: 🔗 绑定已有坐席 ==================== -->
    <div v-if="showBindAgentModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-md w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div>
            <span class="font-bold text-sm text-slate-900">绑定已有坐席至技能组</span>
            <div class="text-[11px] text-slate-400">从系统现有坐席库中选择人员，关联至【{{ selectedDept }}】</div>
          </div>
          <button @click="showBindAgentModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        
        <div v-if="loadingSystemAgents" class="py-8 text-center text-xs text-slate-400">
          正在读取系统坐席档案...
        </div>
        <div v-else class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-bold">选择坐席人员 *</label>
            <select
              v-model="selectedBindAgentId"
              class="w-full border border-slate-200 rounded-xl px-3 py-2.5 text-xs text-slate-800 focus:outline-none focus:border-[#1677ff] bg-white font-medium"
            >
              <option :value="null" disabled>-- 请选择待绑定的坐席 --</option>
              <option
                v-for="agent in availableAgentsToBind"
                :key="agent.id"
                :value="agent.id"
              >
                {{ agent.agentName || agent.realName }} (工号: {{ agent.workNo }}) - 手机: {{ agent.phoneNumber || agent.phone || '无' }}
              </option>
            </select>
            <div v-if="availableAgentsToBind.length === 0" class="text-[11px] text-amber-600 mt-1">
              提示：系统中暂无未入组的坐席，可通过「新增坐席」录入新员工。
            </div>
          </div>

          <div class="grid grid-cols-2 gap-3">
            <div>
              <label class="block text-slate-700 mb-1 font-bold">组内身份</label>
              <select v-model="bindMemberRole" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
                <option value="MEMBER">👤 普通坐席</option>
                <option value="LEADER">👑 班长席 / 组长</option>
              </select>
            </div>
            <div>
              <label class="block text-slate-700 mb-1 font-bold">调度优先级</label>
              <input v-model.number="bindPriority" type="number" min="0" max="100" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
            </div>
          </div>
        </div>

        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showBindAgentModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button
            :disabled="!selectedBindAgentId"
            @click="handleConfirmBindAgent"
            class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 disabled:opacity-40 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs"
          >
            确认绑定入组
          </button>
        </div>
      </div>
    </div>

    <!-- ==================== 弹窗 6: 修改组员在组内的身份与优先级 ==================== -->
    <div v-if="showEditMemberModal" class="fixed inset-0 bg-slate-900/40 backdrop-blur-xs flex items-center justify-center z-50 p-4">
      <div class="bg-white rounded-2xl p-6 max-w-sm w-full shadow-2xl border border-slate-100 space-y-4">
        <div class="flex justify-between items-center pb-2 border-b border-slate-100">
          <div>
            <span class="font-bold text-sm text-slate-900">修改组员配置</span>
            <div class="text-[11px] text-slate-400">坐席: {{ editingMember?.agentName }} ({{ editingMember?.workNo }})</div>
          </div>
          <button @click="showEditMemberModal = false" class="text-slate-400 hover:text-slate-600 cursor-pointer text-base">✕</button>
        </div>
        <div class="space-y-3 text-xs">
          <div>
            <label class="block text-slate-700 mb-1 font-bold">组内身份</label>
            <select v-model="editMemberRole" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs text-slate-800 focus:outline-none bg-white">
              <option value="MEMBER">👤 普通坐席</option>
              <option value="LEADER">👑 班长席 / 组长</option>
            </select>
          </div>
          <div>
            <label class="block text-slate-700 mb-1 font-bold">调度优先级 (数值越小优先级越高)</label>
            <input v-model.number="editMemberPriority" type="number" min="0" max="100" class="w-full border border-slate-200 rounded-xl px-3 py-2 text-xs font-mono text-slate-800 focus:outline-none focus:border-[#1677ff]" />
          </div>
        </div>
        <div class="flex justify-end gap-2.5 pt-2">
          <button @click="showEditMemberModal = false" class="px-4 py-2 bg-slate-100 hover:bg-slate-200 text-slate-600 rounded-xl text-xs font-bold cursor-pointer">
            取消
          </button>
          <button @click="handleConfirmEditMember" class="px-5 py-2 bg-[#1677ff] hover:bg-blue-600 text-white rounded-xl text-xs font-bold cursor-pointer shadow-xs">
            保存修改
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.custom-scrollbar::-webkit-scrollbar {
  width: 4px;
}
.custom-scrollbar::-webkit-scrollbar-thumb {
  background-color: #cbd5e1;
  border-radius: 9999px;
}
</style>
