import { renderCalendar } from '/src/features/motivation/calendar.js'
import {
  branchStatusName,
  clamp,
  escapeHtml,
  formatDate,
  fulfillmentStatusName,
  pointIcon,
  pointName,
  rewardMainFlowName,
  statusName,
} from '/src/shared/text.js'

const navItems = [
  ['profile', '个人信息'],
  ['growth', '宝贝成长'],
  ['calendar', '任务日历'],
  ['reward-calendar', '奖励日历'],
  ['store', '奖励商店'],
  ['system-config', '系统配置'],
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
  ['STAR', '1级'],
  ['FLOWER', '2级'],
  ['CROWN', '3级'],
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
const goalIconOptions = ['★', '✦', '✿', '📚', '✏️', '🎯', '🏃', '🎵', '🎨', '🧩', '🌱', '🏆', '👑', '🌈', '🕒', '🧠', '💪', '🧹', '📖', '🚀', '⭐', '🎈']

function isChildUser(state) {
  return String(state.user?.userType || '').toUpperCase() === 'CHILD'
}

function isManagementUser(state) {
  const userType = String(state.user?.userType || '').toUpperCase()
  return userType === 'PARENT' || userType === 'GUARDIAN'
}

function visibleNavItems(state) {
  if (!isManagementUser(state)) {
    return navItems.filter(([view]) => ['profile', 'calendar', 'reward-calendar', 'store'].includes(view))
  }
  return navItems
}

function profileSubnavItems(state) {
  if (!isManagementUser(state)) {
    return [['home', '首页']]
  }
  return [
    ['home', '首页'],
    ['account', '账号信息'],
    ['rewards', '奖励管理'],
    ['currencies', '币值管理'],
  ]
}

function profileSubView(state) {
  const items = profileSubnavItems(state).map(([view]) => view)
  return items.includes(state.profileSubView) ? state.profileSubView : items[0]
}

function growthSubView(state) {
  return state.growthSubView === 'tasks' ? 'tasks' : 'goals'
}

function renderAvatarImage(src, fallback, className, previewKey = '') {
  const previewAttr = previewKey ? ` data-avatar-preview="${escapeHtml(previewKey)}"` : ''
  if (src) {
    return `<span class="${className}"${previewAttr}><img src="${escapeHtml(src)}" alt="" /></span>`
  }
  return `<span class="${className}"${previewAttr}>${escapeHtml((fallback || '宝').slice(0, 1))}</span>`
}

function pendingAvatarPreview(state, scope, childId = '') {
  const editor = state.avatarEditor || {}
  if (editor.scope !== scope) return ''
  if (String(editor.childId || '') !== String(childId || '')) return ''
  return editor.processedPreviewUrl || ''
}

function renderAvatarUploadControl({ src, fallback, scope, childId = '', previewKey = '' }) {
  return `
    <label class="avatar-change-button" title="点击更换头像" aria-label="点击更换头像">
      ${renderAvatarImage(src, fallback, 'account-avatar-preview', previewKey)}
      <input class="avatar-file-input" type="file" accept="image/*" data-avatar-file="${escapeHtml(scope)}" data-child-id="${escapeHtml(childId)}" />
      <span class="avatar-change-mask" aria-hidden="true">📷</span>
    </label>
  `
}

function parseDateKey(value) {
  const match = String(value || '').match(/^(\d{4})-(\d{2})-(\d{2})/)
  if (!match) return null
  const date = new Date(Number(match[1]), Number(match[2]) - 1, Number(match[3]))
  return Number.isNaN(date.getTime()) ? null : date
}

function monthKey(date) {
  const value = date instanceof Date ? date : new Date()
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, '0')}`
}

function chineseDateLabel(value, emptyLabel = '请选择日期') {
  const date = parseDateKey(value)
  if (!date) return emptyLabel
  return `${date.getFullYear()}年${date.getMonth() + 1}月${date.getDate()}日`
}

function renderChineseDatePicker(name, value, options = {}) {
  const label = options.label || '日期'
  const placeholder = options.placeholder || `请选择${label}`
  const ariaLabel = options.ariaLabel || `选择${label}`
  const selectedDate = parseDateKey(value)
  const currentMonth = monthKey(selectedDate || new Date())
  return `
    <div class="date-picker-field" data-date-picker data-current-month="${escapeHtml(currentMonth)}" data-selected-date="${escapeHtml(value || '')}" data-date-empty-label="${escapeHtml(placeholder)}">
      <span class="field-label">${escapeHtml(label)}</span>
      <input type="hidden" name="${escapeHtml(name)}" value="${escapeHtml(value || '')}" data-date-picker-input />
      <button class="date-picker-trigger" type="button" data-action="toggle-date-picker" aria-label="${escapeHtml(ariaLabel)}">
        <span data-date-picker-label class="${value ? '' : 'placeholder'}">${escapeHtml(chineseDateLabel(value, placeholder))}</span>
        <span class="date-picker-icon" aria-hidden="true">日</span>
      </button>
      <div class="date-picker-popover">
        <div class="date-picker-head">
          <button class="date-picker-nav" type="button" data-action="date-picker-prev" aria-label="上个月">‹</button>
          <strong data-date-picker-title>${escapeHtml(currentMonth.replace('-', '年'))}月</strong>
          <button class="date-picker-nav" type="button" data-action="date-picker-next" aria-label="下个月">›</button>
        </div>
        <div class="date-picker-weekdays">
          ${['一', '二', '三', '四', '五', '六', '日'].map((day) => `<span>${day}</span>`).join('')}
        </div>
        <div class="date-picker-grid" data-date-picker-grid></div>
        <div class="date-picker-foot">
          <button class="small-btn" type="button" data-action="date-picker-clear">清空</button>
          <button class="small-btn primary-lite" type="button" data-action="date-picker-today">今天</button>
        </div>
      </div>
    </div>
  `
}

function renderAvatarEditor(state) {
  const editor = state.avatarEditor || {}
  if (!editor.open || !editor.sourceObjectUrl) return ''
  const previewSize = 260
  const naturalWidth = Math.max(1, Number(editor.naturalWidth || 1))
  const naturalHeight = Math.max(1, Number(editor.naturalHeight || 1))
  const baseScale = Math.max(previewSize / naturalWidth, previewSize / naturalHeight)
  const imageWidth = Math.round(naturalWidth * baseScale)
  const imageHeight = Math.round(naturalHeight * baseScale)
  const zoom = Math.max(1, Number(editor.zoom || 1))
  const offsetX = Number(editor.offsetX || 0)
  const offsetY = Number(editor.offsetY || 0)
  const transform = `translate(-50%, -50%) translate(${offsetX}px, ${offsetY}px) scale(${zoom})`
  return `
    <div class="modal-backdrop avatar-editor-backdrop">
      <section class="modal narrow avatar-editor-modal" aria-label="调整头像">
        <div class="modal-head">
          <div>
            <h2>调整头像</h2>
            <p>拖动照片，把最想展示的位置放进头像框里。</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-avatar-editor">×</button>
        </div>
        <div class="avatar-editor-body" data-avatar-editor-body>
          <div class="avatar-editor-stage" data-avatar-editor-handle>
            <img
              src="${escapeHtml(editor.sourceObjectUrl)}"
              alt=""
              draggable="false"
              data-avatar-editor-image
              style="width:${imageWidth}px;height:${imageHeight}px;transform:${escapeHtml(transform)};"
            />
            <div class="avatar-editor-ring" aria-hidden="true"></div>
          </div>
          <div class="avatar-editor-tools">
            <button class="small-btn" type="button" data-action="avatar-zoom-out">缩小</button>
            <div class="avatar-zoom-value">${Math.round(zoom * 100)}%</div>
            <button class="small-btn" type="button" data-action="avatar-zoom-in">放大</button>
            <button class="small-btn" type="button" data-action="avatar-reset">重置</button>
          </div>
        </div>
        <div class="modal-actions avatar-editor-actions">
          <button class="btn" type="button" data-action="close-avatar-editor">取消</button>
          <button class="btn primary" type="button" data-action="save-avatar-editor">使用头像</button>
        </div>
      </section>
    </div>
  `
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
    ${renderRewardTicketModal(state)}
    ${renderPointCurrencyModal(state)}
    ${renderPointAdjustModal(state)}
    ${renderBalanceModal(state)}
    ${renderAccountModal(state)}
    ${renderAvatarEditor(state)}
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
        <form class="login-form" data-form="auth" autocomplete="on">
          <label>
            <span>账号</span>
            <input name="username" autocomplete="username" autocapitalize="none" spellcheck="false" placeholder="请输入账号" />
          </label>
          <label>
            <span>密码</span>
            <input name="password" autocomplete="current-password" type="password" placeholder="请输入密码" />
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
          <div><h2>注册账号</h2><p>创建家长账号后开始配置宝贝档案。</p></div>
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
          <span class="sidebar-child-name">${escapeHtml(state.selectedChild?.nickname || '未选择宝贝')}</span>
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
    home: isChildUser(state) ? ['个人信息', '查看今日进度、积分余额和成长记录。'] : ['个人信息', '家庭成员、宝贝档案和积分概览。'],
    account: ['账号信息', '查看账号资料和宝贝档案。'],
    rewards: ['奖励管理', '维护奖励库存、颜色、货币要求和审核规则。'],
    currencies: ['币值管理', '配置星星、红花、皇冠的图标、颜色和比例。'],
  }
  const titles = {
    profile: profileTitles[profileSubView(state)] || profileTitles.home,
    growth: ['宝贝成长', '管理成长目标和每日、每周、每月任务。'],
    calendar: ['任务日历', '按月/周查看任务安排，切换积分查看入账与扣减。'],
    'reward-calendar': ['奖励日历', '查看奖励兑换、购买、运输和日程实现进度。'],
    store: ['奖励商店', '宝贝可以用星星、红花或皇冠兑换奖励。'],
    'system-config': ['日历样式', '调整任务日历日期大小和颜色。'],
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
  if (state.currentView === 'growth') return renderGrowthView(state)
  if (state.currentView === 'calendar') return renderCalendarView(state, actions)
  if (state.currentView === 'reward-calendar') return renderRewardCalendarView(state, actions)
  if (state.currentView === 'store') return renderStoreView(state)
  if (state.currentView === 'system-config') return renderSystemConfigPage(state)
  return renderProfileView(state)
}

function renderProfileView(state) {
  const activeSubView = profileSubView(state)
  const childUser = isChildUser(state)
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
  if (activeSubView === 'account') {
    return `
      <div class="view-grid account-info-grid">
        <section class="panel panel-pad wide">
          ${renderAccountSummary(state)}
        </section>
        ${isManagementUser(state) ? `<section class="panel panel-pad wide">${renderChildList(state)}</section>` : ''}
        <section class="panel panel-pad wide">
          ${renderAccountActivityLogsPanel(state)}
        </section>
      </div>
    `
  }
  if (!state.selectedChild) {
    return `
      <div class="view-grid">
        <section class="panel panel-pad wide">
          <div class="section-inline-head">
            <h2>${childUser ? '我的档案' : '宝贝档案'}</h2>
            ${childUser ? '' : '<button class="btn primary" type="button" data-action="open-child-modal">新增宝贝</button>'}
          </div>
          <div class="empty compact-empty">${childUser ? '当前宝贝账号还没有绑定宝贝档案，请联系家长。' : '还没有宝贝档案，先新增一个宝贝再开始记录吧。'}</div>
        </section>
      </div>
    `
  }
  return `
    <div class="view-grid">
      <section class="panel panel-pad">
        <div class="profile-card">
          ${renderAvatarImage(state.avatarObjectUrls?.children?.[state.selectedChild?.id] || '', state.selectedChild?.nickname || '宝', 'avatar')}
          <div>
            <h2>${escapeHtml(state.selectedChild?.nickname || '未选择宝贝')}</h2>
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

