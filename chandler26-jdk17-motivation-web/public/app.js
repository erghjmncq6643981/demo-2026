import { api, getToken, setToken } from '/src/shared/api.js'
import { buildDemoCalendar, demoState } from '/src/features/motivation/demo-data.js'
import { renderApp } from '/src/features/motivation/render.js'
import { formatDate, pointIcon } from '/src/shared/text.js'

const state = {
  ...structuredClone(demoState),
  user: getToken() ? { nickname: '正在恢复登录' } : null,
  monthDate: new Date(),
  todayKey: formatDate(new Date()),
  selectedChildId: demoState.children[0]?.id || null,
  selectedChild: demoState.children[0] || null,
  currentView: 'tasks',
  profileSubView: 'children',
  taskModalOpen: false,
  rewardModalOpen: false,
  childModalOpen: false,
  goalModalOpen: false,
  pointAdjustModalOpen: false,
  calendarDayModalOpen: false,
  calendarEventModalOpen: false,
  selectedCalendarDateKey: '',
  selectedCalendarEventTaskId: '',
  confirmDialog: null,
  editingTask: null,
  editingReward: null,
  editingChild: null,
  editingGoal: null,
  childFilters: {
    keyword: '',
    gender: '',
    status: '',
  },
  goalFilters: {
    keyword: '',
    status: '',
  },
  taskFilters: {
    keyword: '',
    category: '',
    periodType: '',
    pointType: '',
    status: '',
  },
  rewardFilters: {
    keyword: '',
    pointType: '',
    status: '',
  },
  connectionMessage: '正在尝试连接后端...',
  toast: '',
}

state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)

const actions = {
  setView(view) {
    state.currentView = view
    render()
  },
  previousMonth() {
    state.monthDate = new Date(state.monthDate.getFullYear(), state.monthDate.getMonth() - 1, 1)
    void loadCalendar()
  },
  nextMonth() {
    state.monthDate = new Date(state.monthDate.getFullYear(), state.monthDate.getMonth() + 1, 1)
    void loadCalendar()
  },
}

const app = document.querySelector('#app')

function render() {
  app.innerHTML = renderApp(state, actions)
  bindEvents()
}

