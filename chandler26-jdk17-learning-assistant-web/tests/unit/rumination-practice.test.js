import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createRuminationMaterialView } from '../../public/src/features/rumination/rumination-material-view.js'
import { createRuminationPracticeView } from '../../public/src/features/rumination/rumination-practice-view.js'
import { createQuickLookupFeature } from '../../public/src/features/learning/study/quick-lookup.js'

vi.mock('/src/shared/audio.js', () => ({
  playUiTone: vi.fn(),
}))

import { playUiTone } from '/src/shared/audio.js'

function createPracticeDomElements() {
  document.body.innerHTML = `
    <div class="celebration-layer" id="celebrationLayer"></div>
    <span id="materialsNavBadge"></span>
    <span id="archiveCountBadge"></span>
    <span id="archiveCountInfo"></span>
    <div id="materialsEmptyState"></div>
    <div id="materialsArchiveList"></div>
    <article id="ruminationArticleCard" class="hidden">
      <h3 id="ruminationArticleTitle"></h3>
      <div id="ruminationArticleBody"></div>
      <details id="ruminationTranslationDetails">
        <summary>查看中文对照</summary>
        <div id="ruminationTranslationBox"></div>
      </details>
    </article>
    <section id="ruminationPracticeModal" class="modal-backdrop hidden">
      <div class="config-modal rumination-practice-modal">
        <section id="ruminationPracticeCard" class="hidden"></section>
      </div>
    </section>
    <section id="ruminationProbesCard" class="hidden">
      <span id="ruminationProbesSubtitle"></span>
      <div id="probesEmptyState"></div>
      <div id="ruminationProbeList"></div>
    </section>
  `
  const $ = (id) => document.getElementById(id)
  return {
    celebrationLayer: $('celebrationLayer'),
    materialsNavBadge: $('materialsNavBadge'),
    archiveCountBadge: $('archiveCountBadge'),
    archiveCountInfo: $('archiveCountInfo'),
    materialsEmptyState: $('materialsEmptyState'),
    materialsArchiveList: $('materialsArchiveList'),
    articleCard: $('ruminationArticleCard'),
    articleTitle: $('ruminationArticleTitle'),
    articleBody: $('ruminationArticleBody'),
    translationDetails: $('ruminationTranslationDetails'),
    translationBox: $('ruminationTranslationBox'),
    practiceModal: $('ruminationPracticeModal'),
    practiceCard: $('ruminationPracticeCard'),
    probesCard: $('ruminationProbesCard'),
    probesSubtitle: $('ruminationProbesSubtitle'),
    probesEmptyState: $('probesEmptyState'),
    probeList: $('ruminationProbeList'),
  }
}