function renderGrowthView(state) {
  if (!isManagementUser(state)) {
    return `
      <section class="panel panel-pad">
        <div class="empty compact-empty">宝贝账号暂无管理权限。</div>
      </section>
    `
  }
  return renderGrowthManageView(state)
}

function renderSystemConfigPage(state) {
  if (!isManagementUser(state)) {
    return `
      <section class="panel panel-pad">
        <div class="empty compact-empty">宝贝账号暂无系统配置权限。</div>
      </section>
    `
  }
  return `
    <div class="view-grid">
      <section class="panel panel-pad wide">
        ${renderSystemConfigView(state)}
      </section>
    </div>
  `
}

function renderAccountSummary(state) {
  const user = state.user || {}
  const avatarSrc = state.avatarObjectUrls?.account || ''
  return `
    <div class="section-inline-head">
      <h2>当前账号</h2>
      <button class="small-btn primary-lite" type="button" data-action="open-account-modal">修改</button>
    </div>
    <div class="account-simple-card">
      ${renderAvatarUploadControl({
        src: avatarSrc,
        fallback: user.nickname || user.username || '账',
        scope: 'account',
        previewKey: 'account-summary',
      })}
      <div class="account-simple-copy">
        <span>当前账号</span>
        <strong>${escapeHtml(user.nickname || user.username || '未命名账号')}</strong>
        <p>${escapeHtml(user.username || '未设置账号')}</p>
      </div>
    </div>
  `
}

function renderAccountActivityLogsPanel(state) {
  return `
    <section class="activity-log-panel">
      <div class="section-inline-head">
        <h2>宝贝成长日志</h2>
        <span>${(state.activityLogs || []).length} 条</span>
      </div>
      ${renderChildActivityLogs(state)}
    </section>
  `
}

function renderChildActivityLogs(state) {
  const logs = state.activityLogs || []
  return `
    <div class="activity-timeline">
      ${logs.map((log) => `
        <article class="activity-item">
          <span class="activity-dot ${escapeHtml(String(log.logType || '').toLowerCase())}"></span>
          <div>
            <strong>${escapeHtml(activityLogTitle(log))}</strong>
            <p>${escapeHtml(activityLogDetail(log))}</p>
            <small>${escapeHtml(formatActivityTime(log.createTime))}</small>
          </div>
        </article>
      `).join('') || '<div class="empty compact-empty">暂无宝贝活动日志</div>'}
    </div>
  `
}

function activityLogTitle(log) {
  return log.childNickname ? `${log.childNickname}宝贝` : '宝贝动态'
}

function activityLogDetail(log) {
  const detail = String(log.detail || log.title || '').trim()
  if (!detail) return '记录了一次成长活动'
  return detail
    .replace(/^用户「([^」]+)」于\s*\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\s*/, '')
    .replace(/^孩子「\d+」的/, '宝贝的')
    .replaceAll('孩子', '宝贝')
}

function formatActivityTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hour}:${minute}`
}

function renderSystemConfigView(state) {
  const config = state.systemConfig || {}
  const dateSize = Number(config.calendarDateSize || 20)
  const dateColor = config.calendarDateColor || '#1f2937'
  return `
    <div class="section-inline-head">
      <h2>日历样式</h2>
      <div class="section-actions">
        <button class="btn primary" type="button" data-action="save-system-config">保存配置</button>
      </div>
    </div>
    <form class="system-config-form" data-form="system-config">
      <label>
        <span>日期大小</span>
        <input type="range" name="calendarDateSize" min="14" max="28" value="${escapeHtml(dateSize)}" />
        <strong data-system-config-size>${escapeHtml(dateSize)}px</strong>
      </label>
      <label>
        <span>日期颜色</span>
        <input type="color" name="calendarDateColor" value="${escapeHtml(dateColor)}" />
      </label>
    </form>
    <div class="system-config-preview" data-system-config-preview style="--calendar-date-size:${dateSize}px; --calendar-date-color:${dateColor};">
      <div class="day">
        <div class="date">18<span>今天</span></div>
        <div class="events">
          <div class="event"><i></i><span>晨读</span></div>
        </div>
      </div>
    </div>
  `
}

function renderAccountModal(state) {
  if (!state.accountModalOpen) return ''
  const user = state.user || {}
  const draft = state.accountDraft || {}
  const username = user.username || ''
  const nickname = draft.nickname ?? user.nickname ?? username
  const avatarSrc = pendingAvatarPreview(state, 'account') || state.avatarObjectUrls?.account || ''
  return `
    <div class="modal-backdrop" data-action="close-account-modal">
      <section class="modal narrow account-modal" data-account-modal>
        <div class="modal-head">
          <div>
            <h2>账号信息</h2>
            <p>修改当前登录账号的基础资料。</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-account-modal">×</button>
        </div>
        <form class="modal-form account-modal-form" data-form="account">
          <div class="account-summary-card wide">
            ${renderAvatarUploadControl({
              src: avatarSrc,
              fallback: nickname || username || '账',
              scope: 'account',
              previewKey: 'account',
            })}
            <div class="account-summary-copy">
              <strong>${escapeHtml(nickname || '未命名账号')}</strong>
              <span>${escapeHtml(username || '未设置账号')}</span>
              <small>点击头像更换照片</small>
            </div>
          </div>
          <label>
            <span>账号</span>
            <input name="username" value="${escapeHtml(username)}" disabled />
          </label>
          <label>
            <span>昵称</span>
            <input name="nickname" value="${escapeHtml(nickname)}" placeholder="请输入昵称" required />
          </label>
          <div class="modal-actions wide">
            <button class="btn" type="button" data-action="close-account-modal">取消</button>
            <button class="btn primary" type="submit">保存</button>
          </div>
        </form>
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
      <h2>宝贝档案</h2>
      <div class="section-actions">
        <span>${children.length} 个</span>
        <button class="btn primary" type="button" data-action="open-child-modal">新增宝贝</button>
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
        <span>宝贝</span><span>性别</span><span>生日</span><span>状态</span><span>操作</span>
      </div>
      ${children.map((child) => renderChildRow(state, child, state.selectedChildId)).join('') || '<div class="empty">暂无宝贝档案</div>'}
    </div>
  `
}

function renderChildRow(state, child, selectedChildId) {
  const selected = String(child.id) === String(selectedChildId)
  const avatarSrc = state.avatarObjectUrls?.children?.[child.id] || ''
  const childAccountName = child.childUsername || child.childAccountUsername || ''
  return `
    <div class="table-row child-table">
      <div class="task-name-cell">
        ${renderAvatarImage(avatarSrc, child.nickname || '宝', 'child-mini', `child-row-${child.id}`)}
        <div><strong>${escapeHtml(child.nickname)}</strong><small>${escapeHtml(childAccountName ? `子账户：${childAccountName}` : (child.remark || '暂无备注'))}</small></div>
      </div>
      <span>${genderLabel(child.gender)}</span>
      <span>${escapeHtml(child.birthday || '未设置')}</span>
      <span>${statusName(child.status)}</span>
      <div class="row-actions task-row-actions">
        <button class="small-btn child-watch-btn ${selected ? 'active' : ''}" type="button" data-action="select-child" data-child-id="${child.id}" ${selected ? 'disabled' : ''}>${selected ? '观察中' : '查看'}</button>
        <button class="row-icon-btn" type="button" data-action="edit-child" data-child-id="${child.id}" aria-label="修改宝贝档案" title="修改宝贝档案">${renderActionIcon('edit')}</button>
        <button class="row-icon-btn danger" type="button" data-action="delete-child" data-child-id="${child.id}" aria-label="删除宝贝档案" title="删除宝贝档案">${renderActionIcon('delete')}</button>
      </div>
    </div>
  `
}

function renderGrowthManageView(state) {
  const activeSubView = growthSubView(state)
  return `
    <div class="growth-workspace">
      ${renderGrowthSubnav(activeSubView)}
      <section class="panel panel-pad">
        ${activeSubView === 'tasks' ? renderTaskManageView(state) : renderGoalList(state)}
      </section>
    </div>
  `
}

function renderGrowthSubnav(activeSubView) {
  const items = [
    ['goals', '成长目标'],
    ['tasks', '任务管理'],
  ]
  return `
    <nav class="profile-subnav growth-subnav" aria-label="宝贝成长导航">
      ${items.map(([view, label]) => `
        <button class="${activeSubView === view ? 'active' : ''}" type="button" data-growth-subview="${view}">
          ${label}
        </button>
      `).join('')}
    </nav>
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
    <div class="task-manage-content">
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
    </div>
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
  const style = `style="--task-color:${escapeHtml(task.taskColor || '#6c63ff')}"`
  if (periodType === 'WEEKLY') {
    const selectedDays = new Set((schedule.days || []).map(Number))
    return `
      <div class="time-blocks week-blocks compact-blocks" ${style}>
        ${weekDays.map(([value, label]) => `
          <span class="time-block ${selectedDays.has(value) ? 'active' : ''}" title="${escapeHtml(label)}">${label.slice(1)}</span>
        `).join('')}
      </div>
    `
  }
  if (periodType === 'MONTHLY') {
    const selectedDays = new Set((schedule.days || []).map(Number))
    return `
      <div class="time-blocks month-blocks compact-blocks" ${style}>
        ${dayOfMonthOptions.map(([value]) => `
          <span class="time-block ${selectedDays.has(value) ? 'active' : ''}" title="${value}日">${value}</span>
        `).join('')}
      </div>
    `
  }
  const startHour = Number(schedule.timeRange?.startHour ?? schedule.startHour ?? 6)
  const endHour = Number(schedule.timeRange?.endHour ?? schedule.endHour ?? 22)
  return renderDailyHourBlocks(getDailyHours(schedule, startHour, endHour), 'compact-blocks', task.taskColor || '#6c63ff')
}

