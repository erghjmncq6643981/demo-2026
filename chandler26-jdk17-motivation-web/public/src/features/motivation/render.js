import { renderCalendar } from '/src/features/motivation/calendar.js'
import { clamp, escapeHtml, formatDate, pointIcon, pointName, statusName } from '/src/shared/text.js'

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
const rewardIconOptions = ['🎁', '🍦', '🧸', '📚', '🎨', '🚲', '🎮', '🎬', '🎟️', '🍰', '🍕', '🏆', '👑', '⭐', '🌈', '🎵', '⚽', '🛝', '🍭', '🪁', '🧩', '🚀', '💎', '🎯', '🪄', '📷', '🌟', '🐣', '🦄', '🍓', '🍉', '🍎', '🍪', '🛹', '🎈']

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
      ${renderToast(state.toast)}
    `
  }
  return `
    <main class="app-shell">
      ${renderSidebar(state)}
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
    ${renderRewardModal(state)}
    ${renderPointAdjustModal(state)}
    ${renderConfirmModal(state)}
    ${renderToast(state.toast)}
  `
}

function renderLoginPage(state) {
  return `
    <main class="login-page">
      <section class="login-showcase" aria-label="系统能力概览">
        <div class="login-hero-copy">
          <p class="login-kicker">FAMILY MOTIVATION</p>
          <h1>儿童激励记录系统</h1>
          <p>把每日任务、成长目标、积分入账和奖励兑换，收进一个清爽又适合孩子操作的家庭激励工作台。</p>
        </div>
        <div class="login-feature-grid">
          <article>
            <strong>任务日历</strong>
            <span>每日、每周、每月计划一眼看清</span>
          </article>
          <article>
            <strong>彩色积分</strong>
            <span>星星、红花、皇冠都能自定义颜色</span>
          </article>
          <article>
            <strong>奖励商店</strong>
            <span>孩子申请兑换，父母侧待办审核</span>
          </article>
        </div>
        <div class="login-preview-card">
          <div class="preview-head">
            <div>
              <span>今日成长</span>
              <strong>小星的任务板</strong>
            </div>
            <em>今天</em>
          </div>
          <div class="preview-task-list">
            <div class="preview-task pink">
              <span>★</span>
              <div><strong>晨读 20 分钟</strong><small>07:00 · 打卡生效</small></div>
              <b>+8</b>
            </div>
            <div class="preview-task green">
              <span>✿</span>
              <div><strong>整理书包</strong><small>18:00 · 审批后生效</small></div>
              <b>+5</b>
            </div>
            <div class="preview-task violet">
              <span>♛</span>
              <div><strong>练字作业</strong><small>周一 / 周四 / 周五 · 完成 2 次</small></div>
              <b>+15</b>
            </div>
          </div>
          <div class="preview-reward-strip">
            <div><span>⭐</span><strong>128</strong><small>星星余额</small></div>
            <div><span>🌸</span><strong>42</strong><small>红花余额</small></div>
            <div><span>🎁</span><strong>3</strong><small>待办奖励</small></div>
          </div>
        </div>
      </section>
      <section class="login-panel" aria-label="账号登录">
        <div class="login-brand">
          <div class="brand-mark">★</div>
          <div>
            <p class="login-panel-kicker">ACCOUNT</p>
            <h2>欢迎回来</h2>
            <p>${escapeHtml(state.connectionMessage || '登录后进入家庭激励工作台')}</p>
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
            <button class="btn" type="submit" data-auth-mode="register">注册</button>
          </div>
        </form>
      </section>
    </main>
  `
}

function renderSidebar(state) {
  const items = visibleNavItems(state)
  return `
    <aside class="sidebar">
      <div class="sidebar-brand">
        <div class="brand-mark">★</div>
        <div>
          <strong>成长激励</strong>
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
        <p class="eyebrow">Motivation 1.0</p>
        <h1>${title}</h1>
        <p>${subtitle}</p>
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
      <section class="panel panel-pad wide">
        ${renderLedger(state)}
      </section>
    </div>
  `
}

function renderChildList(state) {
  const filters = state.childFilters || {}
  const children = filterChildren(state.children || [], filters)
  return `
    <div class="section-inline-head">
      <h2>孩子档案</h2>
      <div class="section-actions">
        <span>${children.length} 个</span>
        <button class="btn primary" type="button" data-action="open-child-modal">新增孩子</button>
      </div>
    </div>
    <div class="filter-bar compact">
      <label><span>关键词</span><input name="keyword" data-filter="child-keyword" data-filter-scope="child" value="${escapeHtml(filters.keyword || '')}" placeholder="昵称或备注" /></label>
      <label><span>性别</span>${renderSelect('child-gender', filters.gender || '', [['', '全部'], ['UNKNOWN', '未设置'], ['MALE', '男孩'], ['FEMALE', '女孩']], 'child')}</label>
      <label><span>状态</span>${renderSelect('child-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['INACTIVE', '停用']], 'child')}</label>
      <div class="filter-action"><span>筛选</span><div class="filter-actions"><button class="small-btn primary-lite" type="button" data-action="search-child-filters">搜索</button><button class="small-btn" type="button" data-action="reset-child-filters">重置</button></div></div>
    </div>
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
  return `
    <div class="section-inline-head">
      <h2>成长目标</h2>
      <div class="section-actions">
        <span>${goals.length} 个</span>
        <button class="btn primary" type="button" data-action="open-goal-modal">新增目标</button>
      </div>
    </div>
    <div class="filter-bar compact">
      <label><span>关键词</span><input name="keyword" data-filter="goal-keyword" data-filter-scope="goal" value="${escapeHtml(filters.keyword || '')}" placeholder="目标名称或说明" /></label>
      <label><span>状态</span>${renderSelect('goal-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停'], ['FINISHED', '完成']], 'goal')}</label>
      <div class="filter-action"><span>筛选</span><div class="filter-actions"><button class="small-btn primary-lite" type="button" data-action="search-goal-filters">搜索</button><button class="small-btn" type="button" data-action="reset-goal-filters">重置</button></div></div>
    </div>
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

function renderTaskManageView(state) {
  const filters = state.taskFilters || {}
  const tasks = filterTasks(state.tasks, filters)
  return `
    <section class="panel panel-pad">
      <div class="section-inline-head">
        <h2>任务列表</h2>
        <div class="section-actions">
          <span>${tasks.length} 个</span>
          <button class="btn primary" type="button" data-action="open-task-modal">新增任务</button>
        </div>
      </div>
      <div class="filter-bar">
        <label><span>关键词</span><input name="keyword" data-filter="task-keyword" data-filter-scope="task" value="${escapeHtml(filters.keyword || '')}" placeholder="任务名称" /></label>
        <label><span>任务分类</span>${renderSelect('task-category', filters.category || '', [['', '全部'], ['STUDY', '学习'], ['LIFE', '生活'], ['SPORT', '运动'], ['HABIT', '习惯']], 'task')}</label>
        <label><span>任务类型</span>${renderSelect('task-period', filters.periodType || '', [['', '全部'], ['DAILY', '每日'], ['WEEKLY', '每周'], ['MONTHLY', '每月']], 'task')}</label>
        <label><span>积分类型</span>${renderSelect('task-point', filters.pointType || '', [['', '全部'], ['STAR', '星星'], ['FLOWER', '红花'], ['CROWN', '皇冠']], 'task')}</label>
        <label><span>状态</span>${renderSelect('task-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停']], 'task')}</label>
        <div class="filter-action"><span>筛选</span><div class="filter-actions"><button class="small-btn primary-lite" type="button" data-action="search-task-filters">搜索</button><button class="small-btn" type="button" data-action="reset-task-filters">重置</button></div></div>
      </div>
      <div class="table-card">
        <div class="table-head task-table">
          <span>任务</span><span>任务类型和时间段</span><span>积分</span><span>操作</span>
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
    ...normalizeTaskCalendarEvents(state.calendarEvents || []),
    ...normalizePointCalendarEvents(state.ledger || []),
    ...normalizeRewardCalendarEvents(state.exchanges || []),
  ].filter((event) => event.date)
}

