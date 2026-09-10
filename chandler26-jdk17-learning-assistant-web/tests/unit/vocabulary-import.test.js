import { describe, expect, it, vi } from 'vitest'
import { createVocabularyImportWorkflow } from '../../public/src/features/learning/scene-plan/vocabulary-import.js'

describe('Vocabulary Import Workflow', () => {
  it('passes warningOnly=true when checkbox is checked in real mode', async () => {
    const warningOnlyCheckbox = document.createElement('input')
    warningOnlyCheckbox.type = 'checkbox'
    warningOnlyCheckbox.checked = false

    const keywordInput = document.createElement('input')
    keywordInput.value = ''

    const modal = document.createElement('div')
    const reviewSection = document.createElement('div')
    const thead = document.createElement('thead')
    const rows = document.createElement('tbody')
    const pageInfo = document.createElement('span')
    const prevBtn = document.createElement('button')
    const nextBtn = document.createElement('button')
    const summary = document.createElement('span')

    const elements = {
      vocabularyImportModal: modal,
      vocabularyReviewSection: reviewSection,
      vocabularyWarningOnly: warningOnlyCheckbox,
      vocabularyWarningOnlyLabel: document.createElement('label'),
      vocabularyImportKeyword: keywordInput,
      vocabularyImportSummary: summary,
      vocabularyWarningSummary: document.createElement('span'),
      vocabularyReviewThead: thead,
      vocabularyReviewRows: rows,
      vocabularyPageInfo: pageInfo,
      vocabularyPrevPageBtn: prevBtn,
      vocabularyNextPageBtn: nextBtn,
      vocabularyImportName: document.createElement('input'),
      vocabularyImportSourceType: document.createElement('select'),
      vocabularyImportPurpose: document.createElement('input'),
      vocabularyImportFile: document.createElement('input'),
      startVocabularyImportBtn: document.createElement('button'),
      saveVocabularyImportMetadataBtn: document.createElement('button'),
      publishVocabularyImportBtn: document.createElement('button'),
      vocabularyBatchConfirmBtn: document.createElement('button'),
    }

    const state = {
      preview: false,
      token: 'test-token',
      user: { roleCode: 'ADMIN' },
      currentVocabularyImport: null,
      vocabularyImportPage: 1,
      vocabularyImportPageSize: 100,
    }

    const getImportMock = vi.fn().mockImplementation((jobId, params) => {
      const isWarningOnly = params.get('warningOnly') === 'true'
      return Promise.resolve({
        jobId,
        catalogId: '10',
        catalogVersionId: '20',
        catalogName: '小升初440',
        status: 'reviewing',
        totalCount: 440,
        warningCount: 0,
        reviewedWarningCount: 0,
        pendingWarningCount: 0,
        page: Number(params.get('page') || 1),
        pageSize: 100,
        filteredTotal: isWarningOnly ? 0 : 440,
        items: isWarningOnly ? [] : [{ sourceOrder: 1, originalTerm: 'answer', approvedTerm: 'answer', suspicious: false }],
      })
    })

    const catalogApi = {
      getImport: getImportMock,
      listTags: vi.fn().mockResolvedValue([]),
    }

    const workflow = createVocabularyImportWorkflow({
      state,
      elements,
      catalogApi,
      renderSourceOptions: vi.fn(),
      setButtonLoading: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmAction: vi.fn(),
      escapeHtml: (s) => s,
      sameId: (a, b) => String(a) === String(b),
      loadWordbooks: vi.fn(),
    })

    // 1. Open review
    await workflow.openReview('job-123')
    expect(getImportMock).toHaveBeenCalledTimes(1)
    expect(getImportMock.mock.calls[0][1].get('warningOnly')).toBe('false')
    expect(pageInfo.textContent).toContain('440 条')
    expect(rows.innerHTML).toContain('answer')

    // 2. Check the checkbox and trigger loadReview
    warningOnlyCheckbox.checked = true
    await workflow.loadReview()

    expect(getImportMock).toHaveBeenCalledTimes(2)
    expect(getImportMock.mock.calls[1][1].get('warningOnly')).toBe('true')
    expect(pageInfo.textContent).toContain('0 条')
    expect(rows.innerHTML).toContain('没有符合条件的词条')
  })

  it('triggers review reload when checkbox fires change event via bindAppEvents', async () => {
    const warningOnlyCheckbox = document.createElement('input')
    warningOnlyCheckbox.type = 'checkbox'
    warningOnlyCheckbox.checked = false

    const elements = {
      vocabularyWarningOnly: warningOnlyCheckbox,
    }

    const loadImportReview = vi.fn()
    const { bindAppEvents } = await import('../../public/src/app/events.js')
    bindAppEvents({ elements, loadImportReview })

    warningOnlyCheckbox.checked = true
    warningOnlyCheckbox.dispatchEvent(new Event('change'))

    expect(loadImportReview).toHaveBeenCalledTimes(1)
  })

  it('exposes loadImportReview and openImportReview on scenePlanFeature', async () => {
    const { createScenePlanFeature } = await import('../../public/src/features/learning/scene-plan/scene-plan.js')
    const { createFeatureFacade } = await import('../../public/src/app/facade.js')

    let feature
    const scenePlan = createFeatureFacade(() => feature)

    feature = createScenePlanFeature({
      state: { preview: true },
      elements: {},
      request: vi.fn(),
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmAction: vi.fn(),
      escapeHtml: (s) => s,
      sameId: (a, b) => a === b,
      speakSentence: vi.fn(),
      loadWordbooks: vi.fn(),
    })

    expect(typeof scenePlan.loadImportReview).toBe('function')
    expect(typeof scenePlan.openImportReview).toBe('function')
    expect(() => scenePlan.loadImportReview).not.toThrow()
    expect(() => scenePlan.loadImportReview()).not.toThrow()
  })

  it('controls analysis action in reviewing state based on pending warning count', async () => {
    const analysisAction = document.createElement('div')
    const analysisStatus = document.createElement('span')
    const triggerAnalysisBtn = document.createElement('button')
    const publishBtn = document.createElement('button')

    const elements = {
      vocabularyImportModal: document.createElement('div'),
      vocabularyReviewSection: document.createElement('div'),
      vocabularyWarningOnly: document.createElement('input'),
      vocabularyWarningOnlyLabel: document.createElement('label'),
      vocabularyImportKeyword: document.createElement('input'),
      vocabularyImportSummary: document.createElement('span'),
      vocabularyWarningSummary: document.createElement('span'),
      vocabularyReviewThead: document.createElement('thead'),
      vocabularyReviewRows: document.createElement('tbody'),
      vocabularyPageInfo: document.createElement('span'),
      vocabularyPrevPageBtn: document.createElement('button'),
      vocabularyNextPageBtn: document.createElement('button'),
      vocabularyImportName: document.createElement('input'),
      vocabularyImportSourceType: document.createElement('select'),
      vocabularyImportPurpose: document.createElement('input'),
      vocabularyImportFile: document.createElement('input'),
      vocabularyAnalysisAction: analysisAction,
      vocabularyAnalysisStatus: analysisStatus,
      triggerVocabularyAnalysisBtn: triggerAnalysisBtn,
      publishVocabularyImportBtn: publishBtn,
      startVocabularyImportBtn: document.createElement('button'),
      saveVocabularyImportMetadataBtn: document.createElement('button'),
    }

    const state = {
      preview: false,
      user: { roleCode: 'ADMIN' },
      currentVocabularyImport: null,
      currentVocabularyAnalysis: null,
      vocabularyImportPage: 1,
      vocabularyImportPageSize: 100,
    }

    const catalogApi = {
      getImport: vi.fn().mockResolvedValue({
        jobId: 'job-1',
        catalogId: '10',
        catalogVersionId: '20',
        catalogName: '测试词表',
        status: 'reviewing',
        totalCount: 100,
        warningCount: 1,
        reviewedWarningCount: 0,
        pendingWarningCount: 1,
        page: 1,
        pageSize: 100,
        filteredTotal: 100,
        items: [],
      }),
      getAnalysis: vi.fn().mockResolvedValue({
        catalogId: '10',
        catalogVersionId: '20',
        status: 'not_started',
        publishedCount: 100,
        analyzedCount: 0,
        unanalyzedCount: 100,
        canTrigger: true,
      }),
      triggerAnalysis: vi.fn().mockResolvedValue({
        catalogId: '10',
        catalogVersionId: '20',
        status: 'pending',
        publishedCount: 100,
        analyzedCount: 0,
        unanalyzedCount: 100,
        canTrigger: false,
      }),
      publish: vi.fn().mockResolvedValue({
        jobId: 'job-1',
        catalogId: '10',
        catalogVersionId: '20',
        catalogName: '测试词表',
        status: 'published',
        totalCount: 100,
      }),
      listTags: vi.fn().mockResolvedValue([]),
    }

    const confirmAction = vi.fn().mockResolvedValue(true)
    const toast = vi.fn()

    const workflow = createVocabularyImportWorkflow({
      state,
      elements,
      catalogApi,
      renderSourceOptions: vi.fn(),
      setButtonLoading: vi.fn(),
      toast,
      logEvent: vi.fn(),
      confirmAction,
      escapeHtml: (s) => s,
      sameId: (a, b) => String(a) === String(b),
    })

    // 1. With pending warnings: analysis panel is shown, but trigger button is disabled with warning prompt
    await workflow.loadReview('job-1')
    expect(analysisAction.classList.contains('hidden')).toBe(false)
    expect(triggerAnalysisBtn.disabled).toBe(true)
    expect(triggerAnalysisBtn.textContent).toBe('待确认断词')
    expect(analysisStatus.textContent).toContain('请先确认上方 1 个疑似断词')

    // 2. Clear pending warnings: trigger button is enabled
    catalogApi.getImport.mockResolvedValueOnce({
      jobId: 'job-1',
      catalogId: '10',
      catalogVersionId: '20',
      catalogName: '测试词表',
      status: 'reviewing',
      totalCount: 100,
      warningCount: 1,
      reviewedWarningCount: 1,
      pendingWarningCount: 0,
      page: 1,
      pageSize: 100,
      filteredTotal: 100,
      items: [],
    })
    await workflow.loadReview('job-1')
    expect(triggerAnalysisBtn.disabled).toBe(false)
    expect(triggerAnalysisBtn.textContent).toContain('分析剩余 100 词')

    // 3. Trigger analysis in reviewing state
    await workflow.triggerAnalysis()
    expect(confirmAction).toHaveBeenCalledWith(expect.objectContaining({
      title: '分析公共词本',
    }))
    expect(catalogApi.triggerAnalysis).toHaveBeenCalledWith('20', expect.any(Object))

    // 4. Publishing while unanalyzed prompts soft warning
    await workflow.publish()
    expect(confirmAction).toHaveBeenCalledWith(expect.objectContaining({
      title: '发布未完全分析的词本',
      message: expect.stringContaining('当前词本尚未完成 AI 语义关联分析'),
    }))
    expect(catalogApi.publish).toHaveBeenCalledWith('job-1')
  })
})