function renderCalendarView(state, actions) {
  const calendarEvents = buildCalendarDisplayEvents(state)
  state.calendarDisplayEvents = calendarEvents
  const eventKind = state.calendarEventKind || 'tasks'
  return `
    <section class="panel">
      ${renderCalendar({
        monthDate: state.monthDate,
        events: calendarEvents.filter((event) => event.kind === eventKind),
        viewMode: state.taskCalendarViewMode || state.systemConfig?.taskCalendarViewMode || state.calendarViewMode || 'month',
        eventKind,
        systemConfig: state.systemConfig || {},
      })}
    </section>
  `
}

function renderRewardCalendarView(state, actions) {
  const calendarEvents = normalizeRewardCalendarEvents(state.exchanges || [], state.rewards || [])
  state.calendarDisplayEvents = calendarEvents
  return `
    <section class="panel">
      ${renderCalendar({
        monthDate: state.monthDate,
        events: calendarEvents,
        viewMode: state.rewardCalendarViewMode || state.systemConfig?.rewardCalendarViewMode || state.calendarViewMode || 'month',
        eventKind: 'rewards',
        systemConfig: state.systemConfig || {},
        titleLabel: '奖励日历',
        showKindToolbar: false,
      })}
    </section>
  `
}

function buildCalendarDisplayEvents(state) {
  return [
    ...normalizeTaskCalendarEvents(state.calendarEvents || [], state.tasks || []),
    ...normalizePointCalendarEvents(state.ledger || [], state.tasks || []),
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
  return exchanges.flatMap((exchange) => expandRewardExchangeEvents(exchange, rewards))
}

function expandRewardExchangeEvents(exchange, rewards = []) {
  const reward = rewards.find((item) => String(item.id) === String(exchange.rewardId)
    || String(item.name || '') === String(exchange.rewardNameSnapshot || ''))
  const status = exchange.status || 'REQUESTED'
  const fulfillmentStatus = exchange.fulfillmentStatus || 'PENDING'
  const branchStatus = exchange.branchStatus || 'PENDING'
  const fulfillmentType = exchange.fulfillmentType || reward?.fulfillmentType || 'INVENTORY_DEDUCT'
  const colorMap = {
    REQUESTED: exchange.rewardColorSnapshot || '#ff9f43',
    APPROVED: exchange.rewardColorSnapshot || '#6c63ff',
    COMPLETED: '#22c55e',
    REJECTED: '#ef4444',
  }
  const stateLabel = rewardMainFlowName(status, fulfillmentStatus)
  const branchLabel = status === 'APPROVED' ? branchStatusName(branchStatus) : ''
  const baseEvent = (date, phase = 'current', subtitle = '') => ({
    ...exchange,
    kind: 'rewards',
    kindLabel: '奖励',
    uid: `reward-${exchange.id || `${date}-${exchange.rewardNameSnapshot || 'exchange'}`}-${phase}-${date}`,
    date,
    phase,
    title: `${exchange.rewardIconSnapshot || '🎁'} ${exchange.rewardNameSnapshot || '奖励兑换'}`,
    exchangeId: exchange.id,
    rewardId: exchange.rewardId,
    rewardName: exchange.rewardNameSnapshot || '奖励兑换',
    rewardDetail: exchange.remark || `${exchange.requiredPointsSnapshot || 0} ${pointName(exchange.requiredPointType)}兑换`,
    rewardDescription: reward?.description || exchange.remark || '',
    requiredPointType: exchange.requiredPointType,
    requiredPoints: exchange.requiredPointsSnapshot || 0,
    rewardIcon: exchange.rewardIconSnapshot || '🎁',
    rewardColor: exchange.rewardColorSnapshot || '#ff9f43',
    rewardStateLabel: stateLabel,
    mainFlowLabel: stateLabel,
    branchFlowLabel: branchLabel,
    subtitle: subtitle || `${stateLabel}${branchLabel ? ` · ${branchLabel}` : ''}`,
    status,
    fulfillmentStatus,
    branchStatus,
    fulfillmentType,
    expectedArrivalDate: exchange.expectedArrivalDate || '',
    scheduleStartDate: exchange.scheduleStartDate || '',
    scheduleEndDate: exchange.scheduleEndDate || '',
    statusLabel: stateLabel,
    color: colorMap[status] || exchange.rewardColorSnapshot || '#ff9f43',
    note: status === 'REQUESTED'
      ? '宝贝已提交兑换申请，等待父母确认。'
      : status === 'APPROVED'
        ? `主流程：${stateLabel}${branchLabel ? `；分支：${branchLabel}` : ''}`
      : status === 'COMPLETED'
        ? '这条奖励兑换已经完成。'
        : '这条奖励兑换未通过。',
  })
  if (status === 'REQUESTED') {
    return [baseEvent(formatDateValue(exchange.requestedAt || exchange.createTime), 'request')]
  }
  if (status === 'REJECTED') {
    return [baseEvent(formatDateValue(exchange.reviewedAt || exchange.requestedAt || exchange.createTime), 'reject')]
  }
  if (status === 'COMPLETED' || fulfillmentStatus === 'CONFIRMED') {
    return [baseEvent(formatDateValue(exchange.confirmedAt || exchange.completedAt || exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt), 'confirm')]
  }
  if (fulfillmentType === 'PARENT_FULFILL' && (exchange.scheduleStartDate || exchange.scheduleEndDate)) {
    return expandDateRange(exchange.scheduleStartDate || exchange.scheduleEndDate, exchange.scheduleEndDate || exchange.scheduleStartDate)
      .map((date) => baseEvent(date, 'schedule', `${stateLabel} · ${branchLabel || '奖励日程'}`))
  }
  if (fulfillmentType === 'PARENT_PURCHASE' && branchStatus === 'PURCHASE_SHIPPING' && exchange.expectedArrivalDate) {
    return [baseEvent(formatDateValue(exchange.expectedArrivalDate), 'arrival', `${branchLabel} · 预计到达`)]
  }
  return [baseEvent(formatDateValue(exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt || exchange.createTime), 'fulfillment')]
}

function expandDateRange(startDate, endDate) {
  const start = dateFromKey(startDate)
  const end = dateFromKey(endDate) || start
  if (!start || !end) return []
  const safeEnd = end < start ? start : end
  const dates = []
  const cursor = new Date(start)
  while (cursor <= safeEnd && dates.length < 45) {
    dates.push(formatDate(cursor))
    cursor.setDate(cursor.getDate() + 1)
  }
  return dates
}

function dateFromKey(value) {
  const key = formatDateValue(value)
  if (!key) return null
  const date = new Date(`${key}T00:00:00`)
  return Number.isNaN(date.getTime()) ? null : date
}

function addDaysToDateKey(value, days) {
  const date = dateFromKey(value)
  if (!date) return ''
  date.setDate(date.getDate() + Number(days || 0))
  return formatDate(date)
}

function renderCalendarDayModal(state) {
  if (!state.calendarDayModalOpen || !state.selectedCalendarDateKey) return ''
  const events = calendarEventsForDate(state, state.selectedCalendarDateKey)
  const visibleEvents = filterCalendarEventsByKind(state, events)
  const activeKind = state.currentView === 'reward-calendar' ? 'rewards' : (state.calendarEventKind || 'tasks')
  const summary = renderCalendarDaySummary(activeKind, visibleEvents)
  const canQuickCreate = isManagementUser(state) && activeKind === 'tasks'
  const rewardDayTodo = activeKind === 'rewards' && isManagementUser(state)
    ? renderRewardDayTodo(state, state.selectedCalendarDateKey)
    : ''
  return `
    <div class="modal-backdrop calendar-layer calendar-day-layer">
      <section class="modal calendar-modal calendar-day-modal">
        <div class="modal-head">
          <div>
            <h2>${escapeHtml(formatCalendarDateLabel(state.selectedCalendarDateKey))}</h2>
            <p>${activeKind === 'tasks'
              ? '点开任务看说明，直接完成打卡。'
              : '点开记录看图标数量。'}</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-calendar-day-modal">×</button>
        </div>
        <div class="calendar-summary">
          ${summary}
          ${canQuickCreate ? `<button class="small-btn primary-lite calendar-quick-add" type="button" data-action="open-task-modal-for-date" data-task-date="${escapeHtml(state.selectedCalendarDateKey)}">新增当日任务</button>` : ''}
        </div>
        ${rewardDayTodo}
        <div class="calendar-log-list">
          ${visibleEvents.map((event) => renderCalendarLogItem(event)).join('') || '<div class="empty compact-empty">这一天没有对应记录</div>'}
        </div>
      </section>
    </div>
  `
}

function renderRewardDayTodo(state, dateKey) {
  const todos = (state.exchanges || [])
    .filter((exchange) => ['REQUESTED', 'APPROVED'].includes(exchange.status) && exchange.fulfillmentStatus !== 'CONFIRMED')
    .sort((left, right) => String(right.requestedAt || right.reviewedAt || '').localeCompare(String(left.requestedAt || left.reviewedAt || '')))
  if (!todos.length) return ''
  return `
    <section class="reward-day-todo">
      <div class="section-inline-head">
        <h3>待办礼物</h3>
        <span>${todos.length} 个</span>
      </div>
      <div class="reward-day-todo-list">
        ${todos.map((exchange) => renderRewardDayTodoItem(exchange, state.rewards || [], dateKey)).join('')}
      </div>
    </section>
  `
}

function renderRewardDayTodoItem(exchange, rewards, dateKey) {
  const reward = findRewardForExchange(exchange, rewards)
  const enrichedExchange = enrichRewardExchangeForAction(exchange, reward, dateKey)
  return `
    <article class="reward-day-todo-item">
      <span class="reward-mini" style="--reward-color:${escapeHtml(enrichedExchange.rewardColorSnapshot || enrichedExchange.rewardColor || '#ff9f43')}">${escapeHtml(enrichedExchange.rewardIconSnapshot || enrichedExchange.rewardIcon || '🎁')}</span>
      <div class="reward-day-todo-main">
        <strong>${escapeHtml(enrichedExchange.rewardNameSnapshot || enrichedExchange.rewardName || '奖励兑换')}</strong>
        <small>${escapeHtml(rewardMainFlowName(enrichedExchange.status, enrichedExchange.fulfillmentStatus))} · ${escapeHtml(fulfillmentTypeName(enrichedExchange.fulfillmentType))}</small>
        ${enrichedExchange.status === 'REQUESTED'
          ? `<div class="reward-flow-controls compact-flow-controls">
              <button class="small-btn primary-lite" type="button" data-action="approve-exchange" data-exchange-id="${escapeHtml(enrichedExchange.id)}">通过</button>
              <button class="small-btn danger" type="button" data-action="reject-exchange" data-exchange-id="${escapeHtml(enrichedExchange.id)}">拒绝</button>
            </div>`
          : `${renderFulfillmentControls(enrichedExchange, dateKey)}${renderParentRewardConfirmAction(enrichedExchange)}`}
      </div>
    </article>
  `
}

function enrichRewardExchangeForAction(exchange, reward, dateKey) {
  const fulfillmentType = exchange.fulfillmentType || reward?.fulfillmentType || 'INVENTORY_DEDUCT'
  const defaults = rewardFlowDateDefaults({ ...exchange, fulfillmentType }, dateKey)
  return {
    ...exchange,
    exchangeId: exchange.id,
    fulfillmentType,
    rewardName: exchange.rewardNameSnapshot || reward?.name || '奖励兑换',
    rewardIcon: exchange.rewardIconSnapshot || reward?.rewardIcon || '🎁',
    rewardColor: exchange.rewardColorSnapshot || reward?.rewardColor || '#ff9f43',
    rewardDescription: reward?.description || exchange.remark || '',
    expectedArrivalDate: defaults.expectedArrivalDate,
    scheduleStartDate: defaults.scheduleStartDate,
    scheduleEndDate: defaults.scheduleEndDate,
  }
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
  const canManageReward = isReward && isManagementUser(state)
  return `
    <div class="modal-backdrop calendar-layer calendar-event-layer">
      <section class="modal calendar-modal narrow calendar-event-modal">
        <div class="modal-head">
          <div>
            <h2>${escapeHtml(event.title)}</h2>
            <p>${escapeHtml(isTask ? (event.description || event.note || '') : isPoint ? (event.note || '') : (event.rewardDescription || event.note || ''))}</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-calendar-event-modal">×</button>
        </div>
        <div class="calendar-detail">
          ${isReward ? '' : `<div class="calendar-detail-head">
            <span class="${isReward ? 'reward-mini' : 'task-dot'}" style="${isReward ? `--reward-color:${event.rewardColor || event.color || '#ff9f43'}` : `--task-color:${event.color || '#6c63ff'}`}">${isReward ? escapeHtml(event.rewardIcon || '🎁') : ''}</span>
            <div>
              <strong>${escapeHtml(isTask ? event.title || '任务' : event.title || '积分')}</strong>
              <p>${escapeHtml(isTask ? (event.note || event.description || '') : isPoint ? (event.note || '') : (event.rewardDescription || event.note || ''))}</p>
            </div>
          </div>`}
          <div class="calendar-detail-focus">
            ${isPoint
              ? renderPointChangeIcons(event, 'large')
              : isReward
                ? renderPointCostSummary(event.requiredPointType, event.requiredPoints, state, 'calendar-cost')
                : renderTaskRewardIcons(event, scoreCount, 'calendar large')}
          </div>
          ${isReward ? renderRewardFlowDetail(event, canManageReward) : ''}
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
  const canCheckIn = event.kind === 'tasks' && event.status !== 'APPROVED'
  const taskDate = formatDateValue(event.date || event.taskDate)
  const canOpenDetail = event.kind !== 'tasks'
  const openDetailAttrs = canOpenDetail
    ? `role="button" tabindex="0" data-action="open-calendar-event" data-calendar-event-id="${escapeHtml(event.uid)}" data-task-id="${escapeHtml(event.taskId || '')}" data-task-date="${escapeHtml(taskDate)}"`
    : ''
  return `
    <article class="calendar-log-item ${canOpenDetail ? '' : 'calendar-log-item-static'}" ${openDetailAttrs}>
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
        ? canCheckIn
          ? `<button class="calendar-log-action calendar-log-action-btn" type="button" data-action="complete-task" data-task-id="${escapeHtml(event.taskId || '')}" data-task-date="${escapeHtml(taskDate)}">打卡</button>`
          : '<span class="calendar-log-action done">已完成</span>'
        : ''}
    </article>
  `
}

function renderCalendarStatusScore(event, count, density = '') {
  const completed = event.status === 'APPROVED'
  const color = completed ? event.pointColor : '#cbd5e1'
  return completed
    ? renderScoreIcons(event.pointType, count, color, density)
    : renderScoreIcons(event.pointType, count, color, `${density} muted`.trim())
}

function renderRewardFlowDetail(event, canManageReward) {
  const branchMeta = rewardBranchDateMeta(event)
  return `
    <div class="reward-flow-detail">
      <div class="reward-flow-card">
        <span>主流程</span>
        <strong>${escapeHtml(event.mainFlowLabel || rewardMainFlowName(event.status, event.fulfillmentStatus))}</strong>
      </div>
      <div class="reward-flow-card">
        <span>分支流程</span>
        <strong>${escapeHtml(event.branchFlowLabel || branchStatusName(event.branchStatus))}</strong>
      </div>
      ${branchMeta ? `<div class="reward-flow-card wide">
        <span>${escapeHtml(branchMeta.label)}</span>
        <strong>${escapeHtml(branchMeta.value)}</strong>
      </div>` : ''}
      ${event.rewardDescription ? `<div class="reward-flow-card wide reward-description-card">
        <span>奖励描述</span>
        <p>${escapeHtml(event.rewardDescription)}</p>
      </div>` : ''}
      ${canManageReward ? renderRewardCalendarManageActions(event) : ''}
    </div>
  `
}

function renderRewardCalendarManageActions(event) {
  if (event.status === 'REQUESTED') {
    return `
      <div class="reward-flow-card wide reward-calendar-actions">
        <span>父母操作</span>
        <div class="reward-flow-controls">
          <button class="small-btn primary-lite" type="button" data-action="approve-exchange" data-exchange-id="${event.exchangeId || event.id}">通过</button>
          <button class="small-btn danger" type="button" data-action="reject-exchange" data-exchange-id="${event.exchangeId || event.id}">拒绝</button>
        </div>
      </div>
    `
  }
  if (event.status === 'APPROVED') {
    return `
      <div class="reward-flow-card wide reward-calendar-actions">
        <span>父母维护</span>
        ${renderFulfillmentControls(event, event.date)}
        ${renderParentRewardConfirmAction(event)}
      </div>
    `
  }
  return ''
}

function rewardBranchDateMeta(event) {
  if (event.fulfillmentType === 'PARENT_PURCHASE' && event.expectedArrivalDate) {
    return { label: '预计到达', value: event.expectedArrivalDate }
  }
  if (event.fulfillmentType === 'PARENT_FULFILL' && (event.scheduleStartDate || event.scheduleEndDate)) {
    return { label: '奖励日程', value: dateRange(event.scheduleStartDate, event.scheduleEndDate) }
  }
  return null
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
  const event = (state.calendarEvents || []).find((item) => String(item.taskId) === String(state.selectedCheckInTaskId) && formatDateValue(item.taskDate) === taskDate)
  const task = (state.tasks || []).find((item) => String(item.id) === String(state.selectedCheckInTaskId))
  const taskMeta = task || event
  if (!taskMeta) return ''
  const pointType = taskMeta.pointType || 'STAR'
  const pointColor = taskMeta.pointColor || pointTypeColor(pointType)
  const basePoints = Math.max(1, Number(taskMeta.basePoints || 1))
  const selectedCount = clamp(Number(state.selectedCheckInRewardCount || basePoints), 1, basePoints)
  const activeIconCount = basePoints
  return `
    <div class="modal-backdrop checkin-layer">
      <section class="modal narrow checkin-modal">
        <div class="modal-head">
          <div><h2>任务打卡</h2><p>${escapeHtml(taskMeta.name || taskMeta.taskName || '任务')}</p></div>
          <button class="icon-btn" type="button" data-action="close-checkin-modal">×</button>
        </div>
        <form class="modal-form" data-form="task-checkin">
          <div class="checkin-task-note wide">
            ${escapeHtml(taskMeta.note || taskMeta.description || '选择这次完成获得的奖励数量。')}
          </div>
          <div class="checkin-reward-panel wide" style="--point-color:${escapeHtml(pointColor)}">
            <div class="checkin-reward-head">
              <div>
                <div class="field-label">奖励</div>
                <strong>你的奖励预览</strong>
              </div>
              <span><b data-checkin-reward-count data-total="${basePoints}">${selectedCount}</b> / ${basePoints}</span>
            </div>
            <div class="checkin-reward-row">
              <button class="checkin-reward-adjust" type="button" data-action="decrease-checkin-reward" aria-label="减少奖励数量">−</button>
              <div class="checkin-reward-grid" aria-label="奖励图标预览">
                ${Array.from({ length: activeIconCount }, (_, index) => {
                  const number = index + 1
                  return `
                    <button class="checkin-reward-icon ${number <= selectedCount ? 'active' : ''}" type="button" data-action="select-checkin-reward" data-reward-index="${number}" aria-label="选择 ${number} 个奖励">
                      ${pointIcon(pointType)}
                    </button>
                  `
                }).join('')}
              </div>
              <button class="checkin-reward-adjust" type="button" data-action="increase-checkin-reward" aria-label="增加奖励数量">+</button>
            </div>
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
          <label><span>货币类型</span>${renderSelect('reward-point', filters.pointType || '', [['', '全部'], ...pointTypeOptions], 'reward')}</label>
          <label><span>状态</span>${renderSelect('reward-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['PAUSED', '暂停']], 'reward')}</label>
        `,
      })}
      <div class="table-card">
        <div class="table-head reward-table">
        <span>奖励</span><span>需要货币</span><span>库存</span><span>实现方式</span><span>操作</span>
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
          <label><span>货币类型</span>${renderSelect('currency-point', filters.pointType || '', [['', '全部'], ...pointTypeOptions], 'currency')}</label>
          <label><span>状态</span>${renderSelect('currency-status', filters.status || '', [['', '全部'], ['ACTIVE', '启用'], ['INACTIVE', '停用']], 'currency')}</label>
        `,
      })}
      <div class="table-card">
        <div class="table-head currency-table">
          <span>币值</span><span>货币类型</span><span>比例</span><span>状态</span><span>操作</span>
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
      <span>${escapeHtml(pointLevelName(currency.pointType))}</span>
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
        ${visibleTodos.map((exchange) => {
          const reward = findRewardForExchange(exchange, state.rewards || [])
          const enrichedExchange = { ...exchange, fulfillmentType: exchange.fulfillmentType || reward?.fulfillmentType || 'INVENTORY_DEDUCT' }
          return `
          <div class="todo-item">
            <span class="reward-mini" style="--reward-color:${exchange.rewardColorSnapshot || '#6c63ff'}">${escapeHtml(exchange.rewardIconSnapshot || '🎁')}</span>
            <div>
              <strong>${escapeHtml(exchange.rewardNameSnapshot || '奖励兑换')}</strong>
              <small>${exchange.requiredPointsSnapshot || 0} ${pointIcon(exchange.requiredPointType)} · ${exchange.status === 'REQUESTED' ? '待确认' : `${fulfillmentStatusName(exchange.fulfillmentStatus)} · ${branchStatusName(exchange.branchStatus)}`}</small>
            </div>
            <div class="row-actions">
              ${exchange.status === 'REQUESTED'
                ? `<button class="small-btn primary-lite" type="button" data-action="approve-exchange" data-exchange-id="${exchange.id}">通过</button><button class="small-btn danger" type="button" data-action="reject-exchange" data-exchange-id="${exchange.id}">拒绝</button>`
                : `${renderFulfillmentControls(enrichedExchange)}${renderParentRewardConfirmAction(enrichedExchange)}`}
            </div>
          </div>
        `}).join('') || '<div class="empty compact-empty">暂无兑换待办</div>'}
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
  )).sort((left, right) => String(right.requestedAt || right.reviewedAt || right.completedAt || right.createdAt || '')
    .localeCompare(String(left.requestedAt || left.reviewedAt || left.completedAt || left.createdAt || '')))
  const activeTab = state.rewardTicketTab === 'used' ? 'used' : 'unused'
  const unusedTickets = tickets.filter((exchange) => rewardTicketBucket(exchange) === 'unused')
  const usedTickets = tickets.filter((exchange) => rewardTicketBucket(exchange) === 'used')
  const activeTickets = activeTab === 'used' ? usedTickets : unusedTickets
  return `
    <section class="panel panel-pad wide">
      <div class="section-inline-head">
        <h2>礼物兑换券</h2>
        <span>${tickets.length} 张</span>
      </div>
      <div class="ticket-tabs" role="tablist" aria-label="礼物兑换券分类">
        <button class="${activeTab === 'unused' ? 'active' : ''}" type="button" role="tab" aria-selected="${activeTab === 'unused' ? 'true' : 'false'}" data-ticket-tab="unused">未使用 <b>${unusedTickets.length}</b></button>
        <button class="${activeTab === 'used' ? 'active' : ''}" type="button" role="tab" aria-selected="${activeTab === 'used' ? 'true' : 'false'}" data-ticket-tab="used">已使用 <b>${usedTickets.length}</b></button>
      </div>
      <div class="ticket-list">
        ${activeTickets.map((exchange) => renderRewardTicketItem(exchange)).join('') || '<div class="empty compact-empty">暂无礼物兑换券</div>'}
      </div>
    </section>
  `
}

