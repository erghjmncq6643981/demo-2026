import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createSystemManagementFeature } from '../../public/src/features/system/system-management.js'

describe('system-management feature tab switching and data loading', () => {
  let state
  let elements
  let ctx
  let systemManagement

  beforeEach(() => {
    document.body.innerHTML = `
      <section id="systemAdminView" class="view">
        <div class="system-tabs">
          <button class="system-tab active" data-system-tab="adminUserPanel">用户中心</button>
          <button class="system-tab" data-system-tab="adminVocabularyPanel">公共词本</button>
          <button class="system-tab" data-system-tab="aiTaskPanel">AI 任务</button>
          <button class="system-tab" data-system-tab="modelManagePanel">模型管理</button>
          <button class="system-tab" data-system-tab="agentManagePanel">Agent 与模板</button>
          <button class="system-tab" data-system-tab="systemJobPanel">定时任务</button>
          <button class="system-tab" data-system-tab="systemLogPanel">系统日志</button>
        </div>
        <div id="systemManagedPanels"></div>
        <section class="system-section active" id="adminUserPanel">
          <tbody id="adminUserRows"></tbody>
          <span id="adminUserSummary"></span>
          <span id="adminUserPageInfo"></span>
        </section>
        <section class="system-section" id="adminVocabularyPanel">
          <div id="sceneImportList" class="scene-import-list empty">暂无公共词本管理记录</div>
          <span id="vocabularyImportHistoryPageInfo">第 1 页</span>
        </section>
        <section class="system-section" id="aiTaskPanel"></section>
        <section class="system-section" id="modelManagePanel"></section>
        <section class="system-section" id="agentManagePanel"></section>
        <section class="system-section" id="systemJobPanel"></section>
        <section class="system-section" id="systemLogPanel"></section>
      </section>
    `

    state = {
      preview: false,
      user: { roleCode: 'ADMIN' },
      activeView: 'systemAdminView',
      activeSystemTab: 'adminUserPanel',
      adminUsers: [],
      vocabularyImports: [],
    }

    elements = {
      systemAdminView: document.getElementById('systemAdminView'),
      systemManagedPanels: document.getElementById('systemManagedPanels'),
      adminUserPanel: document.getElementById('adminUserPanel'),
      adminVocabularyPanel: document.getElementById('adminVocabularyPanel'),
      sceneImportList: document.getElementById('sceneImportList'),
      adminUserRows: document.getElementById('adminUserRows'),
      adminUserSummary: document.getElementById('adminUserSummary'),
      adminUserPageInfo: document.getElementById('adminUserPageInfo'),
    }

    ctx = {
      state,
      elements,
      request: vi.fn(),
      setLoading: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmDelete: vi.fn(),
      loadAiTasks: vi.fn(),
      reloadVocabularyImports: vi.fn(),
      loadModelConfigs: vi.fn(),
      loadAgents: vi.fn(),
      loadPromptTemplates: vi.fn(),
      loadSystemLogs: vi.fn(),
    }

    systemManagement = createSystemManagementFeature(ctx)
  })

  it('triggers reloadVocabularyImports when switching to adminVocabularyPanel', () => {
    systemManagement.renderSystemTab('adminVocabularyPanel')

    expect(ctx.reloadVocabularyImports).toHaveBeenCalledTimes(1)
    expect(state.activeSystemTab).toBe('adminVocabularyPanel')

    const vocabTabBtn = document.querySelector('[data-system-tab="adminVocabularyPanel"]')
    expect(vocabTabBtn.classList.contains('active')).toBe(true)

    const vocabPanel = document.getElementById('adminVocabularyPanel')
    expect(vocabPanel.classList.contains('active')).toBe(true)
  })

  it('triggers loadModelConfigs when switching to modelManagePanel', () => {
    systemManagement.renderSystemTab('modelManagePanel')

    expect(ctx.loadModelConfigs).toHaveBeenCalledTimes(1)
    expect(state.activeSystemTab).toBe('modelManagePanel')
  })

  it('triggers loadAgents and loadPromptTemplates when switching to agentManagePanel', () => {
    systemManagement.renderSystemTab('agentManagePanel')

    expect(ctx.loadAgents).toHaveBeenCalledTimes(1)
    expect(ctx.loadPromptTemplates).toHaveBeenCalledTimes(1)
    expect(state.activeSystemTab).toBe('agentManagePanel')
  })

  it('triggers loadSystemLogs when switching to systemLogPanel', () => {
    systemManagement.renderSystemTab('systemLogPanel')

    expect(ctx.loadSystemLogs).toHaveBeenCalledTimes(1)
    expect(state.activeSystemTab).toBe('systemLogPanel')
  })

  it('triggers loadAiTasks with { all: true } when switching to aiTaskPanel', () => {
    systemManagement.renderSystemTab('aiTaskPanel')

    expect(ctx.loadAiTasks).toHaveBeenCalledWith({ all: true })
    expect(state.activeSystemTab).toBe('aiTaskPanel')
  })
})