function bindEvents() {
  document.querySelector('[data-form="auth"]')?.addEventListener('submit', handleAuthSubmit)
  document.querySelectorAll('[data-nav-view]').forEach((button) => {
    button.addEventListener('click', () => {
      state.currentView = button.dataset.navView || 'tasks'
      render()
    })
  })
  document.querySelectorAll('[data-profile-subview]').forEach((button) => {
    button.addEventListener('click', () => {
      state.profileSubView = button.dataset.profileSubview || 'children'
      render()
    })
  })
  document.querySelector('[data-action="refresh"]')?.addEventListener('click', () => loadInitialData(true))
  document.querySelector('[data-action="open-adjust-modal"]')?.addEventListener('click', openPointAdjustModal)
  document.querySelectorAll('[data-action="close-adjust-modal"]').forEach((button) => {
    button.addEventListener('click', closePointAdjustModal)
  })
  document.querySelector('[data-action="open-child-modal"]')?.addEventListener('click', () => openChildModal())
  document.querySelectorAll('[data-action="close-child-modal"]').forEach((button) => {
    button.addEventListener('click', closeChildModal)
  })
  document.querySelector('[data-action="open-goal-modal"]')?.addEventListener('click', () => openGoalModal())
  document.querySelectorAll('[data-action="close-goal-modal"]').forEach((button) => {
    button.addEventListener('click', closeGoalModal)
  })
  document.querySelector('[data-action="open-task-modal"]')?.addEventListener('click', () => openTaskModal())
  document.querySelectorAll('[data-action="close-task-modal"]').forEach((button) => {
    button.addEventListener('click', closeTaskModal)
  })
  document.querySelector('[data-action="open-reward-modal"]')?.addEventListener('click', () => openRewardModal())
  document.querySelectorAll('[data-action="close-reward-modal"]').forEach((button) => {
    button.addEventListener('click', closeRewardModal)
  })
  document.querySelector('[data-form="task"]')?.addEventListener('submit', handleCreateTaskSubmit)
  document.querySelector('[data-form="reward"]')?.addEventListener('submit', handleRewardSubmit)
  document.querySelector('[data-form="child"]')?.addEventListener('submit', handleChildSubmit)
  document.querySelector('[data-form="goal"]')?.addEventListener('submit', handleGoalSubmit)
  document.querySelector('[data-form="point-adjust"]')?.addEventListener('submit', handlePointAdjustSubmit)
  document.querySelectorAll('[name="rewardIconChoice"]').forEach((input) => {
    input.addEventListener('change', () => {
      const form = input.closest('form')
      if (form?.rewardIcon) {
        form.rewardIcon.value = input.value
      }
    })
  })
  bindTaskModalControls()
  document.querySelectorAll('[data-action="close-confirm-modal"]').forEach((button) => {
    button.addEventListener('click', closeConfirmDialog)
  })
  document.querySelector('[data-action="confirm-modal-submit"]')?.addEventListener('click', handleConfirmDialogSubmit)
  document.querySelectorAll('[data-action="select-child"]').forEach((button) => {
    button.addEventListener('click', () => selectChild(button.dataset.childId))
  })
  document.querySelectorAll('[data-action="edit-child"]').forEach((button) => {
    button.addEventListener('click', () => openChildModal(button.dataset.childId))
  })
  document.querySelectorAll('[data-action="edit-goal"]').forEach((button) => {
    button.addEventListener('click', () => openGoalModal(button.dataset.goalId))
  })
  document.querySelectorAll('[data-action="edit-task"]').forEach((button) => {
    button.addEventListener('click', () => openTaskModal(button.dataset.taskId))
  })
  document.querySelectorAll('[data-action="edit-reward"]').forEach((button) => {
    button.addEventListener('click', () => openRewardModal(button.dataset.rewardId))
  })
  document.querySelectorAll('[data-action="delete-child"]').forEach((button) => {
    button.addEventListener('click', () => openDeleteConfirm('child', button.dataset.childId))
  })
  document.querySelectorAll('[data-action="delete-goal"]').forEach((button) => {
    button.addEventListener('click', () => openDeleteConfirm('goal', button.dataset.goalId))
  })
  document.querySelectorAll('[data-action="delete-task"]').forEach((button) => {
    button.addEventListener('click', () => openDeleteConfirm('task', button.dataset.taskId))
  })
  document.querySelectorAll('[data-action="delete-reward"]').forEach((button) => {
    button.addEventListener('click', () => openDeleteConfirm('reward', button.dataset.rewardId))
  })
  document.querySelectorAll('[data-action="approve-exchange"]').forEach((button) => {
    button.addEventListener('click', () => handleRewardExchangeReview(button.dataset.exchangeId, true))
  })
  document.querySelectorAll('[data-action="reject-exchange"]').forEach((button) => {
    button.addEventListener('click', () => handleRewardExchangeReview(button.dataset.exchangeId, false))
  })
  document.querySelectorAll('[data-action="open-calendar-day"]').forEach((target) => {
    target.addEventListener('click', () => openCalendarDayModal(target.dataset.date))
  })
  document.querySelectorAll('[data-action="open-calendar-event"]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.stopPropagation()
      openCalendarEventModal(button.dataset.taskId, button.dataset.taskDate)
    })
  })
  document.querySelectorAll('[data-action="close-calendar-day-modal"]').forEach((button) => {
    button.addEventListener('click', closeCalendarDayModal)
  })
  document.querySelectorAll('[data-action="close-calendar-event-modal"]').forEach((button) => {
    button.addEventListener('click', closeCalendarEventModal)
  })
  bindFilterActions()
  document.querySelectorAll('[data-schedule-mode]').forEach((input) => {
    input.addEventListener('change', () => {
      const form = input.closest('form')
      form?.querySelectorAll('[data-schedule-panel]').forEach((panel) => {
        panel.classList.toggle('hidden', panel.dataset.schedulePanel !== input.value)
      })
    })
  })
  document.querySelectorAll('[data-action="exchange-reward"]').forEach((button) => {
    button.addEventListener('click', () => handleExchangeReward(button.dataset.rewardId))
  })
  document.querySelectorAll('[data-action="complete-task"]').forEach((button) => {
    button.addEventListener('click', () => handleCompleteTask(button.dataset.taskId, button.dataset.taskDate))
  })
}

function bindTaskModalControls() {
  const form = document.querySelector('[data-form="task"]')
  if (!form) return
  const syncPreview = () => {
    const pointType = String(new FormData(form).get('pointType') || 'STAR')
    const pointColor = String(form.pointColor?.value || '#ffd84d')
    const basePoints = Math.max(1, Math.min(99, Number(form.basePoints?.value || 1)))
    const preview = form.querySelector('[data-point-preview-icons]')
    if (preview) {
      preview.style.setProperty('--point-color', pointColor)
      preview.innerHTML = Array.from({ length: basePoints }, () => `<span class="score-icon">${pointIcon(pointType)}</span>`).join('')
    }
  }
  form.querySelectorAll('[name="pointType"]').forEach((input) => {
    input.addEventListener('change', syncPreview)
  })
  form.pointColor?.addEventListener('input', syncPreview)
  form.basePoints?.addEventListener('input', syncPreview)
  form.querySelectorAll('[name="dailyHours"]').forEach((input) => {
    input.addEventListener('change', () => syncDailyHourRange(form, input))
  })
  syncPreview()
  syncDailyHiddenHours(form)
}

function syncDailyHourRange(form, changedInput) {
  const picker = changedInput.closest('[data-daily-hour-picker]')
  if (!picker) return
  const hours = Array.from(picker.querySelectorAll('[name="dailyHours"]:not(:disabled)'))
  const selected = hours.filter((input) => input.checked).map((input) => Number(input.value))
  if (!selected.length) {
    changedInput.checked = true
    selected.push(Number(changedInput.value))
  }
  const startHour = Math.min(...selected)
  const endHour = Math.max(...selected)
  hours.forEach((input) => {
    const hour = Number(input.value)
    input.checked = hour >= startHour && hour <= endHour
  })
  syncDailyHiddenHours(form)
}