function renderRewardTicketItem(exchange) {
  const used = rewardTicketBucket(exchange) === 'used'
  const badge = rewardTicketBadgeLabel(exchange)
  const subtitle = rewardTicketSubtitle(exchange)
  return `
    <button class="ticket-item ${used ? 'confirmed' : ''} ${exchange.status === 'REQUESTED' ? 'pending' : ''}" type="button" data-action="open-reward-ticket" data-exchange-id="${escapeHtml(exchange.id)}">
      <span class="reward-mini" style="--reward-color:${exchange.rewardColorSnapshot || '#6c63ff'}">${escapeHtml(exchange.rewardIconSnapshot || '🎁')}</span>
      <div>
        <strong>${escapeHtml(exchange.rewardNameSnapshot || '礼物券')}</strong>
        <small>${escapeHtml(subtitle)}</small>
      </div>
      <span class="ticket-state">${escapeHtml(badge)}</span>
    </button>
  `
}

function renderRewardTicketModal(state) {
  if (!state.rewardTicketModalOpen || !state.selectedRewardTicketId) return ''
  const exchange = (state.exchanges || []).find((item) => String(item.id) === String(state.selectedRewardTicketId))
  if (!exchange) return ''
  const reward = findRewardForExchange(exchange, state.rewards || [])
  const rewardIcon = exchange.rewardIconSnapshot || reward?.rewardIcon || '🎁'
  const rewardColor = exchange.rewardColorSnapshot || reward?.rewardColor || '#6c63ff'
  const requiredPointType = exchange.requiredPointType || reward?.requiredPointType || 'STAR'
  const requiredPoints = Math.max(1, Number(exchange.requiredPointsSnapshot || reward?.requiredPoints || 1))
  const currencies = getPointCurrencies(state)
  const requiredCurrency = currencyMeta(requiredPointType, currencies)
  const canConfirm = exchange.status === 'APPROVED' && exchange.fulfillmentStatus !== 'CONFIRMED'
  const timelineItems = renderRewardTicketTimelineItems(exchange)
  return `
    <div class="modal-backdrop reward-ticket-backdrop">
      <section class="modal narrow reward-ticket-modal" aria-label="礼物兑换券详情">
        <div class="modal-head">
          <div>
            <h2>礼物兑换券</h2>
            <p>${escapeHtml(rewardTicketTitle(exchange))}</p>
          </div>
          <button class="icon-btn" type="button" data-action="close-reward-ticket-modal">×</button>
        </div>
        <div class="ticket-modal-summary">
          <span class="reward-mini large" style="--reward-color:${escapeHtml(rewardColor)}">${escapeHtml(rewardIcon)}</span>
          <div class="ticket-modal-summary-main">
            <h3>${escapeHtml(exchange.rewardNameSnapshot || reward?.name || '奖励')}</h3>
            <p>${escapeHtml(reward?.description || exchange.remark || '暂无奖励说明')}</p>
            <div class="ticket-modal-meta">
              <span class="ticket-meta-chip">${escapeHtml(rewardMainFlowName(exchange.status, exchange.fulfillmentStatus))}</span>
              <span class="ticket-meta-chip">${escapeHtml(branchStatusName(exchange.branchStatus))}</span>
              <span class="ticket-meta-chip ${exchange.status === 'APPROVED' && exchange.fulfillmentStatus !== 'CONFIRMED' ? 'accent' : ''}">${escapeHtml(rewardTicketBadgeLabel(exchange))}</span>
            </div>
          </div>
          <div class="ticket-cost-panel" style="--point-color:${escapeHtml(requiredCurrency.color)}">
            ${renderCurrencyAmount(requiredCurrency, requiredPoints, 'cost-amount')}
            ${renderLimitedPointIcons(requiredCurrency.icon, requiredPoints, requiredCurrency.color, 9)}
          </div>
        </div>
        <div class="ticket-timeline-head">
          <h3>礼物动态</h3>
          <span>${timelineItems.length} 条动态</span>
        </div>
        <div class="ticket-timeline">
          ${renderRewardTicketTimeline(timelineItems)}
        </div>
        <div class="modal-actions wide ticket-modal-actions">
          ${canConfirm ? `<button class="btn primary" type="button" data-action="confirm-reward-ticket" data-exchange-id="${escapeHtml(exchange.id)}">确认礼物券</button>` : ''}
          <button class="btn" type="button" data-action="close-reward-ticket-modal">关闭</button>
        </div>
      </section>
    </div>
  `
}

