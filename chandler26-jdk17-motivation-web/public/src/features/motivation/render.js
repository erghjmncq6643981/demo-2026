import { renderCalendar } from '/src/features/motivation/calendar.js'
import { clamp, escapeHtml, formatDate, fulfillmentStatusName, pointIcon, pointName, statusName } from '/src/shared/text.js'

const navItems = [
  ['profile', '个人信息'],
  ['calendar', '任务日历'],
  ['store', '奖励商店'],
]

const weekDays = [
  [1, '周一'],
  [2, '周二'],
  [3, '周三'],
  [4, '周四'],
  [5, '周五'],
  [6, '周六'],
  [7, '周日'],
]

const dayOfMonthOptions = Array.from({ length: 31 }, (_, index) => [index + 1, `${index + 1} 日`])
const dailyHourOptions = Array.from({ length: 24 }, (_, hour) => hour)
const pointTypeOptions = [
  ['STAR', '星星'],
  ['FLOWER', '红花'],
  ['CROWN', '皇冠'],
]
const defaultCurrencyMeta = {
  STAR: { name: '星星', icon: '★', color: '#f59e0b', exchangeWeight: 1, sortNo: 1 },
  FLOWER: { name: '红花', icon: '✿', color: '#ec4899', exchangeWeight: 10, sortNo: 2 },
  CROWN: { name: '皇冠', icon: '♛', color: '#7c3aed', exchangeWeight: 100, sortNo: 3 },
}
const fulfillmentTypeOptions = [
  ['INVENTORY_DEDUCT', '库存扣减'],
  ['PARENT_EXECUTE', '家长执行'],
  ['PARENT_PURCHASE', '家长购买'],
  ['PARENT_FULFILL', '家长实现'],
]
const rewardIconOptions = ['🎁', '🍦', '🧸', '📚', '🎨', '🚲', '🎮', '🎬', '🎟️', '🍰', '🍕', '🏆', '👑', '⭐', '🌈', '🎵', '⚽', '🛝', '🍭', '🪁', '🧩', '🚀', '💎', '🎯', '🪄', '📷', '🌟', '🐣', '🦄', '🍓', '🍉', '🍎', '🍪', '🛹', '🎈']
const currencyIconOptions = ['★', '☆', '✦', '✧', '✿', '❀', '♛', '👑', '🏆', '🎖️', '🏅', '💎', '🌟', '⭐', '🌈', '🎁', '🍭', '🎈', '🪄', '🚀', '🎯']

function isChildUser(state) {
  return String(state.user?.userType || '').toUpperCase() === 'CHILD'
}

function isManagementUser(state) {
  const userType = String(state.user?.userType || '').toUpperCase()
  return userType === 'PARENT' || userType === 'GUARDIAN'
}

function visibleNavItems(state) {
  return navItems
}

function profileSubnavItems(state) {
  if (!isManagementUser(state)) {
    return [['home', '首页']]
  }
  return [
    ['home', '首页'],
    ['children', '孩子档案'],
    ['goals', '成长目标'],
    ['tasks', '任务管理'],
    ['rewards', '奖励管理'],
    ['currencies', '币值管理'],
  ]
}

function profileSubView(state) {
  const items = profileSubnavItems(state).map(([view]) => view)
  return items.includes(state.profileSubView) ? state.profileSubView : items[0]
}

export function renderApp(state, actions) {
  if (!state.user) {
    return `
      ${renderLoginPage(state)}
      ${renderRegisterModal(state)}
      ${renderToast(state.toast)}
    `
  }
  return `
    <main class="app-shell ${state.sidebarCollapsed ? 'sidebar-collapsed' : ''}">
      ${renderSidebar(state)}
      <button class="sidebar-backdrop" type="button" data-action="close-sidebar" aria-label="隐藏导航"></button>
      <section class="workspace">
        ${renderWorkspaceHeader(state)}
        ${renderCurrentView(state, actions)}
      </section>
    </main>
    ${renderCalendarDayModal(state)}
    ${renderCalendarEventModal(state)}
    ${renderChildModal(state)}
    ${renderGoalModal(state)}
    ${renderTaskModal(state)}
    ${renderTaskCheckInModal(state)}
    ${renderRewardModal(state)}
    ${renderRewardExchangeModal(state)}
    ${renderPointCurrencyModal(state)}
    ${renderPointAdjustModal(state)}
    ${renderBalanceModal(state)}
    ${renderConfirmModal(state)}
    ${renderToast(state.toast)}
  `
}

function renderLoginPage(state) {
  const activeIndex = Number(state.loginCarouselIndex || 0)
  return `
    <main class="login-page">
      <section class="login-showcase" aria-label="系统能力概览">
        <div class="login-hero-copy">
          <h1 class="rainbow-title">
            ${Array.from('宝贝激励助手').map((char) => `<span>${char}</span>`).join('')}
          </h1>
          <p>把每日努力变成看得见的积分、日历和奖励。</p>
        </div>
        <div class="login-carousel" data-login-carousel aria-label="核心功能预览">
          <button class="carousel-arrow left" type="button" data-action="login-carousel-prev" aria-label="上一张">‹</button>
          <div class="login-showcase-grid" style="--carousel-index:${activeIndex}">
            ${renderLoginCarouselSlides(activeIndex)}
          </div>
          <button class="carousel-arrow right" type="button" data-action="login-carousel-next" aria-label="下一张">›</button>
          <div class="carousel-dots">
            ${[0, 1, 2].map((index) => `
              <button class="${activeIndex === index ? 'active' : ''}" type="button" data-login-carousel-nav="${index}" aria-label="第 ${index + 1} 张"></button>
            `).join('')}
          </div>
        </div>
      </section>
      <section class="login-panel" aria-label="账号登录">
        <div class="login-brand">
          <div class="brand-mark">★</div>
          <div>
            <p class="login-panel-kicker">ACCOUNT</p>
            <h2>欢迎回来</h2>
            <p>登录后继续奖励兑换</p>
          </div>
        </div>
        <form class="login-form" data-form="auth">
          <label>
            <span>账号</span>
            <input name="username" autocomplete="username" placeholder="请输入账号" value="demo-parent" />
          </label>
          <label>
            <span>密码</span>
            <input name="password" autocomplete="current-password" type="password" placeholder="请输入密码" value="123456" />
          </label>
          <div class="login-actions">
            <button class="btn primary" type="submit" data-auth-mode="login">登录</button>
            <button class="btn" type="button" data-action="open-register-modal">注册</button>
          </div>
        </form>
      </section>
    </main>
  `
}

function renderLoginCarouselSlides() {
  const slides = ['store', 'task', 'calendar']
  return slides.map((type) => renderLoginShot(type)).join('')
}

function renderLoginShot(type) {
  if (type === 'task') {
    return `
      <article class="login-shot task-shot">
        <div class="shot-bar"><span></span><b>任务管理</b></div>
        <div class="shot-list">
          <div><i style="--task-color:#ff5c8a"></i><strong>晨读</strong><span>★★★</span></div>
          <div><i style="--task-color:#34c759"></i><strong>整理书包</strong><span>✿✿</span></div>
          <div><i style="--task-color:#6c63ff"></i><strong>练字作业</strong><span>♛</span></div>
        </div>
      </article>
    `
  }
  if (type === 'calendar') {
    return `
      <article class="login-shot calendar-shot">
        <div class="shot-bar"><span></span><b>任务日历</b></div>
        <div class="shot-calendar">
          ${Array.from({ length: 21 }, (_, index) => `<span class="${[2, 4, 8, 13, 18].includes(index) ? 'active' : ''}">${index + 1}</span>`).join('')}
        </div>
      </article>
    `
  }
  return `
    <article class="login-shot store-shot">
      <div class="shot-bar"><span></span><b>奖励商店</b></div>
      <div class="shot-rewards">
        <div><em>🎁</em><strong>积木礼物</strong><small>80 ★</small></div>
        <div><em>🍦</em><strong>冰淇淋</strong><small>40 ✿</small></div>
      </div>
    </article>
  `
}

