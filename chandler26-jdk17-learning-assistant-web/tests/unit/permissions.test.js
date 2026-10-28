import { describe, expect, it } from 'vitest'
import {
  LearningPermission,
  canAccessSystemAdmin,
  canManageAiModels,
  canManageCatalogs,
  hasPermission,
} from '../../public/src/shared/permissions.js'

describe('Permission Semantic Governance (permissions.js)', () => {
  it('returns false when state or user is null', () => {
    expect(hasPermission(null, LearningPermission.SYSTEM_ADMIN)).toBe(false)
    expect(hasPermission({}, LearningPermission.SYSTEM_ADMIN)).toBe(false)
    expect(hasPermission({ user: null }, LearningPermission.SYSTEM_ADMIN)).toBe(false)
  })

  it('allows access in preview mode', () => {
    expect(hasPermission({ preview: true }, LearningPermission.SYSTEM_ADMIN)).toBe(true)
    expect(canAccessSystemAdmin({ preview: true })).toBe(true)
    expect(canManageCatalogs({ preview: true })).toBe(true)
    expect(canManageAiModels({ preview: true })).toBe(true)
  })

  it('evaluates permissions via explicit permissions array when provided', () => {
    const userWithAdmin = { permissions: ['system:admin'], roleCode: 'USER' }
    expect(hasPermission({ user: userWithAdmin }, LearningPermission.SYSTEM_ADMIN)).toBe(true)

    const userWithoutAdmin = { permissions: ['vocabulary:read'], roleCode: 'ADMIN' }
    expect(hasPermission({ user: userWithoutAdmin }, LearningPermission.SYSTEM_ADMIN)).toBe(false)
  })

  it('gracefully falls back to roleCode for backward compatibility', () => {
    const adminUser = { roleCode: 'ADMIN' }
    expect(hasPermission({ user: adminUser }, LearningPermission.SYSTEM_ADMIN)).toBe(true)
    expect(canAccessSystemAdmin({ user: adminUser })).toBe(true)
    expect(canManageCatalogs({ user: adminUser })).toBe(true)
    expect(canManageAiModels({ user: adminUser })).toBe(true)

    const normalUser = { roleCode: 'USER' }
    expect(hasPermission({ user: normalUser }, LearningPermission.SYSTEM_ADMIN)).toBe(false)
    expect(canAccessSystemAdmin({ user: normalUser })).toBe(false)
  })
})
