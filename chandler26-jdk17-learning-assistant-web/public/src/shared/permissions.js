/**
 * 权限定义与统一权限判断。
 *
 * 与后端 LearningPermission.SYSTEM_ADMIN ("system:admin") 对齐，
 * 避免在前端业务层硬编码判断 roleCode === 'ADMIN'。
 */

export const LearningPermission = Object.freeze({
  /** 系统管理与运维配置权限 */
  SYSTEM_ADMIN: 'system:admin',
})

/**
 * 判断当前状态/用户是否拥有指定权限。
 * @param {{ preview?: boolean, user?: { permissions?: string[], roleCode?: string } } | null} state
 * @param {string} permission
 * @returns {boolean}
 */
export function hasPermission(state, permission) {
  if (!state) return false
  if (state.preview) return true
  const user = state.user
  if (!user) return false

  // 1. 若后端返回了显式 permissions 权限数组，优先判断权限列表
  if (Array.isArray(user.permissions)) {
    return user.permissions.includes(permission)
  }

  // 2. 向下兼容：若尚未配置 permissions 数组，基于管理员角色推导
  if (permission === LearningPermission.SYSTEM_ADMIN) {
    return user.roleCode === 'ADMIN'
  }

  return false
}

/**
 * 判断是否具备系统管理中心访问权限。
 * @param {{ preview?: boolean, user?: { permissions?: string[], roleCode?: string } } | null} state
 * @returns {boolean}
 */
export function canAccessSystemAdmin(state) {
  return hasPermission(state, LearningPermission.SYSTEM_ADMIN)
}

/**
 * 判断是否具备公共词本与词书目录管理权限。
 * @param {{ preview?: boolean, user?: { permissions?: string[], roleCode?: string } } | null} state
 * @returns {boolean}
 */
export function canManageCatalogs(state) {
  return hasPermission(state, LearningPermission.SYSTEM_ADMIN)
}

/**
 * 判断是否具备全量模型配置管理权限。
 * @param {{ preview?: boolean, user?: { permissions?: string[], roleCode?: string } } | null} state
 * @returns {boolean}
 */
export function canManageAiModels(state) {
  return hasPermission(state, LearningPermission.SYSTEM_ADMIN)
}