function normalizeTaskCalendarEvents(events) {
  return events.map((event) => {
    const date = formatDateValue(event.taskDate)
    const statusText = calendarEventStatusText({ ...event, kind: 'tasks' })
    return {
      ...event,
      kind: 'tasks',
      kindLabel: '任务',
      uid: `task-${event.taskId}-${date}`,
      date,
      title: event.taskName || '任务',
      color: event.taskColor || '#6c63ff',
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

function normalizePointCalendarEvents(ledger) {
  return ledger.map((item) => {
    const amount = Number(item.changeAmount || 0)
    const date = formatDateValue(item.eventTime || item.createTime)
    return {
      kind: 'points',
      kindLabel: '积分',
      uid: `point-${item.id || `${date}-${item.sourceName || 'ledger'}`}`,
      date,
      title: item.sourceName || '积分变动',
      subtitle: `${amount >= 0 ? '+' : ''}${amount} ${pointName(item.pointType)}`,
      statusLabel: amount >= 0 ? '积分增加' : '积分扣减',
      color: amount >= 0 ? '#22c55e' : '#ef4444',
      pointType: item.pointType,
      changeAmount: amount,
      note: item.reason || '积分流水记录。',
    }
  })
}

function normalizeRewardCalendarEvents(exchanges) {
  return exchanges.map((exchange) => {
    const date = formatDateValue(exchange.completedAt || exchange.reviewedAt || exchange.requestedAt || exchange.createTime)
    const status = exchange.status || 'REQUESTED'
    const colorMap = {
      REQUESTED: exchange.rewardColorSnapshot || '#ff9f43',
      COMPLETED: '#22c55e',
      REJECTED: '#ef4444',
    }
    return {
      kind: 'rewards',
      kindLabel: '奖励',
      uid: `reward-${exchange.id || `${date}-${exchange.rewardNameSnapshot || 'exchange'}`}`,
      date,
      title: `${exchange.rewardIconSnapshot || '🎁'} ${exchange.rewardNameSnapshot || '奖励兑换'}`,
      rewardName: exchange.rewardNameSnapshot || '奖励兑换',
      rewardDetail: exchange.remark || `${exchange.requiredPointsSnapshot || 0} ${pointName(exchange.requiredPointType)}兑换`,
      rewardStateLabel: statusName(status),
      subtitle: `${exchange.requiredPointsSnapshot || 0} ${pointName(exchange.requiredPointType)} · ${statusName(status)}`,
      status,
      statusLabel: statusName(status),
      color: colorMap[status] || exchange.rewardColorSnapshot || '#ff9f43',
      note: status === 'REQUESTED'
        ? '孩子已提交兑换申请，等待父母确认。'
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
  return `
    <div class="modal-backdrop calendar-layer calendar-day-layer">
      <section class="modal calendar-modal calendar-day-modal">
        <div class="modal-head">
          <div>
            <h2>${escapeHtml(formatCalendarDateLabel(state.selectedCalendarDateKey))}</h2>
            <p>任务日志里每一条都能点开查看并打卡。</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-calendar-day-modal">×</button>
        </div>
        <div class="calendar-summary">
          ${summary}
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
  const approved = event.status === 'APPROVED'
  const canCheckIn = isTask && !approved
  return `
    <div class="modal-backdrop calendar-layer calendar-event-layer">
      <section class="modal calendar-modal narrow calendar-event-modal">
        <div class="modal-head">
          <div>
            <h2>${escapeHtml(event.title)}</h2>
            <p>${escapeHtml(formatCalendarDateLabel(event.date))} · ${escapeHtml(calendarEventStatusText(event))}</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-calendar-event-modal">×</button>
        </div>
        <div class="calendar-detail">
          <div class="calendar-detail-head">
            <span class="task-dot" style="--task-color:${event.color || '#6c63ff'}"></span>
            <div>
              <strong>${escapeHtml(event.kindLabel)}</strong>
              <p>${escapeHtml(event.subtitle)}</p>
            </div>
          </div>
          <div class="calendar-detail-grid">
            <div class="calendar-detail-card">
              <span>日期</span>
              <strong>${escapeHtml(event.date)}</strong>
            </div>
            <div class="calendar-detail-card">
              <span>状态</span>
              <strong>${escapeHtml(event.statusLabel)}</strong>
            </div>
            <div class="calendar-detail-card wide">
              <span>${event.kind === 'points' ? '积分变化' : event.kind === 'rewards' ? '奖励信息' : '积分预览'}</span>
              ${event.kind === 'points'
                ? `<div class="calendar-point-change ${event.changeAmount >= 0 ? 'positive' : 'negative'}">${event.changeAmount >= 0 ? '+' : ''}${event.changeAmount} ${pointName(event.pointType)}</div>`
                : event.kind === 'rewards'
                  ? `<div class="calendar-reward-detail"><strong>${escapeHtml(event.rewardName || event.title)}</strong><p>${escapeHtml(event.rewardDetail || '')}</p></div>`
                  : renderScoreIcons(event.pointType, scoreCount, event.pointColor, 'calendar')}
            </div>
          </div>
          <div class="calendar-detail-note">
            ${escapeHtml(event.note)}
          </div>
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
  return `
    <button class="calendar-log-item" type="button" data-action="open-calendar-event" data-calendar-event-id="${escapeHtml(event.uid)}" data-task-id="${escapeHtml(event.taskId || '')}" data-task-date="${escapeHtml(event.date)}">
      <span class="task-dot" style="--task-color:${event.color || '#6c63ff'}"></span>
      <div class="calendar-log-main">
        <strong>${escapeHtml(event.title)}</strong>
        <small>${escapeHtml(event.subtitle)}</small>
      </div>
      <div class="calendar-log-score">
        ${event.kind === 'points'
          ? `<span class="calendar-point-change ${event.changeAmount >= 0 ? 'positive' : 'negative'}">${event.changeAmount >= 0 ? '+' : ''}${event.changeAmount}</span>`
          : event.kind === 'rewards'
            ? `<span class="calendar-log-status">${escapeHtml(event.rewardStateLabel)}</span>`
            : renderScoreIcons(event.pointType, Number(event.scoreAwarded || event.basePoints || 1), event.pointColor, 'log')}
        <span class="calendar-log-status">${escapeHtml(event.statusLabel)}</span>
      </div>
      ${event.kind === 'tasks'
        ? `<span class="calendar-log-action ${event.status === 'APPROVED' ? 'done' : ''}">${event.status === 'APPROVED' ? '完成' : '打卡'}</span>`
        : `<span class="calendar-log-action done">${event.kindLabel}</span>`}
    </button>
  `
}

function renderCalendarDaySummary(kind, events) {
  if (kind === 'points') {
    const positiveCount = events.filter((event) => event.changeAmount >= 0).length
    const negativeCount = events.filter((event) => event.changeAmount < 0).length
    return `
      <span>${events.length} 条积分</span>
      <span>${positiveCount} 增加</span>
      <span>${negativeCount} 扣减</span>
    `
  }
  if (kind === 'rewards') {
    const requested = events.filter((event) => event.status === 'REQUESTED').length
    const completed = events.filter((event) => event.status === 'COMPLETED').length
    const rejected = events.filter((event) => event.status === 'REJECTED').length
    return `
      <span>${events.length} 条奖励</span>
      <span>${requested} 待确认</span>
      <span>${completed} 已完成</span>
      <span>${rejected} 已拒绝</span>
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

function renderStoreView(state) {
  return `
    <section class="panel panel-pad">
      <div class="reward-grid">
        ${state.rewards.map((reward) => `
          <article class="reward-card">
            <div class="reward-icon" style="--reward-color:${reward.rewardColor || '#6c63ff'}">${escapeHtml(reward.rewardIcon || '🎁')}</div>
            <div>
              <h3>${escapeHtml(reward.name)}</h3>
              <p>${escapeHtml(reward.description || '家庭奖励')}</p>
            </div>
            <button class="btn primary" type="button" data-action="exchange-reward" data-reward-id="${reward.id}">
              ${reward.requiredPoints} ${pointIcon(reward.requiredPointType)}
            </button>
          </article>
        `).join('') || '<div class="empty">暂无奖励</div>'}
      </div>
    </section>
  `
}

function renderRewardManageView(state) {
  const filters = state.rewardFilters || {}
  const rewards = filterRewards(state.rewards, filters)
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
      <div class="filter-bar">
        <label><span>关键词</span><input name="keyword" data-filter="reward-keyword" data-filter-scope="reward" value="${escapeHtml(filters.keyword || '')}" placeholder="奖励名称" /></label>
        <label><span>积分类型</span>${renderSelect('reward-point', filters.pointType || '', [['', '全部'], ['STAR', '星星'], ['FLOWER', '红花'], ['CROWN', '皇冠']], 'reward')}</label>
        <label><span>状态</span>${renderSelect('reward-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停']], 'reward')}</label>
        <div class="filter-action"><span>筛选</span><div class="filter-actions"><button class="small-btn primary-lite" type="button" data-action="search-reward-filters">搜索</button><button class="small-btn" type="button" data-action="reset-reward-filters">重置</button></div></div>
      </div>
      <div class="table-card">
        <div class="table-head reward-table">
          <span>奖励</span><span>需要积分</span><span>库存</span><span>审核</span><span>操作</span>
        </div>
        ${rewards.map(renderRewardRow).join('') || '<div class="empty">暂无奖励</div>'}
      </div>
    </section>
  `
}

function renderExchangeTodo(state) {
  const todos = (state.exchanges || []).filter((exchange) => exchange.status === 'REQUESTED')
  return `
    <section class="todo-panel">
      <div class="section-inline-head">
        <h2>兑换待办</h2>
        <span>${todos.length} 条待确认</span>
      </div>
      <div class="todo-list">
        ${todos.map((exchange) => `
          <div class="todo-item">
            <span class="reward-mini" style="--reward-color:${exchange.rewardColorSnapshot || '#6c63ff'}">${escapeHtml(exchange.rewardIconSnapshot || '🎁')}</span>
            <div>
              <strong>${escapeHtml(exchange.rewardNameSnapshot || '奖励兑换')}</strong>
              <small>${exchange.requiredPointsSnapshot || 0} ${pointName(exchange.requiredPointType)} · ${escapeHtml(exchange.requestedAt || '')}</small>
            </div>
            <div class="row-actions">
              <button class="small-btn primary-lite" type="button" data-action="approve-exchange" data-exchange-id="${exchange.id}">通过</button>
              <button class="small-btn danger" type="button" data-action="reject-exchange" data-exchange-id="${exchange.id}">拒绝</button>
            </div>
          </div>
        `).join('') || '<div class="empty compact-empty">暂无待确认兑换</div>'}
      </div>
    </section>
  `
}

function renderRewardRow(reward) {
  return `
    <div class="table-row reward-table">
      <div class="task-name-cell">
        <span class="reward-mini" style="--reward-color:${reward.rewardColor || '#6c63ff'}">${escapeHtml(reward.rewardIcon || '🎁')}</span>
        <div><strong>${escapeHtml(reward.name)}</strong><small>${escapeHtml(reward.description || '')}</small></div>
      </div>
      <span class="score-pill">${reward.requiredPoints} ${pointIcon(reward.requiredPointType)}</span>
      <span>${reward.stockTotal > 0 ? `${reward.stockRemaining}/${reward.stockTotal}` : '不限'}</span>
      <span>${Number(reward.requireApproval) === 1 ? '需要确认' : '自动兑换'}</span>
      <div class="row-actions task-row-actions">
        <button class="row-icon-btn" type="button" data-action="edit-reward" data-reward-id="${reward.id}" aria-label="修改奖励" title="修改奖励">${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-reward" data-reward-id="${reward.id}" aria-label="删除奖励" title="删除奖励">${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderStats(state) {
  const balances = new Map((state.balances || []).map((item) => [item.pointType, item]))
  const todayEvents = state.calendarEvents.filter((event) => event.taskDate === state.todayKey)
  const completedToday = todayEvents.filter((event) => event.status === 'APPROVED').length
  const todayTasks = todayEvents.length || state.tasks.length
  const completionRate = todayTasks ? Math.round((completedToday / Math.max(todayTasks, 1)) * 100) : 0
  return `
    <div class="stats">
      ${['STAR', 'FLOWER', 'CROWN'].map((type) => {
        const balance = balances.get(type) || { balance: 0, earnedTotal: 0, spentTotal: 0 }
        return `
          <div class="stat">
            <div class="label">${pointName(type)}余额</div>
            <div class="value">${pointIcon(type)} ${balance.balance}</div>
            <div class="foot">累计获得 ${balance.earnedTotal}，已使用 ${balance.spentTotal}</div>
          </div>
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
  const task = state.editingTask || {}
  const schedule = parseSchedule(task.scheduleJson)
  const periodType = task.periodType || schedule.type || 'DAILY'
  const startHour = Number(schedule.timeRange?.startHour ?? schedule.startHour ?? 6)
  const endHour = Number(schedule.timeRange?.endHour ?? schedule.endHour ?? 22)
  const dailyHours = getDailyHours(schedule, startHour, endHour)
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
          <div><h2>${task.id ? '修改任务' : '新增任务'}</h2><p>设置周期、可完成时间、完成次数和积分。</p></div>
          <button class="icon-btn" type="button" data-action="close-task-modal">×</button>
        </div>
        <form class="modal-form" data-form="task">
          <input type="hidden" name="taskId" value="${escapeHtml(task.id || '')}" />
          <label><span>任务名称</span><input name="name" value="${escapeHtml(task.name || '')}" placeholder="例如：晨读 20 分钟" /></label>
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
            <div class="field-label">积分图标</div>
            ${renderPointTypePicker(pointType)}
          </div>
          <label><span>分值</span><input type="number" min="1" max="99" name="basePoints" value="${escapeHtml(basePoints)}" /></label>
          <div class="point-preview-field">
            <span class="field-label">积分预览</span>
            <label class="point-preview" title="点击图标选择积分颜色">
              <input type="color" name="pointColor" value="${escapeHtml(pointColor)}" />
              <span class="score-icons preview" data-point-preview-icons style="--point-color:${escapeHtml(pointColor)}">${renderScoreIconItems(pointType, basePoints)}</span>
            </label>
          </div>
          <div class="approval-mode wide">
            <span class="field-label">积分生效方式</span>
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

function getDailyHours(schedule, startHour, endHour) {
  if (Array.isArray(schedule.hours) && schedule.hours.length) {
    return schedule.hours.map(Number).filter((hour) => hour >= 6 && hour <= 22)
  }
  const safeStart = clamp(Number(startHour), 6, 22)
  const safeEnd = clamp(Number(endHour), 6, 22)
  return dailyHourOptions.filter((hour) => hour >= safeStart && hour <= safeEnd)
}

function renderWeekDayPicker(selectedDays, taskColor = '#30d5ff') {
  return `
    <div class="time-choice-grid week-picker" data-task-color-surface style="--task-color:${escapeHtml(taskColor)}">
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
    <div class="time-choice-grid month-day-picker" data-task-color-surface style="--task-color:${escapeHtml(taskColor)}">
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
            <label><span>图标</span><input name="rewardIcon" value="${escapeHtml(reward.rewardIcon || '🎁')}" /></label>
            <div class="icon-picker">
              ${rewardIconOptions.map((icon) => `
                <label class="icon-choice" title="${escapeHtml(icon)}">
                  <input type="radio" name="rewardIconChoice" value="${escapeHtml(icon)}" ${String(reward.rewardIcon || '🎁') === icon ? 'checked' : ''} />
                  <span>${escapeHtml(icon)}</span>
                </label>
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
          <label class="check-line wide"><input type="checkbox" name="requireApproval" ${Number(reward.requireApproval ?? 1) === 1 ? 'checked' : ''} /> <span>兑换需要家长确认</span></label>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-reward-modal">取消</button>
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
    return event.status === 'REQUESTED' ? '待确认' : statusName(event.status)
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