function syncDailyHiddenHours(form) {
  const selected = Array.from(form.querySelectorAll('[name="dailyHours"]:checked')).map((input) => Number(input.value))
  const startHour = selected.length ? Math.min(...selected) : 6
  const endHour = selected.length ? Math.max(...selected) : 22
  if (form.startHour) form.startHour.value = String(startHour)
  if (form.endHour) form.endHour.value = String(endHour)
}

function bindFilterActions() {
  document.querySelector('[data-action="search-child-filters"]')?.addEventListener('click', () => {
    state.childFilters = {
      keyword: readControlValue('[data-filter="child-keyword"]'),
      gender: readControlValue('[data-select="child-gender"]'),
      status: readControlValue('[data-select="child-status"]'),
    }
    render()
  })
  document.querySelector('[data-action="reset-child-filters"]')?.addEventListener('click', () => {
    state.childFilters = { keyword: '', gender: '', status: '' }
    render()
  })
  document.querySelector('[data-action="search-goal-filters"]')?.addEventListener('click', () => {
    state.goalFilters = {
      keyword: readControlValue('[data-filter="goal-keyword"]'),
      status: readControlValue('[data-select="goal-status"]'),
    }
    render()
  })
  document.querySelector('[data-action="reset-goal-filters"]')?.addEventListener('click', () => {
    state.goalFilters = { keyword: '', status: '' }
    render()
  })
  document.querySelector('[data-action="search-task-filters"]')?.addEventListener('click', () => {
    state.taskFilters = {
      keyword: readControlValue('[data-filter="task-keyword"]'),
      category: readControlValue('[data-select="task-category"]'),
      periodType: readControlValue('[data-select="task-period"]'),
      pointType: readControlValue('[data-select="task-point"]'),
      status: readControlValue('[data-select="task-status"]'),
    }
    render()
  })
  document.querySelector('[data-action="reset-task-filters"]')?.addEventListener('click', () => {
    state.taskFilters = { keyword: '', category: '', periodType: '', pointType: '', status: '' }
    render()
  })
  document.querySelector('[data-action="search-reward-filters"]')?.addEventListener('click', () => {
    state.rewardFilters = {
      keyword: readControlValue('[data-filter="reward-keyword"]'),
      pointType: readControlValue('[data-select="reward-point"]'),
      status: readControlValue('[data-select="reward-status"]'),
    }
    render()
  })
  document.querySelector('[data-action="reset-reward-filters"]')?.addEventListener('click', () => {
    state.rewardFilters = { keyword: '', pointType: '', status: '' }
    render()
  })
  document.querySelectorAll('[data-filter], [data-select]').forEach((control) => {
    control.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter') return
      event.preventDefault()
      const scope = control.dataset.filterScope || control.dataset.selectScope
      document.querySelector(`[data-action="search-${scope}-filters"]`)?.click()
    })
  })
}

async function handleAuthSubmit(event) {
  event.preventDefault()
  const submitter = event.submitter
  const formData = new FormData(event.currentTarget)
  const payload = {
    username: String(formData.get('username') || '').trim(),
    password: String(formData.get('password') || '').trim(),
    nickname: '星星家长',
  }
  const mode = submitter?.dataset.authMode || 'login'
  try {
    const response = mode === 'register' ? await api.register(payload) : await api.login(payload)
    setToken(response.token)
    state.user = response.user
    state.offline = false
    state.currentView = 'tasks'
    state.connectionMessage = '已登录真实后端'
    toast(mode === 'register' ? '注册成功' : '登录成功')
    await ensureStarterData()
    await loadInitialData(false)
  } catch (error) {
    toast(error.message)
    state.connectionMessage = '后端未登录，继续显示演示数据'
    render()
  }
}

async function loadInitialData(showToast = false, options = {}) {
  try {
    await api.health()
    const user = await api.profile().catch(() => null)
    if (!user) {
      state.connectionMessage = '后端已启动，请登录'
      state.offline = true
      state.user = null
      setToken('')
      if (showToast) toast('请先登录后端')
      render()
      return
    }
    state.user = user
    state.offline = false
    state.currentView = state.currentView || 'tasks'
    state.connectionMessage = '已连接后端 1.0 API'
    const children = await api.children()
    state.children = children
    state.selectedChild = children.find((child) => String(child.id) === String(state.selectedChildId)) || children[0] || null
    state.selectedChildId = state.selectedChild?.id || null
    if (!state.selectedChildId) {
      resetChildScopedData()
      if (options.skipStarterData) {
        if (showToast) toast('已刷新')
        render()
        return
      }
      await ensureStarterData()
      return loadInitialData(showToast)
    }
    await Promise.all([loadCoreData(), loadCalendar()])
    if (showToast) toast('已刷新')
    render()
  } catch {
    state.offline = true
    state.connectionMessage = '后端未启动或未登录，当前为演示模式'
    state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)
    render()
  }
}

