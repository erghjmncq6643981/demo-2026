import { api, getToken, setToken } from '/src/shared/api.js'
import { buildDemoCalendar, demoState } from '/src/features/motivation/demo-data.js'
import { renderApp } from '/src/features/motivation/render.js'
import { formatDate, fulfillmentStatusName, pointName } from '/src/shared/text.js'

const SIDEBAR_KEY = 'motivation.sidebarCollapsed'
const ACCOUNT_DRAFT_KEY = 'motivation.accountDraft'
const SYSTEM_CONFIG_KEY = 'motivation.systemConfig'
const AVATAR_MAX_BYTES = 1024 * 1024
const DEFAULT_CURRENCIES = [
  { pointType: 'STAR', name: '星星', icon: '★', color: '#f59e0b', exchangeWeight: 1, status: 'ACTIVE', sortNo: 1 },
  { pointType: 'FLOWER', name: '红花', icon: '✿', color: '#ec4899', exchangeWeight: 10, status: 'ACTIVE', sortNo: 2 },
  { pointType: 'CROWN', name: '皇冠', icon: '♛', color: '#7c3aed', exchangeWeight: 100, status: 'ACTIVE', sortNo: 3 },
]
const isCompactLayout = () => window.matchMedia('(max-width: 1180px)').matches
const initialSidebarCollapsed = localStorage.getItem(SIDEBAR_KEY) === '1' || isCompactLayout()
const defaultSystemConfig = {
  calendarDateSize: 20,
  calendarDateColor: '#1f2937',
  calendarTodayColor: '#4338ca',
}
const storedSystemConfig = readSystemConfigFromStorage()

const state = {
  ...structuredClone(demoState),
  user: getToken() ? { nickname: '正在恢复登录' } : null,
  monthDate: new Date(),
  todayKey: formatDate(new Date()),
  selectedChildId: demoState.children[0]?.id || null,
  selectedChild: demoState.children[0] || null,
  currentView: 'profile',
  profileSubView: 'home',
  accountModalOpen: false,
  accountDraft: readAccountDraftFromStorage(),
  avatarObjectUrls: {
    account: '',
    children: {},
  },
  avatarEditor: createEmptyAvatarEditor(),
  systemConfig: { ...defaultSystemConfig, ...storedSystemConfig },
  sidebarCollapsed: initialSidebarCollapsed,
  calendarViewMode: 'month',
  calendarEventKind: 'tasks',
  shouldFocusToday: false,
  taskModalOpen: false,
  rewardModalOpen: false,
  pointCurrencyModalOpen: false,
  registerModalOpen: false,
  childModalOpen: false,
  goalModalOpen: false,
  pointAdjustModalOpen: false,
  balanceModalOpen: false,
  rewardExchangeModalOpen: false,
  taskCheckInModalOpen: false,
  calendarDayModalOpen: false,
  calendarEventModalOpen: false,
  selectedCalendarDateKey: '',
  selectedCalendarEventId: '',
  selectedBalancePointType: '',
  selectedRewardId: '',
  selectedCheckInTaskId: '',
  selectedCheckInTaskDate: '',
  selectedCheckInRewardCount: 0,
  exchangeSuccess: null,
  rewardExchangeSuccess: null,
  loginCarouselIndex: 0,
  loginCarouselPinned: false,
  rewardIconPickerOpen: false,
  childAccountDraftEnabled: false,
  confirmDialog: null,
  editingTask: null,
  editingReward: null,
  editingPointCurrency: null,
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
  currencyFilters: {
    keyword: '',
    pointType: '',
    status: '',
  },
  filterAdvanced: {
    child: false,
    goal: false,
    task: false,
    reward: false,
    currency: false,
  },
  todoExpanded: {
    task: false,
    reward: false,
  },
  pointExchangeRule: demoState.pointExchangeRule || { starWeight: 1, flowerWeight: 10, crownWeight: 100 },
  pointCurrencies: normalizePointCurrencies(demoState.pointCurrencies, demoState.pointExchangeRule),
  connectionMessage: '正在尝试连接后端...',
  toast: '',
}

state.calendarEvents = buildDemoCalendar(state.tasks, state.monthDate)

function readAccountDraftFromStorage() {
  try {
    return JSON.parse(localStorage.getItem(ACCOUNT_DRAFT_KEY) || '{}') || {}
  } catch {
    return {}
  }
}

function readSystemConfigFromStorage() {
  try {
    return JSON.parse(localStorage.getItem(SYSTEM_CONFIG_KEY) || '{}') || {}
  } catch {
    return {}
  }
}

function persistAccountDraft(draft) {
  const value = {
    nickname: String(draft?.nickname || '').trim(),
  }
  localStorage.setItem(ACCOUNT_DRAFT_KEY, JSON.stringify(value))
  state.accountDraft = value
}

function createEmptyAvatarEditor() {
  return {
    open: false,
    scope: '',
    childId: '',
    sourceObjectUrl: '',
    processedPreviewUrl: '',
    processedFile: null,
    image: null,
    fileName: '',
    naturalWidth: 0,
    naturalHeight: 0,
    zoom: 1,
    offsetX: 0,
    offsetY: 0,
    dragging: false,
    dragStartX: 0,
    dragStartY: 0,
    dragOriginX: 0,
    dragOriginY: 0,
  }
}

function getAvatarPreviewUrl(scope, childId = '') {
  const editor = state.avatarEditor || {}
  if (editor.scope === scope && String(editor.childId || '') === String(childId || '')) {
    return editor.processedPreviewUrl || ''
  }
  if (scope === 'account') {
    return state.avatarObjectUrls.account || ''
  }
  return state.avatarObjectUrls.children?.[childId] || ''
}

function isSameAvatarDraft(editor, scope, childId = '') {
  return editor?.scope === scope && String(editor.childId || '') === String(childId || '')
}

function clearAvatarEditorDraft(options = {}) {
  const editor = state.avatarEditor || createEmptyAvatarEditor()
  const keepProcessedDraft = Boolean(options.keepProcessedDraft)
  if (editor.sourceObjectUrl) {
    URL.revokeObjectURL(editor.sourceObjectUrl)
  }
  if (editor.processedPreviewUrl && !keepProcessedDraft) {
    URL.revokeObjectURL(editor.processedPreviewUrl)
  }
  const next = createEmptyAvatarEditor()
  next.scope = options.scope || ''
  next.childId = options.childId || ''
  if (keepProcessedDraft) {
    next.processedPreviewUrl = editor.processedPreviewUrl || ''
    next.processedFile = editor.processedFile || null
  }
  state.avatarEditor = next
}

function persistSystemConfig(config) {
  localStorage.setItem(SYSTEM_CONFIG_KEY, JSON.stringify(config))
  state.systemConfig = { ...defaultSystemConfig, ...config }
}

const actions = {
  setView(view) {
    state.currentView = normalizeViewForRole(view)
    render()
  },
  previousMonth() {
    if (state.calendarViewMode === 'week') {
      state.monthDate = addDays(state.monthDate, -7)
    } else {
      state.monthDate = new Date(state.monthDate.getFullYear(), state.monthDate.getMonth() - 1, 1)
    }
    void loadCalendar()
  },
  nextMonth() {
    if (state.calendarViewMode === 'week') {
      state.monthDate = addDays(state.monthDate, 7)
    } else {
      state.monthDate = new Date(state.monthDate.getFullYear(), state.monthDate.getMonth() + 1, 1)
    }
    void loadCalendar()
  },
  goToToday() {
    state.todayKey = formatDate(new Date())
    state.monthDate = new Date()
    state.shouldFocusToday = true
    void loadCalendar()
  },
  setCalendarViewMode(viewMode) {
    state.calendarViewMode = viewMode === 'week' ? 'week' : 'month'
    render()
  },
  setCalendarEventKind(eventKind) {
    state.calendarEventKind = ['tasks', 'points', 'rewards'].includes(eventKind) ? eventKind : 'tasks'
    render()
  },
}

const app = document.querySelector('#app')

function render() {
  app.innerHTML = renderApp(state, actions)
  bindEvents()
  focusTodayAfterRender()
}