function rewardTicketBucket(exchange) {
  return exchange.status === 'COMPLETED' || exchange.fulfillmentStatus === 'CONFIRMED' ? 'used' : 'unused'
}

function rewardTicketBadgeLabel(exchange) {
  if (exchange.status === 'REQUESTED') return '待家长确认'
  if (exchange.status === 'APPROVED' && exchange.fulfillmentStatus !== 'CONFIRMED') return '可提前确认'
  return '已使用'
}

function rewardTicketSubtitle(exchange) {
  if (exchange.status === 'REQUESTED') return '等待家长确认'
  if (exchange.status === 'APPROVED' && exchange.fulfillmentStatus !== 'CONFIRMED') return '家长已确认，可提前确认'
  return '礼物已使用'
}

function rewardTicketTitle(exchange) {
  if (exchange.status === 'REQUESTED') return '等待家长确认，确认后就能继续查看动态'
  if (exchange.status === 'APPROVED' && exchange.fulfillmentStatus !== 'CONFIRMED') return '家长已确认，宝贝可以提前确认'
  return '礼物已使用完成'
}

function renderRewardTicketTimeline(items) {
  return items.map((item) => `
    <article class="ticket-timeline-item ${item.tone}">
      <span class="ticket-timeline-dot"></span>
      <div>
        <div class="ticket-timeline-headline">
          <strong>${escapeHtml(item.title)}</strong>
          ${item.time ? `<small>${escapeHtml(item.time)}</small>` : ''}
        </div>
        <p>${escapeHtml(item.detail)}</p>
      </div>
    </article>
  `).join('')
}

