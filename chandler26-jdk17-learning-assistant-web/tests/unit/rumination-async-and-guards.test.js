import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'
import { createRuminationMaterialTaskTracker } from '../../public/src/features/rumination/rumination-material-task.js'
import { createRuminationGraphTaskTracker } from '../../public/src/features/rumination/rumination-graph-task.js'
import { createRuminationMaterialView } from '../../public/src/features/rumination/rumination-material-view.js'
import { computeGalaxyLayout } from '../../public/src/features/rumination/rumination-graph-layout.js'
import { initRuminationWorkspace } from '../../public/src/features/rumination/rumination-controller.js'

describe('知识反刍异步材料任务追踪与防串线/防剧透校验', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    document.body.innerHTML = ''
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('提交异步任务展示排队状态，轮询完成后按详情更新并明确渲染降级来源标签', async () => {
    const bannerEl = document.createElement('div')
    const currentCtx = { wordbookId: '2001', scenarioCode: 'produce_kitchen' }
    const onMaterialCompleted = vi.fn(async () => {})
    const toast = vi.fn()

    const mockApi = {
      submitRuminationMaterialTask: vi.fn(async () => ({
        id: '900012345678901234',
        status: 'pending',
        progressPercent: 0,
        steps: [],
      })),
      getAiTask: vi.fn(async () => ({
        id: '900012345678901234',
        status: 'completed',
        progressPercent: 100,
        businessId: '777777',
        steps: [
          { stepCode: 'generate_content', status: 'completed' },
          { stepCode: 'persist_material', status: 'completed' },
        ],
      })),
      getRuminationMaterialDetail: vi.fn(async () => ({
        id: '777777',
        wordbookId: '2001',
        scenarioCode: 'produce_kitchen',
        title: 'Fallback Harvest Kitchen',
        generationSource: 'fallback',
        targetWords: ['apple', 'banana', 'carrot', 'garlic', 'onion'],
      })),
    }

    const tracker = createRuminationMaterialTaskTracker({
      bannerEl,
      api: mockApi,
      toast,
      getContext: () => currentCtx,
      onMaterialCompleted,
    })

    await tracker.submit({
      wordbookId: '2001',
      scenarioCode: 'produce_kitchen',
      targetWords: ['apple', 'banana', 'carrot', 'garlic', 'onion'],
    })

    expect(bannerEl.className).toContain('status-pending')
    expect(bannerEl.textContent).toContain('900012345678901234')
    expect(tracker.isTaskActive()).toBe(true)

    await vi.advanceTimersByTimeAsync(1250)

    expect(mockApi.getAiTask).toHaveBeenCalledWith('900012345678901234')
    expect(mockApi.getRuminationMaterialDetail).toHaveBeenCalledWith('777777')
    expect(onMaterialCompleted).toHaveBeenCalledTimes(1)
    expect(bannerEl.className).toContain('status-completed')
    expect(bannerEl.textContent).toContain('本地语义降级')
  })

  it('任务等待期间切换场景后，旧场景回包不会触发当前新场景的结算与覆盖', async () => {
    const bannerEl = document.createElement('div')
    const currentCtx = { wordbookId: '2001', scenarioCode: 'produce_kitchen' }
    const onMaterialCompleted = vi.fn(async () => {})

    let resolvePoll = null
    const mockApi = {
      submitRuminationMaterialTask: vi.fn(async () => ({
        id: '9001',
        status: 'running',
        progressPercent: 50,
        steps: [{ stepCode: 'generate_content', status: 'completed' }],
      })),
      getAiTask: vi.fn(() => new Promise((resolve) => {
        resolvePoll = resolve
      })),
      getRuminationMaterialTask: vi.fn(async () => null),
    }

    const tracker = createRuminationMaterialTaskTracker({
      bannerEl,
      api: mockApi,
      toast: () => {},
      getContext: () => currentCtx,
      onMaterialCompleted,
    })

    await tracker.submit({ wordbookId: '2001', scenarioCode: 'produce_kitchen', targetWords: ['a', 'b', 'c', 'd', 'e'] })
    await vi.advanceTimersByTimeAsync(1250)

    // 用户在中途切换到另一场景
    currentCtx.scenarioCode = 'hospital_visit'
    await tracker.recoverForScenario('2001', 'hospital_visit')

    // 旧场景 poll 迟到返回已完成
    resolvePoll({
      id: '9001',
      status: 'completed',
      businessId: '888',
    })
    await Promise.resolve()

    expect(onMaterialCompleted).not.toHaveBeenCalled()
    expect(bannerEl.className).toContain('hidden')
  })

  it('失败任务在页面恢复时展示重试按钮，点击可触发断点续跑重试', async () => {
    const bannerEl = document.createElement('div')
    const currentCtx = { wordbookId: '2001', scenarioCode: 'produce_kitchen' }

    const mockApi = {
      getRuminationMaterialTask: vi.fn(async () => ({
        id: '9555',
        status: 'failed',
        errorMessage: '模拟数据库连接波动',
        steps: [
          { stepCode: 'generate_content', status: 'completed' },
          { stepCode: 'persist_material', status: 'failed' },
        ],
      })),
      retryAiTask: vi.fn(async () => ({
        id: '9555',
        status: 'pending',
        progressPercent: 50,
        steps: [
          { stepCode: 'generate_content', status: 'completed' },
          { stepCode: 'persist_material', status: 'pending' },
        ],
      })),
      getAiTask: vi.fn(async () => ({
        id: '9555',
        status: 'running',
        progressPercent: 50,
      })),
    }

    const tracker = createRuminationMaterialTaskTracker({
      bannerEl,
      api: mockApi,
      toast: () => {},
      getContext: () => currentCtx,
      onMaterialCompleted: async () => {},
    })

    await tracker.recoverForScenario('2001', 'produce_kitchen')
    expect(bannerEl.className).toContain('status-failed')
    expect(bannerEl.textContent).toContain('阶段 1 检查点')

    const retryBtn = bannerEl.querySelector('[data-task-action="retry"]')
    expect(retryBtn).not.toBeNull()
    retryBtn.click()
    await Promise.resolve()
    await Promise.resolve()

    expect(mockApi.retryAiTask).toHaveBeenCalledWith('9555')
    expect(bannerEl.className).toContain('status-pending')
  })

  it('未作答探针 DOM 按钮不得包含 data-correct 答案属性，且材料展示正确标明生成来源', () => {
    document.body.innerHTML = `
      <span id="materialsNavBadge"></span>
      <span id="archiveCountBadge"></span>
      <span id="archiveCountInfo"></span>
      <div id="materialsEmptyState"></div>
      <div id="materialsArchiveList"></div>
      <div id="ruminationArticleCard" class="hidden"></div>
      <h3 id="ruminationArticleTitle"></h3>
      <div id="ruminationArticleBody"></div>
      <details id="ruminationTranslationDetails"><div id="ruminationTranslationBox"></div></details>
      <section id="ruminationProbesCard" class="hidden">
        <span id="ruminationProbesSubtitle"></span>
        <div id="probesEmptyState"></div>
        <div id="ruminationProbeList"></div>
      </section>
    `
    const elements = {
      materialsNavBadge: document.getElementById('materialsNavBadge'),
      archiveCountBadge: document.getElementById('archiveCountBadge'),
      archiveCountInfo: document.getElementById('archiveCountInfo'),
      materialsEmptyState: document.getElementById('materialsEmptyState'),
      materialsArchiveList: document.getElementById('materialsArchiveList'),
      articleCard: document.getElementById('ruminationArticleCard'),
      articleTitle: document.getElementById('ruminationArticleTitle'),
      articleBody: document.getElementById('ruminationArticleBody'),
      translationDetails: document.getElementById('ruminationTranslationDetails'),
      translationBox: document.getElementById('ruminationTranslationBox'),
      probesCard: document.getElementById('ruminationProbesCard'),
      probesSubtitle: document.getElementById('ruminationProbesSubtitle'),
      probesEmptyState: document.getElementById('probesEmptyState'),
      probeList: document.getElementById('ruminationProbeList'),
    }

    const view = createRuminationMaterialView(elements, { state: { currentWordbookId: '2001' } })
    view.displayMaterial({
      id: '301',
      title: 'Kitchen Narrative',
      scenarioSummary: '厨房准备过程',
      articleContent: 'We wash the apple carefully.',
      translationContent: '我们仔细清洗苹果。',
      targetWords: ['apple'],
      generationSource: 'fallback',
      probes: [
        {
          id: '401',
          targetWord: 'apple',
          probeType: 'context_match',
          probeTypeName: '情境辨析题',
          question: 'Which fruit is washed?',
          options: ['apple', 'stone', 'lamp', 'clock'],
          correctAnswer: null,
          hint: '结合清洗食材判断',
        },
      ],
    })

    expect(elements.articleTitle.textContent).toBe('Kitchen Narrative')
    expect(document.getElementById('ruminationArticleContext')).toBeNull()
    const optionButtons = elements.probeList.querySelectorAll('.probe-option-btn')
    expect(optionButtons).toHaveLength(4)
    optionButtons.forEach((btn) => {
      expect(btn.hasAttribute('data-correct')).toBe(false)
    })
  })

  it('自定义场景与节点裂变异步任务：支持切场景保通知与重检隔离、并可从刷新恢复', async () => {
    const bannerEl = document.createElement('div')
    const currentCtx = { wordbookId: '2001', scenarioCode: 'custom_airport' }
    const onScenarioCompleted = vi.fn(async () => {})
    const onFissionCompleted = vi.fn()
    const toast = vi.fn()

    const mockApi = {
      submitCustomScenarioTask: vi.fn(async () => ({
        id: '9901',
        taskType: 'rumination_scenario',
        taskName: '知识反刍自定义场景：✈️ 机场登机',
        status: 'pending',
        progressPercent: 0,
      })),
      getAiTask: vi.fn()
        .mockResolvedValueOnce({
          id: '9901',
          taskType: 'rumination_scenario',
          status: 'completed',
          progressPercent: 100,
          businessId: '5001',
        })
        .mockResolvedValueOnce({
          id: '9902',
          taskType: 'rumination_fission',
          status: 'completed',
          progressPercent: 100,
          businessId: 'produce_kitchen',
        }),
      listRuminationTasks: vi.fn(async () => ([
        {
          task: {
            id: '9902',
            taskType: 'rumination_fission',
            taskName: '知识反刍 4D 裂变：carrot',
            status: 'running',
            progressPercent: 50,
          },
          wordbookId: '2001',
          scenarioCode: 'produce_kitchen',
          scenarioName: '生鲜采购与厨房料理',
          parentNodeId: 'core-carrot',
          targetWord: 'carrot',
        },
      ])),
      getRuminationFissionResult: vi.fn(async () => ({
        wordbookId: '2001',
        scenarioCode: 'produce_kitchen',
        parentNodeId: 'core-carrot',
        targetWord: 'carrot',
        applyStatus: 'applied',
        aiPowered: true,
        nodes: [{ id: 'exp-dice', term: 'dice' }],
        edges: [{ source: 'core-carrot', target: 'exp-dice' }],
      })),
    }

    const tracker = createRuminationGraphTaskTracker({
      bannerEl,
      api: mockApi,
      toast,
      getContext: () => currentCtx,
      onScenarioCompleted,
      onFissionCompleted,
      onSwitchScenario: () => {},
    })

    const submitted = await mockApi.submitCustomScenarioTask({})
    await tracker.trackSubmission(submitted, {
      wordbookId: '2001',
      scenarioCode: 'custom_airport',
      scenarioName: '机场登机',
      scenarioIcon: '✈️',
    })
    expect(bannerEl.textContent).toContain('机场登机')

    await vi.advanceTimersByTimeAsync(1250)
    expect(onScenarioCompleted).toHaveBeenCalledTimes(1)

    // 恢复 produce_kitchen 的裂变任务，但用户在完成后已切换到 custom_airport
    await tracker.recoverForWordbook('2001', 'custom_airport')
    expect(bannerEl.textContent).toContain('查看原场景')
    await vi.advanceTimersByTimeAsync(1250)

    expect(mockApi.getRuminationFissionResult).toHaveBeenCalledWith('9902')
    expect(onFissionCompleted).toHaveBeenCalledTimes(1)
    expect(onFissionCompleted.mock.calls[0][0].fissionResult.scenarioCode).toBe('produce_kitchen')
  })

  it('头部 2 个子 Tab 切换控制图谱/场景材料显隐，支持归档抽屉折叠与内联探针绑定，并支持纯粹极坐标布局计算', () => {
    document.body.innerHTML = `
      <select id="articleWordbookSelect"><option value="2001">Book</option></select>
      <select id="ruminationScenarioSelect"><option value="produce_kitchen">🥦 生鲜采购</option></select>
      <button id="btnNavGalaxyGraph" class="active"></button>
      <button id="btnNavScenarioMaterial"></button>
      <span id="materialsNavBadge">0 篇</span>
      <div id="ruminationGraphControls"></div>
      <div id="ruminationCanvasWrap"><canvas id="ruminationGraphCanvas"></canvas></div>
      <div id="scenarioMaterialPanel" class="hidden">
        <button id="toggleArchiveDrawerBtn" aria-expanded="false"></button>
        <span id="archiveCountBadge">0 篇</span>
        <div id="materialsArchiveBox" class="hidden">
          <span id="archiveCountInfo"></span>
          <div id="materialsEmptyState"></div>
          <div id="materialsArchiveList"></div>
        </div>
        <div id="ruminationArticleCard" class="hidden">
          <h3 id="ruminationArticleTitle"></h3>
          <p id="ruminationArticleContext"></p>
          <div id="ruminationArticleBody"></div>
          <details id="ruminationTranslationDetails"><div id="ruminationTranslationBox"></div></details>
        </div>
        <section id="ruminationProbesCard" class="hidden">
          <span id="ruminationProbesSubtitle"></span>
          <div id="probesEmptyState"></div>
          <div id="ruminationProbeList"></div>
        </section>
      </div>
    `
    const mockCtx = {
      font: '',
      measureText: (txt) => ({ width: String(txt || '').length * 8 }),
      clearRect: () => {},
      createRadialGradient: () => ({ addColorStop: () => {} }),
      createLinearGradient: () => ({ addColorStop: () => {} }),
      fillRect: () => {},
      save: () => {},
      restore: () => {},
      translate: () => {},
      scale: () => {},
      beginPath: () => {},
      closePath: () => {},
      arc: () => {},
      fill: () => {},
      moveTo: () => {},
      lineTo: () => {},
      quadraticCurveTo: () => {},
      stroke: () => {},
      roundRect: () => {},
      setLineDash: () => {},
      fillText: () => {},
      setTransform: () => {},
    }
    const canvas = document.getElementById('ruminationGraphCanvas')
    canvas.getContext = () => mockCtx
    globalThis.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }

    const layout = computeGalaxyLayout(mockCtx, {
      nodes: [
        { id: 'hub', nodeType: 'hub', label: '🥦 生鲜采购' },
        { id: 'core-apple', nodeType: 'core', term: 'apple', definition: '苹果' },
      ],
      edges: [{ source: 'hub', target: 'core-apple', relationType: 'peer' }],
    })
    expect(layout.placedNodes).toHaveLength(2)
    expect(layout.edges).toHaveLength(1)

    const ws = initRuminationWorkspace({ state: { currentWordbookId: '2001' } })
    expect(ws).not.toBeNull()

    document.getElementById('btnNavScenarioMaterial').click()
    expect(document.getElementById('btnNavScenarioMaterial').classList.contains('active')).toBe(true)
    expect(document.getElementById('ruminationCanvasWrap').classList.contains('hidden')).toBe(true)
    expect(document.getElementById('ruminationGraphControls').classList.contains('hidden')).toBe(true)
    expect(document.getElementById('scenarioMaterialPanel').classList.contains('hidden')).toBe(false)

    // 归档抽屉支持折叠/展开
    const toggleBtn = document.getElementById('toggleArchiveDrawerBtn')
    const archiveBox = document.getElementById('materialsArchiveBox')
    expect(archiveBox.classList.contains('hidden')).toBe(true)
    toggleBtn.click()
    expect(archiveBox.classList.contains('hidden')).toBe(false)
    expect(toggleBtn.getAttribute('aria-expanded')).toBe('true')
    toggleBtn.click()
    expect(archiveBox.classList.contains('hidden')).toBe(true)

    document.getElementById('btnNavGalaxyGraph').click()
    expect(document.getElementById('btnNavGalaxyGraph').classList.contains('active')).toBe(true)
    expect(document.getElementById('ruminationCanvasWrap').classList.contains('hidden')).toBe(false)
    expect(document.getElementById('ruminationGraphControls').classList.contains('hidden')).toBe(false)
    expect(document.getElementById('scenarioMaterialPanel').classList.contains('hidden')).toBe(true)

    ws.destroy?.()
  })

  it('场景材料加载无数据时自动展开归档抽屉，有材料时自动收起抽屉、默认隐藏中文对照且学习结果检查内联绑定文章标题', async () => {
    document.body.innerHTML = `
      <span id="materialsNavBadge"></span>
      <span id="archiveCountBadge"></span>
      <button id="toggleArchiveDrawerBtn" aria-expanded="true"></button>
      <div id="materialsArchiveBox"></div>
      <span id="archiveCountInfo"></span>
      <div id="materialsEmptyState"></div>
      <div id="materialsArchiveList"></div>
      <div id="ruminationArticleCard" class="hidden"></div>
      <h3 id="ruminationArticleTitle"></h3>
      <p id="ruminationArticleContext"></p>
      <div id="ruminationArticleBody"></div>
      <details id="ruminationTranslationDetails" open><div id="ruminationTranslationBox"></div></details>
      <section id="ruminationProbesCard" class="hidden">
        <span id="ruminationProbesSubtitle"></span>
        <div id="probesEmptyState"></div>
        <div id="ruminationProbeList"></div>
      </section>
    `
    const $ = (id) => document.getElementById(id)
    const elements = {
      materialsNavBadge: $('materialsNavBadge'),
      archiveCountBadge: $('archiveCountBadge'),
      toggleArchiveDrawerBtn: $('toggleArchiveDrawerBtn'),
      materialsArchiveBox: $('materialsArchiveBox'),
      archiveCountInfo: $('archiveCountInfo'),
      materialsEmptyState: $('materialsEmptyState'),
      materialsArchiveList: $('materialsArchiveList'),
      articleCard: $('ruminationArticleCard'),
      articleTitle: $('ruminationArticleTitle'),
      articleContext: $('ruminationArticleContext'),
      articleBody: $('ruminationArticleBody'),
      translationDetails: $('ruminationTranslationDetails'),
      translationBox: $('ruminationTranslationBox'),
      probesCard: $('ruminationProbesCard'),
      probesSubtitle: $('ruminationProbesSubtitle'),
      probesEmptyState: $('probesEmptyState'),
      probeList: $('ruminationProbeList'),
    }

    const materialPayload = {
      id: '302',
      title: 'Morning Orchard Harvest',
      scenarioSummary: '果园采摘场景',
      articleContent: 'We picked fresh apples before noon.',
      translationContent: '我们在正午前采摘了新鲜苹果。',
      targetWords: ['apple'],
      probes: [
        {
          id: '402',
          targetWord: 'apple',
          probeType: 'context_match',
          probeTypeName: 'Context Match',
          question: 'When were the fresh apples picked in the story?',
          options: ['Before noon', 'After midnight', 'During snow', 'Under water'],
          correctAnswer: null,
          hint: '注意文中的时间状语',
        },
      ],
    }
    const listRuminationMaterials = vi
      .fn()
      .mockResolvedValueOnce([])
      .mockResolvedValueOnce([materialPayload])

    const view = createRuminationMaterialView(elements, {
      state: { currentWordbookId: '2001', currentScenarioCode: 'produce_kitchen' },
      api: { listRuminationMaterials },
      isContextActive: () => true,
    })

    // 空材料列表时自动展开归档引导
    await view.loadMaterials('2001', 'produce_kitchen')
    expect(elements.materialsArchiveBox.classList.contains('hidden')).toBe(false)
    expect(elements.toggleArchiveDrawerBtn.getAttribute('aria-expanded')).toBe('true')
    expect(elements.probesCard.classList.contains('hidden')).toBe(true)

    // 有材料时自动折叠归档抽屉、折叠译文并内联展示当前文章探针
    await view.loadMaterials('2001', 'produce_kitchen')
    expect(elements.materialsArchiveBox.classList.contains('hidden')).toBe(true)
    expect(elements.toggleArchiveDrawerBtn.getAttribute('aria-expanded')).toBe('false')
    expect(elements.translationDetails.open).toBe(false)
    expect(elements.probesCard.classList.contains('hidden')).toBe(false)
    expect(elements.probesSubtitle.textContent).toContain('Morning Orchard Harvest')
    expect(elements.probesSubtitle.textContent).toContain('1 题')
    // 高亮目标词时不包含中文释义且无释义开关
    expect(elements.articleBody.querySelectorAll('.target-word-mark').length).toBeGreaterThan(0)
    expect(elements.articleBody.textContent).not.toContain('苹果')
  })

  it('拖拽单个节点时不触发其余节点物理位移，并立即持久化保存与恢复坐标', async () => {
    vi.useFakeTimers()
    const rafCallbacks = []
    vi.stubGlobal('requestAnimationFrame', (cb) => {
      rafCallbacks.push(cb)
      return rafCallbacks.length
    })
    vi.stubGlobal('cancelAnimationFrame', () => {})

    document.body.innerHTML = `
      <select id="articleWordbookSelect"><option value="2001">Book</option></select>
      <select id="ruminationScenarioSelect"><option value="produce_kitchen">🥦 生鲜采购</option></select>
      <button id="btnNavGalaxyGraph" class="active"></button>
      <button id="btnNavScenarioMaterial"></button>
      <button id="btnNavMicroProbe"></button>
      <span id="materialsNavBadge">0 篇</span>
      <span id="probesNavBadge">0 题</span>
      <div id="ruminationGraphControls"></div>
      <div id="ruminationCanvasWrap"><canvas id="ruminationGraphCanvas"></canvas></div>
      <span id="graphSaveStatus">💾 拖拽位置自动保存</span>
      <div id="scenarioMaterialPanel" class="hidden"></div>
      <div id="microProbePanel" class="hidden"></div>
    `

    const mockCtx = {
      font: '',
      measureText: (txt) => ({ width: String(txt || '').length * 8 }),
      clearRect: () => {},
      createRadialGradient: () => ({ addColorStop: () => {} }),
      createLinearGradient: () => ({ addColorStop: () => {} }),
      fillRect: () => {},
      save: () => {},
      restore: () => {},
      translate: () => {},
      scale: () => {},
      beginPath: () => {},
      closePath: () => {},
      arc: () => {},
      fill: () => {},
      moveTo: () => {},
      lineTo: () => {},
      quadraticCurveTo: () => {},
      stroke: () => {},
      roundRect: () => {},
      setLineDash: () => {},
      fillText: () => {},
      setTransform: () => {},
    }
    const canvas = document.getElementById('ruminationGraphCanvas')
    canvas.getContext = () => mockCtx
    canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width: 900, height: 600 })
    globalThis.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }

    const savedPayloads = []
    const mockApi = {
      saveRuminationGraph: vi.fn(async (payload) => {
        savedPayloads.push(payload)
        return { ok: true }
      }),
    }

    const ws = initRuminationWorkspace({
      state: { currentWordbookId: '2001' },
      api: mockApi,
    })

    // 传入尚未保存 fixed 坐标的初始核心词与衍生词（模拟首次打开场景）
    const initialGraph = {
      scenarioCode: 'produce_kitchen',
      scenarioName: '生鲜采购与厨房料理',
      nodes: [
        { id: 'hub', nodeType: 'hub', term: '生鲜采购与厨房料理', label: '🥦 生鲜采购与厨房料理' },
        { id: 'n-carrot', nodeType: 'core', term: 'carrot', definition: '胡萝卜' },
        { id: 'n-apple', nodeType: 'core', term: 'apple', definition: '苹果' },
        { id: 'n-peel', nodeType: 'expansion', term: 'peel', definition: '削皮', dimension: 'action' },
      ],
      edges: [
        { source: 'hub', target: 'n-carrot', relationType: 'peer' },
        { source: 'hub', target: 'n-apple', relationType: 'peer' },
        { source: 'n-apple', target: 'n-peel', relationType: 'action' },
      ],
    }
    ws.getGraphData().nodes = initialGraph.nodes
    ws.getGraphData().edges = initialGraph.edges
    ws.graph.setData(ws.getGraphData(), { isNewScenario: true })

    // 先跑完初始退火收敛，确保进入静止态
    for (let i = 0; i < 120 && rafCallbacks.length > 0; i++) {
      const cb = rafCallbacks.shift()
      cb?.(performance.now())
    }

    const beforeNodes = new Map(
      ws.graph.getNodes().map((n) => [n.id, { x: n.x, y: n.y }])
    )
    const carrot = ws.graph.getNodes().find((n) => n.id === 'n-carrot')
    const tf = ws.graph.getTransform()
    const startSx = tf.x + carrot.x * tf.scale
    const startSy = tf.y + carrot.y * tf.scale

    // 模拟拖拽 carrot 节点向右下方移动 80px
    canvas.dispatchEvent(new MouseEvent('mousedown', { button: 0, clientX: startSx, clientY: startSy }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: startSx + 80, clientY: startSy + 60 }))
    // 再推进若干帧动画，验证物理模拟不会唤醒其它节点
    for (let i = 0; i < 20 && rafCallbacks.length > 0; i++) {
      const cb = rafCallbacks.shift()
      cb?.(performance.now())
    }
    window.dispatchEvent(new MouseEvent('mouseup', { button: 0, clientX: startSx + 80, clientY: startSy + 60 }))
    for (let i = 0; i < 20 && rafCallbacks.length > 0; i++) {
      const cb = rafCallbacks.shift()
      cb?.(performance.now())
    }
    await vi.runAllTimersAsync()

    // 除 carrot 外其余节点位置零偏移
    for (const n of ws.graph.getNodes()) {
      const prev = beforeNodes.get(n.id)
      if (n.id === 'n-carrot') {
        expect(Math.hypot(n.x - prev.x, n.y - prev.y)).toBeGreaterThan(40)
        expect(n.fixed).toBe(true)
      } else {
        expect(n.x).toBe(prev.x)
        expect(n.y).toBe(prev.y)
      }
    }

    // 拖拽松手后应立即调用持久化保存，无需等待 200ms 防抖
    expect(mockApi.saveRuminationGraph).toHaveBeenCalledTimes(1)
    const persistedNodes = JSON.parse(savedPayloads[0].graphNodesJson)
    const persistedCarrot = persistedNodes.find((n) => n.id === 'n-carrot')
    expect(persistedCarrot.fixed).toBe(true)
    expect(persistedCarrot.x).toBe(Math.round(carrot.x))
    expect(persistedCarrot.y).toBe(Math.round(carrot.y))
    expect(document.getElementById('graphSaveStatus').textContent).toContain('布局位置已保存')

    // 模拟刷新后从服务端重新载入：carrot 保持保存位置且不会复位
    const reloadedLayout = computeGalaxyLayout(mockCtx, {
      nodes: persistedNodes,
      edges: JSON.parse(savedPayloads[0].graphEdgesJson),
    })
    const reloadedCarrot = reloadedLayout.nodeMap.get('n-carrot')
    expect(reloadedCarrot.x).toBe(persistedCarrot.x)
    expect(reloadedCarrot.y).toBe(persistedCarrot.y)
    expect(reloadedCarrot.fixed).toBe(true)

    // 按 Command+S 快捷键触发手动保存并阻止浏览器默认保存弹窗
    const cmdSEvent = new KeyboardEvent('keydown', { key: 's', code: 'KeyS', metaKey: true, cancelable: true })
    document.dispatchEvent(cmdSEvent)
    expect(cmdSEvent.defaultPrevented).toBe(true)
    await vi.runAllTimersAsync()
    expect(mockApi.saveRuminationGraph).toHaveBeenCalledTimes(2)

    // 弹窗打开时按 Command+S 不误触底层图谱保存
    const openModal = document.createElement('div')
    openModal.className = 'modal-backdrop'
    document.body.appendChild(openModal)
    const modalSEvent = new KeyboardEvent('keydown', { key: 's', code: 'KeyS', metaKey: true, cancelable: true })
    document.dispatchEvent(modalSEvent)
    expect(modalSEvent.defaultPrevented).toBe(false)
    await vi.runAllTimersAsync()
    expect(mockApi.saveRuminationGraph).toHaveBeenCalledTimes(2)

    ws.destroy?.()
  })

  it('拖拽与词袋操作走增量接口不覆盖并发裂变，整图保存发生 409 冲突时自动同步服务端最新拓扑与版本，探针混合来源分别标记', async () => {
    vi.useFakeTimers()
    const rafCallbacks = []
    vi.stubGlobal('requestAnimationFrame', (cb) => {
      rafCallbacks.push(cb)
      return rafCallbacks.length
    })
    vi.stubGlobal('cancelAnimationFrame', () => {})

    document.body.innerHTML = `
      <select id="articleWordbookSelect"><option value="10">Book</option></select>
      <select id="ruminationScenarioSelect"><option value="produce_kitchen">🥦 生鲜采购</option></select>
      <button id="graphToggleBagBtn"></button>
      <span id="graphBagCounter">0</span>
      <div id="bagChipsWrap"></div>
      <button id="generateMaterialBtn"></button>
      <button id="btnNavGalaxyGraph" class="active"></button>
      <button id="btnNavScenarioMaterial"></button>
      <button id="btnNavMicroProbe"></button>
      <span id="materialsNavBadge">0 篇</span>
      <span id="probesNavBadge">0 题</span>
      <div id="ruminationGraphControls"></div>
      <div id="ruminationCanvasWrap"><canvas id="ruminationGraphCanvas"></canvas></div>
      <span id="graphSaveStatus">💾 拖拽位置自动保存</span>
      <div id="scenarioMaterialPanel" class="hidden">
        <div id="materialsArchiveBox" class="hidden">
          <div id="materialsEmptyState"></div>
          <div id="materialsArchiveList"></div>
        </div>
        <button id="toggleArchiveDrawerBtn" aria-expanded="false"></button>
        <span id="archiveCountBadge"></span>
      </div>
      <div id="microProbePanel" class="hidden"></div>
      <div id="ruminationArticleCard" class="hidden">
        <h4 id="ruminationArticleTitle"></h4>
        <div id="ruminationArticleContext"></div>
        <div id="ruminationArticleBody"></div>
        <div id="ruminationTranslationBox"></div>
      </div>
      <div id="ruminationProbesCard" class="hidden">
        <div id="ruminationProbeList"></div>
      </div>
      <div id="probesEmptyState"></div>
    `
    const mockCtx = {
      font: '',
      measureText: (txt) => ({ width: String(txt || '').length * 8 }),
      clearRect: () => {},
      createRadialGradient: () => ({ addColorStop: () => {} }),
      createLinearGradient: () => ({ addColorStop: () => {} }),
      fillRect: () => {},
      save: () => {},
      restore: () => {},
      translate: () => {},
      scale: () => {},
      beginPath: () => {},
      closePath: () => {},
      arc: () => {},
      fill: () => {},
      moveTo: () => {},
      lineTo: () => {},
      quadraticCurveTo: () => {},
      stroke: () => {},
      roundRect: () => {},
      setLineDash: () => {},
      fillText: () => {},
      setTransform: () => {},
    }
    const canvas = document.getElementById('ruminationGraphCanvas')
    canvas.getContext = () => mockCtx
    canvas.getBoundingClientRect = () => ({ left: 0, top: 0, width: 900, height: 520 })
    globalThis.ResizeObserver = class {
      observe() {}
      disconnect() {}
    }

    let serverVersion = 3
    let reloadedOnce = false
    const latestServerGraph = {
      scenarioCode: 'produce_kitchen',
      scenarioName: '生鲜采购与厨房料理',
      version: 5,
      nodes: [
        { id: 'hub', nodeType: 'hub', term: '生鲜采购与厨房料理', label: '🥦 生鲜采购与厨房料理' },
        { id: 'n-carrot', nodeType: 'core', term: 'carrot', definition: '胡萝卜' },
        { id: 'n-apple', nodeType: 'core', term: 'apple', definition: '苹果' },
        { id: 'exp-harvest', nodeType: 'expansion', term: 'harvest', definition: '收获', dimension: 'action' },
      ],
      edges: [
        { source: 'hub', target: 'n-carrot', relationType: 'peer' },
        { source: 'hub', target: 'n-apple', relationType: 'peer' },
        { source: 'n-carrot', target: 'exp-harvest', relationType: 'action' },
      ],
      bagTerms: ['carrot', 'harvest'],
    }

    const mockApi = {
      listRuminationScenarios: vi.fn(async () => [
        { code: 'produce_kitchen', name: '生鲜采购与厨房料理', icon: '🥦' },
      ]),
      getRuminationGraph: vi.fn(async () => {
        if (!reloadedOnce) {
          reloadedOnce = true
          return {
            scenarioCode: 'produce_kitchen',
            scenarioName: '生鲜采购与厨房料理',
            version: serverVersion,
            nodes: [
              { id: 'hub', nodeType: 'hub', term: '生鲜采购与厨房料理', label: '🥦 生鲜采购与厨房料理' },
              { id: 'n-carrot', nodeType: 'core', term: 'carrot', definition: '胡萝卜' },
              { id: 'n-apple', nodeType: 'core', term: 'apple', definition: '苹果' },
            ],
            edges: [
              { source: 'hub', target: 'n-carrot', relationType: 'peer' },
              { source: 'hub', target: 'n-apple', relationType: 'peer' },
            ],
            bagTerms: ['carrot'],
          }
        }
        return latestServerGraph
      }),
      listRuminationMaterials: vi.fn(async () => [
        {
          id: 'm1',
          wordbookId: '10',
          scenarioCode: 'produce_kitchen',
          title: 'Mixed Sources Test',
          scenarioSummary: 'summary',
          articleContent: 'We peel and chop carrots in the kitchen.',
          translationContent: '我们在厨房削皮并切胡萝卜。',
          targetWords: ['carrot', 'apple', 'potato', 'onion', 'peel'],
          generationSource: 'ai',
          createTime: '2026-11-12T10:27:00',
          probes: [
            {
              id: 'p-ai',
              targetWord: 'carrot',
              probeType: 'context_match',
              probeTypeName: '情境辨析题',
              generationSource: 'ai',
              question: 'AI question',
              options: ['opt1', 'opt2', 'opt3', 'opt4'],
              hint: 'hint',
            },
            {
              id: 'p-fb',
              targetWord: 'apple',
              probeType: 'collocation',
              probeTypeName: '动作搭配题',
              generationSource: 'fallback',
              question: 'Fallback question',
              options: ['optA', 'optB', 'optC', 'optD'],
              hint: 'hint2',
            },
          ],
        },
      ]),
      getRuminationMaterialTask: vi.fn(async () => null),
      listRuminationTasks: vi.fn(async () => []),
      regenerateRuminationMaterialTask: vi.fn(async (matId) => ({
        id: 't-regen-1',
        status: 'pending',
        progressPercent: 10,
        steps: [],
        sourceMaterialId: matId,
      })),
      getAiTask: vi.fn(async (taskId) => ({
        id: taskId,
        status: 'completed',
        businessId: 'm2',
        progressPercent: 100,
        finishedTime: new Date().toISOString(),
        steps: [],
      })),
      getRuminationMaterialDetail: vi.fn(async (materialId) => {
        if (materialId === 'm2') {
          return {
            id: 'm2',
            wordbookId: '10',
            scenarioCode: 'produce_kitchen',
            title: 'Regenerated Fresh Story',
            scenarioSummary: 'regenerated summary',
            articleContent: 'Fresh carrots and apples sit on the table.',
            translationContent: '新鲜胡萝卜和苹果放在桌上。',
            targetWords: ['carrot', 'apple', 'potato', 'onion', 'peel'],
            generationSource: 'ai',
            bagConsumed: false,
            probes: [],
          }
        }
        return {
          id: 'm1',
          wordbookId: '10',
          scenarioCode: 'produce_kitchen',
          title: 'Mixed Sources Test',
          scenarioSummary: 'summary',
          articleContent: 'We peel and chop carrots in the kitchen.',
          translationContent: '我们在厨房削皮并切胡萝卜。',
          targetWords: ['carrot'],
          generationSource: 'ai',
          graphVersion: 6,
          probes: [
            {
              id: 'p-ai',
              targetWord: 'carrot',
              probeType: 'context_match',
              probeTypeName: '情境辨析题',
              generationSource: 'ai',
              question: 'AI question',
              options: ['opt1', 'opt2', 'opt3', 'opt4'],
              hint: 'hint',
            },
            {
              id: 'p-fb',
              targetWord: 'apple',
              probeType: 'collocation',
              probeTypeName: '动作搭配题',
              generationSource: 'fallback',
              question: 'Fallback question',
              options: ['optA', 'optB', 'optC', 'optD'],
              hint: 'hint2',
            },
          ],
        }
      }),
      updateRuminationGraphPositions: vi.fn(async (payload) => {
        serverVersion += 1
        return { wordbookId: payload.wordbookId, scenarioCode: payload.scenarioCode, version: serverVersion, bagTerms: ['carrot'] }
      }),
      updateRuminationBagItem: vi.fn(async (payload) => {
        serverVersion += 1
        return {
          wordbookId: payload.wordbookId,
          scenarioCode: payload.scenarioCode,
          version: serverVersion,
          bagTerms: payload.add ? ['carrot', payload.term] : ['carrot'],
        }
      }),
      saveRuminationGraph: vi.fn(async () => {
        const err = new Error('当前星系图谱已有更新版本')
        err.status = 409
        err.errorCode = 'RUMINATION_GRAPH_VERSION_CONFLICT'
        throw err
      }),
    }

    const toastCalls = []
    const ws = initRuminationWorkspace({
      state: { currentWordbookId: '10', activeView: 'articleStudyView' },
      api: mockApi,
      toast: (msg) => toastCalls.push(msg),
    })
    ws.reload('10')

    for (let i = 0; i < 40 && rafCallbacks.length > 0; i++) {
      rafCallbacks.shift()?.(performance.now())
    }
    await vi.runAllTimersAsync()
    for (let i = 0; i < 40 && rafCallbacks.length > 0; i++) {
      rafCallbacks.shift()?.(performance.now())
    }
    expect(ws.getGraphData().version).toBe(3)

    // 1) 拖拽节点走 /graph/positions 增量接口且递增版本为 4，不调用全量 /graph/save
    const carrot = ws.graph.getNodes().find((n) => n.id === 'n-carrot')
    const tf = ws.graph.getTransform()
    const sx = tf.x + carrot.x * tf.scale
    const sy = tf.y + carrot.y * tf.scale
    canvas.dispatchEvent(new MouseEvent('mousedown', { button: 0, clientX: sx, clientY: sy }))
    window.dispatchEvent(new MouseEvent('mousemove', { clientX: sx + 70, clientY: sy + 50 }))
    window.dispatchEvent(new MouseEvent('mouseup', { button: 0, clientX: sx + 70, clientY: sy + 50 }))
    await vi.runAllTimersAsync()

    expect(mockApi.updateRuminationGraphPositions).toHaveBeenCalledTimes(1)
    expect(mockApi.saveRuminationGraph).not.toHaveBeenCalled()
    expect(ws.getGraphData().version).toBe(4)

    // 2) 词袋加入操作走 /graph/bag 增量接口
    ws.selectNode({ id: 'n-apple' })
    document.getElementById('graphToggleBagBtn').click()
    await vi.runAllTimersAsync()

    expect(mockApi.updateRuminationBagItem).toHaveBeenCalledWith(
      expect.objectContaining({ wordbookId: '10', scenarioCode: 'produce_kitchen', term: 'apple', add: true })
    )
    expect(mockApi.saveRuminationGraph).not.toHaveBeenCalled()
    expect(ws.getGraphData().version).toBe(5)

    // 3) 手动全量保存(Cmd+S) 携带 expectedVersion=5，遇 409 冲突时自动同步最新服务端图谱而不覆盖
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 's', code: 'KeyS', metaKey: true, cancelable: true }))
    await vi.runAllTimersAsync()
    for (let i = 0; i < 40 && rafCallbacks.length > 0; i++) {
      rafCallbacks.shift()?.(performance.now())
    }

    expect(mockApi.saveRuminationGraph).toHaveBeenCalledWith(
      expect.objectContaining({ expectedVersion: 5 }),
      expect.any(Object)
    )
    expect(mockApi.getRuminationGraph).toHaveBeenCalledTimes(2)
    expect(ws.getGraphData().nodes.some((n) => n.term === 'harvest')).toBe(true)
    expect(toastCalls.some((m) => m.includes('自动同步最新节点与词袋'))).toBe(true)

    // 4) 加载混合 AI / 本地补题材料时，探针顶部清晰区分“🤖 AI 出题”与“🧩 本地补题”
    const { createRuminationMaterialView } = await import('/src/features/rumination/rumination-material-view.js')
    const mv = createRuminationMaterialView(
      {
        articleCard: document.getElementById('ruminationArticleCard'),
        articleTitle: document.getElementById('ruminationArticleTitle'),
        articleContext: document.getElementById('ruminationArticleContext'),
        articleBody: document.getElementById('ruminationArticleBody'),
        translationBox: document.getElementById('ruminationTranslationBox'),
        probesCard: document.getElementById('ruminationProbesCard'),
        probeList: document.getElementById('ruminationProbeList'),
        probesEmptyState: document.getElementById('probesEmptyState'),
      },
      { api: mockApi, state: {}, toast: () => {} }
    )
    mv.displayMaterial(await mockApi.getRuminationMaterialDetail('m1'))
    const probeBadges = Array.from(document.querySelectorAll('#ruminationProbeList .probe-source-badge')).map((el) => el.textContent.trim())
    expect(probeBadges).toEqual(['🤖 AI 出题', '🧩 本地补题'])

    // 5) 点击归档材料「🔄 重新生成」后即使当前词袋含重叠词汇，也完整保留词袋且置顶展示新材料
    const bagBeforeRegen = [...ws.getSelectedNodeIds()]
    expect(bagBeforeRegen.length).toBeGreaterThanOrEqual(2)
    const regenBtn = document.querySelector('#materialsArchiveList .material-regenerate-btn')
    expect(regenBtn).not.toBeNull()
    regenBtn.click()
    await vi.runAllTimersAsync()

    expect(mockApi.regenerateRuminationMaterialTask).toHaveBeenCalledWith('m1', expect.any(String))
    expect(ws.getSelectedNodeIds()).toEqual(bagBeforeRegen)
    expect(toastCalls.some((m) => m.includes('当前场景材料已覆盖重新生成'))).toBe(true)

    ws.destroy?.()
  })
})