function bindEvents() {
  initLoginCarousel()
  document.querySelector('[data-form="auth"]')?.addEventListener('submit', handleAuthSubmit)
  document.querySelector('[data-action="open-register-modal"]')?.addEventListener('click', openRegisterModal)
  document.querySelectorAll('[data-action="close-register-modal"]').forEach((button) => {
    button.addEventListener('click', closeRegisterModal)
  })
  document.querySelector('[data-form="register"]')?.addEventListener('submit', handleRegisterSubmit)
  document.querySelectorAll('[data-login-carousel-nav]').forEach((button) => {
    button.addEventListener('click', () => setLoginCarouselIndex(Number(button.dataset.loginCarouselNav || 0), true))
  })
  document.querySelectorAll('[data-action="login-carousel-prev"]').forEach((button) => {
    button.addEventListener('click', () => stepLoginCarousel(-1))
  })
  document.querySelectorAll('[data-action="login-carousel-next"]').forEach((button) => {
    button.addEventListener('click', () => stepLoginCarousel(1))
  })
  document.querySelector('[data-action="toggle-sidebar"]')?.addEventListener('click', toggleSidebar)
  document.querySelectorAll('[data-action="close-sidebar"]').forEach((button) => {
    button.addEventListener('click', () => setSidebarCollapsed(true))
  })
  document.querySelector('[data-action="calendar-prev"]')?.addEventListener('click', actions.previousMonth)
  document.querySelector('[data-action="calendar-next"]')?.addEventListener('click', actions.nextMonth)
  document.querySelector('[data-action="calendar-today"]')?.addEventListener('click', actions.goToToday)
  document.querySelectorAll('[data-calendar-view-mode]').forEach((button) => {
    button.addEventListener('click', () => actions.setCalendarViewMode(button.dataset.calendarViewMode))
  })
  document.querySelectorAll('[data-calendar-event-kind]').forEach((button) => {
    button.addEventListener('click', () => actions.setCalendarEventKind(button.dataset.calendarEventKind))
  })
  document.querySelectorAll('[data-nav-view]').forEach((button) => {
    button.addEventListener('click', () => {
      state.currentView = normalizeViewForRole(button.dataset.navView || 'profile')
      collapseSidebarOnMobile()
      render()
    })
  })
  document.querySelectorAll('[data-profile-subview]').forEach((button) => {
    button.addEventListener('click', () => {
      state.profileSubView = normalizeProfileSubViewForRole(button.dataset.profileSubview || 'home')
      render()
    })
  })
  document.querySelector('[data-action="open-account-modal"]')?.addEventListener('click', openAccountModal)
  document.querySelectorAll('[data-action="close-account-modal"]').forEach((button) => {
    button.addEventListener('click', closeAccountModal)
  })
  document.querySelector('[data-action="logout"]')?.addEventListener('click', logout)
  document.querySelector('[data-action="open-adjust-modal"]')?.addEventListener('click', openPointAdjustModal)
  document.querySelectorAll('[data-action="close-adjust-modal"]').forEach((button) => {
    button.addEventListener('click', closePointAdjustModal)
  })
  document.querySelectorAll('[data-action="open-balance-modal"]').forEach((button) => {
    button.addEventListener('click', () => openBalanceModal(button.dataset.pointType))
  })
  document.querySelectorAll('[data-action="close-balance-modal"]').forEach((button) => {
    button.addEventListener('click', closeBalanceModal)
  })
  document.querySelector('[data-form="point-exchange"]')?.addEventListener('submit', handlePointExchangeSubmit)
  document.querySelectorAll('[data-action="quick-point-exchange"]').forEach((button) => {
    button.addEventListener('click', () => handleQuickPointExchange(button.dataset.toPointType, Number(button.dataset.fromAmount || 1)))
  })
  document.querySelector('[data-form="point-exchange-rule"]')?.addEventListener('submit', handlePointExchangeRuleSubmit)
  document.querySelector('[data-action="open-currency-modal"]')?.addEventListener('click', () => openPointCurrencyModal())
  document.querySelectorAll('[data-action="close-currency-modal"]').forEach((button) => {
    button.addEventListener('click', closePointCurrencyModal)
  })
  document.querySelector('[data-form="point-currency"]')?.addEventListener('submit', handlePointCurrencySubmit)
  document.querySelectorAll('[data-action="open-currency-icon-picker"]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault()
      const field = button.closest('.icon-field')
      field?.querySelector('.icon-popover')?.classList.toggle('hidden')
    })
  })
  document.querySelectorAll('[data-action="select-currency-icon"]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault()
      const icon = button.dataset.currencyIcon || '★'
      const field = button.closest('.icon-field')
      const form = button.closest('form')
      if (form?.icon) {
        form.icon.value = icon
      }
      field?.querySelector('.icon-select-btn')?.replaceChildren(document.createTextNode(icon))
      form?.querySelector('[data-currency-preview-icon]')?.replaceChildren(document.createTextNode(icon))
      field?.querySelectorAll('[data-action="select-currency-icon"]').forEach((item) => {
        item.classList.toggle('active', item === button)
      })
      field?.querySelector('.icon-popover')?.classList.add('hidden')
    })
  })
  document.querySelectorAll('[data-action="go-reward-store"]').forEach((button) => {
    button.addEventListener('click', goRewardStore)
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
  document.querySelectorAll('[data-action="open-task-modal-for-date"]').forEach((button) => {
    button.addEventListener('click', () => openTaskModal(null, { date: button.dataset.taskDate }))
  })
  document.querySelectorAll('[data-action="close-task-modal"]').forEach((button) => {
    button.addEventListener('click', closeTaskModal)
  })
  document.querySelector('[data-action="open-reward-modal"]')?.addEventListener('click', () => openRewardModal())
  document.querySelectorAll('[data-action="close-reward-modal"]').forEach((button) => {
    button.addEventListener('click', closeRewardModal)
  })
  document.querySelectorAll('[data-action="open-reward-icon-picker"]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault()
      const field = button.closest('.icon-field')
      field?.querySelector('.icon-popover')?.classList.toggle('hidden')
    })
  })
  document.querySelectorAll('[data-action="select-reward-icon"]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.preventDefault()
      const icon = button.dataset.rewardIcon || '🎁'
      const field = button.closest('.icon-field')
      const form = button.closest('form')
      if (form?.rewardIcon) {
        form.rewardIcon.value = icon
      }
      field?.querySelector('.icon-select-btn')?.replaceChildren(document.createTextNode(icon))
      field?.querySelectorAll('[data-action="select-reward-icon"]').forEach((item) => {
        item.classList.toggle('active', item === button)
      })
      field?.querySelector('.icon-popover')?.classList.add('hidden')
    })
  })
  document.querySelectorAll('[data-action="close-reward-exchange-modal"]').forEach((button) => {
    button.addEventListener('click', closeRewardExchangeModal)
  })
  document.querySelectorAll('[data-action="select-reward-payment"]').forEach((button) => {
    button.addEventListener('click', () => handleExchangeReward(state.selectedRewardId, button.dataset.paymentPointType))
  })
  document.querySelector('[data-form="task"]')?.addEventListener('submit', handleCreateTaskSubmit)
  document.querySelector('[data-form="reward"]')?.addEventListener('submit', handleRewardSubmit)
  document.querySelector('[data-form="child"]')?.addEventListener('submit', handleChildSubmit)
  document.querySelector('[data-form="goal"]')?.addEventListener('submit', handleGoalSubmit)
  document.querySelector('[data-form="point-adjust"]')?.addEventListener('submit', handlePointAdjustSubmit)
  document.querySelector('[data-form="account"]')?.addEventListener('submit', handleAccountSubmit)
  document.querySelectorAll('[data-avatar-file]').forEach((input) => {
    input.addEventListener('change', handleAvatarFilePreview)
  })
  document.querySelectorAll('[data-avatar-editor-handle]').forEach((handle) => {
    handle.addEventListener('pointerdown', startAvatarEditorDrag)
  })
  document.querySelectorAll('[data-action="close-avatar-editor"]').forEach((button) => {
    button.addEventListener('click', closeAvatarEditor)
  })
  document.querySelectorAll('[data-action="save-avatar-editor"]').forEach((button) => {
    button.addEventListener('click', saveAvatarEditor)
  })
  document.querySelectorAll('[data-action="avatar-zoom-in"]').forEach((button) => {
    button.addEventListener('click', () => changeAvatarZoom(0.08))
  })
  document.querySelectorAll('[data-action="avatar-zoom-out"]').forEach((button) => {
    button.addEventListener('click', () => changeAvatarZoom(-0.08))
  })
  document.querySelectorAll('[data-action="avatar-reset"]').forEach((button) => {
    button.addEventListener('click', resetAvatarEditorTransform)
  })
  document.querySelector('[data-form="system-config"]')?.addEventListener('submit', handleSystemConfigSubmit)
  document.querySelector('[data-action="save-system-config"]')?.addEventListener('click', () => {
    document.querySelector('[data-form="system-config"]')?.requestSubmit()
  })
  document.querySelector('[name="calendarDateSize"]')?.addEventListener('input', (event) => {
    const input = event.currentTarget
    const preview = document.querySelector('[data-system-config-preview]')
    const value = Number(input.value || defaultSystemConfig.calendarDateSize)
    document.querySelector('[data-system-config-size]')?.replaceChildren(document.createTextNode(`${value}px`))
    if (preview) {
      preview.style.setProperty('--calendar-date-size', `${value}px`)
    }
  })
  document.querySelector('[name="calendarDateColor"]')?.addEventListener('input', (event) => {
    const input = event.currentTarget
    const preview = document.querySelector('[data-system-config-preview]')
    if (preview) {
      preview.style.setProperty('--calendar-date-color', input.value || defaultSystemConfig.calendarDateColor)
    }
  })
  document.querySelector('[name="createChildAccount"]')?.addEventListener('change', () => {
    const enabled = Boolean(document.querySelector('[name="createChildAccount"]')?.checked)
    const fields = document.querySelector('.child-account-fields')
    fields?.classList.toggle('hidden', !enabled)
    if (!enabled && fields) {
      const childUsername = fields.querySelector('[name="childUsername"]')
      const childPassword = fields.querySelector('[name="childPassword"]')
      if (childUsername) childUsername.value = ''
      if (childPassword) childPassword.value = ''
    }
  })
  bindTaskModalControls()
  bindPointExchangePreview()
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
  document.querySelectorAll('[data-action="edit-currency"]').forEach((button) => {
    button.addEventListener('click', () => openPointCurrencyModal(button.dataset.currencyId))
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
  document.querySelectorAll('[data-action="delete-currency"]').forEach((button) => {
    button.addEventListener('click', () => openDeleteConfirm('currency', button.dataset.currencyId))
  })
  document.querySelectorAll('[data-action="approve-exchange"]').forEach((button) => {
    button.addEventListener('click', () => handleRewardExchangeReview(button.dataset.exchangeId, true))
  })
  document.querySelectorAll('[data-action="reject-exchange"]').forEach((button) => {
    button.addEventListener('click', () => handleRewardExchangeReview(button.dataset.exchangeId, false))
  })
  document.querySelectorAll('[data-action="update-fulfillment"]').forEach((button) => {
    button.addEventListener('click', () => handleRewardFulfillmentUpdate(button.dataset.exchangeId, button.dataset.fulfillmentStatus))
  })
  document.querySelectorAll('[data-action="confirm-reward-ticket"]').forEach((button) => {
    button.addEventListener('click', () => handleRewardTicketConfirm(button.dataset.exchangeId))
  })
  document.querySelectorAll('[data-action="approve-task-record"]').forEach((button) => {
    button.addEventListener('click', () => handleTaskRecordReview(button.dataset.recordId, true))
  })
  document.querySelectorAll('[data-action="reject-task-record"]').forEach((button) => {
    button.addEventListener('click', () => handleTaskRecordReview(button.dataset.recordId, false))
  })
  document.querySelector('[data-action="toggle-task-todo"]')?.addEventListener('click', () => {
    state.todoExpanded = { ...state.todoExpanded, task: !state.todoExpanded?.task }
    render()
  })
  document.querySelector('[data-action="toggle-reward-todo"]')?.addEventListener('click', () => {
    state.todoExpanded = { ...state.todoExpanded, reward: !state.todoExpanded?.reward }
    render()
  })
  document.querySelectorAll('[data-action="open-calendar-day"]').forEach((target) => {
    target.addEventListener('click', () => openCalendarDayModal(target.dataset.date))
  })
  document.querySelectorAll('[data-action="open-calendar-event"]').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.stopPropagation()
      openCalendarEventModal(button.dataset.calendarEventId || button.dataset.taskId, button.dataset.taskDate)
    })
  })
  document.querySelectorAll('[data-action="close-calendar-day-modal"]').forEach((button) => {
    button.addEventListener('click', closeCalendarDayModal)
  })
  document.querySelectorAll('[data-action="close-calendar-event-modal"]').forEach((button) => {
    button.addEventListener('click', closeCalendarEventModal)
  })
  document.querySelectorAll('[data-action="close-checkin-modal"]').forEach((button) => {
    button.addEventListener('click', closeTaskCheckInModal)
  })
  document.querySelectorAll('[data-action="select-checkin-reward"]').forEach((button) => {
    button.addEventListener('pointerdown', (event) => {
      event.preventDefault()
      beginCheckInRewardDrag(Number(button.dataset.rewardIndex || 0))
    })
    button.addEventListener('pointerenter', () => {
      extendCheckInRewardDrag(Number(button.dataset.rewardIndex || 0))
    })
    button.addEventListener('click', (event) => {
      event.preventDefault()
      if (state.checkInRewardDragging) {
        state.checkInRewardDragging = false
        return
      }
      toggleCheckInReward(Number(button.dataset.rewardIndex || 0))
    })
  })
  document.querySelector('[data-form="task-checkin"]')?.addEventListener('submit', handleTaskCheckInSubmit)
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
  document.querySelectorAll('.reward-card').forEach((card) => {
    card.addEventListener('click', (event) => {
      if (event.target.closest('[data-no-card-click]')) return
      const button = card.querySelector('[data-action="exchange-reward"]')
      if (button?.dataset.rewardId) {
        handleExchangeReward(button.dataset.rewardId)
      }
    })
  })
  document.querySelectorAll('[data-action="complete-task"]').forEach((button) => {
    button.addEventListener('click', () => openTaskCheckInModal(button.dataset.taskId, button.dataset.taskDate))
  })
  bindBlockDragSelection('[data-daily-hour-picker]', '[name="dailyHours"]')
  bindBlockDragSelection('[data-weekday-picker]', '[name="weekDays"]')
  bindBlockDragSelection('[data-monthday-picker]', '[name="monthDays"]')
}

