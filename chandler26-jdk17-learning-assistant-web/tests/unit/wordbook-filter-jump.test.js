import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createWordbookProfileFeature } from '../../public/src/features/vocabulary/wordbook.js'
import { createWordbookEntryListFeature } from '../../public/src/features/vocabulary/wordbook-entry-list.js'

describe('Wordbook Card Status Filter & Page Jump', () => {
  let state
  let elements
  let ctx
  let requestMock
  let toastMock

  beforeEach(() => {
    document.body.innerHTML = `
      <select id="wordStatusFilter">
        <option value="">全部状态</option>
        <option value="familiar">熟悉</option>
      </select>
      <select id="wordCardStatusFilter">
        <option value="">全部词卡</option>
        <option value="need_card">⚡ 缺少词卡</option>
        <option value="ready">已就绪</option>
        <option value="failed">生成失败</option>
      </select>
      <input id="wordPrefixInput" value="" />
      <button id="wordbookBatchGenerateCardBtn">补全词卡</button>
      <span id="wordbookMissingCardBadge" class="hidden">0</span>
      <span id="wordbookCountSummary">共 0 个单词</span>
      <button id="wordbookPrevBtn">上一页</button>
      <span id="wordbookPageInfo">第 1 页</span>
      <button id="wordbookNextBtn">下一页</button>
      <input id="wordbookJumpPageInput" />
      <button id="wordbookJumpPageBtn">跳转</button>
      <table>
        <tbody id="wordbookEntryList"></tbody>
      </table>
    `

    elements = {
      wordStatusFilter: document.getElementById('wordStatusFilter'),
      wordCardStatusFilter: document.getElementById('wordCardStatusFilter'),
      wordPrefixInput: document.getElementById('wordPrefixInput'),
      wordbookBatchGenerateCardBtn: document.getElementById('wordbookBatchGenerateCardBtn'),
      wordbookMissingCardBadge: document.getElementById('wordbookMissingCardBadge'),
      wordbookCountSummary: document.getElementById('wordbookCountSummary'),
      wordbookPrevBtn: document.getElementById('wordbookPrevBtn'),
      wordbookNextBtn: document.getElementById('wordbookNextBtn'),
      wordbookPageInfo: document.getElementById('wordbookPageInfo'),
      wordbookJumpPageInput: document.getElementById('wordbookJumpPageInput'),
      wordbookJumpPageBtn: document.getElementById('wordbookJumpPageBtn'),
      wordbookEntryList: document.getElementById('wordbookEntryList'),
    }

    state = {
      preview: false,
      token: 'test-token',
      currentWordbookId: 'wb-001',
      wordbookPage: 1,
      wordbookPageSize: 30,
      wordbookTotal: 4509,
      wordbookEntries: [],
      selectedEntry: null,
    }

    requestMock = vi.fn().mockResolvedValue({
      total: 4509,
      page: 1,
      pageSize: 30,
      items: [
        { id: '1', term: 'apple', cardStatus: 'ready' },
        { id: '2', term: 'banana', cardStatus: 'missing' },
      ],
    })

    toastMock = vi.fn()

    ctx = {
      state,
      elements,
      request: requestMock,
      toast: toastMock,
      sameId: (a, b) => String(a) === String(b),
      normalizeDefinitions: () => [],
      entryMatchesKeyword: (entry, kw) => (entry.term || '').toLowerCase().includes(kw),
      renderProfileMetrics: vi.fn(),
      loadDueReviewsFromCtx: vi.fn(),
      loadActivity: vi.fn(),
      confirmAction: vi.fn(),
      logEvent: vi.fn(),
    }
  })

  it('passes cardStatus in query parameters when filter is set', async () => {
    const feature = createWordbookProfileFeature(ctx)
    elements.wordCardStatusFilter.value = 'need_card'

    await feature.loadWordbookEntries()

    expect(requestMock).toHaveBeenCalled()
    const url = requestMock.mock.calls[0][0]
    expect(url).toContain('cardStatus=need_card')
    expect(url).toContain('page=1')
  })

  it('jumps to specified valid page and clamps to maxPage', async () => {
    const feature = createWordbookProfileFeature(ctx)
    // 4509 / 30 = 151 pages
    feature.jumpToWordbookPage(15)
    expect(state.wordbookPage).toBe(15)

    // jump beyond maxPage
    feature.jumpToWordbookPage(999)
    expect(state.wordbookPage).toBe(151)

    // ignore invalid negative page
    feature.jumpToWordbookPage(-1)
    expect(state.wordbookPage).toBe(151)
  })

  it('prompts user to switch to need_card filter when all cards on current page are ready', async () => {
    state.wordbookEntries = [
      { id: '1', term: 'apple', cardStatus: 'ready' },
      { id: '2', term: 'banana', cardStatus: 'ready' },
    ]
    elements.wordCardStatusFilter.value = ''
    ctx.confirmAction = vi.fn().mockResolvedValue(true)

    const feature = createWordbookProfileFeature(ctx)
    await feature.batchGenerateMissingCards()

    expect(ctx.confirmAction).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '快捷筛选缺少词卡',
        acceptText: '切换到缺少词卡',
      })
    )
    expect(elements.wordCardStatusFilter.value).toBe('need_card')
    expect(state.wordbookPage).toBe(1)
  })

  it('filters entries by need_card in preview mode', () => {
    state.preview = true
    state.wordbookEntries = [
      { id: '1', term: 'apple', cardStatus: 'ready' },
      { id: '2', term: 'banana', cardStatus: 'missing' },
      { id: '3', term: 'cherry', cardStatus: 'not_required' },
      { id: '4', term: 'date', cardStatus: 'failed' },
    ]
    elements.wordCardStatusFilter.value = 'need_card'

    const listFeature = createWordbookEntryListFeature({
      ...ctx,
      renderWordbookFocus: vi.fn(),
      renderNotes: vi.fn(),
      openEntryStatusModal: vi.fn(),
      generateEntryCardWithConfirm: vi.fn(),
      deleteWordbookEntry: vi.fn(),
      openEntryTransferModal: vi.fn(),
      loadWordbookEntryDetail: vi.fn(),
      selectWordbookEntry: vi.fn(),
    })

    listFeature.render()

    const rows = elements.wordbookEntryList.querySelectorAll('tr.wordbook-row')
    expect(rows.length).toBe(3) // banana (missing), cherry (not_required), date (failed)
  })
})