function buildSampleMaterial() {
  return {
    id: '910001',
    wordbookId: '2001',
    scenarioCode: 'produce_kitchen',
    title: 'Kitchen Preparation Flow',
    scenarioSummary: '生鲜采购与厨房料理场景短文',
    generationSource: 'ai',
    articleContent:
      'She started peeling fresh carrots quickly. Serve warm mashed potato tonight.\n\nClean towels sat by the sink. Later we boiled sweet corn. Finally he chopped crispy cabbage.',
    translationContent: '她迅速开始削新鲜胡萝卜。\n\n干净的毛巾放在水槽旁。',
    targetWords: ['peel', 'mashed potato', 'boil', 'chop'],
    practiceSummary: {
      totalTargetWords: 4,
      independentlySettledWords: 0,
      copyCompletedSentences: 0,
      totalTargetSentences: 4,
      recommendedCount: 3,
      clozePassedSentences: 0,
    },
    practiceSentences: [
      {
        sentenceIndex: 0,
        paragraphIndex: 0,
        sentenceText: 'She started peeling fresh carrots quickly.',
        primaryTargetWord: 'peel',
        recommended: true,
        copyCount: 0,
        clozeIndependentPassed: false,
        targetSpans: [
          {
            targetWord: 'peel',
            surfaceForm: 'peeling',
            startOffset: 12,
            endOffset: 19,
            copyCount: 0,
            clozeAttemptCount: 0,
            clozeIndependentPassed: false,
          },
        ],
      },
      {
        sentenceIndex: 1,
        paragraphIndex: 0,
        sentenceText: 'Serve warm mashed potato tonight.',
        primaryTargetWord: 'mashed potato',
        recommended: true,
        copyCount: 0,
        clozeIndependentPassed: false,
        targetSpans: [
          {
            targetWord: 'mashed potato',
            surfaceForm: 'mashed potato',
            startOffset: 11,
            endOffset: 24,
            copyCount: 0,
            clozeAttemptCount: 0,
            clozeIndependentPassed: false,
          },
        ],
      },
      {
        sentenceIndex: 2,
        paragraphIndex: 1,
        sentenceText: 'Clean towels sat by the sink.',
        primaryTargetWord: null,
        recommended: false,
        copyCount: 0,
        clozeIndependentPassed: false,
        targetSpans: [],
      },
      {
        sentenceIndex: 3,
        paragraphIndex: 1,
        sentenceText: 'Later we boiled sweet corn.',
        primaryTargetWord: 'boil',
        recommended: true,
        copyCount: 0,
        clozeIndependentPassed: false,
        targetSpans: [
          {
            targetWord: 'boil',
            surfaceForm: 'boiled',
            startOffset: 9,
            endOffset: 15,
            copyCount: 0,
            clozeAttemptCount: 0,
            clozeIndependentPassed: false,
          },
        ],
      },
      {
        sentenceIndex: 4,
        paragraphIndex: 1,
        sentenceText: 'Finally he chopped crispy cabbage.',
        primaryTargetWord: 'chop',
        recommended: false,
        copyCount: 0,
        clozeIndependentPassed: false,
        targetSpans: [
          {
            targetWord: 'chop',
            surfaceForm: 'chopped',
            startOffset: 11,
            endOffset: 18,
            copyCount: 0,
            clozeAttemptCount: 0,
            clozeIndependentPassed: false,
          },
        ],
      },
    ],
    probes: [
      {
        id: '5001',
        targetWord: 'chop',
        probeTypeName: '动作搭配辨析',
        generationSource: 'ai',
        question: 'Which verb fits finely cutting cabbage?',
        hint: '注意厨房刀工动作词。',
        options: ['chop', 'steam', 'boil'],
        correctAnswer: 'chop',
      },
      {
        id: '5002',
        targetWord: 'peel',
        probeTypeName: '动作搭配辨析',
        generationSource: 'ai',
        question: 'Which action removes the outer skin?',
        hint: '留意去皮动作。',
        options: ['peel', 'bake', 'fry'],
        correctAnswer: 'peel',
      },
    ],
  }
}

async function flushMicrotasks() {
  await Promise.resolve()
  await Promise.resolve()
}