function renderRegisterModal(state) {
  if (!state.registerModalOpen) return ''
  return `
    <div class="modal-backdrop">
      <section class="modal narrow register-modal">
        <div class="modal-head">
          <div><h2>注册账号</h2><p>创建家长账号后开始配置孩子档案。</p></div>
          <button class="icon-btn" type="button" data-action="close-register-modal">×</button>
        </div>
        <form class="modal-form" data-form="register">
          <label><span>账号</span><input name="username" autocomplete="username" placeholder="请输入账号" required /></label>
          <label><span>密码</span><input name="password" type="password" autocomplete="new-password" placeholder="至少 6 位" required /></label>
          <label><span>手机号码</span><input name="phoneNumber" inputmode="tel" placeholder="请输入手机号码" required /></label>
          <label><span>邀请码</span><input name="invitationCode" placeholder="请输入邀请码" required /></label>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-register-modal">取消</button>
            <button class="btn primary" type="submit">注册</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderSidebar(state) {
  const items = visibleNavItems(state)
  return `
    <aside class="sidebar">
      <button class="sidebar-close" type="button" data-action="close-sidebar" aria-label="隐藏导航">×</button>
      <div class="sidebar-brand">
        <div class="brand-mark">★</div>
        <div>
          <strong class="rainbow-title brand-title">${Array.from('宝贝激励助手').map((char) => `<span>${char}</span>`).join('')}</strong>
          <span>${escapeHtml(state.selectedChild?.nickname || '未选择孩子')}</span>
        </div>
      </div>
      <nav class="side-nav">
        ${items.map(([view, label]) => `
          <button class="${state.currentView === view ? 'active' : ''}" type="button" data-nav-view="${view}">
            ${label}
          </button>
        `).join('')}
      </nav>
      <div class="sidebar-foot">
        <span>${state.offline ? '演示模式' : '后端已连接'}</span>
        <button class="small-btn" type="button" data-action="logout">登出</button>
      </div>
    </aside>
  `
}

function renderWorkspaceHeader(state) {
  const profileTitles = {
    home: isChildUser(state) ? ['个人信息', '查看今日进度、积分余额和成长记录。'] : ['个人信息', '家庭成员、孩子档案和积分概览。'],
    children: ['孩子档案', '维护孩子资料、状态和家庭档案。'],
    goals: ['成长目标', '管理目标方向，并承载每日、每周和每月任务。'],
    tasks: ['任务管理', '用列表筛选任务，新增或修改任务规则。'],
    rewards: ['奖励管理', '维护奖励库存、颜色、积分要求和审核规则。'],
    currencies: ['币值管理', '配置星星、红花、皇冠的图标、颜色和比例。'],
  }
  const titles = {
    profile: profileTitles[profileSubView(state)] || profileTitles.home,
    calendar: ['任务日历', 'Notion 风格月视图，展示任务计划和完成记录。'],
    store: ['奖励商店', '孩子可以用星星、红花或皇冠兑换奖励。'],
  }
  const [title, subtitle] = titles[state.currentView] || titles.profile
  return `
    <header class="workspace-header">
      <div>
        <div class="workspace-title-row">
          <button class="icon-btn nav-toggle" type="button" data-action="toggle-sidebar" title="${state.sidebarCollapsed ? '显示导航' : '隐藏导航'}" aria-label="${state.sidebarCollapsed ? '显示导航' : '隐藏导航'}" aria-expanded="${state.sidebarCollapsed ? 'false' : 'true'}">☰</button>
          <div>
            <p class="eyebrow">Motivation 1.0</p>
            <h1>${title}</h1>
            <p>${subtitle}</p>
          </div>
        </div>
        ${state.currentView === 'profile' ? renderProfileSubnav(state) : ''}
      </div>
      <div class="header-actions"></div>
    </header>
  `
}

function renderProfileSubnav(state) {
  const activeSubView = profileSubView(state)
  const items = profileSubnavItems(state)
  if (items.length <= 1) return ''
  return `
    <nav class="profile-subnav" aria-label="个人信息导航">
      ${items.map(([view, label]) => `
        <button class="${activeSubView === view ? 'active' : ''}" type="button" data-profile-subview="${view}">
          ${label}
        </button>
      `).join('')}
    </nav>
  `
}

function renderCurrentView(state, actions) {
  if (state.currentView === 'profile') return renderProfileView(state)
  if (state.currentView === 'calendar') return renderCalendarView(state, actions)
  if (state.currentView === 'store') return renderStoreView(state)
  return renderProfileView(state)
}

function renderProfileView(state) {
  const activeSubView = profileSubView(state)
  const childUser = isChildUser(state)
  if (!state.selectedChild) {
    return `
      <div class="view-grid">
        <section class="panel panel-pad wide">
          <div class="section-inline-head">
            <h2>${childUser ? '我的档案' : '孩子档案'}</h2>
            ${childUser ? '' : '<button class="btn primary" type="button" data-action="open-child-modal">新增孩子</button>'}
          </div>
          <div class="empty compact-empty">${childUser ? '当前孩子账号还没有绑定孩子档案，请联系家长。' : '还没有孩子档案，先新增一个孩子再开始记录吧。'}</div>
        </section>
      </div>
    `
  }
  if (activeSubView === 'children') {
    return `
      <div class="view-grid">
        <section class="panel panel-pad wide">
          ${renderChildList(state)}
        </section>
      </div>
    `
  }
  if (activeSubView === 'goals') {
    return `
      <div class="view-grid">
        <section class="panel panel-pad wide">
          ${renderGoalList(state)}
        </section>
      </div>
    `
  }
  if (activeSubView === 'tasks') {
    return `
      <div class="view-grid">
        <div class="wide">
          ${renderTaskManageView(state)}
        </div>
      </div>
    `
  }
  if (activeSubView === 'rewards') {
    return `
      <div class="view-grid">
        <div class="wide">
          ${renderRewardManageView(state)}
        </div>
      </div>
    `
  }
  if (activeSubView === 'currencies') {
    return `
      <div class="view-grid">
        <div class="wide">
          ${renderCurrencyManageView(state)}
        </div>
      </div>
    `
  }
  return `
    <div class="view-grid">
      <section class="panel panel-pad">
        <div class="profile-card">
          <div class="avatar">${escapeHtml((state.selectedChild?.nickname || '孩').slice(0, 1))}</div>
          <div>
            <h2>${escapeHtml(state.selectedChild?.nickname || '未选择孩子')}</h2>
            <p>${escapeHtml(state.selectedChild?.remark || '暂无备注')}</p>
          </div>
        </div>
      </section>
      <section class="panel panel-pad">
        ${renderStats(state)}
      </section>
      ${renderRewardTickets(state)}
      <section class="panel panel-pad wide">
        ${renderLedger(state)}
      </section>
    </div>
  `
}

function renderChildList(state) {
  const filters = state.childFilters || {}
  const children = filterChildren(state.children || [], filters)
  const expanded = Boolean(state.filterAdvanced?.child)
  return `
    <div class="section-inline-head">
      <h2>孩子档案</h2>
      <div class="section-actions">
        <span>${children.length} 个</span>
        <button class="btn primary" type="button" data-action="open-child-modal">新增孩子</button>
      </div>
    </div>
    ${renderManagementFilterBar({
      scope: 'child',
      filters,
      keywordPlaceholder: '昵称或备注',
      expanded,
      fields: `
        <label><span>性别</span>${renderSelect('child-gender', filters.gender || '', [['', '全部'], ['UNKNOWN', '未设置'], ['MALE', '男孩'], ['FEMALE', '女孩']], 'child')}</label>
        <label><span>状态</span>${renderSelect('child-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['INACTIVE', '停用']], 'child')}</label>
      `,
    })}
    <div class="table-card">
      <div class="table-head child-table">
        <span>孩子</span><span>性别</span><span>生日</span><span>状态</span><span>操作</span>
      </div>
      ${children.map((child) => renderChildRow(child, state.selectedChildId)).join('') || '<div class="empty">暂无孩子档案</div>'}
    </div>
  `
}

function renderChildRow(child, selectedChildId) {
  const selected = String(child.id) === String(selectedChildId)
  return `
    <div class="table-row child-table">
      <div class="task-name-cell">
        <span class="child-mini">${escapeHtml((child.nickname || '孩').slice(0, 1))}</span>
        <div><strong>${escapeHtml(child.nickname)}</strong><small>${escapeHtml(child.remark || '暂无备注')}</small></div>
      </div>
      <span>${genderLabel(child.gender)}</span>
      <span>${escapeHtml(child.birthday || '未设置')}</span>
      <span>${statusName(child.status)}</span>
      <div class="row-actions task-row-actions">
        <button class="small-btn" type="button" data-action="select-child" data-child-id="${child.id}" ${selected ? 'disabled' : ''}>${selected ? '当前' : '选择'}</button>
        <button class="row-icon-btn" type="button" data-action="edit-child" data-child-id="${child.id}" aria-label="修改孩子档案" title="修改孩子档案">${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-child" data-child-id="${child.id}" aria-label="删除孩子档案" title="删除孩子档案">${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderGoalList(state) {
  const filters = state.goalFilters || {}
  const goals = filterGoals(state.goals || [], filters)
  const expanded = Boolean(state.filterAdvanced?.goal)
  return `
    <div class="section-inline-head">
      <h2>成长目标</h2>
      <div class="section-actions">
        <span>${goals.length} 个</span>
        <button class="btn primary" type="button" data-action="open-goal-modal">新增目标</button>
      </div>
    </div>
    ${renderManagementFilterBar({
      scope: 'goal',
      filters,
      keywordPlaceholder: '目标名称或说明',
      expanded,
      fields: `
        <label><span>状态</span>${renderSelect('goal-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停'], ['FINISHED', '完成']], 'goal')}</label>
      `,
    })}
    <div class="table-card">
      <div class="table-head goal-table">
        <span>目标</span><span>目标积分</span><span>周期</span><span>状态</span><span>操作</span>
      </div>
      ${goals.map(renderGoalRow).join('') || '<div class="empty">暂无成长目标</div>'}
    </div>
  `
}