async function loadCoreData() {
  const childId = state.selectedChildId
  if (!childId || state.offline) {
    return
  }
  const [goals, tasks, summary, ledger, rewards, exchanges] = await Promise.all([
    api.goals(childId),
    api.tasks(childId),
    api.pointSummary(childId),
    api.ledger(childId),
    api.rewards(childId),
    api.rewardExchanges(childId),
  ])
  state.goals = goals
  state.tasks = tasks
  state.balances = summary?.balances || []
  state.ledger = ledger
  state.rewards = rewards
  state.exchanges = exchanges
}

async function selectChild(childId) {
  const child = state.children.find((item) => String(item.id) === String(childId))
  if (!child) return
  state.selectedChild = child
  state.selectedChildId = child.id
  if (state.offline) {
    state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)
    render()
    return
  }
  try {
    await Promise.all([loadCoreData(), loadCalendar()])
    toast(`已切换到 ${child.nickname}`)
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function loadCalendar() {
  if (state.offline || !state.selectedChildId) {
    state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)
    render()
    return
  }
  state.calendarEvents = await api.calendar(state.selectedChildId, state.monthDate)
  render()
}

async function ensureStarterData() {
  let children = await api.children()
  if (!children.length) {
    await api.createChild({
      nickname: '小星',
      gender: 'UNKNOWN',
      remark: '1.0 默认孩子档案',
    })
    children = await api.children()
  }
  const child = children[0]
  state.selectedChildId = child.id
  state.selectedChild = child
  let goals = await api.goals(child.id)
  if (!goals.length) {
    await api.createGoal({
      childId: child.id,
      name: '自主管理小达人',
      description: '培养每日阅读、整理和运动习惯',
      goalColor: '#6c63ff',
      icon: '★',
      targetPoints: 220,
      sortNo: 1,
    })
    goals = await api.goals(child.id)
  }
  const tasks = await api.tasks(child.id)
  if (!tasks.length) {
    await createStarterTasks(child.id, goals[0].id)
  }
  const rewards = await api.rewards(child.id)
  if (!rewards.length) {
    await createStarterRewards(child.id)
  }
}

async function createStarterTasks(childId, goalId) {
  const tasks = [
    ['晨读 20 分钟', 'DAILY', '{"type":"DAILY","category":"STUDY","timeRange":{"startHour":7,"endHour":9},"requiredCount":1}', '#ff5c8a', 'STAR', '#ffd84d', 8, false],
    ['整理书包', 'DAILY', '{"type":"DAILY","category":"LIFE","timeRange":{"startHour":18,"endHour":21},"requiredCount":1}', '#34c759', 'FLOWER', '#ff6fa6', 5, true],
    ['练字作业', 'WEEKLY', '{"type":"WEEKLY","category":"STUDY","days":[1,4,5],"requiredCount":2}', '#6c63ff', 'CROWN', '#8b5cf6', 15, true],
    ['月初整理书桌', 'MONTHLY', '{"type":"MONTHLY","category":"LIFE","days":[1,15],"requiredCount":1}', '#ff9f43', 'STAR', '#30d5ff', 12, false],
  ]
  for (const [name, periodType, scheduleJson, taskColor, pointType, pointColor, basePoints, requireApproval] of tasks) {
    await api.createTask({
      childId,
      goalId,
      name,
      description: `${periodType} 任务，完成后获得 ${basePoints} 分`,
      periodType,
      scheduleJson,
      taskColor,
      pointType,
      pointColor,
      basePoints,
      requireApproval,
      allowPenalty: true,
    })
  }
}

async function createStarterRewards(childId) {
  const rewards = [
    ['积木礼物', '完成一周自主管理后兑换', '🎁', '#ff9f43', 'STAR', 80, true],
    ['周末冰淇淋', '每周限兑一次的小甜点', '🍦', '#34c759', 'FLOWER', 40, false],
    ['皇冠特权', '亲子游戏时间 30 分钟', '♛', '#6c63ff', 'CROWN', 1, true],
  ]
  for (const [name, description, rewardIcon, rewardColor, requiredPointType, requiredPoints, requireApproval] of rewards) {
    await api.createReward({
      childId,
      name,
      description,
      rewardIcon,
      rewardColor,
      requiredPointType,
      requiredPoints,
      stockTotal: 0,
      exchangeLimitType: 'UNLIMITED',
      exchangeLimitCount: 0,
      requireApproval,
    })
  }
}