function renderRewardTicketTimelineItems(exchange) {
  const items = []
  const pushItem = (tone, title, detail, time = '') => {
    items.push({ tone, title, detail, time })
  }
  pushItem('neutral', '兑换申请', '宝贝发起了礼物兑换。', formatActivityTime(exchange.requestedAt || exchange.createTime))
  if (exchange.status === 'REJECTED') {
    pushItem('negative', '家长拒绝', '这次兑换申请没有通过。', formatActivityTime(exchange.reviewedAt || exchange.requestedAt || exchange.createTime))
    return items
  }
  if (exchange.reviewedAt || exchange.status === 'APPROVED' || exchange.status === 'COMPLETED') {
    pushItem('positive', '家长确认', exchange.status === 'APPROVED' ? '家长已确认，宝贝可以提前确认。' : '兑换已经通过审核。', formatActivityTime(exchange.reviewedAt || exchange.requestedAt || exchange.createTime))
  } else {
    pushItem('neutral', '等待确认', '正在等待家长处理。', '')
    return items
  }
  const fulfillmentType = exchange.fulfillmentType || 'INVENTORY_DEDUCT'
  if (fulfillmentType === 'PARENT_PURCHASE') {
    if (exchange.expectedArrivalDate) {
      pushItem('neutral', '预计到达', `奖励预计在 ${exchange.expectedArrivalDate} 到达。`, exchange.expectedArrivalDate)
    }
    if (exchange.branchStatus === 'PURCHASE_ORDERED' || exchange.branchStatus === 'PURCHASE_SHIPPING' || exchange.branchStatus === 'PURCHASE_ARRIVED' || exchange.fulfillmentUpdatedAt) {
      pushItem('positive', '家长已下单', '奖励正在运送中。', formatActivityTime(exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt))
    }
    if (exchange.branchStatus === 'PURCHASE_SHIPPING' || exchange.branchStatus === 'PURCHASE_ARRIVED') {
      pushItem('positive', '奖励运输中', '礼物已经进入运输阶段。', formatActivityTime(exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt))
    }
    if (exchange.branchStatus === 'PURCHASE_ARRIVED' || exchange.status === 'COMPLETED' || exchange.fulfillmentStatus === 'CONFIRMED') {
      pushItem('positive', '奖励已到货', '礼物已经准备好，可以确认。', formatActivityTime(exchange.fulfillmentUpdatedAt || exchange.completedAt || exchange.reviewedAt))
    }
  } else if (fulfillmentType === 'PARENT_FULFILL') {
    if (exchange.scheduleStartDate || exchange.scheduleEndDate) {
      const scheduleText = exchange.scheduleStartDate && exchange.scheduleEndDate
        ? `${exchange.scheduleStartDate} 至 ${exchange.scheduleEndDate}`
        : exchange.scheduleStartDate || exchange.scheduleEndDate
      pushItem('neutral', '家长加入日程', `日程安排：${scheduleText}。`, scheduleText)
    }
    if (exchange.branchStatus === 'SCHEDULED' || exchange.branchStatus === 'IN_PROGRESS' || exchange.branchStatus === 'COMPLETED' || exchange.fulfillmentUpdatedAt) {
      pushItem('positive', '奖励进行中', '家长已经开始安排这次奖励。', formatActivityTime(exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt))
    }
  } else if (fulfillmentType === 'PARENT_EXECUTE') {
    if (exchange.branchStatus === 'IN_PROGRESS' || exchange.branchStatus === 'COMPLETED' || exchange.fulfillmentUpdatedAt) {
      pushItem('positive', '家长执行中', '奖励正在由家长执行。', formatActivityTime(exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt))
    }
  } else {
    pushItem('positive', '奖励已扣减', '库存扣减型奖励已经处理。', formatActivityTime(exchange.fulfillmentUpdatedAt || exchange.reviewedAt || exchange.requestedAt))
  }
  if (exchange.confirmedAt || exchange.status === 'COMPLETED' || exchange.fulfillmentStatus === 'CONFIRMED') {
    pushItem('positive', '宝贝确认', '宝贝已经完成确认。', formatActivityTime(exchange.confirmedAt || exchange.completedAt || exchange.fulfillmentUpdatedAt))
  } else if (exchange.status === 'APPROVED') {
    pushItem('neutral', '等待宝贝确认', '宝贝可以在这里提前确认。', '')
  }
  return items
}