function renderGoalRow(goal) {
  return `
    <div class="table-row goal-table">
      <div class="task-name-cell">
        <span class="reward-mini" style="--reward-color:${goal.goalColor || '#6c63ff'}">${escapeHtml(goal.icon || '★')}</span>
        <div><strong>${escapeHtml(goal.name)}</strong><small>${escapeHtml(goal.description || '')}</small></div>
      </div>
      <span>${goal.targetPoints || 0}</span>
      <span>${escapeHtml(dateRange(goal.startDate, goal.endDate))}</span>
      <span>${statusName(goal.status)}</span>
      <div class="row-actions task-row-actions">
        <button class="row-icon-btn" type="button" data-action="edit-goal" data-goal-id="${goal.id}" aria-label="修改成长目标" title="修改成长目标">${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-goal" data-goal-id="${goal.id}" aria-label="删除成长目标" title="删除成长目标">${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderManagementFilterBar({ scope, filters, keywordPlaceholder, expanded, fields }) {
  return `
    <div class="filter-bar management-filter ${expanded ? 'expanded' : ''}">
      <label class="keyword-filter"><span>关键词</span><input name="keyword" data-filter="${scope}-keyword" data-filter-scope="${scope}" value="${escapeHtml(filters.keyword || '')}" placeholder="${escapeHtml(keywordPlaceholder)}" /></label>
      <div class="filter-action">
        <span>筛选</span>
        <div class="filter-actions">
          <button class="small-btn primary-lite" type="button" data-action="search-${scope}-filters">搜索</button>
          <button class="small-btn" type="button" data-action="toggle-${scope}-filters" aria-expanded="${expanded ? 'true' : 'false'}">更多</button>
          <button class="small-btn" type="button" data-action="reset-${scope}-filters">重置</button>
        </div>
      </div>
      <div class="advanced-filters ${expanded ? '' : 'hidden'}">
        ${fields}
      </div>
    </div>
  `
}

function renderTaskManageView(state) {
  const filters = state.taskFilters || {}
  const tasks = filterTasks(state.tasks, filters)
  const expanded = Boolean(state.filterAdvanced?.task)
  return `
    <section class="panel panel-pad">
      ${renderTaskReviewTodo(state)}
      <div class="section-inline-head">
        <h2>任务列表</h2>
        <div class="section-actions">
          <span>${tasks.length} 个</span>
          <button class="btn primary" type="button" data-action="open-task-modal">新增任务</button>
        </div>
      </div>
      ${renderManagementFilterBar({
        scope: 'task',
        filters,
        keywordPlaceholder: '任务名称',
        expanded,
        fields: `
          <label><span>任务分类</span>${renderSelect('task-category', filters.category || '', [['', '全部'], ['STUDY', '学习'], ['LIFE', '生活'], ['SPORT', '运动'], ['HABIT', '习惯']], 'task')}</label>
          <label><span>任务类型</span>${renderSelect('task-period', filters.periodType || '', [['', '全部'], ['DAILY', '每日'], ['WEEKLY', '每周'], ['MONTHLY', '每月']], 'task')}</label>
          <label><span>奖励类型</span>${renderSelect('task-point', filters.pointType || '', [['', '全部'], ['STAR', '星星'], ['FLOWER', '红花'], ['CROWN', '皇冠']], 'task')}</label>
          <label><span>状态</span>${renderSelect('task-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停']], 'task')}</label>
        `,
      })}
      <div class="table-card">
        <div class="table-head task-table">
          <span>任务</span><span>任务类型和时间段</span><span>奖励</span><span>操作</span>
        </div>
        ${tasks.map(renderTaskRow).join('') || '<div class="empty">暂无任务</div>'}
      </div>
    </section>
  `
}

function renderTaskRow(task) {
  return `
    <div class="table-row task-table">
      <div class="task-name-cell">
        <span class="task-dot" style="--task-color:${task.taskColor || '#6c63ff'}"></span>
        <div><strong>${escapeHtml(task.name)}</strong><small>${escapeHtml(task.description || '')}</small></div>
      </div>
      ${renderTaskScheduleCell(task)}
      <span class="score-pill score-pill-icons" title="${escapeHtml(`${task.basePoints || 1} ${pointName(task.pointType)}`)}">${renderScoreIcons(task.pointType, task.basePoints, task.pointColor, 'compact')}</span>
      <div class="row-actions task-row-actions">
        <button class="row-icon-btn" type="button" data-action="edit-task" data-task-id="${task.id}" aria-label="修改任务" title="修改任务">${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-task" data-task-id="${task.id}" aria-label="删除任务" title="删除任务">${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderTaskReviewTodo(state) {
  if (!isManagementUser(state)) return ''
  const todos = (state.calendarEvents || [])
    .filter((event) => event.status === 'SUBMITTED' && event.recordId)
    .sort((left, right) => String(right.submittedAt || right.taskDate || '').localeCompare(String(left.submittedAt || left.taskDate || '')))
  const expanded = Boolean(state.todoExpanded?.task)
  const visibleTodos = expanded ? todos : todos.slice(0, 3)
  return `
    <section class="todo-panel task-review-panel">
      <div class="section-inline-head">
        <h2>任务待确认</h2>
        <span>${todos.length} 条</span>
      </div>
      <div class="todo-list">
        ${visibleTodos.map((event) => `
          <div class="todo-item">
            <span class="task-dot" style="--task-color:${event.taskColor || '#6c63ff'}"></span>
            <div>
              <strong>${escapeHtml(event.taskName || '任务打卡')}</strong>
              <small>${escapeHtml(formatDateValue(event.taskDate))} · ${renderScoreIcons(event.pointType, Number(event.basePoints || 1), event.pointColor, 'tiny')}</small>
            </div>
            <div class="row-actions">
              <button class="small-btn primary-lite" type="button" data-action="approve-task-record" data-record-id="${event.recordId}">通过</button>
              <button class="small-btn danger" type="button" data-action="reject-task-record" data-record-id="${event.recordId}">拒绝</button>
            </div>
          </div>
        `).join('') || '<div class="empty compact-empty">暂无任务待确认</div>'}
        ${todos.length > 3 ? `<button class="todo-more-btn" type="button" data-action="toggle-task-todo">${expanded ? '收起' : `更多 ${todos.length - 3} 条`}</button>` : ''}
      </div>
    </section>
  `
}

function renderTaskScheduleCell(task) {
  const schedule = parseSchedule(task.scheduleJson)
  const periodType = task.periodType || schedule.type || 'DAILY'
  return `
    <div class="task-time-cell">
      <div class="task-time-meta">
        <strong>${periodLabel(periodType)}</strong>
        <span>${categoryLabel(task)} · ${escapeHtml(scheduleLabel(task).replace(/^每日\s*/, '').replace(/^每周\s*/, '').replace(/^每月\s*/, ''))}</span>
      </div>
      ${renderTaskTimeBlocks(task, schedule, periodType)}
    </div>
  `
}

function renderTaskTimeBlocks(task, schedule, periodType) {
  if (periodType === 'WEEKLY') {
    const selectedDays = new Set((schedule.days || []).map(Number))
    return `
      <div class="time-blocks week-blocks compact-blocks">
        ${weekDays.map(([value, label]) => `
          <span class="time-block ${selectedDays.has(value) ? 'active' : ''}" title="${escapeHtml(label)}">${label.slice(1)}</span>
        `).join('')}
      </div>
    `
  }
  if (periodType === 'MONTHLY') {
    const selectedDays = new Set((schedule.days || []).map(Number))
    return `
      <div class="time-blocks month-blocks compact-blocks">
        ${dayOfMonthOptions.map(([value]) => `
          <span class="time-block ${selectedDays.has(value) ? 'active' : ''}" title="${value}日">${value}</span>
        `).join('')}
      </div>
    `
  }
  const startHour = Number(schedule.timeRange?.startHour ?? schedule.startHour ?? 6)
  const endHour = Number(schedule.timeRange?.endHour ?? schedule.endHour ?? 22)
  return renderDailyHourBlocks(getDailyHours(schedule, startHour, endHour), 'compact-blocks')
}

function renderCalendarView(state, actions) {
  const calendarEvents = buildCalendarDisplayEvents(state)
  state.calendarDisplayEvents = calendarEvents
  return `
    <section class="panel">
      ${renderCalendar({
        monthDate: state.monthDate,
        events: calendarEvents.filter((event) => event.kind === (state.calendarEventKind || 'tasks')),
        viewMode: state.calendarViewMode || 'month',
        eventKind: state.calendarEventKind || 'tasks',
      })}
    </section>
  `
}

function buildCalendarDisplayEvents(state) {
  return [
    ...normalizeTaskCalendarEvents(state.calendarEvents || [], state.tasks || []),
    ...normalizePointCalendarEvents(state.ledger || [], state.tasks || []),
    ...normalizeRewardCalendarEvents(state.exchanges || [], state.rewards || []),
  ].filter((event) => event.date)
}

function normalizeTaskCalendarEvents(events, tasks = []) {
  return events.map((event) => {
    const date = formatDateValue(event.taskDate)
    const task = tasks.find((item) => String(item.id) === String(event.taskId))
    const statusText = calendarEventStatusText({ ...event, kind: 'tasks' })
    return {
      ...event,
      kind: 'tasks',
      kindLabel: '任务',
      uid: `task-${event.taskId}-${date}`,
      date,
      title: event.taskName || task?.name || '任务',
      description: event.description || task?.description || '',
      color: event.taskColor || task?.taskColor || '#6c63ff',
      pointType: event.pointType || task?.pointType,
      pointColor: event.pointColor || task?.pointColor,
      basePoints: event.basePoints ?? task?.basePoints,
      scheduleJson: event.scheduleJson || task?.scheduleJson,
      subtitle: event.status === 'PENDING' ? scheduleLabel(event) : statusText,
      statusLabel: statusText,
      note: event.status === 'APPROVED'
        ? '这条任务已经完成并入账。'
        : event.status === 'SUBMITTED'
          ? '这条任务已经提交，等待家长审核。'
          : '这条任务还可以打卡。',
    }
  })
}

function normalizePointCalendarEvents(ledger, tasks = []) {
  return ledger.map((item) => {
    const amount = Number(item.changeAmount || 0)
    const date = formatDateValue(item.eventTime || item.createTime)
    const task = tasks.find((task) => String(task.name || '') === String(item.sourceName || ''))
    return {
      kind: 'points',
      kindLabel: '积分',
      uid: `point-${item.id || `${date}-${item.sourceName || 'ledger'}`}`,
      date,
      title: item.sourceName || '积分变动',
      subtitle: `${amount >= 0 ? '+' : ''}${amount} ${pointIcon(item.pointType)}`,
      statusLabel: amount >= 0 ? '增加' : '扣减',
      color: task?.taskColor || (amount >= 0 ? '#22c55e' : '#94a3b8'),
      pointType: item.pointType,
      pointColor: task?.pointColor,
      changeAmount: amount,
      note: item.reason || '',
    }
  })
}

function normalizeRewardCalendarEvents(exchanges, rewards = []) {
  return exchanges.map((exchange) => {
    const reward = rewards.find((item) => String(item.id) === String(exchange.rewardId)
      || String(item.name || '') === String(exchange.rewardNameSnapshot || ''))
    const date = formatDateValue(exchange.confirmedAt || exchange.completedAt || exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt || exchange.createTime)
    const status = exchange.status || 'REQUESTED'
    const fulfillmentStatus = exchange.fulfillmentStatus || 'PENDING'
    const colorMap = {
      REQUESTED: exchange.rewardColorSnapshot || '#ff9f43',
      APPROVED: '#6c63ff',
      COMPLETED: '#22c55e',
      REJECTED: '#ef4444',
    }
    const stateLabel = status === 'APPROVED' ? fulfillmentStatusName(fulfillmentStatus) : statusName(status)
    return {
      kind: 'rewards',
      kindLabel: '奖励',
      uid: `reward-${exchange.id || `${date}-${exchange.rewardNameSnapshot || 'exchange'}`}`,
      date,
      title: `${exchange.rewardIconSnapshot || '🎁'} ${exchange.rewardNameSnapshot || '奖励兑换'}`,
      rewardName: exchange.rewardNameSnapshot || '奖励兑换',
      rewardDetail: exchange.remark || `${exchange.requiredPointsSnapshot || 0} ${pointName(exchange.requiredPointType)}兑换`,
      rewardDescription: reward?.description || exchange.remark || '',
      requiredPointType: exchange.requiredPointType,
      requiredPoints: exchange.requiredPointsSnapshot || 0,
      rewardIcon: exchange.rewardIconSnapshot || '🎁',
      rewardColor: exchange.rewardColorSnapshot || '#ff9f43',
      rewardStateLabel: stateLabel,
      subtitle: `${exchange.requiredPointsSnapshot || 0} ${pointName(exchange.requiredPointType)} · ${stateLabel}`,
      status,
      fulfillmentStatus,
      statusLabel: stateLabel,
      color: colorMap[status] || exchange.rewardColorSnapshot || '#ff9f43',
      note: status === 'REQUESTED'
        ? '孩子已提交兑换申请，等待父母确认。'
        : status === 'APPROVED'
          ? `这张礼物兑换券当前为「${fulfillmentStatusName(fulfillmentStatus)}」。`
        : status === 'COMPLETED'
          ? '这条奖励兑换已经完成。'
          : '这条奖励兑换未通过。',
    }
  })
}

function renderCalendarDayModal(state) {
  if (!state.calendarDayModalOpen || !state.selectedCalendarDateKey) return ''
  const events = calendarEventsForDate(state, state.selectedCalendarDateKey)
  const visibleEvents = filterCalendarEventsByKind(state, events)
  const summary = renderCalendarDaySummary(state.calendarEventKind || 'tasks', visibleEvents)
  const canQuickCreate = isManagementUser(state) && (state.calendarEventKind || 'tasks') === 'tasks'
  return `
    <div class="modal-backdrop calendar-layer calendar-day-layer">
      <section class="modal calendar-modal calendar-day-modal">
        <div class="modal-head">
          <div>
            <h2>${escapeHtml(formatCalendarDateLabel(state.selectedCalendarDateKey))}</h2>
            <p>${state.calendarEventKind === 'tasks' ? '点开任务可以查看说明并打卡。' : state.calendarEventKind === 'points' ? '点开记录查看图标数量。' : '点开奖励查看兑换内容。'}</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-calendar-day-modal">×</button>
        </div>
        <div class="calendar-summary">
          ${summary}
          ${canQuickCreate ? `<button class="small-btn primary-lite calendar-quick-add" type="button" data-action="open-task-modal-for-date" data-task-date="${escapeHtml(state.selectedCalendarDateKey)}">新增当日任务</button>` : ''}
        </div>
        <div class="calendar-log-list">
          ${visibleEvents.map((event) => renderCalendarLogItem(event)).join('') || '<div class="empty compact-empty">这一天没有对应记录</div>'}
        </div>
      </section>
    </div>
  `
}

function renderCalendarEventModal(state) {
  if (!state.calendarEventModalOpen || !state.selectedCalendarDateKey || !state.selectedCalendarEventId) return ''
  const event = getCalendarEvent(state, state.selectedCalendarEventId, state.selectedCalendarDateKey)
  if (!event) return ''
  const scoreCount = Number(event.scoreAwarded || event.basePoints || 1)
  const isTask = event.kind === 'tasks'
  const isPoint = event.kind === 'points'
  const isReward = event.kind === 'rewards'
  const approved = event.status === 'APPROVED'
  const canCheckIn = isTask && !approved
  return `
    <div class="modal-backdrop calendar-layer calendar-event-layer">
      <section class="modal calendar-modal narrow calendar-event-modal">
        <div class="modal-head">
          <div>
            <h2>${escapeHtml(event.title)}</h2>
            <p>${escapeHtml(isTask ? (event.description || event.note || '') : isReward ? (event.rewardDescription || '') : (event.note || event.title || ''))}</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-calendar-event-modal">×</button>
        </div>
        <div class="calendar-detail">
          ${isReward ? '' : `<div class="calendar-detail-head">
            <span class="${isReward ? 'reward-mini' : 'task-dot'}" style="${isReward ? `--reward-color:${event.rewardColor || event.color || '#ff9f43'}` : `--task-color:${event.color || '#6c63ff'}`}">${isReward ? escapeHtml(event.rewardIcon || '🎁') : ''}</span>
            <div>
              <strong>${escapeHtml(isTask ? event.title || '任务' : event.title || '积分')}</strong>
              <p>${escapeHtml(isTask ? (event.description || event.note || '') : isReward ? (event.rewardDescription || '') : (event.note || ''))}</p>
            </div>
          </div>`}
          <div class="calendar-detail-focus">
            ${isPoint
              ? renderPointChangeIcons(event, 'large')
              : isReward
                ? renderLimitedPointIcons(pointIcon(event.requiredPointType), event.requiredPoints, pointTypeColor(event.requiredPointType), 12)
                : renderTaskRewardIcons(event, scoreCount, 'calendar large')}
          </div>
          ${event.note && !isPoint && !isReward ? `<div class="calendar-detail-note">
            ${escapeHtml(event.note)}
          </div>` : ''}
        </div>
        <div class="modal-actions calendar-modal-actions">
          <button class="btn" type="button" data-action="close-calendar-event-modal">返回</button>
          ${canCheckIn ? `<button class="btn primary" type="button" data-action="complete-task" data-task-id="${event.taskId}" data-task-date="${escapeHtml(event.date)}">${event.status === 'SUBMITTED' ? '重新提交' : '打卡'}</button>` : ''}
        </div>
      </section>
    </div>
  `
}

function renderCalendarLogItem(event) {
  const scoreCount = Number(event.scoreAwarded || event.basePoints || 1)
  const isPoint = event.kind === 'points'
  const isReward = event.kind === 'rewards'
  return `
    <button class="calendar-log-item" type="button" data-action="open-calendar-event" data-calendar-event-id="${escapeHtml(event.uid)}" data-task-id="${escapeHtml(event.taskId || '')}" data-task-date="${escapeHtml(event.date)}">
      <span class="${isReward ? 'reward-mini' : 'task-dot'}" style="${isReward ? `--reward-color:${event.rewardColor || event.color || '#ff9f43'}` : `--task-color:${event.color || '#6c63ff'}`}">${isReward ? escapeHtml(event.rewardIcon || '🎁') : ''}</span>
      <div class="calendar-log-main">
        <strong>${escapeHtml(event.title)}</strong>
        <small>${escapeHtml(event.kind === 'tasks' ? (event.description || event.note || '') : isReward ? (event.rewardDescription || '') : (event.note || ''))}</small>
      </div>
      <div class="calendar-log-score">
        ${isPoint
          ? renderPointChangeIcons(event, 'compact')
          : isReward
            ? renderLimitedPointIcons(pointIcon(event.requiredPointType), event.requiredPoints, pointTypeColor(event.requiredPointType), 6)
            : renderCalendarStatusScore(event, scoreCount, 'log')}
      </div>
      ${event.kind === 'tasks'
        ? `<span class="calendar-log-action ${event.status === 'APPROVED' ? 'done' : ''}">打卡</span>`
        : ''}
    </button>
  `
}

function renderCalendarStatusScore(event, count, density = '') {
  const completed = event.status === 'APPROVED'
  const color = completed ? event.pointColor : '#cbd5e1'
  return renderScoreIcons(event.pointType, count, color, `${density} ${completed ? '' : 'muted'}`.trim())
}

function renderPointChangeIcons(event, density = '') {
  const amount = Number(event.changeAmount || 0)
  const color = amount >= 0 ? '#22c55e' : '#cbd5e1'
  return `
    <span class="point-change-icons ${amount >= 0 ? 'positive' : 'negative'} ${density}" style="--point-color:${color}">
      <b>${amount >= 0 ? '+' : '-'}</b>
      <strong>${Math.abs(amount)}</strong>
      <span>${pointIcon(event.pointType)}</span>
    </span>
  `
}

function renderTaskRewardIcons(event, count, density = '') {
  const color = event.status === 'APPROVED' ? event.pointColor : '#cbd5e1'
  return renderScoreIcons(event.pointType, count, color, `${density} ${event.status === 'APPROVED' ? '' : 'muted'}`.trim())
}

function renderCalendarDaySummary(kind, events) {
  if (kind === 'points') {
    const positiveCount = events.reduce((sum, event) => sum + Math.max(0, Number(event.changeAmount || 0)), 0)
    const negativeCount = events.reduce((sum, event) => sum + Math.abs(Math.min(0, Number(event.changeAmount || 0))), 0)
    return `
      <span>${events.length} 条</span>
      <span>+${positiveCount}</span>
      <span>-${negativeCount}</span>
    `
  }
  if (kind === 'rewards') {
    return `
      <span>${events.length} 个奖励</span>
    `
  }
  const completedCount = events.filter((event) => event.status === 'APPROVED').length
  const submittedCount = events.filter((event) => event.status === 'SUBMITTED').length
  const pendingCount = events.filter((event) => event.status === 'PENDING').length
  return `
    <span>${events.length} 个任务</span>
    <span>${completedCount} 已完成</span>
    <span>${submittedCount} 待审核</span>
    <span>${pendingCount} 待打卡</span>
  `
}

function renderTaskCheckInModal(state) {
  if (!state.taskCheckInModalOpen || !state.selectedCheckInTaskId) return ''
  const taskDate = state.selectedCheckInTaskDate || state.todayKey
  const event = (state.calendarEvents || []).find((item) => String(item.taskId) === String(state.selectedCheckInTaskId) && item.taskDate === taskDate)
  const task = (state.tasks || []).find((item) => String(item.id) === String(state.selectedCheckInTaskId))
  const taskMeta = task || event
  if (!taskMeta) return ''
  const pointType = taskMeta.pointType || 'STAR'
  const pointColor = taskMeta.pointColor || pointTypeColor(pointType)
  const basePoints = Math.max(1, Number(taskMeta.basePoints || 1))
  const selectedCount = clamp(Number(state.selectedCheckInRewardCount || basePoints), 1, basePoints)
  return `
    <div class="modal-backdrop">
      <section class="modal narrow checkin-modal">
        <div class="modal-head">
          <div><h2>任务打卡</h2><p>${escapeHtml(taskMeta.name || taskMeta.taskName || '任务')}</p></div>
          <button class="icon-btn" type="button" data-action="close-checkin-modal">×</button>
        </div>
        <form class="modal-form" data-form="task-checkin">
          <div class="checkin-task-note wide">
            ${escapeHtml(taskMeta.description || '完成后选择本次获得的奖励数量。')}
          </div>
          <div class="checkin-reward-panel wide" style="--point-color:${escapeHtml(pointColor)}">
            <div class="field-label">奖励</div>
            <div class="checkin-reward-grid">
              ${Array.from({ length: basePoints }, (_, index) => {
                const number = index + 1
                return `
                  <button class="checkin-reward-icon ${number <= selectedCount ? 'active' : ''}" type="button" data-action="select-checkin-reward" data-reward-index="${number}" aria-label="选择 ${number} 个奖励">
                    ${pointIcon(pointType)}
                  </button>
                `
              }).join('')}
            </div>
            <div class="checkin-reward-count" data-checkin-reward-count data-total="${basePoints}">${selectedCount} / ${basePoints}</div>
          </div>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-checkin-modal">取消</button>
            <button class="btn primary" type="submit">打卡</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderStoreView(state) {
  return `
    <section class="panel panel-pad">
      ${state.rewardExchangeSuccess ? renderRewardCheer(state.rewardExchangeSuccess) : ''}
      <div class="reward-grid">
        ${state.rewards.map((reward) => `
          <article class="reward-card">
            <div class="reward-icon" style="--reward-color:${reward.rewardColor || '#6c63ff'}">${escapeHtml(reward.rewardIcon || '🎁')}</div>
            <div>
              <h3>${escapeHtml(reward.name)}</h3>
              <p>${escapeHtml(reward.description || '家庭奖励')}</p>
            </div>
            <div class="reward-card-price">
              ${renderRewardPriceSummary(reward, state)}
            </div>
            <button class="btn primary" type="button" data-action="exchange-reward" data-reward-id="${reward.id}" data-no-card-click="true">
              ${reward.requiredPoints} ${pointIcon(reward.requiredPointType)}
            </button>
          </article>
        `).join('') || '<div class="empty">暂无奖励</div>'}
      </div>
    </section>
  `
}

function renderRewardPriceSummary(reward, state) {
  const currencies = getPointCurrencies(state)
  const meta = currencyMeta(reward.requiredPointType || 'STAR', currencies)
  return `
    <span style="--point-color:${escapeHtml(meta.color)}">${escapeHtml(meta.icon)}</span>
    <strong>${escapeHtml(reward.requiredPoints || 1)}</strong>
  `
}

function renderRewardCheer(success) {
  return `
    <div class="reward-cheer" style="--reward-color:${escapeHtml(success.rewardColor || '#ff9f43')}">
      <div class="cheer-burst">
        ${Array.from({ length: 10 }, (_, index) => `<span style="--i:${index}">${escapeHtml(success.rewardIcon || '🎁')}</span>`).join('')}
      </div>
      <strong>兑换成功</strong>
      <p>获得一个${escapeHtml(success.rewardName || '奖励')}奖励的兑换券</p>
    </div>
  `
}

function renderRewardManageView(state) {
  const filters = state.rewardFilters || {}
  const rewards = filterRewards(state.rewards, filters)
  const expanded = Boolean(state.filterAdvanced?.reward)
  return `
    <section class="panel panel-pad">
      ${renderExchangeTodo(state)}
      <div class="section-inline-head">
        <h2>奖励列表</h2>
        <div class="section-actions">
          <span>${rewards.length} 个</span>
          <button class="btn primary" type="button" data-action="open-reward-modal">新增奖励</button>
        </div>
      </div>
      ${renderManagementFilterBar({
        scope: 'reward',
        filters,
        keywordPlaceholder: '奖励名称',
        expanded,
        fields: `
          <label><span>积分类型</span>${renderSelect('reward-point', filters.pointType || '', [['', '全部'], ['STAR', '星星'], ['FLOWER', '红花'], ['CROWN', '皇冠']], 'reward')}</label>
          <label><span>状态</span>${renderSelect('reward-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停']], 'reward')}</label>
        `,
      })}
      <div class="table-card">
        <div class="table-head reward-table">
          <span>奖励</span><span>需要积分</span><span>库存</span><span>实现方式</span><span>操作</span>
        </div>
        ${rewards.map(renderRewardRow).join('') || '<div class="empty">暂无奖励</div>'}
      </div>
    </section>
  `
}

function renderCurrencyManageView(state) {
  const filters = state.currencyFilters || {}
  const currencies = filterCurrencies(getPointCurrencies(state), filters)
  const expanded = Boolean(state.filterAdvanced?.currency)
  return `
    <section class="panel panel-pad">
      <div class="section-inline-head">
        <h2>币值列表</h2>
        <div class="section-actions">
          <span>${currencies.length} 个</span>
          <button class="btn primary" type="button" data-action="open-currency-modal">新增币值</button>
        </div>
      </div>
      ${renderManagementFilterBar({
        scope: 'currency',
        filters,
        keywordPlaceholder: '名称或图标',
        expanded,
        fields: `
          <label><span>积分类型</span>${renderSelect('currency-point', filters.pointType || '', [['', '全部'], ...pointTypeOptions], 'currency')}</label>
          <label><span>状态</span>${renderSelect('currency-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['INACTIVE', '停用']], 'currency')}</label>
        `,
      })}
      <div class="table-card">
        <div class="table-head currency-table">
          <span>币值</span><span>积分类型</span><span>比例</span><span>状态</span><span>操作</span>
        </div>
        ${currencies.map(renderCurrencyRow).join('') || '<div class="empty">暂无币值配置</div>'}
      </div>
    </section>
  `
}

function renderCurrencyRow(currency) {
  const meta = currencyMeta(currency.pointType, [currency])
  const canMutate = Boolean(currency.id)
  return `
    <div class="table-row currency-table">
      <div class="task-name-cell">
        <span class="currency-mini" style="--point-color:${escapeHtml(meta.color)}">${escapeHtml(meta.icon)}</span>
        <div><strong>${escapeHtml(meta.name)}</strong><small>${escapeHtml(meta.color)} · 排序 ${currency.sortNo ?? meta.sortNo ?? 0}</small></div>
      </div>
      <span>${escapeHtml(pointName(currency.pointType))}</span>
      <span class="score-pill">${escapeHtml(meta.icon)} 1:${escapeHtml(currency.exchangeWeight || meta.exchangeWeight)}</span>
      <span>${statusName(currency.status || 'ACTIVE')}</span>
      <div class="row-actions task-row-actions">
        <button class="row-icon-btn" type="button" data-action="edit-currency" data-currency-id="${escapeHtml(currency.id || '')}" aria-label="修改币值" title="修改币值" ${canMutate ? '' : 'disabled'}>${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-currency" data-currency-id="${escapeHtml(currency.id || '')}" aria-label="删除币值" title="删除币值" ${canMutate ? '' : 'disabled'}>${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderPointExchangeRulePanel(state) {
  const rule = normalizeExchangeRule(state.pointExchangeRule)
  const currencies = getPointCurrencies(state)
  return `
    <div class="exchange-rule-panel">
      <div>
        <h2>币值</h2>
      </div>
      <form class="exchange-rule-form" data-form="point-exchange-rule">
        <label><span>星星币</span><input type="number" min="1" name="starWeight" value="${escapeHtml(rule.starWeight)}" /></label>
        <label><span>红花币</span><input type="number" min="1" name="flowerWeight" value="${escapeHtml(rule.flowerWeight)}" /></label>
        <label><span>皇冠币</span><input type="number" min="1" name="crownWeight" value="${escapeHtml(rule.crownWeight)}" /></label>
        <button class="btn primary" type="submit">保存</button>
      </form>
      <div class="exchange-ratio-preview">
        <span>${currencyIcon('STAR', currencies)} ${currencyName('STAR', currencies)} 1:${rule.starWeight}</span>
        <span>${currencyIcon('FLOWER', currencies)} ${currencyName('FLOWER', currencies)} 1:${rule.flowerWeight}</span>
        <span>${currencyIcon('CROWN', currencies)} ${currencyName('CROWN', currencies)} 1:${rule.crownWeight}</span>
      </div>
    </div>
  `
}

function renderExchangeTodo(state) {
  const todos = (state.exchanges || []).filter((exchange) => ['REQUESTED', 'APPROVED'].includes(exchange.status))
  const expanded = Boolean(state.todoExpanded?.reward)
  const visibleTodos = expanded ? todos : todos.slice(0, 3)
  return `
    <section class="todo-panel">
      <div class="section-inline-head">
        <h2>兑换待办</h2>
        <span>${todos.length} 条</span>
      </div>
      <div class="todo-list">
        ${visibleTodos.map((exchange) => `
          <div class="todo-item">
            <span class="reward-mini" style="--reward-color:${exchange.rewardColorSnapshot || '#6c63ff'}">${escapeHtml(exchange.rewardIconSnapshot || '🎁')}</span>
            <div>
              <strong>${escapeHtml(exchange.rewardNameSnapshot || '奖励兑换')}</strong>
              <small>${exchange.requiredPointsSnapshot || 0} ${pointIcon(exchange.requiredPointType)} · ${exchange.status === 'REQUESTED' ? '待确认' : fulfillmentStatusName(exchange.fulfillmentStatus)}</small>
            </div>
            <div class="row-actions">
              ${exchange.status === 'REQUESTED'
                ? `<button class="small-btn primary-lite" type="button" data-action="approve-exchange" data-exchange-id="${exchange.id}">通过</button><button class="small-btn danger" type="button" data-action="reject-exchange" data-exchange-id="${exchange.id}">拒绝</button>`
                : renderFulfillmentActions(exchange)}
            </div>
          </div>
        `).join('') || '<div class="empty compact-empty">暂无兑换待办</div>'}
        ${todos.length > 3 ? `<button class="todo-more-btn" type="button" data-action="toggle-reward-todo">${expanded ? '收起' : `更多 ${todos.length - 3} 条`}</button>` : ''}
      </div>
    </section>
  `
}

function renderRewardRow(reward) {
  const fulfillmentType = fulfillmentTypeName(reward.fulfillmentType)
  return `
    <div class="table-row reward-table">
      <div class="task-name-cell">
        <span class="reward-mini" style="--reward-color:${reward.rewardColor || '#6c63ff'}">${escapeHtml(reward.rewardIcon || '🎁')}</span>
        <div>
          <strong>${escapeHtml(reward.name)}</strong>
          <small>${escapeHtml(reward.description || '')}</small>
          <span class="fulfillment-chip">${escapeHtml(fulfillmentType)}</span>
        </div>
      </div>
      <span class="score-pill">${reward.requiredPoints} ${pointIcon(reward.requiredPointType)}</span>
      <span>${reward.stockTotal > 0 ? `${reward.stockRemaining}/${reward.stockTotal}` : '不限'}</span>
      <span>${escapeHtml(fulfillmentType)}</span>
      <div class="row-actions task-row-actions">
        <button class="row-icon-btn" type="button" data-action="edit-reward" data-reward-id="${reward.id}" aria-label="修改奖励" title="修改奖励">${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-reward" data-reward-id="${reward.id}" aria-label="删除奖励" title="删除奖励">${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderStats(state) {
  const balances = new Map((state.balances || []).map((item) => [item.pointType, item]))
  const currencies = getPointCurrencies(state)
  const todayEvents = state.calendarEvents.filter((event) => event.taskDate === state.todayKey)
  const completedToday = todayEvents.filter((event) => event.status === 'APPROVED').length
  const todayTasks = todayEvents.length || state.tasks.length
  const completionRate = todayTasks ? Math.round((completedToday / Math.max(todayTasks, 1)) * 100) : 0
  return `
    <div class="stats">
      ${['STAR', 'FLOWER', 'CROWN'].map((type) => {
        const balance = balances.get(type) || { balance: 0, earnedTotal: 0, spentTotal: 0 }
        const meta = currencyMeta(type, currencies)
        return `
          <button class="stat balance-stat balance-stat-compact" type="button" data-action="open-balance-modal" data-point-type="${type}" title="${escapeHtml(meta.name)}">
            <div class="balance-number">${Number(balance.balance || 0)}</div>
            <div class="balance-big-icon" style="--point-color:${escapeHtml(meta.color)}">${escapeHtml(meta.icon)}</div>
          </button>
        `
      }).join('')}
      <div class="stat">
        <div class="label">今日完成率</div>
        <div class="value">${clamp(completionRate, 0, 100)}%</div>
        <div class="foot">${completedToday} / ${todayTasks} 个日历任务</div>
      </div>
    </div>
  `
}

function renderRewardTickets(state) {
  const tickets = (state.exchanges || []).filter((exchange) => (
    ['REQUESTED', 'APPROVED', 'COMPLETED'].includes(exchange.status)
    && exchange.status !== 'REJECTED'
  ))
  return `
    <section class="panel panel-pad wide">
      <div class="section-inline-head">
        <h2>礼物兑换券</h2>
        <span>${tickets.length} 张</span>
      </div>
      <div class="ticket-list">
        ${tickets.map((exchange) => {
          const confirmed = exchange.status === 'COMPLETED' || exchange.fulfillmentStatus === 'CONFIRMED'
          const confirmable = exchange.status === 'APPROVED' && !confirmed
          const stateText = exchange.status === 'REQUESTED'
            ? '待家长确认'
            : confirmed ? '已确认' : '可确认'
          const subtitle = exchange.status === 'REQUESTED'
            ? '待家长确认'
            : fulfillmentStatusName(exchange.fulfillmentStatus)
          return `
          <button class="ticket-item ${confirmed ? 'confirmed' : ''} ${exchange.status === 'REQUESTED' ? 'pending' : ''}" type="button" ${confirmable ? `data-action="confirm-reward-ticket" data-exchange-id="${exchange.id}"` : 'disabled'}>
            <span class="reward-mini" style="--reward-color:${exchange.rewardColorSnapshot || '#6c63ff'}">${escapeHtml(exchange.rewardIconSnapshot || '🎁')}</span>
            <div>
              <strong>${escapeHtml(exchange.rewardNameSnapshot || '礼物券')}</strong>
              <small>${escapeHtml(subtitle)}</small>
            </div>
            <span class="ticket-state">${escapeHtml(stateText)}</span>
          </button>
        `}).join('') || '<div class="empty compact-empty">暂无礼物兑换券</div>'}
      </div>
    </section>
  `
}

function renderFulfillmentActions(exchange) {
  const status = exchange.fulfillmentStatus || 'PENDING'
  const options = [
    ['SCHEDULED', '加入日程'],
    ['IN_PROGRESS', '待实现'],
    ['COMPLETED', '已实现'],
  ]
  return options.map(([value, label]) => `
    <button class="small-btn ${status === value ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.id}" data-fulfillment-status="${value}">${label}</button>
  `).join('') + `<button class="small-btn" type="button" data-action="confirm-reward-ticket" data-exchange-id="${exchange.id}">确认</button>`
}

function fulfillmentTypeName(type) {
  const names = {
    INVENTORY_DEDUCT: '库存扣减',
    PARENT_EXECUTE: '家长执行',
    PARENT_PURCHASE: '家长购买',
    PARENT_FULFILL: '家长实现',
  }
  return names[type] || '库存扣减'
}

function renderLedger(state) {
  return `
    <div class="section-inline-head">
      <h2>积分流水</h2>
      <span>${state.ledger.length} 条</span>
    </div>
    <div class="timeline">
      ${state.ledger.map((item) => `
        <div class="timeline-item">
          <div class="timeline-dot ${item.changeAmount >= 0 ? 'positive' : 'negative'}"></div>
          <div>
            <h5>${item.changeAmount >= 0 ? '+' : ''}${item.changeAmount} ${pointName(item.pointType)} · ${escapeHtml(item.sourceName || '积分变动')}</h5>
            <p>${escapeHtml(item.reason || '')}</p>
          </div>
        </div>
      `).join('') || '<div class="empty">暂无流水</div>'}
    </div>
  `
}

function renderTaskModal(state) {
  if (!state.taskModalOpen) return ''
  const draftDate = state.taskDraftDate || ''
  const draftDay = draftDate ? Number(String(draftDate).slice(8, 10)) : 0
  const task = state.editingTask || (draftDay ? { periodType: 'MONTHLY', scheduleJson: JSON.stringify({ type: 'MONTHLY', category: 'HABIT', days: [draftDay], requiredCount: 1 }) } : {})
  const schedule = parseSchedule(task.scheduleJson)
  const periodType = task.periodType || schedule.type || 'DAILY'
  const startHour = Number(schedule.timeRange?.startHour ?? schedule.startHour ?? 6)
  const endHour = Number(schedule.timeRange?.endHour ?? schedule.endHour ?? 22)
  const isEditingTask = Boolean(task.id || draftDay)
  const dailyHours = getDailyHours(schedule, startHour, endHour, isEditingTask)
  const selectedDays = Array.isArray(schedule.days) ? schedule.days.map(Number) : []
  const requiredCount = Number(schedule.requiredCount ?? schedule.timesPerWeek ?? 1)
  const pointType = task.pointType || 'STAR'
  const pointColor = task.pointColor || '#ffd84d'
  const basePoints = Number(task.basePoints || 1)
  const requireApproval = Number(task.requireApproval) === 1 || task.requireApproval === true
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${task.id ? '修改任务' : '新增任务'}</h2><p>设置周期、可完成时间、任务次数和奖励。</p></div>
          <button class="icon-btn" type="button" data-action="close-task-modal">×</button>
        </div>
        <form class="modal-form" data-form="task">
          <input type="hidden" name="taskId" value="${escapeHtml(task.id || '')}" />
          <label><span>任务名称</span><input name="name" value="${escapeHtml(task.name || '')}" placeholder="${draftDate ? '例如：当天阅读任务' : '例如：晨读 20 分钟'}" /></label>
          <label><span>所属目标</span>${renderSelect('goalId', task.goalId || state.goals[0]?.id || '', state.goals.map((goal) => [goal.id, goal.name]))}</label>
          <label><span>任务分类</span>${renderSelect('taskCategory', schedule.category || 'HABIT', [['STUDY', '学习'], ['LIFE', '生活'], ['SPORT', '运动'], ['HABIT', '习惯']])}</label>
          <label class="wide"><span>说明</span><input name="description" value="${escapeHtml(task.description || '')}" placeholder="给孩子看的简短说明" /></label>
          <div class="schedule-type-row wide">
            <div>
              <div class="schedule-title">任务类型</div>
              <div class="segmented">
                ${[['DAILY', '每日'], ['WEEKLY', '每周'], ['MONTHLY', '每月']].map(([value, label]) => `
                  <label><input type="radio" name="periodType" value="${value}" data-schedule-mode ${periodType === value ? 'checked' : ''} /> <span>${label}</span></label>
                `).join('')}
              </div>
            </div>
            <label><span>色彩</span><input type="color" name="taskColor" value="${escapeHtml(task.taskColor || '#30d5ff')}" /></label>
          </div>
          <div class="schedule-panel wide ${periodType === 'DAILY' ? '' : 'hidden'}" data-schedule-panel="DAILY">
            <div class="schedule-title">每日可完成时间</div>
            ${renderDailyHourPicker(dailyHours, task.taskColor || '#30d5ff')}
            <input type="hidden" name="startHour" value="${escapeHtml(startHour)}" />
            <input type="hidden" name="endHour" value="${escapeHtml(endHour)}" />
            <label class="count-field"><span>任务次数</span><input type="number" min="1" max="24" name="dailyRequiredCount" value="${escapeHtml(requiredCount || 1)}" /></label>
          </div>
          <div class="schedule-panel wide ${periodType === 'WEEKLY' ? '' : 'hidden'}" data-schedule-panel="WEEKLY">
            <div class="schedule-title">每周可完成日期</div>
            ${renderWeekDayPicker(selectedDays, task.taskColor || '#30d5ff')}
            <label class="count-field"><span>每周完成次数</span><input type="number" min="1" max="7" name="weeklyRequiredCount" value="${escapeHtml(requiredCount || 1)}" /></label>
          </div>
          <div class="schedule-panel wide ${periodType === 'MONTHLY' ? '' : 'hidden'}" data-schedule-panel="MONTHLY">
            <div class="schedule-title">每月可完成日期</div>
            ${renderMonthDayPicker(selectedDays, task.taskColor || '#30d5ff')}
            <label class="count-field"><span>每月完成次数</span><input type="number" min="1" max="31" name="monthlyRequiredCount" value="${escapeHtml(requiredCount || 1)}" /></label>
          </div>
          <div class="point-config wide">
            <div class="field-label">奖励类型</div>
            ${renderPointTypePicker(pointType)}
          </div>
          <label><span>奖励数量</span><input type="number" min="1" max="99" name="basePoints" value="${escapeHtml(basePoints)}" /></label>
          <label><span>奖励颜色</span><input type="color" name="pointColor" value="${escapeHtml(pointColor)}" title="选择奖励图标颜色" /></label>
          <div class="approval-mode wide">
            <span class="field-label">奖励生效方式</span>
            <div class="segmented">
              <label><input type="radio" name="requireApproval" value="false" ${requireApproval ? '' : 'checked'} /> <span>打卡生效</span></label>
              <label><input type="radio" name="requireApproval" value="true" ${requireApproval ? 'checked' : ''} /> <span>审批后生效</span></label>
            </div>
          </div>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-task-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderDailyHourPicker(selectedHours, taskColor) {
  const selectedSet = new Set(selectedHours.map(Number))
  return `
    <div class="time-choice-grid hour-choice-grid" data-daily-hour-picker data-task-color-surface style="--task-color:${escapeHtml(taskColor || '#30d5ff')}">
      ${dailyHourOptions.map((hour) => {
        const selectable = hour >= 6 && hour <= 22
        const checked = selectable && selectedSet.has(hour)
        const label = `${String(hour).padStart(2, '0')}:00`
        return `
          <label class="block-choice hour-choice ${selectable ? '' : 'disabled'}" title="${escapeHtml(selectable ? label : `${label} 不可选`)}">
            <input type="checkbox" name="dailyHours" value="${hour}" ${checked ? 'checked' : ''} ${selectable ? '' : 'disabled'} />
            <span>${hour}</span>
          </label>
        `
      }).join('')}
    </div>
  `
}

function getDailyHours(schedule, startHour, endHour, useRangeFallback = true) {
  if (Array.isArray(schedule.hours) && schedule.hours.length) {
    return schedule.hours.map(Number).filter((hour) => hour >= 6 && hour <= 22)
  }
  if (!useRangeFallback) {
    return []
  }
  const safeStart = clamp(Number(startHour), 6, 22)
  const safeEnd = clamp(Number(endHour), 6, 22)
  return dailyHourOptions.filter((hour) => hour >= safeStart && hour <= safeEnd)
}

function renderWeekDayPicker(selectedDays, taskColor = '#30d5ff') {
  return `
    <div class="time-choice-grid week-picker" data-weekday-picker data-task-color-surface style="--task-color:${escapeHtml(taskColor)}">
      ${weekDays.map(([value, label]) => `
        <label class="block-choice day-choice" title="${escapeHtml(label)}">
          <input type="checkbox" name="weekDays" value="${value}" ${selectedDays.includes(value) ? 'checked' : ''} />
          <span>${label}</span>
        </label>
      `).join('')}
    </div>
  `
}

function renderMonthDayPicker(selectedDays, taskColor = '#30d5ff') {
  return `
    <div class="time-choice-grid month-day-picker" data-monthday-picker data-task-color-surface style="--task-color:${escapeHtml(taskColor)}">
      ${dayOfMonthOptions.map(([value, label]) => `
        <label class="block-choice month-choice" title="${escapeHtml(label)}">
          <input type="checkbox" name="monthDays" value="${value}" ${selectedDays.includes(value) ? 'checked' : ''} />
          <span>${value}</span>
        </label>
      `).join('')}
    </div>
  `
}

function renderPointTypePicker(selectedPointType) {
  return `
    <div class="point-type-picker">
      ${pointTypeOptions.map(([value, label]) => `
        <label class="point-type-choice" title="${escapeHtml(label)}">
          <input type="radio" name="pointType" value="${value}" ${selectedPointType === value ? 'checked' : ''} />
          <span><b>${pointIcon(value)}</b>${escapeHtml(label)}</span>
        </label>
      `).join('')}
    </div>
  `
}

function renderScoreIcons(pointType, count, color, density = '') {
  return `
    <span class="score-icons ${density}" style="--point-color:${escapeHtml(color || '#f59e0b')}">
      ${renderScoreIconItems(pointType, count)}
    </span>
  `
}

function renderScoreIconItems(pointType, count) {
  const safeCount = clamp(Number(count || 1), 1, 99)
  return Array.from({ length: safeCount }, () => `<span class="score-icon">${pointIcon(pointType)}</span>`).join('')
}

function renderPointIcons(pointType, count) {
  const safeCount = Math.max(0, Number(count || 0))
  const visibleCount = Math.min(safeCount, 10)
  return `
    <span class="balance-icon-cluster" style="--point-color:${pointTypeColor(pointType)}" aria-label="${safeCount} ${pointName(pointType)}">
      ${Array.from({ length: visibleCount }, () => `<span>${pointIcon(pointType)}</span>`).join('')}
      <b>${safeCount}</b>
    </span>
  `
}

function renderFullScoreIcons(pointType, count) {
  const safeCount = Math.max(0, Number(count || 0))
  const visibleCount = Math.min(safeCount, 300)
  const icons = Array.from({ length: visibleCount }, () => `<span class="score-icon">${pointIcon(pointType)}</span>`).join('')
  const more = safeCount > visibleCount ? `<span class="score-more">+${safeCount - visibleCount}</span>` : ''
  return `${icons}${more}`
    || '<span class="empty compact-empty">暂无积分</span>'
}

function renderLimitedPointIcons(icon, count, color, limit = 9) {
  const safeCount = Math.max(0, Number(count || 0))
  const visibleCount = Math.min(safeCount, limit)
  return `
    <span class="limited-point-icons" style="--point-color:${escapeHtml(color || '#f59e0b')}">
      ${Array.from({ length: visibleCount }, () => `<i>${escapeHtml(icon)}</i>`).join('')}
      ${safeCount > visibleCount ? '<b>...</b>' : ''}
    </span>
  `
}

function renderDailyHourBlocks(selectedHours, density = '') {
  const selectedSet = new Set(selectedHours.map(Number))
  return `
    <div class="time-blocks hour-blocks ${density}">
      ${dailyHourOptions.map((hour) => {
        const selectable = hour >= 6 && hour <= 22
        const active = selectable && selectedSet.has(hour)
        const label = `${formatHour(hour)} 时间块`
        return `<span class="time-block ${active ? 'active' : ''} ${selectable ? '' : 'disabled'}" title="${escapeHtml(label)}">${hour}</span>`
      }).join('')}
    </div>
  `
}

function renderActionIcon(type) {
  if (type === 'delete') {
    return `
      <svg viewBox="0 0 24 24" aria-hidden="true">
        <path d="M3 6h18"></path>
        <path d="M8 6V4h8v2"></path>
        <path d="M19 6l-1 14H6L5 6"></path>
        <path d="M10 11v5"></path>
        <path d="M14 11v5"></path>
      </svg>
    `
  }
  return `
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path d="M12 20h9"></path>
      <path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"></path>
    </svg>
  `
}

function renderChildModal(state) {
  if (!state.childModalOpen) return ''
  const child = state.editingChild || {}
  const createAccount = Boolean(state.childAccountDraftEnabled)
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${child.id ? '修改孩子档案' : '新增孩子档案'}</h2><p>维护孩子昵称、生日和备注。</p></div>
          <button class="icon-btn" type="button" data-action="close-child-modal">×</button>
        </div>
        <form class="modal-form" data-form="child">
          <input type="hidden" name="childId" value="${escapeHtml(child.id || '')}" />
          <label><span>孩子昵称</span><input name="nickname" value="${escapeHtml(child.nickname || '')}" placeholder="例如：小星" /></label>
          <label><span>性别</span>${renderSelect('gender', child.gender || 'UNKNOWN', [['UNKNOWN', '未设置'], ['MALE', '男孩'], ['FEMALE', '女孩']])}</label>
          <label><span>生日</span><input type="date" name="birthday" value="${escapeHtml(child.birthday || '')}" /></label>
          <label><span>头像链接</span><input name="avatarUrl" value="${escapeHtml(child.avatarUrl || '')}" placeholder="可选" /></label>
          <label class="wide"><span>备注</span><input name="remark" value="${escapeHtml(child.remark || '')}" placeholder="孩子偏好、阶段目标等" /></label>
          <label class="switch-field wide">
            <input type="checkbox" name="createChildAccount" value="true" ${createAccount ? 'checked' : ''} />
            <span>创建子账户</span>
          </label>
          <div class="child-account-fields wide ${createAccount ? '' : 'hidden'}">
            <label><span>子账户账号</span><input name="childUsername" placeholder="例如：baby-star" /></label>
            <label><span>子账户密码</span><input name="childPassword" type="password" placeholder="至少 6 位" /></label>
          </div>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-child-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderGoalModal(state) {
  if (!state.goalModalOpen) return ''
  const goal = state.editingGoal || {}
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${goal.id ? '修改成长目标' : '新增成长目标'}</h2><p>目标用于承载每日、每周和每月任务。</p></div>
          <button class="icon-btn" type="button" data-action="close-goal-modal">×</button>
        </div>
        <form class="modal-form" data-form="goal">
          <input type="hidden" name="goalId" value="${escapeHtml(goal.id || '')}" />
          <label><span>目标名称</span><input name="name" value="${escapeHtml(goal.name || '')}" placeholder="例如：阅读小达人" /></label>
          <label><span>图标</span><input name="icon" value="${escapeHtml(goal.icon || '★')}" /></label>
          <label class="wide"><span>说明</span><input name="description" value="${escapeHtml(goal.description || '')}" placeholder="目标说明" /></label>
          <label><span>目标颜色</span><input type="color" name="goalColor" value="${escapeHtml(goal.goalColor || '#6c63ff')}" /></label>
          <label><span>目标积分</span><input type="number" min="0" name="targetPoints" value="${escapeHtml(goal.targetPoints || 0)}" /></label>
          <label><span>开始日期</span><input type="date" name="startDate" value="${escapeHtml(goal.startDate || '')}" /></label>
          <label><span>结束日期</span><input type="date" name="endDate" value="${escapeHtml(goal.endDate || '')}" /></label>
          <label><span>排序</span><input type="number" min="0" name="sortNo" value="${escapeHtml(goal.sortNo || 0)}" /></label>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-goal-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderRewardModal(state) {
  if (!state.rewardModalOpen) return ''
  const reward = state.editingReward || {}
  const rewardIcon = state.rewardDraftIcon || reward.rewardIcon || '🎁'
  const rewardColor = reward.rewardColor || '#ff9f43'
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${reward.id ? '修改奖励' : '新增奖励'}</h2><p>设置兑换所需积分、库存和颜色。</p></div>
          <button class="icon-btn" type="button" data-action="close-reward-modal">×</button>
        </div>
        <form class="modal-form" data-form="reward">
          <input type="hidden" name="rewardId" value="${escapeHtml(reward.id || '')}" />
          <label><span>奖励名称</span><input name="name" value="${escapeHtml(reward.name || '')}" placeholder="例如：周末冰淇淋" /></label>
          <div class="icon-field">
            <label>
              <span>图标</span>
              <input name="rewardIcon" value="${escapeHtml(rewardIcon)}" readonly data-action="open-reward-icon-picker" />
            </label>
            <button class="icon-select-btn" type="button" data-action="open-reward-icon-picker" aria-label="选择奖励图标" style="--reward-color:${escapeHtml(rewardColor)}">${escapeHtml(rewardIcon)}</button>
            <div class="icon-popover hidden">
              ${rewardIconOptions.map((icon) => `
                <button class="${String(rewardIcon) === icon ? 'active' : ''}" type="button" data-action="select-reward-icon" data-reward-icon="${escapeHtml(icon)}">${escapeHtml(icon)}</button>
              `).join('')}
            </div>
          </div>
          <label class="wide"><span>说明</span><input name="description" value="${escapeHtml(reward.description || '')}" placeholder="奖励说明" /></label>
          <label><span>奖励颜色</span><input type="color" name="rewardColor" value="${escapeHtml(reward.rewardColor || '#ff9f43')}" /></label>
          <label><span>积分类型</span>${renderSelect('requiredPointType', reward.requiredPointType || 'STAR', [['STAR', '星星'], ['FLOWER', '红花'], ['CROWN', '皇冠']])}</label>
          <label><span>所需积分</span><input type="number" min="1" name="requiredPoints" value="${escapeHtml(reward.requiredPoints || 1)}" /></label>
          <label><span>库存</span><input type="number" min="0" name="stockTotal" value="${escapeHtml(reward.stockTotal || 0)}" /></label>
          <label><span>兑换限制</span>${renderSelect('exchangeLimitType', reward.exchangeLimitType || 'UNLIMITED', [['UNLIMITED', '不限'], ['DAILY', '每日'], ['WEEKLY', '每周'], ['MONTHLY', '每月']])}</label>
          <label><span>限制次数</span><input type="number" min="0" name="exchangeLimitCount" value="${escapeHtml(reward.exchangeLimitCount || 0)}" /></label>
          <div class="wide">
            <div class="field-label">实现方式</div>
            <div class="segmented reward-fulfillment">
              ${fulfillmentTypeOptions.map(([value, label]) => `
                <label>
                  <input type="radio" name="fulfillmentType" value="${value}" ${String(reward.fulfillmentType || 'INVENTORY_DEDUCT') === value ? 'checked' : ''} />
                  <span>${label}</span>
                </label>
              `).join('')}
            </div>
          </div>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-reward-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderRewardExchangeModal(state) {
  if (!state.rewardExchangeModalOpen || !state.selectedRewardId) return ''
  const reward = (state.rewards || []).find((item) => String(item.id) === String(state.selectedRewardId))
  if (!reward) return ''
  const currencies = getPointCurrencies(state)
  const balances = new Map((state.balances || []).map((item) => [item.pointType, Number(item.balance || 0)]))
  const options = buildRewardPaymentOptions(reward, state.balances, state.pointExchangeRule)
  const requiredCurrency = currencyMeta(reward.requiredPointType || 'STAR', currencies)
  return `
    <div class="modal-backdrop">
      <section class="modal reward-exchange-modal">
        <div class="modal-head">
          <div class="reward-exchange-title">
            <span class="reward-mini large" style="--reward-color:${escapeHtml(reward.rewardColor || '#ff9f43')}">${escapeHtml(reward.rewardIcon || '🎁')}</span>
            <div><h2>${escapeHtml(reward.name || '奖励')}</h2></div>
          </div>
          <button class="icon-btn" type="button" data-action="close-reward-exchange-modal">×</button>
        </div>
        <div class="reward-balance-strip">
          ${['STAR', 'FLOWER', 'CROWN'].map((pointType) => {
            const meta = currencyMeta(pointType, currencies)
            return `
              <div class="reward-balance-item" style="--point-color:${escapeHtml(meta.color)}">
                <strong>${balances.get(pointType) || 0}</strong>
                <span>${escapeHtml(meta.icon)}</span>
              </div>
            `
          }).join('')}
        </div>
        <div class="reward-price-panel" style="--point-color:${escapeHtml(requiredCurrency.color)}">
          <strong>${escapeHtml(reward.requiredPoints || 1)}</strong>
          ${renderLimitedPointIcons(requiredCurrency.icon, reward.requiredPoints, requiredCurrency.color, 9)}
        </div>
        <div class="reward-payment-options">
          ${options.map((option) => {
            const meta = currencyMeta(option.pointType, currencies)
            const changeText = option.changeAmount > 0 ? `找零 ${option.changeAmount}` : ''
            return `
              <button class="payment-option ${option.enough ? '' : 'disabled'}" type="button" data-action="select-reward-payment" data-payment-point-type="${option.pointType}" ${option.enough ? '' : 'disabled'} style="--point-color:${escapeHtml(meta.color)}">
                <span>${escapeHtml(meta.icon)}</span>
                <strong>${option.payAmount}</strong>
                <small>${escapeHtml(changeText)}</small>
              </button>
            `
          }).join('')}
        </div>
        <div class="modal-actions wide">
          <button class="btn" type="button" data-action="close-reward-exchange-modal">取消</button>
        </div>
      </section>
    </div>
  `
}

function renderPointCurrencyModal(state) {
  if (!state.pointCurrencyModalOpen) return ''
  const currency = state.editingPointCurrency || {}
  const pointType = currency.pointType || 'STAR'
  const defaults = defaultCurrencyMeta[pointType] || defaultCurrencyMeta.STAR
  const icon = currency.icon || defaults.icon
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${currency.id ? '修改币值' : '新增币值'}</h2><p>配置孩子看到的积分图标、颜色和兑换比例。</p></div>
          <button class="icon-btn" type="button" data-action="close-currency-modal">×</button>
        </div>
        <form class="modal-form" data-form="point-currency">
          <input type="hidden" name="currencyId" value="${escapeHtml(currency.id || '')}" />
          <label><span>名称</span><input name="name" value="${escapeHtml(currency.name || defaults.name)}" placeholder="例如：星星" /></label>
          <label><span>积分类型</span>${renderSelect('pointType', pointType, pointTypeOptions)}</label>
          <div class="icon-field wide">
            <label><span>图标</span><input name="icon" value="${escapeHtml(icon)}" /></label>
            <div class="icon-picker currency-icon-picker">
              ${currencyIconOptions.map((option) => `
                <label class="icon-choice" title="${escapeHtml(option)}">
                  <input type="radio" name="currencyIconChoice" value="${escapeHtml(option)}" ${String(icon) === option ? 'checked' : ''} />
                  <span>${escapeHtml(option)}</span>
                </label>
              `).join('')}
            </div>
          </div>
          <label><span>颜色</span><input type="color" name="color" value="${escapeHtml(currency.color || defaults.color)}" /></label>
          <label><span>比例</span><input type="number" min="1" name="exchangeWeight" value="${escapeHtml(currency.exchangeWeight || defaults.exchangeWeight)}" /></label>
          <label><span>状态</span>${renderSelect('status', currency.status || 'ACTIVE', [['ACTIVE', '启用'], ['INACTIVE', '停用']])}</label>
          <label><span>排序</span><input type="number" min="0" name="sortNo" value="${escapeHtml(currency.sortNo ?? defaults.sortNo)}" /></label>
          <div class="currency-modal-preview wide">
            <span class="currency-mini large" style="--point-color:${escapeHtml(currency.color || defaults.color)}">${escapeHtml(icon)}</span>
            <strong>1:${escapeHtml(currency.exchangeWeight || defaults.exchangeWeight)}</strong>
          </div>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-currency-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderPointAdjustModal(state) {
  if (!state.pointAdjustModalOpen) return ''
  return `
    <div class="modal-backdrop">
      <section class="modal narrow">
        <div class="modal-head">
          <div><h2>手动加减分</h2><p>所有调整都会进入积分流水。</p></div>
          <button class="icon-btn" type="button" data-action="close-adjust-modal">×</button>
        </div>
        <form class="modal-form" data-form="point-adjust">
          <label><span>积分类型</span>${renderSelect('pointType', 'STAR', [['STAR', '星星'], ['FLOWER', '红花'], ['CROWN', '皇冠']])}</label>
          <label><span>方向</span>${renderSelect('direction', 'PLUS', [['PLUS', '加分'], ['MINUS', '扣分']])}</label>
          <label><span>分值</span><input type="number" min="1" max="999" name="amount" value="3" /></label>
          <label class="wide"><span>原因</span><input name="reason" placeholder="例如：主动帮助整理书桌" /></label>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-adjust-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
      </section>
    </div>
  `
}

function renderBalanceModal(state) {
  if (!state.balanceModalOpen) return ''
  const pointType = state.selectedBalancePointType || 'STAR'
  const balances = new Map((state.balances || []).map((item) => [item.pointType, item]))
  const balance = balances.get(pointType) || { pointType, balance: 0, earnedTotal: 0, spentTotal: 0 }
  const balanceAmount = Math.max(0, Number(balance.balance || 0))
  const currencies = getPointCurrencies(state)
  const currentCurrency = currencyMeta(pointType, currencies)
  const rule = normalizeExchangeRule(state.pointExchangeRule)
  const targets = pointType === 'STAR' ? ['FLOWER', 'CROWN'] : pointType === 'FLOWER' ? ['CROWN'] : []
  return `
    <div class="modal-backdrop">
      <section class="modal balance-modal">
        <div class="modal-head">
          <div><h2><span class="inline-point-icon" style="--point-color:${escapeHtml(currentCurrency.color)}">${escapeHtml(currentCurrency.icon)}</span></h2></div>
          <div class="modal-head-actions">
            <button class="btn primary" type="button" data-action="go-reward-store">兑换奖励</button>
            <button class="icon-btn" type="button" data-action="close-balance-modal">×</button>
          </div>
        </div>
        <div class="balance-modal-body">
          <div class="balance-showcase">
            <div class="balance-showcase-head">
              <strong>${balanceAmount}</strong>
            </div>
            <div class="balance-showcase-icon" style="--point-color:${escapeHtml(currentCurrency.color)}">${escapeHtml(currentCurrency.icon)}</div>
          </div>
          <form class="point-exchange-form" data-form="point-exchange">
            <div class="balance-exchange-targets">
              ${targets.map((toPointType) => {
                const preview = calculateExchangePreview(pointType, toPointType, balanceAmount, rule)
                const exchangeAmount = exchangeSourceAmount(pointType, toPointType, preview.toAmount, rule)
                const canExchange = preview.toAmount > 0 && exchangeAmount > 0
                const targetCurrency = currencyMeta(toPointType, currencies)
                return `
                  <article class="exchange-target-card" style="--point-color:${escapeHtml(targetCurrency.color)}">
                    <div class="exchange-target-head">
                      <span class="exchange-target-icon">${escapeHtml(targetCurrency.icon)}</span>
                      <strong>${preview.toAmount}</strong>
                    </div>
                    <button class="btn primary" type="button" data-action="quick-point-exchange" data-to-point-type="${toPointType}" data-from-amount="${exchangeAmount}" ${canExchange ? '' : 'disabled'}>兑换</button>
                  </article>
                `
              }).join('') || '<div class="empty compact-empty">当前积分暂无更高币值可兑换</div>'}
            </div>
            <div class="modal-actions hidden">
              <button class="btn" type="button" data-action="close-balance-modal">取消</button>
              <button class="btn primary" type="submit">兑换</button>
            </div>
            ${state.exchangeSuccess ? `
              <div class="exchange-success" style="--point-color:${escapeHtml(currencyColor(state.exchangeSuccess.toPointType, currencies))}">
                <span>${escapeHtml(currencyIcon(state.exchangeSuccess.toPointType, currencies))}</span>
                <strong>兑换成功</strong>
                <b>+${state.exchangeSuccess.toAmount}</b>
              </div>
            ` : ''}
          </form>
        </div>
      </section>
    </div>
  `
}

function renderConfirmModal(state) {
  const dialog = state.confirmDialog
  if (!dialog) return ''
  return `
    <div class="modal-backdrop">
      <section class="modal confirm-modal">
        <div class="modal-head">
          <div><h2>${escapeHtml(dialog.title || '请确认')}</h2><p>${escapeHtml(dialog.message || '')}</p></div>
          <button class="icon-btn" type="button" data-action="close-confirm-modal">×</button>
        </div>
        <div class="modal-actions confirm-actions">
          <button class="btn" type="button" data-action="close-confirm-modal">取消</button>
          <button class="btn ${dialog.variant === 'danger' ? 'danger' : 'primary'}" type="button" data-action="confirm-modal-submit">${escapeHtml(dialog.confirmText || '确认')}</button>
        </div>
      </section>
    </div>
  `
}

function calendarEventsForDate(state, dateKey) {
  return (state.calendarDisplayEvents || state.calendarEvents || []).filter((event) => event.date === dateKey)
}

function getCalendarEvent(state, eventId, dateKey) {
  return calendarEventsForDate(state, dateKey).find((event) => String(event.uid) === String(eventId))
}

function calendarEventStatusText(event) {
  if (event.kind === 'points') {
    return event.changeAmount >= 0 ? '积分入账' : '积分扣减'
  }
  if (event.kind === 'rewards') {
    return event.status === 'APPROVED'
      ? fulfillmentStatusName(event.fulfillmentStatus)
      : event.status === 'REQUESTED' ? '待确认' : statusName(event.status)
  }
  if (event.status === 'APPROVED') {
    return `已完成 +${event.scoreAwarded || event.basePoints || 0} ${pointName(event.pointType)}`
  }
  if (event.status === 'SUBMITTED') return '待审核'
  if (event.status === 'REJECTED') return '未通过'
  return '待打卡'
}

function filterCalendarEventsByKind(state, events) {
  const kind = state.calendarEventKind || 'tasks'
  return events.filter((event) => event.kind === kind)
}

function formatDateValue(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return String(value).slice(0, 10)
  }
  return formatDate(date)
}

function formatCalendarDateLabel(dateKey) {
  if (!dateKey) return '任务日志'
  const date = new Date(`${dateKey}T00:00:00`)
  if (Number.isNaN(date.getTime())) return dateKey
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
  return `${dateKey} ${week}`
}

function renderSelect(name, value, options, scope = '') {
  return `
    <select name="${name}" data-select="${name}" ${scope ? `data-select-scope="${scope}"` : ''}>
      ${options.map(([optionValue, label]) => `<option value="${escapeHtml(optionValue)}" ${String(optionValue) === String(value) ? 'selected' : ''}>${escapeHtml(label)}</option>`).join('')}
    </select>
  `
}

function normalizeExchangeRule(rule = {}) {
  return {
    childId: rule.childId,
    starWeight: Math.max(1, Number(rule.starWeight || 1)),
    flowerWeight: Math.max(1, Number(rule.flowerWeight || 10)),
    crownWeight: Math.max(1, Number(rule.crownWeight || 100)),
  }
}

function calculateExchangePreview(fromPointType, toPointType, fromAmount, rule = {}) {
  const normalizedRule = normalizeExchangeRule(rule)
  const weights = {
    STAR: normalizedRule.starWeight,
    FLOWER: normalizedRule.flowerWeight,
    CROWN: normalizedRule.crownWeight,
  }
  const fromWeight = weights[fromPointType] || weights.STAR
  const toWeight = weights[toPointType] || weights.STAR
  return {
    toAmount: Math.floor((Number(fromAmount || 0) * fromWeight) / toWeight),
  }
}

function buildRewardPaymentOptions(reward, balances = [], rule = {}) {
  if (!reward) return []
  const weights = normalizeRewardWeights(rule)
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
      return {
        pointType,
        payAmount,
        changeAmount: pointType === requiredPointType ? 0 : Math.ceil(changeValue / requiredWeight),
        enough: (balanceMap.get(pointType) || 0) >= payAmount,
      }
    })
}

function normalizeRewardWeights(rule = {}) {
  return {
    STAR: Math.max(1, Number(rule.starWeight || 1)),
    FLOWER: Math.max(1, Number(rule.flowerWeight || 10)),
    CROWN: Math.max(1, Number(rule.crownWeight || 100)),
  }
}

function exchangeSourceAmount(fromPointType, toPointType, toAmount, rule = {}) {
  const normalizedRule = normalizeExchangeRule(rule)
  const weights = {
    STAR: normalizedRule.starWeight,
    FLOWER: normalizedRule.flowerWeight,
    CROWN: normalizedRule.crownWeight,
  }
  const fromWeight = weights[fromPointType] || weights.STAR
  const toWeight = weights[toPointType] || weights.STAR
  return Math.max(0, Math.ceil((Number(toAmount || 0) * toWeight) / fromWeight))
}

function pointTypeColor(pointType) {
  const colors = {
    STAR: '#f59e0b',
    FLOWER: '#ec4899',
    CROWN: '#7c3aed',
  }
  return colors[pointType] || colors.STAR
}

function getPointCurrencies(state) {
  const byType = new Map((state.pointCurrencies || [])
    .filter((currency) => currency && Number(currency.deleted || 0) !== 1)
    .map((currency) => [currency.pointType, currency]))
  return pointTypeOptions.map(([pointType]) => ({
    childId: state.selectedChildId,
    pointType,
    ...defaultCurrencyMeta[pointType],
    ...(byType.get(pointType) || {}),
  }))
}

function currencyMeta(pointType, currencies = []) {
  const defaults = defaultCurrencyMeta[pointType] || defaultCurrencyMeta.STAR
  const currency = currencies.find((item) => item.pointType === pointType) || {}
  return {
    ...defaults,
    ...currency,
    name: currency.name || defaults.name,
    icon: currency.icon || defaults.icon,
    color: currency.color || defaults.color,
    exchangeWeight: Number(currency.exchangeWeight || defaults.exchangeWeight),
  }
}

function currencyName(pointType, currencies = []) {
  return currencyMeta(pointType, currencies).name
}

function currencyIcon(pointType, currencies = []) {
  return currencyMeta(pointType, currencies).icon || pointIcon(pointType)
}

function currencyColor(pointType, currencies = []) {
  return currencyMeta(pointType, currencies).color || pointTypeColor(pointType)
}

function filterTasks(tasks, filters) {
  const keyword = (filters.keyword || '').trim().toLowerCase()
  return tasks.filter((task) => {
    if (keyword && !fuzzyIncludes([task.name, task.description, categoryLabel(task), scheduleLabel(task), pointName(task.pointType)].join(' '), keyword)) return false
    if (filters.category && parseSchedule(task.scheduleJson).category !== filters.category) return false
    if (filters.periodType && task.periodType !== filters.periodType) return false
    if (filters.pointType && task.pointType !== filters.pointType) return false
    if (filters.status && task.status !== filters.status) return false
    return true
  })
}

function filterChildren(children, filters) {
  const keyword = (filters.keyword || '').trim().toLowerCase()
  return children.filter((child) => {
    if (keyword && !fuzzyIncludes([child.nickname, child.remark, genderLabel(child.gender), child.birthday].join(' '), keyword)) return false
    if (filters.gender && child.gender !== filters.gender) return false
    if (filters.status && child.status !== filters.status) return false
    return true
  })
}

function filterGoals(goals, filters) {
  const keyword = (filters.keyword || '').trim().toLowerCase()
  return goals.filter((goal) => {
    if (keyword && !fuzzyIncludes([goal.name, goal.description, goal.icon].join(' '), keyword)) return false
    if (filters.status && goal.status !== filters.status) return false
    return true
  })
}

function filterRewards(rewards, filters) {
  const keyword = (filters.keyword || '').trim().toLowerCase()
  return rewards.filter((reward) => {
    if (keyword && !fuzzyIncludes([reward.name, reward.description, reward.rewardIcon, pointName(reward.requiredPointType)].join(' '), keyword)) return false
    if (filters.pointType && reward.requiredPointType !== filters.pointType) return false
    if (filters.status && reward.status !== filters.status) return false
    return true
  })
}

function filterCurrencies(currencies, filters) {
  const keyword = (filters.keyword || '').trim().toLowerCase()
  return currencies.filter((currency) => {
    if (keyword && !fuzzyIncludes([currency.name, currency.icon, pointName(currency.pointType), currency.exchangeWeight].join(' '), keyword)) return false
    if (filters.pointType && currency.pointType !== filters.pointType) return false
    if (filters.status && (currency.status || 'ACTIVE') !== filters.status) return false
    return true
  })
}

function fuzzyIncludes(haystack, keyword) {
  const normalizedKeyword = normalizeSearchText(keyword)
  if (!normalizedKeyword) return true
  return normalizeSearchText(haystack).includes(normalizedKeyword)
}

function normalizeSearchText(value) {
  return String(value ?? '')
    .toLowerCase()
    .replace(/[\s\u3000]+/g, '')
}

function scheduleLabel(task) {
  const schedule = parseSchedule(task.scheduleJson)
  const periodType = task.periodType || schedule.type || 'DAILY'
  const requiredCount = Number(schedule.requiredCount || schedule.timesPerWeek || 1)
  if (periodType === 'WEEKLY') {
    const days = schedule.days?.map((day) => weekDays.find(([value]) => value === day)?.[1]).filter(Boolean)
    return `每周 ${days?.join('、') || '周一'} · 完成 ${requiredCount} 次`
  }
  if (periodType === 'MONTHLY') {
    const days = schedule.days?.map((day) => `${day}日`).filter(Boolean)
    return `每月 ${days?.join('、') || '1日'} · 完成 ${requiredCount} 次`
  }
  const startHour = schedule.timeRange?.startHour ?? schedule.startHour ?? 6
  const endHour = schedule.timeRange?.endHour ?? schedule.endHour ?? 22
  return `每日 ${formatDailyHours(getDailyHours(schedule, startHour, endHour))}`
}

function formatDailyHours(hours) {
  const safeHours = [...new Set(hours.map(Number).filter((hour) => hour >= 6 && hour <= 22))].sort((left, right) => left - right)
  if (!safeHours.length) return '06:00'
  if (safeHours.length <= 4) {
    return safeHours.map(formatHour).join('、')
  }
  return `${safeHours.slice(0, 3).map(formatHour).join('、')} 等 ${safeHours.length} 个时间`
}

function periodLabel(periodType) {
  const labels = {
    DAILY: '每日',
    WEEKLY: '每周',
    MONTHLY: '每月',
  }
  return labels[periodType] || '每日'
}

function formatHour(hour) {
  const value = Number(hour)
  if (!Number.isFinite(value)) return '06:00'
  return `${String(value).padStart(2, '0')}:00`
}

function categoryLabel(task) {
  const category = parseSchedule(task.scheduleJson).category || 'HABIT'
  const labels = {
    STUDY: '学习',
    LIFE: '生活',
    SPORT: '运动',
    HABIT: '习惯',
  }
  return labels[category] || '习惯'
}

function genderLabel(gender) {
  const labels = {
    UNKNOWN: '未设置',
    MALE: '男孩',
    FEMALE: '女孩',
  }
  return labels[gender] || gender || '未设置'
}

function dateRange(startDate, endDate) {
  if (startDate && endDate) return `${startDate} 至 ${endDate}`
  if (startDate) return `${startDate} 起`
  if (endDate) return `至 ${endDate}`
  return '未设置'
}

function parseSchedule(scheduleJson) {
  try {
    return JSON.parse(scheduleJson || '{}')
  } catch {
    return {}
  }
}

function renderToast(toast) {
  if (!toast) return ''
  return `<div class="toast">${escapeHtml(toast)}</div>`
}
