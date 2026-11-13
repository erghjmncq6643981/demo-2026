/**
 * 跨平台快捷键文案格式化与页面上下文帮助清单。
 */

export function isMacPlatform(nav = typeof navigator !== 'undefined' ? navigator : null) {
  const raw = String(nav?.userAgentData?.platform || nav?.platform || nav?.userAgent || '')
  return /mac|iphone|ipad|ipod/i.test(raw)
}

export function getModKeyLabel(mac = isMacPlatform()) {
  return mac ? '⌘' : 'Ctrl+'
}

export function formatShortcutCombo(combo, mac = isMacPlatform()) {
  const mod = getModKeyLabel(mac)
  return String(combo || '')
    .replace(/\{MOD\}/g, mod)
    .replace(/⌘/g, mac ? '⌘' : 'Ctrl+')
    .replace(/⌥/g, mac ? '⌥' : 'Alt+')
    .replace(/Alt\+/g, mac ? '⌥' : 'Alt+')
}

export function applyPlatformShortcutHints(root = typeof document !== 'undefined' ? document : null) {
  if (!root || typeof root.querySelectorAll !== 'function') return
  const mac = isMacPlatform()
  root.querySelectorAll('[data-shortcut-template]').forEach((el) => {
    const tpl = el.getAttribute('data-shortcut-template') || ''
    el.textContent = formatShortcutCombo(tpl, mac)
  })
  root.querySelectorAll('[data-shortcut-title]').forEach((el) => {
    const tpl = el.getAttribute('data-shortcut-title') || ''
    const resolved = formatShortcutCombo(tpl, mac)
    el.setAttribute('title', resolved)
    const noteText = el.getAttribute('data-shortcut-label')
    if (noteText) {
      el.textContent = noteText.replace(/\{SHORTCUT\}/g, resolved)
    }
  })
}

const GLOBAL_SHORTCUTS = [
  { keys: '{MOD}K', desc: '打开 / 关闭快速查词弹窗（任意页面可用）' },
  { keys: '?', desc: '打开当前页面快捷键帮助' },
  { keys: 'Esc', desc: '关闭最顶层弹窗、收起右键菜单或取消当前编辑态' },
  { keys: 'Tab / Shift+Tab', desc: '在弹窗内部循环切换焦点；关闭弹窗后焦点回到原入口' },
  { keys: 'Enter / Space', desc: '在按钮、标签、链接获焦时执行当前控件操作' },
]

const VIEW_SHORTCUT_GROUPS = {
  reviewView: {
    title: '复习计划（专注复习）',
    items: [
      { keys: 'A–Z / Backspace', desc: '第 1 步明文跟敲；完成后进入第 2 步遮挡默写' },
      { keys: 'Tab', desc: '第 2 步遮挡默写时临时偷看拼写提示（1.2 秒后自动恢复）' },
      { keys: 'Esc', desc: '清空当前输入；第 2 步未输入时退回第 1 步跟敲' },
      { keys: '{MOD}E', desc: '打开 / 关闭复习笔记并切换编辑与预览' },
      { keys: '1 / 2 / 3（结算弹窗）', desc: '分别提交：忘记了 / 有点模糊 / 记住了（P 播放例句）' },
      { keys: '← / ↓ / →（结算弹窗）', desc: '未聚焦具体按钮时快捷选择：忘记 / 模糊 / 记住；Tab 到按钮按 Enter/空格执行对应按钮' },
    ],
  },
  studyView: {
    title: '卡片学习',
    items: [
      { keys: 'T / Alt+T', desc: '开启当前单词的小测验跟敲' },
      { keys: '{MOD}E', desc: '打开 / 切换右侧学习笔记抽屉' },
    ],
  },
  wordbookView: {
    title: '单词本',
    items: [
      { keys: 'Space（未聚焦按钮时）', desc: '播放 / 暂停当前单词本音频' },
      { keys: 'J / K / Esc（播放条激活）', desc: '上一词 / 下一词 / 停止播放' },
      { keys: '{MOD}E（卡片弹窗内）', desc: '编辑当前词条笔记' },
      { keys: '← / →（卡片弹窗内）', desc: '切换上一张 / 下一张单词卡' },
    ],
  },
  scenePlanView: {
    title: '词汇大挑战',
    items: [
      { keys: '{MOD}E', desc: '展开场景笔记或切换编辑 / 预览' },
      { keys: 'Alt+P（评估跟敲时）', desc: '朗读当前目标单词' },
    ],
  },
  ruminationView: {
    title: '知识反刍（星系图谱 & 跟敲弹窗）',
    items: [
      { keys: '{MOD}S', desc: '手动立即保存星系图谱布局与词袋' },
      { keys: '↑ ↓ ← →（画布获焦）', desc: '按空间方向在星系节点间移动焦点（无需右键）' },
      { keys: 'Space / E / A / Enter（画布获焦）', desc: '当前节点加入词袋 / 修改释义 / 新增关联 / 衍生裂变' },
      { keys: 'M（画布获焦）', desc: '打开选中节点操作菜单；Delete 移除非核心节点；0 重置视图' },
      { keys: 'Tab / Enter（跟敲弹窗内）', desc: '第 1 步回车对齐跟敲；第 2 步 Tab 偷看原句，回车提交并喝彩完成' },
    ],
  },
}
VIEW_SHORTCUT_GROUPS.articleStudyView = VIEW_SHORTCUT_GROUPS.ruminationView

export function getShortcutSections(activeView = 'profileView') {
  const mac = isMacPlatform()
  const sections = []
  const current = VIEW_SHORTCUT_GROUPS[activeView]
  if (current) {
    sections.push({
      title: `当前页面 · ${current.title}`,
      items: current.items.map((item) => ({
        keys: formatShortcutCombo(item.keys, mac),
        desc: item.desc,
      })),
    })
  }
  sections.push({
    title: '全局与无障碍导航',
    items: GLOBAL_SHORTCUTS.map((item) => ({
      keys: formatShortcutCombo(item.keys, mac),
      desc: item.desc,
    })),
  })
  return sections
}