async function handleCompleteTask(taskId, taskDate = state.todayKey) {
  const task = state.tasks.find((item) => String(item.id) === String(taskId))
  const event = state.calendarEvents.find((item) => String(item.taskId) === String(taskId) && item.taskDate === taskDate)
  if (!task && !event) return
  const taskMeta = task || event
  if (state.offline) {
    state.calendarEvents = state.calendarEvents.map((event) => (
      String(event.taskId) === String(taskId) && event.taskDate === taskDate
        ? { ...event, status: taskMeta.requireApproval ? 'SUBMITTED' : 'APPROVED', completionProgress: 100, scoreAwarded: taskMeta.basePoints, persisted: true }
        : event
    ))
    toast(taskMeta.requireApproval ? '演示：已提交审核' : '演示：任务已完成')
    render()
    return
  }
  try {
    await api.completeTask(taskId, { taskDate, completionProgress: 100 })
    await Promise.all([loadCoreData(), loadCalendar()])
    state.selectedCalendarDateKey = taskDate
    state.selectedCalendarEventTaskId = taskId
    toast(taskMeta.requireApproval ? '已提交审核' : '完成并入账')
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handleExchangeReward(rewardId) {
  const reward = state.rewards.find((item) => String(item.id) === String(rewardId))
  if (!reward) return
  state.confirmDialog = {
    title: '确认兑换奖励',
    message: `确认申请兑换「${reward.name}」吗？父母侧会看到待确认事项。`,
    confirmText: '确认兑换',
    variant: 'primary',
    action: async () => {
      if (state.offline) {
        state.exchanges = [
          {
            id: `demo-exchange-${Date.now()}`,
            rewardId: reward.id,
            childId: state.selectedChildId,
            rewardNameSnapshot: reward.name,
            rewardColorSnapshot: reward.rewardColor,
            rewardIconSnapshot: reward.rewardIcon,
            requiredPointType: reward.requiredPointType,
            requiredPointsSnapshot: reward.requiredPoints,
            status: 'REQUESTED',
            requestedAt: new Date().toISOString(),
            remark: '演示兑换申请',
          },
          ...state.exchanges,
        ]
        toast('演示：已提交兑换申请')
        return
      }
      await api.exchangeReward({ rewardId: reward.id, remark: '前端发起兑换' })
      await loadCoreData()
      toast(reward.requireApproval ? '已提交兑换申请' : '兑换完成')
    },
  }
  render()
}

async function handleRewardExchangeReview(exchangeId, approved) {
  const exchange = state.exchanges.find((item) => String(item.id) === String(exchangeId))
  if (!exchange) return
  state.confirmDialog = {
    title: approved ? '确认通过兑换' : '确认拒绝兑换',
    message: `${approved ? '通过' : '拒绝'}「${exchange.rewardNameSnapshot}」的兑换申请？`,
    confirmText: approved ? '通过' : '拒绝',
    variant: approved ? 'primary' : 'danger',
    action: async () => {
      if (state.offline) {
        state.exchanges = state.exchanges.map((item) => String(item.id) === String(exchangeId)
          ? { ...item, status: approved ? 'COMPLETED' : 'REJECTED', reviewedAt: new Date().toISOString() }
          : item)
        toast(approved ? '演示：兑换已通过' : '演示：兑换已拒绝')
        return
      }
      if (approved) {
        await api.approveRewardExchange(exchangeId, { remark: '父母确认兑换' })
      } else {
        await api.rejectRewardExchange(exchangeId, { remark: '父母拒绝兑换' })
      }
      await loadCoreData()
      toast(approved ? '兑换已通过' : '兑换已拒绝')
    },
  }
  render()
}

async function handlePointAdjustSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const direction = String(formData.get('direction') || 'PLUS')
  const amountValue = Math.max(1, Number(formData.get('amount') || 1))
  const reason = String(formData.get('reason') || '').trim()
  const amount = direction === 'MINUS' ? -amountValue : amountValue
  const pointType = String(formData.get('pointType') || 'STAR')
  if (!reason) {
    toast('请填写加减分原因')
    return
  }
  if (state.offline) {
    state.balances = upsertBalance(state.balances, pointType, amount)
    state.ledger = [
      {
        id: `demo-ledger-${Date.now()}`,
        pointType,
        changeAmount: amount,
        sourceName: '手动调整',
        reason,
        eventTime: new Date().toISOString(),
      },
      ...state.ledger,
    ]
    state.pointAdjustModalOpen = false
    toast(amount > 0 ? '演示：已手动加分' : '演示：已手动扣分')
    render()
    return
  }
  try {
    await api.manualAdjust(state.selectedChildId, { pointType, amount, reason })
    state.pointAdjustModalOpen = false
    await loadCoreData()
    toast(amount > 0 ? '手动加分已入账' : '手动扣分已入账')
    render()
  } catch (error) {
    toast(error.message)
    render()
  }
}

function openChildModal(childId = null) {
  state.editingChild = childId ? state.children.find((child) => String(child.id) === String(childId)) || null : null
  state.childModalOpen = true
  render()
}

function closeChildModal() {
  state.childModalOpen = false
  state.editingChild = null
  render()
}

function openGoalModal(goalId = null) {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  state.editingGoal = goalId ? state.goals.find((goal) => String(goal.id) === String(goalId)) || null : null
  state.goalModalOpen = true
  render()
}

function closeGoalModal() {
  state.goalModalOpen = false
  state.editingGoal = null
  render()
}

function openPointAdjustModal() {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  state.pointAdjustModalOpen = true
  render()
}

function closePointAdjustModal() {
  state.pointAdjustModalOpen = false
  render()
}

function openTaskModal(taskId = null) {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  if (!state.goals.length) {
    toast('请先新增成长目标')
    return
  }
  state.editingTask = taskId ? state.tasks.find((task) => String(task.id) === String(taskId)) || null : null
  state.taskModalOpen = true
  render()
}

function closeTaskModal() {
  state.taskModalOpen = false
  state.editingTask = null
  render()
}

function openRewardModal(rewardId = null) {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  state.editingReward = rewardId ? state.rewards.find((reward) => String(reward.id) === String(rewardId)) || null : null
  state.rewardModalOpen = true
  render()
}

function closeRewardModal() {
  state.rewardModalOpen = false
  state.editingReward = null
  render()
}

function openCalendarDayModal(dateKey) {
  if (!dateKey) return
  state.selectedCalendarDateKey = dateKey
  state.calendarDayModalOpen = true
  state.calendarEventModalOpen = false
  state.selectedCalendarEventTaskId = ''
  render()
}

function closeCalendarDayModal() {
  state.calendarDayModalOpen = false
  state.selectedCalendarDateKey = ''
  state.selectedCalendarEventTaskId = ''
  render()
}

function openCalendarEventModal(taskId, dateKey) {
  if (!taskId || !dateKey) return
  state.selectedCalendarDateKey = dateKey
  state.selectedCalendarEventTaskId = taskId
  state.calendarDayModalOpen = true
  state.calendarEventModalOpen = true
  render()
}

function closeCalendarEventModal() {
  state.calendarEventModalOpen = false
  state.selectedCalendarEventTaskId = ''
  render()
}

function openDeleteConfirm(type, id) {
  const config = getDeleteTarget(type, id)
  if (!config) return
  state.confirmDialog = {
    title: `删除${config.label}`,
    message: `确认删除「${config.name}」吗？删除后列表将不再展示。`,
    confirmText: '确认删除',
    variant: 'danger',
    action: async () => deleteResource(type, id),
  }
  render()
}

function closeConfirmDialog() {
  state.confirmDialog = null
  render()
}

async function handleConfirmDialogSubmit() {
  const dialog = state.confirmDialog
  if (!dialog?.action) return
  state.confirmDialog = null
  try {
    await dialog.action()
    render()
  } catch (error) {
    toast(error.message)
  }
}

async function handleCreateTaskSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const taskId = String(formData.get('taskId') || '').trim()
  const periodType = String(formData.get('periodType') || 'DAILY')
  const category = String(formData.get('taskCategory') || 'HABIT')
  const weeklyDays = formData.getAll('weekDays').map((value) => Number(value)).filter((value) => value >= 1 && value <= 7)
  const monthDays = formData.getAll('monthDays').map((value) => Number(value)).filter((value) => value >= 1 && value <= 31)
  const dailyHours = formData.getAll('dailyHours').map((value) => Number(value)).filter((value) => value >= 6 && value <= 22)
  const startHour = dailyHours.length ? Math.min(...dailyHours) : Number(formData.get('startHour') || 6)
  const endHour = dailyHours.length ? Math.max(...dailyHours) : Number(formData.get('endHour') || 22)
  const weeklyRequiredCount = Math.max(1, Number(formData.get('weeklyRequiredCount') || 1))
  const monthlyRequiredCount = Math.max(1, Number(formData.get('monthlyRequiredCount') || 1))
  const scheduleJson = buildTaskScheduleJson({
    periodType,
    category,
    startHour,
    endHour,
    weeklyDays,
    monthDays,
    weeklyRequiredCount,
    monthlyRequiredCount,
  })
  const pointType = String(formData.get('pointType') || 'STAR')
  const payload = {
    childId: state.selectedChildId,
    goalId: formData.get('goalId') || state.goals[0]?.id,
    name: String(formData.get('name') || '').trim(),
    description: String(formData.get('description') || '').trim(),
    periodType,
    scheduleJson,
    taskColor: String(formData.get('taskColor') || '#30d5ff'),
    pointType,
    pointColor: String(formData.get('pointColor') || '#ffd84d'),
    basePoints: Number(formData.get('basePoints') || 1),
    requireApproval: String(formData.get('requireApproval') || 'false') === 'true',
    allowPenalty: true,
  }
  if (!payload.name) {
    toast('请填写任务名称')
    return
  }
  if (periodType === 'WEEKLY' && !weeklyDays.length) {
    toast('请选择每周执行的星期')
    return
  }
  if (periodType === 'MONTHLY' && !monthDays.length) {
    toast('请选择每月执行的日期')
    return
  }
  if (periodType === 'DAILY' && startHour > endHour) {
    toast('开始时间不能晚于结束时间')
    return
  }
  if (periodType === 'DAILY' && (!dailyHours.length || startHour < 6 || endHour > 22)) {
    toast('每日时间段请选择 06:00 到 22:00 之间的方块')
    return
  }
  if (periodType === 'WEEKLY' && weeklyRequiredCount > weeklyDays.length) {
    toast('每周完成次数不能大于已选择的星期数量')
    return
  }
  if (periodType === 'MONTHLY' && monthlyRequiredCount > monthDays.length) {
    toast('每月完成次数不能大于已选择的日期数量')
    return
  }
  if (state.offline) {
    const nextTask = {
      id: taskId || `demo-${Date.now()}`,
      ...payload,
      status: 'ACTIVE',
      requireApproval: payload.requireApproval ? 1 : 0,
    }
    state.tasks = taskId
      ? state.tasks.map((task) => String(task.id) === taskId ? nextTask : task)
      : [...state.tasks, nextTask]
    state.taskModalOpen = false
    state.editingTask = null
    state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)
    toast(taskId ? '演示：任务已修改' : '演示：任务已创建')
    render()
    return
  }
  try {
    if (taskId) {
      await api.updateTask(taskId, payload)
    } else {
      await api.createTask(payload)
    }
    state.taskModalOpen = false
    state.editingTask = null
    await Promise.all([loadCoreData(), loadCalendar()])
    toast(taskId ? '任务已修改' : '任务已创建')
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handleChildSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const childId = String(formData.get('childId') || '').trim()
  const payload = {
    nickname: String(formData.get('nickname') || '').trim(),
    avatarUrl: String(formData.get('avatarUrl') || '').trim(),
    birthday: nullableDate(formData.get('birthday')),
    gender: String(formData.get('gender') || 'UNKNOWN'),
    remark: String(formData.get('remark') || '').trim(),
  }
  if (!payload.nickname) {
    toast('请填写孩子昵称')
    return
  }
  if (state.offline) {
    const nextChild = {
      id: childId || `demo-child-${Date.now()}`,
      ...payload,
      status: 'ACTIVE',
    }
    state.children = childId
      ? state.children.map((child) => String(child.id) === childId ? nextChild : child)
      : [...state.children, nextChild]
    state.selectedChild = nextChild
    state.selectedChildId = nextChild.id
    state.childModalOpen = false
    state.editingChild = null
    toast(childId ? '演示：孩子档案已修改' : '演示：孩子档案已创建')
    render()
    return
  }
  try {
    const savedChild = childId
      ? await api.updateChild(childId, payload)
      : await api.createChild(payload)
    state.selectedChildId = savedChild.id
    state.childModalOpen = false
    state.editingChild = null
    await loadInitialData(false)
    toast(childId ? '孩子档案已修改' : '孩子档案已创建')
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handleGoalSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const goalId = String(formData.get('goalId') || '').trim()
  const payload = {
    childId: state.selectedChildId,
    name: String(formData.get('name') || '').trim(),
    description: String(formData.get('description') || '').trim(),
    goalColor: String(formData.get('goalColor') || '#6c63ff'),
    icon: String(formData.get('icon') || '★').trim(),
    startDate: nullableDate(formData.get('startDate')),
    endDate: nullableDate(formData.get('endDate')),
    targetPoints: Number(formData.get('targetPoints') || 0),
    sortNo: Number(formData.get('sortNo') || 0),
  }
  if (!payload.name) {
    toast('请填写目标名称')
    return
  }
  if (!payload.childId) {
    toast('请先选择孩子档案')
    return
  }
  if (state.offline) {
    const nextGoal = {
      id: goalId || `demo-goal-${Date.now()}`,
      ...payload,
      status: 'ACTIVE',
    }
    state.goals = goalId
      ? state.goals.map((goal) => String(goal.id) === goalId ? nextGoal : goal)
      : [...state.goals, nextGoal]
    state.goalModalOpen = false
    state.editingGoal = null
    toast(goalId ? '演示：成长目标已修改' : '演示：成长目标已创建')
    render()
    return
  }
  try {
    if (goalId) {
      await api.updateGoal(goalId, payload)
    } else {
      await api.createGoal(payload)
    }
    state.goalModalOpen = false
    state.editingGoal = null
    await loadCoreData()
    toast(goalId ? '成长目标已修改' : '成长目标已创建')
    render()
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handleRewardSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const rewardId = String(formData.get('rewardId') || '').trim()
  const payload = {
    childId: state.selectedChildId,
    name: String(formData.get('name') || '').trim(),
    description: String(formData.get('description') || '').trim(),
    rewardIcon: String(formData.get('rewardIcon') || '🎁').trim(),
    rewardColor: String(formData.get('rewardColor') || '#ff9f43'),
    requiredPointType: String(formData.get('requiredPointType') || 'STAR'),
    requiredPoints: Number(formData.get('requiredPoints') || 1),
    stockTotal: Number(formData.get('stockTotal') || 0),
    exchangeLimitType: String(formData.get('exchangeLimitType') || 'UNLIMITED'),
    exchangeLimitCount: Number(formData.get('exchangeLimitCount') || 0),
    requireApproval: formData.get('requireApproval') === 'on',
  }
  if (!payload.name) {
    toast('请填写奖励名称')
    return
  }
  if (state.offline) {
    const nextReward = {
      id: rewardId || `demo-reward-${Date.now()}`,
      ...payload,
      status: 'ACTIVE',
      stockRemaining: payload.stockTotal,
      requireApproval: payload.requireApproval ? 1 : 0,
    }
    state.rewards = rewardId
      ? state.rewards.map((reward) => String(reward.id) === rewardId ? nextReward : reward)
      : [...state.rewards, nextReward]
    state.rewardModalOpen = false
    state.editingReward = null
    toast(rewardId ? '演示：奖励已修改' : '演示：奖励已创建')
    render()
    return
  }
  try {
    if (rewardId) {
      await api.updateReward(rewardId, payload)
    } else {
      await api.createReward(payload)
    }
    state.rewardModalOpen = false
    state.editingReward = null
    await loadCoreData()
    toast(rewardId ? '奖励已修改' : '奖励已创建')
    render()
  } catch (error) {
    toast(error.message)
    render()
  }
}

function toast(message) {
  state.toast = message
  render()
  window.clearTimeout(toast.timer)
  toast.timer = window.setTimeout(() => {
    state.toast = ''
    render()
  }, 2200)
}

function nullableDate(value) {
  const text = String(value || '').trim()
  return text || null
}

function buildTaskScheduleJson(options) {
  const base = {
    type: options.periodType,
    category: options.category,
  }
  if (options.periodType === 'DAILY') {
    return JSON.stringify({
      ...base,
      timeRange: {
        startHour: options.startHour,
        endHour: options.endHour,
      },
      requiredCount: 1,
    })
  }
  if (options.periodType === 'MONTHLY') {
    return JSON.stringify({
      ...base,
      days: options.monthDays,
      requiredCount: options.monthlyRequiredCount,
    })
  }
  return JSON.stringify({
    ...base,
    days: options.weeklyDays,
    requiredCount: options.weeklyRequiredCount,
  })
}

function readControlValue(selector) {
  return String(document.querySelector(selector)?.value || '').trim()
}

function getDeleteTarget(type, id) {
  const targetMap = {
    child: {
      label: '孩子档案',
      item: state.children.find((child) => String(child.id) === String(id)),
      name: (item) => item.nickname,
    },
    goal: {
      label: '成长目标',
      item: state.goals.find((goal) => String(goal.id) === String(id)),
      name: (item) => item.name,
    },
    task: {
      label: '任务',
      item: state.tasks.find((task) => String(task.id) === String(id)),
      name: (item) => item.name,
    },
    reward: {
      label: '奖励',
      item: state.rewards.find((reward) => String(reward.id) === String(id)),
      name: (item) => item.name,
    },
  }
  const target = targetMap[type]
  if (!target?.item) return null
  return {
    label: target.label,
    name: target.name(target.item),
  }
}

async function deleteResource(type, id) {
  if (state.offline) {
    deleteOfflineResource(type, id)
    toast('演示：删除成功')
    return
  }
  if (type === 'child') {
    await api.deleteChild(id)
    if (String(state.selectedChildId) === String(id)) {
      state.selectedChildId = null
      state.selectedChild = null
    }
    await loadInitialData(false, { skipStarterData: true })
    toast('孩子档案已删除')
    return
  }
  if (type === 'goal') {
    await api.deleteGoal(id)
    await loadCoreData()
    toast('成长目标已删除')
    return
  }
  if (type === 'task') {
    await api.deleteTask(id)
    await Promise.all([loadCoreData(), loadCalendar()])
    toast('任务已删除')
    return
  }
  if (type === 'reward') {
    await api.deleteReward(id)
    await loadCoreData()
    toast('奖励已删除')
  }
}

function deleteOfflineResource(type, id) {
  if (type === 'child') {
    state.children = state.children.filter((child) => String(child.id) !== String(id))
    state.selectedChild = state.children[0] || null
    state.selectedChildId = state.selectedChild?.id || null
    if (!state.selectedChildId) {
      resetChildScopedData()
    }
  }
  if (type === 'goal') {
    state.goals = state.goals.filter((goal) => String(goal.id) !== String(id))
  }
  if (type === 'task') {
    state.tasks = state.tasks.filter((task) => String(task.id) !== String(id))
    state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)
  }
  if (type === 'reward') {
    state.rewards = state.rewards.filter((reward) => String(reward.id) !== String(id))
  }
}

function resetChildScopedData() {
  state.goals = []
  state.tasks = []
  state.balances = []
  state.ledger = []
  state.rewards = []
  state.exchanges = []
  state.calendarEvents = []
  state.calendarDayModalOpen = false
  state.calendarEventModalOpen = false
  state.selectedCalendarDateKey = ''
  state.selectedCalendarEventTaskId = ''
}

function upsertBalance(balances, pointType, amount) {
  const existing = balances.find((balance) => balance.pointType === pointType)
  if (!existing) {
    return [
      ...balances,
      {
        pointType,
        balance: Math.max(0, amount),
        earnedTotal: amount > 0 ? amount : 0,
        spentTotal: amount < 0 ? Math.abs(amount) : 0,
      },
    ]
  }
  return balances.map((balance) => {
    if (balance.pointType !== pointType) return balance
    return {
      ...balance,
      balance: Math.max(0, Number(balance.balance || 0) + amount),
      earnedTotal: Number(balance.earnedTotal || 0) + (amount > 0 ? amount : 0),
      spentTotal: Number(balance.spentTotal || 0) + (amount < 0 ? Math.abs(amount) : 0),
    }
  })
}

render()
void loadInitialData(false)