function initLoginCarousel() {
  const carousel = document.querySelector('[data-login-carousel]')
  if (!carousel || carousel.dataset.ready === '1') return
  carousel.dataset.ready = '1'
  window.clearInterval(initLoginCarousel.timer)
  initLoginCarousel.timer = window.setInterval(() => {
    if (!state.user && !state.loginCarouselPinned && !state.registerModalOpen) {
      setLoginCarouselIndex((Number(state.loginCarouselIndex || 0) + 1) % 3, false)
    }
  }, 3200)
}

function setLoginCarouselIndex(index, pinned = false) {
  state.loginCarouselIndex = ((Number(index) % 3) + 3) % 3
  state.loginCarouselPinned = pinned
  updateLoginCarousel()
}

function stepLoginCarousel(delta) {
  state.loginCarouselPinned = false
  setLoginCarouselIndex((Number(state.loginCarouselIndex || 0) + delta) % 3, false)
}

function updateLoginCarousel() {
  const activeIndex = Number(state.loginCarouselIndex || 0)
  document.querySelectorAll('.login-showcase-grid').forEach((grid) => {
    grid.style.setProperty('--carousel-index', String(activeIndex))
  })
  document.querySelectorAll('[data-login-carousel-nav]').forEach((button) => {
    const isActive = Number(button.dataset.loginCarouselNav || 0) === activeIndex
    button.classList.toggle('active', isActive)
  })
}

function bindTaskModalControls() {
  const form = document.querySelector('[data-form="task"]')
  if (!form) return
  form.querySelectorAll('[name="dailyHours"]').forEach((input) => {
    input.addEventListener('change', () => syncDailyHiddenHours(form))
  })
  form.taskColor?.addEventListener('input', () => syncTaskColor(form))
  syncTaskColor(form)
  syncDailyHiddenHours(form)
}

function bindBlockDragSelection(containerSelector, inputSelector) {
  document.querySelectorAll(containerSelector).forEach((container) => {
    let dragging = false
    let targetChecked = true
    let customSelection = false
    const applyInput = (input) => {
      if (!input || input.disabled) return
      input.checked = targetChecked
      input.dispatchEvent(new Event('change', { bubbles: true }))
    }
    container.addEventListener('pointerdown', (event) => {
      const label = event.target.closest('label')
      const input = label?.querySelector(inputSelector)
      if (!input || input.disabled) return
      event.preventDefault()
      dragging = true
      customSelection = true
      targetChecked = !input.checked
      container.setPointerCapture?.(event.pointerId)
      applyInput(input)
    })
    container.addEventListener('pointerover', (event) => {
      if (!dragging) return
      const input = event.target.closest('label')?.querySelector(inputSelector)
      applyInput(input)
    })
    const stopDragging = (event) => {
      dragging = false
      try {
        container.releasePointerCapture?.(event.pointerId)
      } catch {
        // Pointer capture may already be released by the browser.
      }
    }
    container.addEventListener('pointerup', stopDragging)
    container.addEventListener('pointercancel', stopDragging)
    container.addEventListener('pointerleave', () => {
      dragging = false
    })
    container.addEventListener('click', (event) => {
      if (!customSelection) return
      event.preventDefault()
      customSelection = false
    })
  })
}

function bindPointExchangePreview() {
  const form = document.querySelector('[data-form="point-exchange"]')
  if (!form) return
  const syncPreview = () => {
    const fromPointType = String(form.fromPointType?.value || 'STAR')
    const toPointType = String(form.toPointType?.value || 'FLOWER')
    const fromAmount = Math.max(1, Number(form.fromAmount?.value || 1))
    const preview = calculatePointExchange(fromPointType, toPointType, fromAmount, state.pointExchangeRule)
    const previewNode = form.querySelector('[data-exchange-preview]')
    if (previewNode) {
      previewNode.textContent = `预估：${fromAmount} ${pointName(fromPointType)} 可兑换 ${preview.toAmount} ${pointName(toPointType)}`
    }
  }
  form.fromPointType?.addEventListener('change', syncPreview)
  form.toPointType?.addEventListener('change', syncPreview)
  form.fromAmount?.addEventListener('input', syncPreview)
  syncPreview()
}

function syncDailyHiddenHours(form) {
  const selected = Array.from(form.querySelectorAll('[name="dailyHours"]:checked')).map((input) => Number(input.value))
  const startHour = selected.length ? Math.min(...selected) : 6
  const endHour = selected.length ? Math.max(...selected) : 22
  if (form.startHour) form.startHour.value = String(startHour)
  if (form.endHour) form.endHour.value = String(endHour)
}

function syncTaskColor(form) {
  const taskColor = String(form.taskColor?.value || '#30d5ff')
  form.querySelectorAll('[data-task-color-surface]').forEach((surface) => {
    surface.style.setProperty('--task-color', taskColor)
  })
}

