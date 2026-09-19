import type { AgentGroupVO } from '../../../api/agentApi';
import type { OrgNode } from '../../../views/OrgTreeItem.vue';

/** Build a hierarchy independent of input ordering while preserving opaque IDs. */
export function buildTree(groups: AgentGroupVO[]): OrgNode[] {
  const byId = new Map(groups.map(group => [group.id, group]));
  const levels = new Map<string, number>();
  function level(id: string, visiting = new Set<string>()): number {
    if (levels.has(id)) return levels.get(id)!;
    if (visiting.has(id)) throw new Error('组织架构存在循环父子关系');
    visiting.add(id);
    const parentId = byId.get(id)?.parentId;
    const result = parentId && byId.has(parentId) ? level(parentId, visiting) + 1 : 0;
    visiting.delete(id);
    levels.set(id, result);
    return result;
  }
  const nodes = new Map<string, OrgNode>();
  for (const group of groups) {
    nodes.set(group.id, {
      id: group.id, name: group.groupName, level: level(group.id),
      icon: !group.parentId || group.groupType === 'COMPANY' ? 'company'
        : group.groupType === 'CENTER' ? 'center' : 'group',
      count: group.memberCount ?? 0, expanded: true, children: []
    });
  }
  const roots: OrgNode[] = [];
  for (const group of groups) {
    const node = nodes.get(group.id)!;
    const parent = group.parentId ? nodes.get(group.parentId) : undefined;
    if (parent) {
      parent.children!.push(node);
      if (parent.icon === 'group') parent.icon = 'center';
    } else roots.push(node);
  }
  return roots;
}