describe('知识反刍阅读后按句跟敲与目标词遮挡补全', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('在包含目标词的句子旁显示紧凑练习图标，隐藏底部巩固启动栏与冗余统计行，并在检查答完后出现错词优先巩固 CTA', async () => {
    const elements = createPracticeDomElements()
    const onPracticeSentence = vi.fn()
    const onStartRecommendedFromProbe = vi.fn()
    const mockApi = {
      submitRuminationProbe: vi.fn(async ({ targetWord, selectedAnswer }) => ({
        isCorrect: selectedAnswer === targetWord,
        correctAnswer: targetWord,
      })),
    }

    const material = buildSampleMaterial()
    const view = createRuminationMaterialView(elements, {
      api: mockApi,
      state: { currentWordbookId: '2001' },
      toast: vi.fn(),
      speakSentence: vi.fn(),
      onPracticeSentence,
      onStartRecommendedFromProbe,
    })

    view.displayMaterial(material)

    expect(document.getElementById('ruminationPracticeLaunchBar')).toBeNull()
    expect(document.getElementById('startRecommendedPracticeBtn')).toBeNull()

    const triggers = elements.articleBody.querySelectorAll('.sentence-practice-trigger')
    expect(triggers).toHaveLength(4)
    expect(triggers[0].textContent.trim()).toBe('⭐')
    expect(triggers[0].getAttribute('aria-label')).toContain('推荐练习')
    expect(triggers[0].textContent).not.toContain('推荐练习')
    expect(triggers[3].textContent.trim()).toBe('✍️')
    expect(triggers[3].textContent).not.toContain('练习这句')
    expect(elements.articleBody.querySelector('[data-sentence-index="2"] .sentence-practice-trigger')).toBeNull()

    triggers[0].click()
    expect(onPracticeSentence).toHaveBeenCalledWith(material, 0)

    // 先答错第 1 题（chop -> steam），再答对第 2 题（peel）
    const cards = elements.probeList.querySelectorAll('.probe-item-card')
    cards[0].querySelector('[data-option="steam"]').click()
    await flushMicrotasks()
    expect(elements.probeList.querySelector('#probePostPracticeCta')).toBeNull()

    cards[1].querySelector('[data-option="peel"]').click()
    await flushMicrotasks()

    const cta = elements.probeList.querySelector('#probePostPracticeCta')
    expect(cta).not.toBeNull()
    expect(cta.textContent).toContain('理解检测已完成')
    // 答错的 chop 所在第 4 句应被即时提升为推荐重点句，且推荐总数维持 <= 3
    expect(material.practiceSentences[4].recommended).toBe(true)
    expect(material.practiceSentences.filter((s) => s.recommended).length).toBeLessThanOrEqual(3)

    cta.querySelector('#startRecommendedFromProbeBtn').click()
    expect(onStartRecommendedFromProbe).toHaveBeenCalledWith(material)
  })

  it('完整走通「原句跟敲 -> 错因定位 -> 查看原句标记提示 -> 重试 -> 独立拼写通过 -> 本轮总结」并回刷文章状态', async () => {
    const elements = createPracticeDomElements()
    const material = buildSampleMaterial()
    // 控制推荐队列仅包含前两句，便于测试完整队列收官
    material.practiceSentences[3].recommended = false

    const submitSpy = vi.fn(async (payload) => {
      if (payload.practiceMode === 'copy_sentence') {
        return {
          practiceMode: 'copy_sentence',
          isCorrect: true,
          copyCount: 1,
          clozeAttemptCount: 0,
          clozeIndependentPassed: false,
          masterySettled: false,
          scheduleAdvanced: false,
          practiceSummary: {
            totalTargetWords: 4,
            independentlySettledWords: payload.sentenceIndex === 1 ? 1 : 0,
            copyCompletedSentences: payload.sentenceIndex === 0 ? 1 : 2,
            totalTargetSentences: 4,
            recommendedCount: 2,
            clozePassedSentences: payload.sentenceIndex === 1 ? 1 : 0,
          },
          feedbackMessage: '原句跟敲完成，已记入练习次数（不影响词条复习排期）',
        }
      }
      const expectedSurface = payload.sentenceIndex === 0 ? 'peeling' : 'mashed potato'
      const isCorrect = payload.userAnswer.toLowerCase() === expectedSurface
      const independent = isCorrect && !payload.usedHint && Number(payload.attemptCount) === 1
      return {
        practiceMode: 'cloze_recall',
        isCorrect,
        expectedSurfaceForm: expectedSurface,
        usedHint: Boolean(payload.usedHint),
        attemptCount: payload.attemptCount,
        copyCount: 1,
        clozeAttemptCount: payload.attemptCount,
        clozeIndependentPassed: independent,
        masterySettled: independent,
        scheduleAdvanced: false,
        practiceSummary: {
          totalTargetWords: 4,
          independentlySettledWords: independent ? 1 : 0,
          copyCompletedSentences: 2,
          totalTargetSentences: 4,
          recommendedCount: 2,
          clozePassedSentences: independent ? 1 : 0,
        },
        feedbackMessage: isCorrect
          ? independent
            ? '独立无提示一次拼写通过！已提升拼写掌握度'
            : '借助提示完成巩固（仅计入练习次数）'
          : '目标词拼写尚未完全匹配，请定位标红部位并听音频后重试',
      }
    })

    let practiceView = null
    const materialView = createRuminationMaterialView(elements, {
      api: { submitRuminationSentencePractice: submitSpy },
      state: { currentWordbookId: '2001' },
      toast: vi.fn(),
      speakSentence: vi.fn(),
      onStartRecommendedFromProbe: (mat) => practiceView.startRecommended(mat),
    })

    practiceView = createRuminationPracticeView(elements, {
      api: { submitRuminationSentencePractice: submitSpy },
      state: { currentWordbookId: '2001' },
      toast: vi.fn(),
      speakSentence: vi.fn(),
      onPracticeUpdated: (updatedMat) => materialView.refreshPracticeUi(updatedMat),
      onClose: () => materialView.refreshPracticeUi(),
    })

    materialView.displayMaterial(material)
    practiceView.startRecommended(material)

    expect(elements.articleCard.classList.contains('hidden')).toBe(false)
    expect(elements.practiceModal.classList.contains('hidden')).toBe(false)
    expect(elements.practiceCard.classList.contains('hidden')).toBe(false)
    expect(elements.practiceCard.querySelector('.rumination-practice-stepbar')).toBeNull()
    expect(elements.practiceCard.querySelector('#skipToClozeBtn')).toBeNull()

    // 第 1 句 Step 1：输入错字母触发原句错因定位，再补全正确句子自动提交 copy_sentence
    const copyInput = elements.practiceCard.querySelector('#ruminationCopyInput')
    expect(copyInput).not.toBeNull()
    copyInput.value = 'she strated '
    copyInput.dispatchEvent(new Event('input', { bubbles: true }))
    const copyErr = elements.practiceCard.querySelector('#ruminationCopyErrorLine')
    expect(copyErr.classList.contains('hidden')).toBe(false)
    expect(copyErr.textContent).toContain('started')

    copyInput.value = 'she started peeling fresh carrots quickly'
    copyInput.dispatchEvent(new Event('input', { bubbles: true }))
    await flushMicrotasks()

    expect(submitSpy).toHaveBeenCalledWith(
      expect.objectContaining({
        materialId: '910001',
        sentenceIndex: 0,
        targetWord: 'peel',
        practiceMode: 'copy_sentence',
      }),
    )
    expect(submitSpy.mock.calls[0][0].wrongKeyCount).toBeGreaterThanOrEqual(1)

    // 420ms 后切至 Step 2：目标词遮挡补全（不剧透原词根）
    vi.advanceTimersByTime(420)
    const clozeInput = elements.practiceCard.querySelector('#ruminationClozeInput')
    expect(clozeInput).not.toBeNull()
    expect(elements.practiceCard.querySelector('#ruminationClozeSlot').textContent).toContain('•')
    expect(elements.practiceCard.querySelector('#clozeHintNotice').textContent).not.toContain('peel')

    // 先填错一次 -> 显示字符 diff、不泄漏原词槽位并进入第 2 次尝试
    clozeInput.value = 'pealing'
    clozeInput.dispatchEvent(new Event('input', { bubbles: true }))
    expect(elements.practiceCard.querySelector('#ruminationClozeSlot').textContent).toBe('•••••••')
    elements.practiceCard.querySelector('#submitClozeBtn').click()
    await flushMicrotasks()

    const diffBox = elements.practiceCard.querySelector('#clozeErrorDiffBox')
    expect(diffBox).not.toBeNull()
    expect(diffBox.querySelector('.diff-seg.wrong').textContent).toBe('a')
    expect(diffBox.querySelector('.diff-seg.missing').textContent).toBe('e')
    expect(elements.practiceCard.querySelector('#clozeHintNotice').textContent).toContain('第 2 次尝试')

    // 使用 Tab 快捷键偷看原句，1200ms 后自动收回且保留 usedHint=true
    const nextClozeInput = elements.practiceCard.querySelector('#ruminationClozeInput')
    nextClozeInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(elements.practiceCard.querySelector('#clozeOriginalPeekBox').classList.contains('hidden')).toBe(false)
    vi.advanceTimersByTime(1200)
    expect(elements.practiceCard.querySelector('#clozeOriginalPeekBox').classList.contains('hidden')).toBe(true)

    // 填写正确的句子变体词形 "peeling" 提交，由于重试+查看提示，不计入独立掌握
    nextClozeInput.value = 'peeling'
    nextClozeInput.dispatchEvent(new Event('input', { bubbles: true }))
    nextClozeInput.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushMicrotasks()

    const clozeCall1 = submitSpy.mock.calls.find(
      ([req]) => req.sentenceIndex === 0 && req.practiceMode === 'cloze_recall' && req.userAnswer === 'peeling',
    )[0]
    expect(clozeCall1.usedHint).toBe(true)
    expect(clozeCall1.attemptCount).toBe(2)

    // 进入第 2 句，完成原句跟敲后自动进入第 2 步无提示首次一次拼写短语 "mashed potato"
    elements.practiceCard.querySelector('#practiceNextSentenceBtn').click()
    const copyInput2 = elements.practiceCard.querySelector('#ruminationCopyInput')
    copyInput2.value = 'serve warm mashed potato tonight'
    copyInput2.dispatchEvent(new Event('input', { bubbles: true }))
    await flushMicrotasks()
    vi.advanceTimersByTime(420)
    const clozeInput2 = elements.practiceCard.querySelector('#ruminationClozeInput')
    clozeInput2.value = 'mashed potato'
    clozeInput2.dispatchEvent(new Event('input', { bubbles: true }))
    clozeInput2.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    await flushMicrotasks()

    const clozeCall2 = submitSpy.mock.calls.find(
      ([req]) => req.sentenceIndex === 1 && req.practiceMode === 'cloze_recall',
    )[0]
    expect(clozeCall2.usedHint).toBe(false)
    expect(clozeCall2.attemptCount).toBe(1)

    // 最后一句 2 步完成后直接播放音效、全屏喝彩并关闭跟敲弹窗（不再展示最终总结弹窗）
    expect(playUiTone).toHaveBeenCalledWith('success')
    expect(elements.celebrationLayer.classList.contains('show')).toBe(true)
    expect(elements.celebrationLayer.querySelectorAll('span')).toHaveLength(34)
    expect(elements.practiceModal.classList.contains('hidden')).toBe(true)
    expect(elements.practiceCard.innerHTML).toBe('')
    expect(elements.practiceCard.textContent).not.toContain('本轮重点句巩固完成')
    expect(elements.articleCard.classList.contains('hidden')).toBe(false)
    const consolidatedTrigger = elements.articleBody.querySelector('[data-sentence-index="1"] .sentence-practice-trigger')
    expect(consolidatedTrigger.classList.contains('is-consolidated')).toBe(true)
    expect(consolidatedTrigger.textContent.trim()).toBe('✓')
  })

  it('跟敲弹窗支持遮罩关闭且与全局查词弹窗不冲突（不抢焦点、关闭查词后焦点回落到输入框）', () => {
    const elements = createPracticeDomElements()
    const quickWrapper = document.createElement('section')
    quickWrapper.id = 'quickLookupModal'
    quickWrapper.className = 'modal-backdrop hidden'
    quickWrapper.innerHTML = `
      <div class="config-modal quick-lookup-modal">
        <div class="panel-heading">
          <p id="quickLookupEyebrow"></p>
          <h3 id="quickLookupTitle"></h3>
          <p id="quickLookupContext" class="hidden"></p>
          <button id="quickLookupToggleSearchBtn" class="hidden" type="button"></button>
          <button id="quickLookupCloseBtn" type="button">×</button>
        </div>
        <form id="quickLookupForm">
          <input id="quickLookupInput" />
        </form>
        <div id="quickLookupRecentBar" class="hidden"><div id="quickLookupRecentChips"></div></div>
        <div id="quickLookupContent"></div>
      </div>
    `
    document.body.appendChild(quickWrapper)

    const material = buildSampleMaterial()
    const practiceView = createRuminationPracticeView(elements, {
      api: {},
      state: { currentWordbookId: '2001' },
      toast: vi.fn(),
      speakSentence: vi.fn(),
    })

    const quickLookup = createQuickLookupFeature({
      elements: {
        quickLookupModal: quickWrapper,
        quickLookupEyebrow: document.getElementById('quickLookupEyebrow'),
        quickLookupTitle: document.getElementById('quickLookupTitle'),
        quickLookupContext: document.getElementById('quickLookupContext'),
        quickLookupToggleSearchBtn: document.getElementById('quickLookupToggleSearchBtn'),
        quickLookupCloseBtn: document.getElementById('quickLookupCloseBtn'),
        quickLookupInput: document.getElementById('quickLookupInput'),
        quickLookupRecentBar: document.getElementById('quickLookupRecentBar'),
        quickLookupRecentChips: document.getElementById('quickLookupRecentChips'),
        quickLookupContent: document.getElementById('quickLookupContent'),
      },
      request: vi.fn(async () => ({})),
      toast: vi.fn(),
    })
    quickLookup.bind()

    vi.useFakeTimers()
    try {
      practiceView.startSingleSentence(material, 0)
      const copyInput = elements.practiceCard.querySelector('#ruminationCopyInput')
      expect(document.activeElement).toBe(copyInput)

      // 打开查词弹窗覆盖在跟敲弹窗上方
      quickLookup.open()
      const quickInput = document.getElementById('quickLookupInput')
      expect(quickWrapper.classList.contains('hidden')).toBe(false)
      expect(elements.practiceModal.classList.contains('hidden')).toBe(false)
      expect(document.activeElement).toBe(quickInput)

      // 查词弹窗打开期间，完成跟敲自动切换至遮挡步骤也不得抢走查词输入框焦点
      copyInput.value = 'she started peeling fresh carrots quickly'
      copyInput.dispatchEvent(new Event('input', { bubbles: true }))
      vi.advanceTimersByTime(420)
      expect(elements.practiceCard.querySelector('#ruminationClozeInput')).not.toBeNull()
      expect(document.activeElement).toBe(quickInput)

      // 点击遮罩关闭查词弹窗时，跟敲弹窗仍保留打开，且焦点平滑回到遮挡输入框
      quickWrapper.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      expect(quickWrapper.classList.contains('hidden')).toBe(true)
      expect(elements.practiceModal.classList.contains('hidden')).toBe(false)
      expect(document.activeElement).toBe(elements.practiceCard.querySelector('#ruminationClozeInput'))

      // 点击跟敲弹窗外侧遮罩应关闭弹窗并清空内部状态
      elements.practiceModal.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      expect(elements.practiceModal.classList.contains('hidden')).toBe(true)
      expect(practiceView.isOpen()).toBe(false)
    } finally {
      vi.useRealTimers()
    }
  })

  it('句中包含多个目标词时：第二步遮挡全部目标词并按句中顺序依次校验通过后完成本句', async () => {
    vi.useFakeTimers()
    try {
      const elements = createPracticeDomElements()
      const sentenceText = "At the farmers' market, I picked up a head of lettuce, a long cucumber, and a handful of mushroom."
      const multiMaterial = {
        id: '910099',
        wordbookId: '2001',
        scenarioCode: 'produce_kitchen',
        title: 'Market Harvest',
        articleContent: sentenceText,
        translationContent: '在农贸市场我买了生菜、黄瓜和蘑菇。',
        targetWords: ['lettuce', 'cucumber', 'mushroom'],
        practiceSentences: [
          {
            sentenceIndex: 0,
            paragraphIndex: 0,
            sentenceText,
            primaryTargetWord: 'cucumber',
            recommended: true,
            copyCount: 0,
            clozeIndependentPassed: false,
            targetSpans: [
              { targetWord: 'lettuce', surfaceForm: 'lettuce', startOffset: 46, endOffset: 53, copyCount: 0, clozeAttemptCount: 0, clozeIndependentPassed: false },
              { targetWord: 'cucumber', surfaceForm: 'cucumber', startOffset: 62, endOffset: 70, copyCount: 0, clozeAttemptCount: 0, clozeIndependentPassed: false },
              { targetWord: 'mushroom', surfaceForm: 'mushroom', startOffset: 90, endOffset: 98, copyCount: 0, clozeAttemptCount: 0, clozeIndependentPassed: false },
            ],
          },
        ],
      }

      const submitSpy = vi.fn(async (req) => ({
        materialId: req.materialId,
        sentenceIndex: req.sentenceIndex,
        targetWord: req.targetWord,
        practiceMode: req.practiceMode,
        isCorrect: true,
        expectedSurfaceForm: req.targetWord,
        copyCount: req.practiceMode === 'copy_sentence' ? 1 : 0,
        clozeAttemptCount: req.practiceMode === 'cloze_recall' ? 1 : 0,
        clozeIndependentPassed: req.practiceMode === 'cloze_recall',
        masterySettled: req.practiceMode === 'cloze_recall',
        usedHint: false,
        feedbackMessage: '独立无提示一次拼写通过！已提升拼写掌握度',
      }))

      const materialView = createRuminationMaterialView(elements, {
        api: { submitRuminationSentencePractice: submitSpy },
        state: { currentWordbookId: '2001' },
        toast: vi.fn(),
        speakSentence: vi.fn(),
      })
      const practiceView = createRuminationPracticeView(elements, {
        api: { submitRuminationSentencePractice: submitSpy },
        state: { currentWordbookId: '2001' },
        toast: vi.fn(),
        speakSentence: vi.fn(),
        onPracticeUpdated: (updated) => materialView.refreshPracticeUi(updated),
        onClose: () => materialView.refreshPracticeUi(),
      })

      materialView.displayMaterial(multiMaterial)
      practiceView.startSingleSentence(multiMaterial, 0)

      const copyInput = elements.practiceCard.querySelector('#ruminationCopyInput')
      copyInput.value = "at the farmers' market i picked up a head of lettuce a long cucumber and a handful of mushroom"
      copyInput.dispatchEvent(new Event('input', { bubbles: true }))
      await flushMicrotasks()
      vi.advanceTimersByTime(420)

      // 第 2 步同时遮挡 3 个目标词，第 1 个词 "lettuce" 高亮激活
      const slotsInitial = elements.practiceCard.querySelectorAll('.rumination-cloze-slot')
      expect(slotsInitial).toHaveLength(3)
      expect(slotsInitial[0].classList.contains('is-active')).toBe(true)
      expect(slotsInitial[1].classList.contains('is-pending')).toBe(true)
      expect(slotsInitial[2].classList.contains('is-pending')).toBe(true)
      expect(elements.practiceCard.querySelector('#clozeHintNotice').textContent).toContain('第 1/3 词')

      // 依次校验 lettuce -> cucumber -> mushroom
      for (const word of ['lettuce', 'cucumber', 'mushroom']) {
        const input = elements.practiceCard.querySelector('#ruminationClozeInput')
        expect(input).not.toBeNull()
        input.value = word
        input.dispatchEvent(new Event('input', { bubbles: true }))
        input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
        await flushMicrotasks()
      }

      const clozeWordsSubmitted = submitSpy.mock.calls
        .filter(([req]) => req.practiceMode === 'cloze_recall')
        .map(([req]) => req.targetWord)
      expect(clozeWordsSubmitted).toEqual(['lettuce', 'cucumber', 'mushroom'])
      expect(elements.celebrationLayer.classList.contains('show')).toBe(true)
      expect(elements.practiceModal.classList.contains('hidden')).toBe(true)
      const trigger = elements.articleBody.querySelector('[data-sentence-index="0"] .sentence-practice-trigger')
      expect(trigger.classList.contains('is-consolidated')).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('归档材料卡片渲染「🔄 重新生成」按钮且点击不会误关闭抽屉或触发切篇', () => {
    const elements = createPracticeDomElements()
    const archiveBox = document.createElement('div')
    elements.materialsArchiveBox = archiveBox
    elements.toggleArchiveDrawerBtn = document.createElement('button')

    const onRegenerateMaterial = vi.fn()
    const mat = buildSampleMaterial()
    mat.createTime = '2026-11-12T10:27:00'

    const view = createRuminationMaterialView(elements, {
      api: { listRuminationMaterials: vi.fn(async () => [mat]) },
      state: { currentWordbookId: '2001', currentScenarioCode: 'produce_kitchen' },
      toast: vi.fn(),
      speakSentence: vi.fn(),
      onRegenerateMaterial,
    })

    view.displayMaterial(mat)
    // 通过手动写入内部列表的方式模拟加载后打开抽屉场景
    return view.loadMaterials('2001', 'produce_kitchen').then(() => {
      archiveBox.classList.remove('hidden')
      const regenBtn = elements.materialsArchiveList.querySelector('.material-regenerate-btn')
      expect(regenBtn).not.toBeNull()
      expect(regenBtn.textContent).toContain('重新生成')

      regenBtn.click()
      expect(onRegenerateMaterial).toHaveBeenCalledTimes(1)
      expect(onRegenerateMaterial).toHaveBeenCalledWith(expect.objectContaining({ id: '910001' }))
      // 点击重新生成按钮不得冒泡到卡片选中逻辑导致抽屉关闭
      expect(archiveBox.classList.contains('hidden')).toBe(false)

      view.setRegeneratingState('910001', true)
      expect(regenBtn.disabled).toBe(true)
      expect(regenBtn.textContent).toContain('生成中')
      view.setRegeneratingState(null, false)
      expect(regenBtn.disabled).toBe(false)
      expect(regenBtn.textContent).toContain('重新生成')
    })
  })
})