function bindFilterActions() {
  ;['child', 'goal', 'task', 'reward', 'currency'].forEach((scope) => {
    document.querySelector(`[data-action="toggle-${scope}-filters"]`)?.addEventListener('click', () => {
      state.filterAdvanced = {
        ...state.filterAdvanced,
        [scope]: !state.filterAdvanced?.[scope],
      }
      render()
    })
  })
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
    state.filterAdvanced = { ...state.filterAdvanced, child: false }
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
    state.filterAdvanced = { ...state.filterAdvanced, goal: false }
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
    state.filterAdvanced = { ...state.filterAdvanced, task: false }
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
    state.filterAdvanced = { ...state.filterAdvanced, reward: false }
    render()
  })
  document.querySelector('[data-action="search-currency-filters"]')?.addEventListener('click', () => {
    state.currencyFilters = {
      keyword: readControlValue('[data-filter="currency-keyword"]'),
      pointType: readControlValue('[data-select="currency-point"]'),
      status: readControlValue('[data-select="currency-status"]'),
    }
    render()
  })
  document.querySelector('[data-action="reset-currency-filters"]')?.addEventListener('click', () => {
    state.currencyFilters = { keyword: '', pointType: '', status: '' }
    state.filterAdvanced = { ...state.filterAdvanced, currency: false }
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
  const formData = new FormData(event.currentTarget)
  const payload = {
    username: String(formData.get('username') || '').trim(),
    password: String(formData.get('password') || '').trim(),
    nickname: '星星家长',
  }
  try {
    const response = await api.login(payload)
    setToken(response.token)
    state.user = response.user
    state.offline = false
    applyRoleLanding()
    state.connectionMessage = '已登录真实后端'
    toast('登录成功')
    if (!isChildUser()) {
      await ensureStarterData()
    }
    await loadInitialData(false)
  } catch (error) {
    toast(error.message)
    state.connectionMessage = '后端未登录，继续显示演示数据'
    render()
  }
}

async function handleRegisterSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const payload = {
    username: String(formData.get('username') || '').trim(),
    password: String(formData.get('password') || '').trim(),
    phoneNumber: String(formData.get('phoneNumber') || '').trim(),
    invitationCode: String(formData.get('invitationCode') || '').trim(),
    nickname: String(formData.get('username') || '').trim() || '星星家长',
  }
  if (!payload.username || !payload.password || !payload.phoneNumber || !payload.invitationCode) {
    toast('请完整填写注册信息')
    return
  }
  try {
    const response = await api.register(payload)
    setToken(response.token)
    state.user = response.user
    state.offline = false
    state.registerModalOpen = false
    applyRoleLanding()
    await ensureStarterData()
    await loadInitialData(false)
    toast('注册成功')
  } catch (error) {
    toast(error.message)
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
    state.currentView = normalizeViewForRole(state.currentView || 'profile')
    state.profileSubView = normalizeProfileSubViewForRole(state.profileSubView || 'home')
    state.connectionMessage = '已连接后端 1.0 API'
    const children = await api.children()
    state.children = children
    await loadAvatarImages()
    state.selectedChild = children.find((child) => String(child.id) === String(state.selectedChildId)) || children[0] || null
    state.selectedChildId = state.selectedChild?.id || null
    if (!state.selectedChildId) {
      resetChildScopedData()
      if (options.skipStarterData) {
        if (showToast) toast('已刷新')
        render()
        return
      }
      if (isChildUser()) {
        if (showToast) toast('当前孩子账号还没有绑定孩子档案')
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
  state.calendarEvents = mergeTaskCalendarEvents(state.calendarEvents, tasks)
  state.balances = summary?.balances || []
  state.pointExchangeRule = summary?.exchangeRule || state.pointExchangeRule || { starWeight: 1, flowerWeight: 10, crownWeight: 100 }
  state.pointCurrencies = normalizePointCurrencies(summary?.currencies || state.pointCurrencies, state.pointExchangeRule)
  state.ledger = ledger
  state.rewards = rewards
  state.exchanges = exchanges
}

async function loadAvatarImages() {
  if (state.offline) return
  await Promise.all([
    loadAccountAvatarImage(),
    ...((state.children || []).map((child) => loadChildAvatarImage(child))),
  ])
}

async function loadAccountAvatarImage() {
  const avatarUrl = state.user?.avatarUrl
  if (!avatarUrl) {
    replaceObjectUrl('account', '')
    return
  }
  try {
    const blob = await api.avatarBlob(avatarUrl)
    replaceObjectUrl('account', URL.createObjectURL(blob))
  } catch {
    replaceObjectUrl('account', '')
  }
}

async function loadChildAvatarImage(child) {
  if (!child?.id) return
  if (!child.avatarUrl) {
    replaceObjectUrl(`child:${child.id}`, '')
    return
  }
  try {
    const blob = await api.avatarBlob(child.avatarUrl)
    replaceObjectUrl(`child:${child.id}`, URL.createObjectURL(blob))
  } catch {
    replaceObjectUrl(`child:${child.id}`, '')
  }
}

function replaceObjectUrl(key, nextUrl) {
  if (key === 'account') {
    if (state.avatarObjectUrls.account && state.avatarObjectUrls.account !== nextUrl) {
      URL.revokeObjectURL(state.avatarObjectUrls.account)
    }
    state.avatarObjectUrls.account = nextUrl
    return
  }
  const childId = key.replace('child:', '')
  const current = state.avatarObjectUrls.children?.[childId]
  if (current && current !== nextUrl) {
    URL.revokeObjectURL(current)
  }
  if (nextUrl) {
    state.avatarObjectUrls.children[childId] = nextUrl
  } else {
    delete state.avatarObjectUrls.children[childId]
  }
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
  state.todayKey = formatDate(new Date())
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
    ['积木礼物', '完成一周自主管理后兑换', '🎁', '#ff9f43', 'STAR', 80, 'PARENT_PURCHASE'],
    ['周末冰淇淋', '每周限兑一次的小甜点', '🍦', '#34c759', 'FLOWER', 40, 'PARENT_EXECUTE'],
    ['皇冠特权', '亲子游戏时间 30 分钟', '♛', '#6c63ff', 'CROWN', 1, 'PARENT_FULFILL'],
  ]
  for (const [name, description, rewardIcon, rewardColor, requiredPointType, requiredPoints, fulfillmentType] of rewards) {
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
      fulfillmentType,
      requireApproval: true,
    })
  }
}

async function handleCompleteTask(taskId, taskDate = state.todayKey) {
  const taskMeta = getTaskMeta(taskId, taskDate)
  if (!taskMeta) return
  const basePoints = Math.max(1, Number(taskMeta.basePoints || 1))
  const selectedCount = Math.max(1, Math.min(Number(state.selectedCheckInRewardCount || basePoints), basePoints))
  const completionProgress = Math.max(1, Math.round((selectedCount / basePoints) * 100))
  const submittedStatus = isChildUser() || Number(taskMeta.requireApproval) === 1 || taskMeta.requireApproval === true
  if (state.offline) {
    state.calendarEvents = state.calendarEvents.map((event) => (
      String(event.taskId) === String(taskId) && event.taskDate === taskDate
        ? {
            ...event,
            recordId: event.recordId || Number(`${Date.now()}${String(taskId).slice(-3)}`),
            status: submittedStatus ? 'SUBMITTED' : 'APPROVED',
            completionProgress,
            scoreAwarded: submittedStatus ? 0 : selectedCount,
            persisted: true,
            submittedAt: new Date().toISOString(),
          }
        : event
    ))
    state.taskCheckInModalOpen = false
    toast(submittedStatus ? '演示：已提交审核' : '演示：任务已完成')
    render()
    return
  }
  try {
    await api.completeTask(taskId, { taskDate, completionProgress, remark: `选择 ${selectedCount}/${basePoints} 个奖励图标` })
    await Promise.all([loadCoreData(), loadCalendar()])
    state.taskCheckInModalOpen = false
    state.selectedCheckInTaskId = ''
    state.selectedCheckInTaskDate = ''
    state.selectedCheckInRewardCount = 0
    state.selectedCalendarDateKey = taskDate
    state.selectedCalendarEventId = calendarTaskEventId(taskId, taskDate)
    toast(submittedStatus ? '已提交审核' : '完成并入账')
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handleTaskCheckInSubmit(event) {
  event.preventDefault()
  await handleCompleteTask(state.selectedCheckInTaskId, state.selectedCheckInTaskDate || state.todayKey)
}

function getTaskMeta(taskId, taskDate = state.todayKey) {
  const task = state.tasks.find((item) => String(item.id) === String(taskId))
  const event = state.calendarEvents.find((item) => String(item.taskId) === String(taskId) && item.taskDate === taskDate)
  return task || event || null
}

async function handleTaskRecordReview(recordId, approved) {
  const record = state.calendarEvents.find((event) => String(event.recordId) === String(recordId))
  if (!record) return
  state.confirmDialog = {
    title: approved ? '通过任务打卡' : '拒绝任务打卡',
    message: `${approved ? '通过' : '拒绝'}「${record.taskName || '任务'}」这次打卡？`,
    confirmText: approved ? '通过' : '拒绝',
    variant: approved ? 'primary' : 'danger',
    action: async () => {
      if (state.offline) {
        state.calendarEvents = state.calendarEvents.map((event) => String(event.recordId) === String(recordId)
          ? {
              ...event,
              status: approved ? 'APPROVED' : 'REJECTED',
              scoreAwarded: approved ? Number(event.basePoints || 0) : 0,
              reviewedAt: new Date().toISOString(),
            }
          : event)
        toast(approved ? '演示：任务已通过' : '演示：任务已拒绝')
        return
      }
      if (approved) {
        await api.approveTaskRecord(recordId, { remark: '父母确认打卡' })
      } else {
        await api.rejectTaskRecord(recordId, { remark: '父母拒绝打卡' })
      }
      await Promise.all([loadCoreData(), loadCalendar()])
      toast(approved ? '任务已通过' : '任务已拒绝')
    },
  }
  render()
}

async function handleExchangeReward(rewardId, paymentPointType = '') {
  const reward = state.rewards.find((item) => String(item.id) === String(rewardId))
  if (!reward) return
  if (!paymentPointType) {
    openRewardExchangeModal(rewardId)
    return
  }
  const option = buildRewardPaymentOptions(reward, state.balances, state.pointExchangeRule)
    .find((item) => item.pointType === paymentPointType)
  if (!option?.enough) {
    toast('余额不足，先攒一攒吧')
    return
  }
  try {
    if (state.offline) {
      state.balances = upsertBalance(state.balances, option.pointType, -option.payAmount)
      if (option.changeAmount > 0) {
        state.balances = upsertBalance(state.balances, reward.requiredPointType, option.changeAmount)
      }
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
          fulfillmentStatus: 'PENDING',
          requestedAt: new Date().toISOString(),
          remark: '演示兑换申请',
        },
        ...state.exchanges,
      ]
    } else {
      await api.exchangeReward({ rewardId: reward.id, paymentPointType: option.pointType, remark: '前端发起兑换' })
      await loadCoreData()
    }
    state.rewardExchangeModalOpen = false
    state.selectedRewardId = ''
    state.rewardExchangeSuccess = {
      rewardName: reward.name,
      rewardIcon: reward.rewardIcon || '🎁',
      rewardColor: reward.rewardColor || '#ff9f43',
    }
    window.clearTimeout(handleExchangeReward.cheerTimer)
    handleExchangeReward.cheerTimer = window.setTimeout(() => {
      state.rewardExchangeSuccess = null
      render()
    }, 3200)
    toast(`获得一个${reward.name}奖励的兑换券`)
    render()
  } catch (error) {
    toast(error.message)
  }
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
          ? { ...item, status: approved ? 'APPROVED' : 'REJECTED', fulfillmentStatus: approved ? 'PENDING' : item.fulfillmentStatus, reviewedAt: new Date().toISOString() }
          : item)
        if (!approved) {
          state.balances = upsertBalance(state.balances, exchange.requiredPointType, Number(exchange.requiredPointsSnapshot || 0))
        }
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