function renderFulfillmentControls(exchange, contextDate = '') {
  const branchStatus = exchange.branchStatus || 'PENDING'
  const fulfillmentType = exchange.fulfillmentType || 'INVENTORY_DEDUCT'
  const defaults = rewardFlowDateDefaults(exchange, contextDate)
  if (fulfillmentType === 'PARENT_PURCHASE') {
    return `
      <div class="reward-flow-controls">
        ${renderChineseDatePicker('expectedArrivalDate', defaults.expectedArrivalDate, { label: '预计到达', placeholder: '请选择预计到达日期', ariaLabel: '选择预计到达日期' })}
        ${renderFulfillmentRemarkField('例如：今天已下单，预计 3 天后到达', exchange.remark || '')}
        <button class="small-btn ${branchStatus === 'PURCHASE_ORDERED' ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="PURCHASE_ORDERED">已下单</button>
        <button class="small-btn ${branchStatus === 'PURCHASE_SHIPPING' ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="PURCHASE_SHIPPING">运输中</button>
        <button class="small-btn ${branchStatus === 'PURCHASE_ARRIVED' ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="PURCHASE_ARRIVED">已到货</button>
      </div>
    `
  }
  if (fulfillmentType === 'PARENT_FULFILL') {
    const scheduleLocked = branchStatus === 'IN_PROGRESS' || branchStatus === 'COMPLETED'
    const scheduleButtonLabel = branchStatus === 'SCHEDULED' ? '修改日程' : '加入日程'
    return `
      <div class="reward-flow-controls schedule-flow-controls">
        ${scheduleLocked
          ? '<div class="flow-locked-note">奖励已进入进行中，日程不可修改</div>'
          : `${renderChineseDatePicker('scheduleStartDate', defaults.scheduleStartDate, { label: '开始日期', placeholder: '请选择开始日期', ariaLabel: '选择开始日期' })}
            ${renderChineseDatePicker('scheduleEndDate', defaults.scheduleEndDate, { label: '结束日期', placeholder: '请选择结束日期', ariaLabel: '选择结束日期' })}
            ${renderFulfillmentRemarkField('例如：周六带宝贝去野生动物园', exchange.remark || '')}
            <button class="small-btn ${branchStatus === 'SCHEDULED' ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="SCHEDULED">${scheduleButtonLabel}</button>`}
        <button class="small-btn ${branchStatus === 'IN_PROGRESS' ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="IN_PROGRESS">进行中</button>
        <button class="small-btn ${branchStatus === 'COMPLETED' ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="COMPLETED">已实现</button>
      </div>
    `
  }
  const options = fulfillmentType === 'PARENT_EXECUTE'
    ? [['IN_PROGRESS', '处理中'], ['COMPLETED', '已完成']]
    : [['COMPLETED', '已完成']]
  return `
    <div class="reward-flow-controls compact-flow-controls">
      ${options.map(([value, label]) => `
        <button class="small-btn ${branchStatus === value ? 'primary-lite' : ''}" type="button" data-action="update-fulfillment" data-exchange-id="${exchange.exchangeId || exchange.id}" data-branch-status="${value}">${label}</button>
      `).join('')}
    </div>
  `
}

function renderFulfillmentRemarkField(placeholder, value = '') {
  return `
    <label class="flow-remark-field">
      <span>备注</span>
      <input name="remark" value="${escapeHtml(value)}" placeholder="${escapeHtml(placeholder)}" />
    </label>
  `
}

function rewardFlowDateDefaults(exchange, contextDate = '') {
  const dateKey = formatDateValue(contextDate)
  const fulfillmentType = exchange.fulfillmentType || 'INVENTORY_DEDUCT'
  const expectedArrivalDate = exchange.expectedArrivalDate
    || (fulfillmentType === 'PARENT_PURCHASE' && dateKey ? addDaysToDateKey(dateKey, 3) : '')
  const scheduleStartDate = exchange.scheduleStartDate
    || (fulfillmentType === 'PARENT_FULFILL' && dateKey ? dateKey : '')
  const scheduleEndDate = exchange.scheduleEndDate
    || (fulfillmentType === 'PARENT_FULFILL' && dateKey ? dateKey : '')
  return { expectedArrivalDate, scheduleStartDate, scheduleEndDate }
}

function renderParentRewardConfirmAction(exchange) {
  if (!canParentConfirmReward(exchange)) return ''
  return `
    <button class="small-btn primary-lite reward-confirm-action" type="button" data-action="confirm-reward-ticket" data-exchange-id="${escapeHtml(exchange.exchangeId || exchange.id)}">
      确认完成
    </button>
  `
}

function canParentConfirmReward(exchange) {
  if (exchange.status !== 'APPROVED' || exchange.fulfillmentStatus === 'CONFIRMED') return false
  const branchStatus = exchange.branchStatus || 'PENDING'
  return exchange.fulfillmentStatus === 'COMPLETED'
    || branchStatus === 'PURCHASE_ARRIVED'
    || branchStatus === 'COMPLETED'
}

function findRewardForExchange(exchange, rewards = []) {
  return rewards.find((reward) => String(reward.id) === String(exchange.rewardId)
    || String(reward.name || '') === String(exchange.rewardNameSnapshot || ''))
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
  const quickDailyMode = Boolean(draftDate && !state.editingTask)
  const task = state.editingTask || (quickDailyMode ? { periodType: 'DAILY', scheduleJson: JSON.stringify({ type: 'DAILY', category: 'HABIT', hours: [], requiredCount: 1 }) } : {})
  const schedule = parseSchedule(task.scheduleJson)
  const periodType = task.periodType || schedule.type || 'DAILY'
  const startHour = Number(schedule.timeRange?.startHour ?? schedule.startHour ?? 6)
  const endHour = Number(schedule.timeRange?.endHour ?? schedule.endHour ?? 22)
  const isEditingTask = Boolean(task.id)
  const dailyHours = getDailyHours(schedule, startHour, endHour, isEditingTask)
  const selectedDays = Array.isArray(schedule.days) ? schedule.days.map(Number) : []
  const requiredCount = Number(schedule.requiredCount ?? schedule.timesPerWeek ?? 1)
  const taskPointTypeOptions = quickDailyMode ? pointTypeOptions.filter(([value]) => value !== 'CROWN') : pointTypeOptions
  const pointType = taskPointTypeOptions.some(([value]) => value === task.pointType) ? task.pointType : 'STAR'
  const pointColor = task.pointColor || '#ffd84d'
  const basePoints = Number(task.basePoints || 1)
  const requireApproval = Number(task.requireApproval) === 1 || task.requireApproval === true
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${task.id ? '修改任务' : quickDailyMode ? '新增当日任务' : '新增任务'}</h2><p>设置可完成时间、任务次数和奖励。</p></div>
          <button class="icon-btn" type="button" data-action="close-task-modal">×</button>
        </div>
        <form class="modal-form" data-form="task">
          <input type="hidden" name="taskId" value="${escapeHtml(task.id || '')}" />
          <label><span>任务名称</span><input name="name" value="${escapeHtml(task.name || '')}" placeholder="${draftDate ? '例如：当天阅读任务' : '例如：晨读 20 分钟'}" /></label>
          <label><span>所属目标</span>${renderSelect('goalId', task.goalId || state.goals[0]?.id || '', state.goals.map((goal) => [goal.id, goal.name]))}</label>
          <label><span>任务分类</span>${renderSelect('taskCategory', schedule.category || 'HABIT', [['STUDY', '学习'], ['LIFE', '生活'], ['SPORT', '运动'], ['HABIT', '习惯']])}</label>
          ${quickDailyMode ? '<input type="hidden" name="periodType" value="DAILY" />' : ''}
          <div class="schedule-type-row wide">
            ${quickDailyMode ? '' : `<div>
              <div class="schedule-title">任务类型</div>
              <div class="segmented">
                ${[['DAILY', '每日'], ['WEEKLY', '每周'], ['MONTHLY', '每月']].map(([value, label]) => `
                  <label><input type="radio" name="periodType" value="${value}" data-schedule-mode ${periodType === value ? 'checked' : ''} /> <span>${label}</span></label>
                `).join('')}
              </div>
            </div>`}
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
            ${renderPointTypePicker(pointType, taskPointTypeOptions)}
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
          <label class="wide"><span>说明</span><input name="description" value="${escapeHtml(task.description || '')}" placeholder="给宝贝看的简短说明" /></label>
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

function renderPointTypePicker(selectedPointType, options = pointTypeOptions) {
  return `
    <div class="point-type-picker">
      ${options.map(([value, label]) => `
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

function renderCurrencyAmount(meta, amount, className = '') {
  const safeAmount = Math.max(0, Number(amount || 0))
  return `
    <span class="currency-amount ${escapeHtml(className)}" style="--point-color:${escapeHtml(meta.color || '#f59e0b')}">
      <strong>${safeAmount}</strong>
      <b>×</b>
      <span>${escapeHtml(meta.icon || '★')}</span>
    </span>
  `
}

function renderPointCostSummary(pointType, amount, state, className = '') {
  const currencies = getPointCurrencies(state)
  const meta = currencyMeta(pointType || 'STAR', currencies)
  const safeAmount = Math.max(0, Number(amount || 0))
  return `
    <div class="point-cost-summary ${escapeHtml(className)}" style="--point-color:${escapeHtml(meta.color)}" aria-label="兑换消耗 ${safeAmount} 个 ${escapeHtml(meta.name)}">
      <div class="point-cost-label">兑换消耗</div>
      <div class="point-cost-main">
        <strong>${safeAmount}</strong>
        <span>个</span>
        <b>${escapeHtml(meta.icon || '★')}</b>
      </div>
      <small>${escapeHtml(meta.name)}</small>
      ${renderLimitedPointIcons(meta.icon, safeAmount, meta.color, 9)}
    </div>
  `
}

function renderRewardExchangeOverview(reward, balances, requiredCurrency, currencies, options) {
  const requiredPointType = reward.requiredPointType || 'STAR'
  const requiredPoints = Math.max(1, Number(reward.requiredPoints || 1))
  const requiredOption = options.find((option) => option.pointType === requiredPointType)
  const higherOption = options.find((option) => option.pointType !== requiredPointType && option.enough)
  const hasEnough = options.some((option) => option.enough)
  const hintClass = requiredOption?.enough ? 'ready' : higherOption ? 'suggest' : 'danger'
  const hintText = requiredOption?.enough
    ? `当前 ${requiredCurrency.icon} 足够，可以直接兑换。`
    : higherOption
      ? `当前 ${requiredCurrency.icon} 不足，可使用更高币值图标兑换，系统会自动找零成 ${requiredCurrency.icon}。`
      : '余额不足，先攒够目标图标或更高币值图标。'
  return `
    <div class="reward-exchange-overview">
      <article class="reward-exchange-card required" style="--point-color:${escapeHtml(requiredCurrency.color)}">
        <span>礼物所需</span>
        ${renderCurrencyAmount(requiredCurrency, requiredPoints, 'large')}
        ${renderLimitedPointIcons(requiredCurrency.icon, requiredPoints, requiredCurrency.color, 9)}
      </article>
      <article class="reward-exchange-card balances">
        <span>当前余额</span>
        <div class="reward-balance-strip">
          ${['STAR', 'FLOWER', 'CROWN'].map((pointType) => {
            const meta = currencyMeta(pointType, currencies)
            return `
              <div class="reward-balance-item ${pointType === requiredPointType ? 'target' : ''}" style="--point-color:${escapeHtml(meta.color)}">
                ${renderCurrencyAmount(meta, balances.get(pointType) || 0, 'balance-amount')}
              </div>
            `
          }).join('')}
        </div>
      </article>
    </div>
    <div class="reward-exchange-hint ${hintClass}">
      ${escapeHtml(hintText)}
      ${hasEnough ? '' : '<strong>兑换按钮已置灰</strong>'}
    </div>
  `
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

function renderDailyHourBlocks(selectedHours, density = '', taskColor = '#6c63ff') {
  const selectedSet = new Set(selectedHours.map(Number))
  return `
    <div class="time-blocks hour-blocks ${density}" style="--task-color:${escapeHtml(taskColor)}">
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
  const existingChildUsername = child.childUsername || child.childAccountUsername || ''
  const createAccount = Boolean(state.childAccountDraftEnabled) && !existingChildUsername
  const childAvatarSrc = pendingAvatarPreview(state, 'child', child.id || '') || state.avatarObjectUrls?.children?.[child.id] || ''
  return `
    <div class="modal-backdrop">
      <section class="modal">
        <div class="modal-head">
          <div><h2>${child.id ? '修改宝贝档案' : '新增宝贝档案'}</h2><p>维护宝贝昵称、生日和备注。</p></div>
          <button class="icon-btn" type="button" data-action="close-child-modal">×</button>
        </div>
        <form class="modal-form" data-form="child" autocomplete="off">
          <input type="hidden" name="childId" value="${escapeHtml(child.id || '')}" />
          <div class="account-summary-card wide">
            ${renderAvatarUploadControl({
              src: childAvatarSrc,
              fallback: child.nickname || '宝',
              scope: 'child',
              childId: child.id || '',
              previewKey: 'child',
            })}
            <div class="account-summary-copy">
              <strong>${escapeHtml(child.nickname || '未命名宝贝')}</strong>
              <span>${escapeHtml(child.remark || '点击头像更换照片')}</span>
            </div>
          </div>
          <label><span>宝贝昵称</span><input name="nickname" value="${escapeHtml(child.nickname || '')}" placeholder="例如：小星" /></label>
          <label><span>性别</span>${renderSelect('gender', child.gender || 'UNKNOWN', [['UNKNOWN', '未设置'], ['MALE', '男孩'], ['FEMALE', '女孩']])}</label>
          ${renderChineseDatePicker('birthday', child.birthday || '', { label: '生日', placeholder: '请选择生日', ariaLabel: '选择生日' })}
          <label class="wide"><span>备注</span><input name="remark" value="${escapeHtml(child.remark || '')}" placeholder="宝贝偏好、阶段目标等" /></label>
          ${existingChildUsername
            ? `<div class="child-account-summary wide">
                <span>已创建子账户</span>
                <strong>${escapeHtml(existingChildUsername)}</strong>
                <div class="child-account-actions">
                  <button class="small-btn primary-lite" type="button" data-action="read-child-password" data-child-id="${escapeHtml(child.id || '')}">查看密码</button>
                  <button class="small-btn" type="button" data-action="enable-child-password-edit" data-child-id="${escapeHtml(child.id || '')}">修改密码</button>
                </div>
                <div class="child-password-view ${state.revealedChildPasswords?.[child.id] ? '' : 'hidden'}">
                  <span>当前密码</span>
                  <strong>${escapeHtml(state.revealedChildPasswords?.[child.id] || '')}</strong>
                </div>
                <div class="child-password-edit ${state.childPasswordEditEnabled ? '' : 'hidden'}">
                  <label><span>新密码</span><input name="childPasswordToUpdate" autocomplete="new-password" type="password" placeholder="至少 6 位" /></label>
                </div>
              </div>`
            : `<label class="switch-field wide">
                <input type="checkbox" name="createChildAccount" value="true" ${createAccount ? 'checked' : ''} />
                <span>创建子账户</span>
              </label>
              <div class="child-account-fields wide ${createAccount ? '' : 'hidden'}">
                <label><span>子账户账号</span><input name="childUsername" autocomplete="new-password" autocapitalize="none" spellcheck="false" placeholder="例如：baby-star" /></label>
                <label><span>子账户密码</span><input name="childPassword" autocomplete="new-password" type="password" placeholder="至少 6 位" /></label>
              </div>`}
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
  const goalIcon = goal.icon || '★'
  const goalColor = goal.goalColor || '#6c63ff'
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
          <div class="icon-field goal-icon-field">
            <label>
              <span>图标</span>
              <input name="icon" value="${escapeHtml(goalIcon)}" readonly data-action="open-goal-icon-picker" />
            </label>
            <button class="icon-select-btn" type="button" data-action="open-goal-icon-picker" aria-label="选择成长目标图标" style="--reward-color:${escapeHtml(goalColor)}">${escapeHtml(goalIcon)}</button>
            <div class="icon-popover hidden">
              ${goalIconOptions.map((icon) => `
                <button class="${String(goalIcon) === icon ? 'active' : ''}" type="button" data-action="select-goal-icon" data-goal-icon="${escapeHtml(icon)}">${escapeHtml(icon)}</button>
              `).join('')}
            </div>
          </div>
          <label><span>目标颜色</span><input type="color" name="goalColor" value="${escapeHtml(goal.goalColor || '#6c63ff')}" /></label>
          <label><span>目标积分</span><input type="number" min="0" name="targetPoints" value="${escapeHtml(goal.targetPoints || 0)}" /></label>
          ${renderChineseDatePicker('startDate', goal.startDate || '', { label: '开始日期', placeholder: '请选择开始日期', ariaLabel: '选择开始日期' })}
          ${renderChineseDatePicker('endDate', goal.endDate || '', { label: '结束日期', placeholder: '请选择结束日期', ariaLabel: '选择结束日期' })}
          <label><span>排序</span><input type="number" min="0" name="sortNo" value="${escapeHtml(goal.sortNo || 0)}" /></label>
          <label class="wide"><span>说明</span><input name="description" value="${escapeHtml(goal.description || '')}" placeholder="目标说明" /></label>
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
          <div><h2>${reward.id ? '修改奖励' : '新增奖励'}</h2><p>设置兑换所需货币、库存和颜色。</p></div>
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
          <label><span>货币类型</span>${renderSelect('requiredPointType', reward.requiredPointType || 'STAR', pointTypeOptions)}</label>
          <label><span>所需数量</span><input type="number" min="1" name="requiredPoints" value="${escapeHtml(reward.requiredPoints || 1)}" /></label>
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
        ${renderRewardExchangeOverview(reward, balances, requiredCurrency, currencies, options)}
        <div class="reward-payment-options">
          ${options.map((option) => {
            const meta = currencyMeta(option.pointType, currencies)
            const changeText = option.changeAmount > 0
              ? `找零 ${option.changeAmount} × ${requiredCurrency.icon}`
              : '直接兑换'
            const disabledText = `余额 ${option.balance || 0} × ${meta.icon}`
            return `
              <button class="payment-option ${option.enough ? '' : 'disabled'}" type="button" data-action="${option.enough ? 'select-reward-payment' : 'show-reward-payment-hint'}" data-payment-point-type="${option.pointType}" style="--point-color:${escapeHtml(meta.color)}">
                <small>使用</small>
                ${renderCurrencyAmount(meta, option.payAmount, 'payment-amount')}
                <em>${option.enough ? '兑换' : '不可兑换'}</em>
                <small>${escapeHtml(option.enough ? changeText : disabledText)}</small>
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
          <div><h2>${currency.id ? '修改币值' : '新增币值'}</h2><p>配置宝贝看到的货币图标、颜色和兑换比例。</p></div>
          <button class="icon-btn" type="button" data-action="close-currency-modal">×</button>
        </div>
        <form class="modal-form" data-form="point-currency">
          <input type="hidden" name="currencyId" value="${escapeHtml(currency.id || '')}" />
          <label><span>名称</span><input name="name" value="${escapeHtml(currency.name || defaults.name)}" placeholder="例如：星星" /></label>
          <label><span>货币类型</span>${renderSelect('pointType', pointType, pointTypeOptions)}</label>
          <div class="icon-field wide">
            <label>
              <span>图标</span>
              <input name="icon" value="${escapeHtml(icon)}" readonly data-action="open-currency-icon-picker" />
            </label>
            <button class="icon-select-btn" type="button" data-action="open-currency-icon-picker" aria-label="选择币值图标" style="--reward-color:${escapeHtml(currency.color || defaults.color)}">${escapeHtml(icon)}</button>
            <div class="icon-popover hidden">
              ${currencyIconOptions.map((option) => `
                <button class="${String(icon) === option ? 'active' : ''}" type="button" data-action="select-currency-icon" data-currency-icon="${escapeHtml(option)}">${escapeHtml(option)}</button>
              `).join('')}
            </div>
          </div>
          <label><span>颜色</span><input type="color" name="color" value="${escapeHtml(currency.color || defaults.color)}" /></label>
          <label><span>比例</span><input type="number" min="1" name="exchangeWeight" value="${escapeHtml(currency.exchangeWeight || defaults.exchangeWeight)}" /></label>
          <label><span>状态</span>${renderSelect('status', currency.status || 'ACTIVE', [['ACTIVE', '启用'], ['INACTIVE', '停用']])}</label>
          <label><span>排序</span><input type="number" min="0" name="sortNo" value="${escapeHtml(currency.sortNo ?? defaults.sortNo)}" /></label>
          <div class="currency-modal-preview wide">
            <span class="currency-mini large" data-currency-preview-icon style="--point-color:${escapeHtml(currency.color || defaults.color)}">${escapeHtml(icon)}</span>
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
          <label><span>货币类型</span>${renderSelect('pointType', 'STAR', pointTypeOptions)}</label>
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
      ? `${rewardMainFlowName(event.status, event.fulfillmentStatus)} · ${branchStatusName(event.branchStatus)}`
      : rewardMainFlowName(event.status, event.fulfillmentStatus)
  }
  if (event.status === 'APPROVED') {
    return `已完成 +${event.scoreAwarded || event.basePoints || 0} ${pointName(event.pointType)}`
  }
  if (event.status === 'SUBMITTED') return '待审核'
  if (event.status === 'REJECTED') return '未通过'
  return '待打卡'
}

function filterCalendarEventsByKind(state, events) {
  if (state.currentView === 'reward-calendar') {
    return events.filter((event) => event.kind === 'rewards')
  }
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
        balance: balanceMap.get(pointType) || 0,
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

function pointLevelName(pointType) {
  const names = {
    STAR: '1级',
    FLOWER: '2级',
    CROWN: '3级',
  }
  return names[pointType] || '1级'
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