async function handleRewardFulfillmentUpdate(exchangeId, fulfillmentStatus = 'PENDING') {
  const exchange = state.exchanges.find((item) => String(item.id) === String(exchangeId))
  if (!exchange) return
  const label = fulfillmentStatusName(fulfillmentStatus)
  state.confirmDialog = {
    title: '更新礼物状态',
    message: `把「${exchange.rewardNameSnapshot}」更新为「${label}」？`,
    confirmText: '更新',
    variant: 'primary',
    action: async () => {
      if (state.offline) {
        state.exchanges = state.exchanges.map((item) => String(item.id) === String(exchangeId)
          ? {
              ...item,
              fulfillmentStatus,
              fulfillmentUpdatedAt: new Date().toISOString(),
              completedAt: fulfillmentStatus === 'COMPLETED' ? new Date().toISOString() : item.completedAt,
            }
          : item)
        toast(`演示：已更新为${label}`)
        return
      }
      await api.updateRewardFulfillment(exchangeId, { fulfillmentStatus, remark: `更新为${label}` })
      await loadCoreData()
      toast(`已更新为${label}`)
    },
  }
  render()
}

async function handleRewardTicketConfirm(exchangeId) {
  const exchange = state.exchanges.find((item) => String(item.id) === String(exchangeId))
  if (!exchange) return
  if (exchange.status === 'COMPLETED' || exchange.fulfillmentStatus === 'CONFIRMED') {
    toast('这张礼物券已经确认')
    return
  }
  state.confirmDialog = {
    title: '确认礼物券',
    message: `确认「${exchange.rewardNameSnapshot}」已经收到或完成了吗？`,
    confirmText: '确认',
    variant: 'primary',
    action: async () => {
      if (state.offline) {
        state.exchanges = state.exchanges.map((item) => String(item.id) === String(exchangeId)
          ? { ...item, status: 'COMPLETED', fulfillmentStatus: 'CONFIRMED', confirmedAt: new Date().toISOString(), completedAt: new Date().toISOString() }
          : item)
        toast('演示：礼物券已确认')
        return
      }
      await api.confirmRewardExchange(exchangeId, { remark: '确认礼物券' })
      await loadCoreData()
      toast('礼物券已确认')
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

async function handlePointExchangeRuleSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const payload = {
    starWeight: Math.max(1, Number(formData.get('starWeight') || 1)),
    flowerWeight: Math.max(1, Number(formData.get('flowerWeight') || 10)),
    crownWeight: Math.max(1, Number(formData.get('crownWeight') || 100)),
  }
  if (!(payload.starWeight < payload.flowerWeight && payload.flowerWeight < payload.crownWeight)) {
    toast('币值必须满足：星星币 < 红花币 < 皇冠币')
    return
  }
  if (state.offline) {
    state.pointExchangeRule = {
      childId: state.selectedChildId,
      ...payload,
    }
    toast('演示：币值已保存')
    render()
    return
  }
  try {
    state.pointExchangeRule = await api.savePointExchangeRule(state.selectedChildId, payload)
    toast('币值已保存')
    render()
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handlePointCurrencySubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const currencyId = String(formData.get('currencyId') || '').trim()
  const pointType = String(formData.get('pointType') || 'STAR')
  const payload = {
    childId: state.selectedChildId,
    pointType,
    name: String(formData.get('name') || '').trim(),
    icon: String(formData.get('icon') || '★').trim(),
    color: String(formData.get('color') || '#f59e0b'),
    exchangeWeight: Math.max(1, Number(formData.get('exchangeWeight') || 1)),
    status: String(formData.get('status') || 'ACTIVE'),
    sortNo: Number(formData.get('sortNo') || defaultCurrencySort(pointType)),
  }
  if (!payload.name) {
    toast('请填写币值名称')
    return
  }
  if (!payload.icon) {
    toast('请选择币值图标')
    return
  }
  const currentDuplicate = state.pointCurrencies.find((currency) => (
    String(currency.pointType) === payload.pointType
    && String(currency.id || '') !== currencyId
    && Number(currency.deleted || 0) !== 1
  ))
  if (currentDuplicate) {
    toast('该积分类型已经存在，请直接修改')
    return
  }
  if (state.offline) {
    const nextCurrency = {
      id: currencyId || `demo-currency-${Date.now()}`,
      ...payload,
      deleted: 0,
    }
    state.pointCurrencies = currencyId
      ? state.pointCurrencies.map((currency) => String(currency.id) === currencyId ? nextCurrency : currency)
      : [...state.pointCurrencies, nextCurrency]
    syncRuleFromCurrencies()
    state.pointCurrencyModalOpen = false
    state.editingPointCurrency = null
    toast(currencyId ? '演示：币值已修改' : '演示：币值已创建')
    render()
    return
  }
  try {
    if (currencyId) {
      await api.updatePointCurrency(state.selectedChildId, currencyId, payload)
    } else {
      await api.createPointCurrency(state.selectedChildId, payload)
    }
    state.pointCurrencyModalOpen = false
    state.editingPointCurrency = null
    await loadCoreData()
    toast(currencyId ? '币值已修改' : '币值已创建')
    render()
  } catch (error) {
    toast(error.message)
    render()
  }
}

async function handlePointExchangeSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  await exchangePoints({
    fromPointType: String(formData.get('fromPointType') || state.selectedBalancePointType || 'STAR'),
    toPointType: String(formData.get('toPointType') || 'FLOWER'),
    fromAmount: Math.max(1, Number(formData.get('fromAmount') || 1)),
  })
}

async function handleQuickPointExchange(toPointType, fromAmount) {
  await exchangePoints({
    fromPointType: state.selectedBalancePointType || 'STAR',
    toPointType: String(toPointType || 'FLOWER'),
    fromAmount: Math.max(0, Number(fromAmount || 0)),
  })
}

async function exchangePoints(request) {
  const payload = {
    fromPointType: String(request.fromPointType || state.selectedBalancePointType || 'STAR'),
    toPointType: String(request.toPointType || 'FLOWER'),
    fromAmount: Math.max(0, Number(request.fromAmount || 0)),
  }
  if (payload.fromAmount <= 0) {
    toast('当前数量不足以兑换目标积分')
    return
  }
  if (payload.fromPointType === payload.toPointType) {
    toast('请选择不同的积分类型')
    return
  }
  const preview = calculatePointExchange(payload.fromPointType, payload.toPointType, payload.fromAmount, state.pointExchangeRule)
  if (preview.toAmount <= 0) {
    toast('当前数量不足以兑换目标积分')
    return
  }
  if (state.offline) {
    state.balances = upsertBalance(upsertBalance(state.balances, payload.fromPointType, -payload.fromAmount), payload.toPointType, preview.toAmount)
    state.selectedBalancePointType = payload.toPointType
    state.ledger = [
      {
        id: `demo-ledger-${Date.now()}`,
        pointType: payload.fromPointType,
        changeAmount: -payload.fromAmount,
        sourceName: '积分互换',
        reason: `兑换为${pointName(payload.toPointType)}`,
        eventTime: new Date().toISOString(),
      },
      {
        id: `demo-ledger-${Date.now() + 1}`,
        pointType: payload.toPointType,
        changeAmount: preview.toAmount,
        sourceName: '积分互换',
        reason: `由${pointName(payload.fromPointType)}兑换`,
        eventTime: new Date().toISOString(),
      },
      ...state.ledger,
    ]
    state.exchangeSuccess = {
      toPointType: payload.toPointType,
      toAmount: preview.toAmount,
    }
    toast(`演示：已兑换 ${preview.toAmount} ${pointName(payload.toPointType)}`)
    render()
    return
  }
  try {
    const result = await api.exchangePoints(state.selectedChildId, payload)
    state.pointExchangeRule = result.exchangeRule || state.pointExchangeRule
    await loadCoreData()
    state.balanceModalOpen = true
    state.selectedBalancePointType = result.toPointType
    state.exchangeSuccess = {
      toPointType: result.toPointType,
      toAmount: result.toAmount,
    }
    toast(`已兑换 ${result.toAmount} ${pointName(result.toPointType)}`)
    render()
  } catch (error) {
    toast(error.message)
    render()
  }
}

function openChildModal(childId = null) {
  state.editingChild = childId ? state.children.find((child) => String(child.id) === String(childId)) || null : null
  state.childModalOpen = true
  state.childAccountDraftEnabled = false
  const nextChildId = state.editingChild?.id || ''
  if (!isSameAvatarDraft(state.avatarEditor, 'child', nextChildId)) {
    clearAvatarEditorDraft({ scope: 'child', childId: nextChildId })
  }
  render()
}

function closeChildModal() {
  state.childModalOpen = false
  state.editingChild = null
  state.childAccountDraftEnabled = false
  clearAvatarEditorDraft({ scope: 'child' })
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

function openBalanceModal(pointType = 'STAR') {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  state.selectedBalancePointType = pointType || 'STAR'
  state.balanceModalOpen = true
  state.exchangeSuccess = null
  render()
}

function closeBalanceModal() {
  state.balanceModalOpen = false
  state.selectedBalancePointType = ''
  state.exchangeSuccess = null
  render()
}

function openRegisterModal() {
  state.registerModalOpen = true
  render()
}

function closeRegisterModal() {
  state.registerModalOpen = false
  render()
}

function openAccountModal() {
  state.accountDraft = {
    nickname: state.user?.nickname || state.accountDraft?.nickname || '',
  }
  state.accountModalOpen = true
  if (!isSameAvatarDraft(state.avatarEditor, 'account')) {
    clearAvatarEditorDraft({ scope: 'account' })
  }
  render()
}

function closeAccountModal(event) {
  if (event?.target?.closest?.('[data-account-modal]') && event.target.dataset.action !== 'close-account-modal') return
  state.accountModalOpen = false
  clearAvatarEditorDraft({ scope: 'account' })
  render()
}

async function handleAccountSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const payload = {
    nickname: String(formData.get('nickname') || '').trim(),
  }
  if (!payload.nickname) {
    toast('请填写昵称')
    return
  }
  persistAccountDraft(payload)
  if (state.offline) {
    state.user = { ...(state.user || {}), ...payload }
    state.accountModalOpen = false
    toast('演示：账号信息已保存')
    render()
    return
  }
  try {
    state.user = await api.updateProfile(payload)
    persistAccountDraft({
      nickname: state.user?.nickname || payload.nickname,
    })
    state.accountModalOpen = false
    toast('账号信息已保存')
    render()
  } catch (error) {
    toast(error.message)
  }
}

function handleSystemConfigSubmit(event) {
  event.preventDefault()
  const formData = new FormData(event.currentTarget)
  const payload = {
    calendarDateSize: Math.min(28, Math.max(14, Number(formData.get('calendarDateSize') || defaultSystemConfig.calendarDateSize))),
    calendarDateColor: normalizeColor(formData.get('calendarDateColor'), defaultSystemConfig.calendarDateColor),
  }
  persistSystemConfig(payload)
  toast('系统配置已保存')
  render()
}

function handleAvatarFilePreview(event) {
  const input = event.currentTarget
  const file = input.files?.[0]
  if (!file) return
  if (file.size > AVATAR_MAX_BYTES) {
    input.value = ''
    toast('头像照片不能超过 1M')
    return
  }
  openAvatarEditor(input.dataset.avatarFile || '', input.dataset.childId || '', file)
    .catch((error) => toast(error.message || '头像读取失败'))
    .finally(() => {
      input.value = ''
    })
}

async function openAvatarEditor(scope, childId = '', file = null) {
  if (!scope) return
  const nextFile = file instanceof File ? file : null
  if (!nextFile && !getAvatarPreviewUrl(scope, childId)) {
    toast('请选择头像照片')
    return
  }
  if (state.avatarEditor.sourceObjectUrl) {
    URL.revokeObjectURL(state.avatarEditor.sourceObjectUrl)
  }
  if (state.avatarEditor.processedPreviewUrl) {
    URL.revokeObjectURL(state.avatarEditor.processedPreviewUrl)
  }
  const editor = createEmptyAvatarEditor()
  editor.open = true
  editor.scope = scope
  editor.childId = String(childId || '')
  editor.fileName = nextFile?.name || ''
  editor.sourceObjectUrl = nextFile ? URL.createObjectURL(nextFile) : getAvatarPreviewUrl(scope, childId)
  try {
    editor.image = await loadImageFromUrl(editor.sourceObjectUrl)
  } catch (error) {
    if (nextFile && editor.sourceObjectUrl) {
      URL.revokeObjectURL(editor.sourceObjectUrl)
    }
    throw error
  }
  editor.naturalWidth = editor.image.naturalWidth
  editor.naturalHeight = editor.image.naturalHeight
  state.avatarEditor = editor
  state.accountModalOpen = scope === 'account' ? true : state.accountModalOpen
  state.childModalOpen = scope === 'child' ? true : state.childModalOpen
  render()
}

function closeAvatarEditor() {
  clearAvatarEditorDraft({
    scope: state.avatarEditor?.scope || '',
    childId: state.avatarEditor?.childId || '',
    keepProcessedDraft: Boolean(state.avatarEditor?.processedFile),
  })
  render()
}

function resetAvatarEditorTransform() {
  state.avatarEditor.zoom = 1
  state.avatarEditor.offsetX = 0
  state.avatarEditor.offsetY = 0
  render()
}

function changeAvatarZoom(delta) {
  state.avatarEditor.zoom = Math.min(3, Math.max(1, Number((state.avatarEditor.zoom + delta).toFixed(2))))
  render()
}

async function saveAvatarEditor() {
  const editor = state.avatarEditor
  if (!editor?.image) {
    toast('请选择头像照片')
    return
  }
  const file = await renderAvatarEditorToFile(editor)
  if (!file) {
    toast('头像处理失败')
    return
  }
  const scope = editor.scope
  if (scope === 'account') {
    await submitAvatarFileForAccount(file)
  } else if (scope === 'child') {
    if (editor.childId) {
      await submitAvatarFileForChild(editor.childId, file)
    } else {
      setPendingChildAvatar(file, editor)
    }
  }
}

function startAvatarEditorDrag(event) {
  const editor = state.avatarEditor
  if (!editor?.open) return
  event.preventDefault()
  const handle = event.currentTarget
  const target = handle.closest('[data-avatar-editor-body]')
  if (!target) return
  editor.dragging = true
  editor.dragStartX = event.clientX
  editor.dragStartY = event.clientY
  editor.dragOriginX = editor.offsetX
  editor.dragOriginY = editor.offsetY
  handle.setPointerCapture?.(event.pointerId)
  const image = target.querySelector('[data-avatar-editor-image]')
  const onMove = (moveEvent) => {
    if (!editor.dragging) return
    const scale = editor.zoom || 1
    editor.offsetX = editor.dragOriginX + (moveEvent.clientX - editor.dragStartX)
    editor.offsetY = editor.dragOriginY + (moveEvent.clientY - editor.dragStartY)
    if (image) {
      image.style.transform = `translate(-50%, -50%) translate(${editor.offsetX}px, ${editor.offsetY}px) scale(${editor.zoom || 1})`
    }
  }
  const onUp = () => {
    editor.dragging = false
    handle.releasePointerCapture?.(event.pointerId)
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    render()
  }
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp, { once: true })
}

function loadImageFromUrl(url) {
  return new Promise((resolve, reject) => {
    const image = new Image()
    image.onload = () => resolve(image)
    image.onerror = () => reject(new Error('头像读取失败'))
    image.src = url
  })
}

async function renderAvatarEditorToFile(editor) {
  const canvas = document.createElement('canvas')
  canvas.width = 320
  canvas.height = 320
  const ctx = canvas.getContext('2d')
  if (!ctx) return null
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  const baseScale = Math.max(canvas.width / editor.naturalWidth, canvas.height / editor.naturalHeight)
  const scale = baseScale * (editor.zoom || 1)
  const drawWidth = editor.naturalWidth * scale
  const drawHeight = editor.naturalHeight * scale
  const stageSize = document.querySelector('[data-avatar-editor-handle]')?.getBoundingClientRect?.().width || 260
  const previewScale = canvas.width / Math.max(1, stageSize)
  const centerX = canvas.width / 2 + (editor.offsetX || 0) * previewScale
  const centerY = canvas.height / 2 + (editor.offsetY || 0) * previewScale
  const drawX = centerX - drawWidth / 2
  const drawY = centerY - drawHeight / 2
  ctx.drawImage(editor.image, drawX, drawY, drawWidth, drawHeight)
  return await new Promise((resolve) => {
    canvas.toBlob((blob) => {
      if (!blob) {
        resolve(null)
        return
      }
      resolve(new File([blob], editor.fileName || 'avatar.jpg', { type: 'image/jpeg' }))
    }, 'image/jpeg', 0.92)
  })
}

function setPendingChildAvatar(file, editor) {
  const previewUrl = URL.createObjectURL(file)
  clearAvatarEditorDraft({
    scope: 'child',
    childId: editor.childId || '',
    keepProcessedDraft: false,
  })
  state.avatarEditor = {
    ...createEmptyAvatarEditor(),
    scope: 'child',
    childId: editor.childId || '',
    processedFile: file,
    processedPreviewUrl: previewUrl,
  }
  state.childModalOpen = true
  toast('头像已调整，保存档案后生效')
  render()
}

async function submitAvatarFileForAccount(file) {
  if (state.offline) {
    const nextPreviewUrl = URL.createObjectURL(file)
    replaceObjectUrl('account', nextPreviewUrl)
    clearAvatarEditorDraft({ scope: 'account' })
    toast('演示：头像已更换')
    render()
    return
  }
  try {
    const avatarPayload = await api.uploadProfileAvatar(file)
    state.user = {
      ...(state.user || {}),
      avatarUrl: avatarPayload?.avatarUrl || state.user?.avatarUrl || '',
    }
    await loadAccountAvatarImage()
    clearAvatarEditorDraft({ scope: 'account' })
    toast('头像已更换')
    render()
  } catch (error) {
    toast(error.message)
  }
}

async function submitAvatarFileForChild(childId, file) {
  if (!childId) {
    toast('请选择孩子')
    return
  }
  if (state.offline) {
    const nextPreviewUrl = URL.createObjectURL(file)
    replaceObjectUrl(`child:${childId}`, nextPreviewUrl)
    clearAvatarEditorDraft({ scope: 'child', childId })
    toast('演示：头像已更换')
    render()
    return
  }
  try {
    const avatarPayload = await api.uploadChildAvatar(childId, file)
    const nextUrl = avatarPayload?.avatarUrl || ''
    state.children = state.children.map((child) => String(child.id) === String(childId)
      ? { ...child, avatarUrl: nextUrl }
      : child)
    if (state.selectedChild && String(state.selectedChild.id) === String(childId)) {
      state.selectedChild = { ...state.selectedChild, avatarUrl: nextUrl }
    }
    await loadInitialData(false, { skipStarterData: true })
    clearAvatarEditorDraft({ scope: 'child', childId })
    toast('头像已更换')
    render()
  } catch (error) {
    toast(error.message)
  }
}

function goRewardStore() {
  state.balanceModalOpen = false
  state.selectedBalancePointType = ''
  state.exchangeSuccess = null
  state.currentView = 'store'
  render()
}

function openTaskModal(taskId = null, options = {}) {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  if (!state.goals.length) {
    toast('请先新增成长目标')
    return
  }
  state.editingTask = taskId ? state.tasks.find((task) => String(task.id) === String(taskId)) || null : null
  state.taskDraftDate = taskId ? '' : String(options.date || '')
  state.taskModalOpen = true
  render()
}

function closeTaskModal() {
  state.taskModalOpen = false
  state.editingTask = null
  state.taskDraftDate = ''
  render()
}

function openRewardModal(rewardId = null) {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  state.editingReward = rewardId ? state.rewards.find((reward) => String(reward.id) === String(rewardId)) || null : null
  state.rewardDraftIcon = state.editingReward?.rewardIcon || '🎁'
  state.rewardIconPickerOpen = false
  state.rewardModalOpen = true
  render()
}

function closeRewardModal() {
  state.rewardModalOpen = false
  state.editingReward = null
  state.rewardDraftIcon = ''
  state.rewardIconPickerOpen = false
  render()
}

function openRewardExchangeModal(rewardId) {
  state.selectedRewardId = rewardId
  state.rewardExchangeSuccess = null
  state.rewardExchangeModalOpen = true
  render()
}

function closeRewardExchangeModal() {
  state.rewardExchangeModalOpen = false
  state.selectedRewardId = ''
  state.rewardExchangeSuccess = null
  render()
}

function openTaskCheckInModal(taskId, taskDate = state.todayKey) {
  const taskMeta = getTaskMeta(taskId, taskDate)
  if (!taskMeta) return
  const basePoints = Math.max(1, Number(taskMeta.basePoints || 1))
  state.selectedCheckInTaskId = taskId
  state.selectedCheckInTaskDate = taskDate || state.todayKey
  state.selectedCheckInRewardCount = basePoints
  state.taskCheckInModalOpen = true
  render()
}

function closeTaskCheckInModal() {
  state.taskCheckInModalOpen = false
  state.selectedCheckInTaskId = ''
  state.selectedCheckInTaskDate = ''
  state.selectedCheckInRewardCount = 0
  render()
}

function beginCheckInRewardDrag(index) {
  if (!Number.isFinite(index) || index < 1) return
  state.checkInRewardDragging = true
  state.selectedCheckInRewardCount = index
  syncCheckInRewardDom()
}

function extendCheckInRewardDrag(index) {
  if (!state.checkInRewardDragging || !Number.isFinite(index) || index < 1) return
  state.selectedCheckInRewardCount = index
  syncCheckInRewardDom()
}

function toggleCheckInReward(index) {
  state.checkInRewardDragging = false
  if (!Number.isFinite(index) || index < 1) return
  state.selectedCheckInRewardCount = Number(state.selectedCheckInRewardCount || 0) === index ? Math.max(1, index - 1) : index
  syncCheckInRewardDom()
}

function syncCheckInRewardDom() {
  const selectedCount = Number(state.selectedCheckInRewardCount || 0)
  document.querySelectorAll('[data-action="select-checkin-reward"]').forEach((button) => {
    const index = Number(button.dataset.rewardIndex || 0)
    button.classList.toggle('active', index <= selectedCount)
  })
  const countNode = document.querySelector('[data-checkin-reward-count]')
  if (countNode) {
    countNode.textContent = `${selectedCount} / ${countNode.dataset.total || selectedCount}`
  }
}

function openPointCurrencyModal(currencyId = null) {
  if (!state.selectedChildId) {
    toast('请先新增孩子档案')
    return
  }
  state.editingPointCurrency = currencyId ? state.pointCurrencies.find((currency) => String(currency.id) === String(currencyId)) || null : null
  state.pointCurrencyModalOpen = true
  render()
}

function closePointCurrencyModal() {
  state.pointCurrencyModalOpen = false
  state.editingPointCurrency = null
  render()
}

function logout() {
  setToken('')
  replaceObjectUrl('account', '')
  Object.keys(state.avatarObjectUrls.children || {}).forEach((childId) => {
    replaceObjectUrl(`child:${childId}`, '')
  })
  state.user = null
  state.currentView = 'profile'
  state.profileSubView = 'home'
  state.connectionMessage = '已登出，请重新登录'
  state.toast = ''
  render()
}

function setSidebarCollapsed(collapsed) {
  state.sidebarCollapsed = Boolean(collapsed)
  localStorage.setItem(SIDEBAR_KEY, state.sidebarCollapsed ? '1' : '0')
  render()
}

function toggleSidebar() {
  setSidebarCollapsed(!state.sidebarCollapsed)
}

function collapseSidebarOnMobile() {
  if (isCompactLayout()) {
    state.sidebarCollapsed = true
    localStorage.setItem(SIDEBAR_KEY, '1')
  }
}

function isChildUser() {
  return String(state.user?.userType || '').toUpperCase() === 'CHILD'
}

function isManagementUser() {
  const userType = String(state.user?.userType || '').toUpperCase()
  return userType === 'PARENT' || userType === 'GUARDIAN'
}

function normalizeViewForRole(view) {
  return ['profile', 'calendar', 'store'].includes(view) ? view : 'profile'
}

function normalizeProfileSubViewForRole(subView) {
  if (!isManagementUser()) {
    return 'home'
  }
  return ['home', 'account', 'system-config', 'children', 'goals', 'tasks', 'rewards', 'currencies'].includes(subView) ? subView : 'home'
}

function applyRoleLanding() {
  state.currentView = isChildUser() ? 'calendar' : 'profile'
  state.profileSubView = 'home'
  if (isCompactLayout()) {
    state.sidebarCollapsed = true
    localStorage.setItem(SIDEBAR_KEY, '1')
  }
}

function openCalendarDayModal(dateKey) {
  if (!dateKey) return
  state.selectedCalendarDateKey = dateKey
  state.calendarDayModalOpen = true
  state.calendarEventModalOpen = false
  state.selectedCalendarEventId = ''
  render()
}

function closeCalendarDayModal() {
  state.calendarDayModalOpen = false
  state.selectedCalendarDateKey = ''
  state.selectedCalendarEventId = ''
  render()
}

function openCalendarEventModal(eventId, dateKey) {
  if (!eventId || !dateKey) return
  state.selectedCalendarDateKey = dateKey
  state.selectedCalendarEventId = eventId
  state.calendarDayModalOpen = true
  state.calendarEventModalOpen = true
  render()
}

function closeCalendarEventModal() {
  state.calendarEventModalOpen = false
  state.selectedCalendarEventId = ''
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
  const dailyRequiredCount = Math.max(1, Number(formData.get('dailyRequiredCount') || 1))
  const weeklyRequiredCount = Math.max(1, Number(formData.get('weeklyRequiredCount') || 1))
  const monthlyRequiredCount = Math.max(1, Number(formData.get('monthlyRequiredCount') || 1))
  const scheduleJson = buildTaskScheduleJson({
    periodType,
    category,
    startHour,
    endHour,
    dailyHours,
    dailyRequiredCount,
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
  if (periodType === 'DAILY' && (!dailyHours.length || startHour < 6 || endHour > 22)) {
    toast('每日时间请选择 06:00 到 22:00 之间的方块')
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
    state.taskDraftDate = ''
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
    state.taskDraftDate = ''
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
  const pendingAvatarFile = isSameAvatarDraft(state.avatarEditor, 'child', childId) ? state.avatarEditor.processedFile : null
  const pendingAvatarPreviewUrl = isSameAvatarDraft(state.avatarEditor, 'child', childId) ? state.avatarEditor.processedPreviewUrl : ''
  const payload = {
    nickname: String(formData.get('nickname') || '').trim(),
    birthday: nullableDate(formData.get('birthday')),
    gender: String(formData.get('gender') || 'UNKNOWN'),
    remark: String(formData.get('remark') || '').trim(),
    createChildAccount: String(formData.get('createChildAccount') || 'false') === 'true',
    childUsername: String(formData.get('childUsername') || '').trim(),
    childPassword: String(formData.get('childPassword') || '').trim(),
  }
  if (!payload.nickname) {
    toast('请填写孩子昵称')
    return
  }
  if (payload.createChildAccount && (!payload.childUsername || !payload.childPassword)) {
    toast('请填写孩子账号和密码')
    return
  }
  if (pendingAvatarFile instanceof File && pendingAvatarFile.size > AVATAR_MAX_BYTES) {
    toast('头像照片不能超过 1M')
    return
  }
  if (state.offline) {
    const nextChild = {
      id: childId || `demo-child-${Date.now()}`,
      ...payload,
      status: 'ACTIVE',
    }
    if (pendingAvatarPreviewUrl) {
      replaceObjectUrl(`child:${nextChild.id}`, pendingAvatarPreviewUrl)
      state.avatarEditor = createEmptyAvatarEditor()
    }
    state.children = childId
      ? state.children.map((child) => String(child.id) === childId ? nextChild : child)
      : [...state.children, nextChild]
    state.selectedChild = nextChild
    state.selectedChildId = nextChild.id
    state.childModalOpen = false
    state.editingChild = null
    state.childAccountDraftEnabled = false
    clearAvatarEditorDraft({ scope: 'child' })
    toast(childId ? '演示：孩子档案已修改' : '演示：孩子档案已创建')
    render()
    return
  }
  try {
    let savedChild = childId
      ? await api.updateChild(childId, payload)
      : await api.createChild(payload)
    if (pendingAvatarFile instanceof File && pendingAvatarFile.size > 0) {
      const avatarPayload = await api.uploadChildAvatar(savedChild.id, pendingAvatarFile)
      savedChild = {
        ...savedChild,
        avatarUrl: avatarPayload?.avatarUrl || savedChild.avatarUrl || '',
      }
    }
    state.selectedChildId = savedChild.id
    state.childModalOpen = false
    state.editingChild = null
    state.childAccountDraftEnabled = false
    clearAvatarEditorDraft({ scope: 'child' })
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
    fulfillmentType: String(formData.get('fulfillmentType') || 'INVENTORY_DEDUCT'),
    requireApproval: true,
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
      requireApproval: 1,
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

function normalizeColor(value, fallback) {
  const text = String(value || '').trim()
  return /^#[0-9a-fA-F]{6}$/.test(text) ? text : fallback
}

function buildTaskScheduleJson(options) {
  const base = {
    type: options.periodType,
    category: options.category,
  }
  if (options.periodType === 'DAILY') {
    return JSON.stringify({
      ...base,
      hours: options.dailyHours,
      timeRange: {
        startHour: options.startHour,
        endHour: options.endHour,
      },
      requiredCount: options.dailyRequiredCount,
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
    currency: {
      label: '币值',
      item: state.pointCurrencies.find((currency) => String(currency.id) === String(id)),
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
    return
  }
  if (type === 'currency') {
    await api.deletePointCurrency(state.selectedChildId, id)
    await loadCoreData()
    toast('币值已删除')
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
  if (type === 'currency') {
    state.pointCurrencies = state.pointCurrencies.filter((currency) => String(currency.id) !== String(id))
    syncRuleFromCurrencies()
  }
}

function resetChildScopedData() {
  state.goals = []
  state.tasks = []
  state.balances = []
  state.ledger = []
  state.rewards = []
  state.exchanges = []
  state.pointCurrencies = normalizePointCurrencies([], state.pointExchangeRule)
  state.calendarEvents = []
  state.calendarDayModalOpen = false
  state.calendarEventModalOpen = false
  state.balanceModalOpen = false
  state.rewardExchangeModalOpen = false
  state.selectedCalendarDateKey = ''
  state.selectedCalendarEventId = ''
  state.selectedBalancePointType = ''
  state.selectedRewardId = ''
}

function mergeTaskCalendarEvents(events, tasks) {
  const taskMap = new Map((tasks || []).map((task) => [String(task.id), task]))
  return (events || []).map((event) => {
    const task = taskMap.get(String(event.taskId))
    if (!task) return event
    return {
      ...event,
      goalId: event.goalId || task.goalId,
      childId: event.childId || task.childId,
      taskName: event.taskName || task.name,
      taskColor: event.taskColor || task.taskColor,
      pointType: event.pointType || task.pointType,
      pointColor: event.pointColor || task.pointColor,
      basePoints: event.basePoints ?? task.basePoints,
      periodType: event.periodType || task.periodType,
      scheduleJson: event.scheduleJson || task.scheduleJson,
      requireApproval: event.requireApproval ?? task.requireApproval,
    }
  })
}

function addDays(date, days) {
  const next = new Date(date)
  next.setDate(next.getDate() + days)
  return next
}

function calendarTaskEventId(taskId, dateKey) {
  return `task-${taskId}-${dateKey}`
}

function focusTodayAfterRender() {
  if (!state.shouldFocusToday) return
  state.shouldFocusToday = false
  requestAnimationFrame(() => {
    document.querySelector(`[data-date="${state.todayKey}"]`)?.scrollIntoView({
      block: 'center',
      inline: 'center',
      behavior: 'smooth',
    })
  })
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

function calculatePointExchange(fromPointType, toPointType, fromAmount, rule = {}) {
  const weights = {
    STAR: Number(rule.starWeight || 1),
    FLOWER: Number(rule.flowerWeight || 10),
    CROWN: Number(rule.crownWeight || 100),
  }
  const fromWeight = weights[fromPointType] || weights.STAR
  const toWeight = weights[toPointType] || weights.STAR
  return {
    toAmount: Math.floor((Number(fromAmount || 0) * fromWeight) / toWeight),
    fromWeight,
    toWeight,
  }
}

function buildRewardPaymentOptions(reward, balances = [], rule = {}) {
  if (!reward) return []
  const weights = normalizeExchangeWeights(rule)
  const requiredPointType = String(reward.requiredPointType || 'STAR')
  const requiredWeight = weights[requiredPointType] || weights.STAR
  const requiredPoints = Math.max(1, Number(reward.requiredPoints || 1))
  const requiredValue = requiredPoints * requiredWeight
  const balanceMap = new Map((balances || []).map((balance) => [balance.pointType, Number(balance.balance || 0)]))
  return ['STAR', 'FLOWER', 'CROWN']
    .filter((pointType) => (weights[pointType] || 0) >= requiredWeight)
    .map((pointType) => {
      const paymentWeight = weights[pointType] || weights.STAR
      const payAmount = Math.max(1, Math.ceil(requiredValue / paymentWeight))
      const changeValue = payAmount * paymentWeight - requiredValue
      const changeAmount = pointType === requiredPointType ? 0 : Math.ceil(changeValue / requiredWeight)
      const balance = balanceMap.get(pointType) || 0
      return {
        pointType,
        payAmount,
        changeAmount,
        balance,
        enough: balance >= payAmount,
      }
    })
}

function normalizeExchangeWeights(rule = {}) {
  return {
    STAR: Math.max(1, Number(rule.starWeight || 1)),
    FLOWER: Math.max(1, Number(rule.flowerWeight || 10)),
    CROWN: Math.max(1, Number(rule.crownWeight || 100)),
  }
}

function normalizePointCurrencies(currencies = [], rule = {}) {
  const normalizedRule = {
    STAR: Number(rule?.starWeight || 1),
    FLOWER: Number(rule?.flowerWeight || 10),
    CROWN: Number(rule?.crownWeight || 100),
  }
  const byType = new Map((currencies || [])
    .filter((currency) => currency && Number(currency.deleted || 0) !== 1)
    .map((currency) => [String(currency.pointType || '').toUpperCase(), currency]))
  return DEFAULT_CURRENCIES.map((defaults) => {
    const current = byType.get(defaults.pointType) || {}
    return {
      childId: current.childId || demoState.children[0]?.id || null,
      ...defaults,
      ...current,
      pointType: defaults.pointType,
      exchangeWeight: Number(current.exchangeWeight || normalizedRule[defaults.pointType] || defaults.exchangeWeight),
      status: current.status || defaults.status,
      sortNo: Number(current.sortNo ?? defaults.sortNo),
    }
  })
}

function defaultCurrencySort(pointType) {
  return DEFAULT_CURRENCIES.find((currency) => currency.pointType === pointType)?.sortNo || 0
}

function syncRuleFromCurrencies() {
  const currencies = normalizePointCurrencies(state.pointCurrencies, state.pointExchangeRule)
  state.pointCurrencies = currencies
  const byType = new Map(currencies.map((currency) => [currency.pointType, currency]))
  state.pointExchangeRule = {
    childId: state.selectedChildId,
    starWeight: Number(byType.get('STAR')?.exchangeWeight || 1),
    flowerWeight: Number(byType.get('FLOWER')?.exchangeWeight || 10),
    crownWeight: Number(byType.get('CROWN')?.exchangeWeight || 100),
  }
}

render()
void loadInitialData(false)
