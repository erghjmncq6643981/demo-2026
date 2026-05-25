import { formatDate } from '/src/shared/text.js'

const today = new Date()

export const demoState = {
  offline: true,
  user: {
    id: 1,
    nickname: '演示家长',
    username: 'demo',
  },
  children: [
    {
      id: 101,
      nickname: '小星',
      gender: 'UNKNOWN',
      remark: '喜欢明亮颜色和皇冠任务',
    },
  ],
  goals: [
    {
      id: 201,
      childId: 101,
      name: '自主管理小达人',
      goalColor: '#6c63ff',
      icon: '★',
      targetPoints: 220,
      status: 'ACTIVE',
    },
    {
      id: 202,
      childId: 101,
      name: '健康运动计划',
      goalColor: '#22c55e',
      icon: '✦',
      targetPoints: 120,
      status: 'ACTIVE',
    },
  ],
  tasks: [
    {
      id: 301,
      childId: 101,
      goalId: 201,
      name: '晨读 20 分钟',
      description: '按时完成 +8 星星，连续完成可以额外奖励。',
      periodType: 'DAILY',
      scheduleJson: '{"type":"DAILY","category":"STUDY","timeRange":{"startHour":7,"endHour":9},"requiredCount":1}',
      taskColor: '#ff5c8a',
      pointType: 'STAR',
      pointColor: '#ffd84d',
      basePoints: 8,
      requireApproval: 0,
      status: 'ACTIVE',
    },
    {
      id: 302,
      childId: 101,
      goalId: 201,
      name: '整理书包',
      description: '晚饭前完成 +5 红花，需要家长确认。',
      periodType: 'DAILY',
      scheduleJson: '{"type":"DAILY","category":"LIFE","timeRange":{"startHour":18,"endHour":21},"requiredCount":1}',
      taskColor: '#34c759',
      pointType: 'FLOWER',
      pointColor: '#ff6fa6',
      basePoints: 5,
      requireApproval: 1,
      status: 'ACTIVE',
    },
    {
      id: 303,
      childId: 101,
      goalId: 202,
      name: '练字作业',
      description: '周一、周四、周五可练，完成 2 次即可达标。',
      periodType: 'WEEKLY',
      scheduleJson: '{"type":"WEEKLY","category":"STUDY","days":[1,4,5],"requiredCount":2}',
      taskColor: '#6c63ff',
      pointType: 'CROWN',
      pointColor: '#8b5cf6',
      basePoints: 15,
      requireApproval: 1,
      status: 'ACTIVE',
    },
    {
      id: 304,
      childId: 101,
      goalId: 201,
      name: '月初整理书桌',
      description: '每月 1 日或 15 日完成 1 次即可。',
      periodType: 'MONTHLY',
      scheduleJson: '{"type":"MONTHLY","category":"LIFE","days":[1,15],"requiredCount":1}',
      taskColor: '#ff9f43',
      pointType: 'STAR',
      pointColor: '#30d5ff',
      basePoints: 12,
      requireApproval: 0,
      status: 'ACTIVE',
    },
  ],
  balances: [
    { pointType: 'STAR', balance: 128, earnedTotal: 188, spentTotal: 60 },
    { pointType: 'FLOWER', balance: 42, earnedTotal: 42, spentTotal: 0 },
    { pointType: 'CROWN', balance: 3, earnedTotal: 4, spentTotal: 1 },
  ],
  rewards: [
    {
      id: 401,
      childId: 101,
      name: '积木礼物',
      description: '完成一周自主管理后兑换。',
      rewardIcon: '🎁',
      rewardColor: '#ff9f43',
      requiredPointType: 'STAR',
      requiredPoints: 80,
      stockRemaining: 3,
      requireApproval: 1,
      status: 'ACTIVE',
    },
    {
      id: 402,
      childId: 101,
      name: '周末冰淇淋',
      description: '每周限兑一次的小甜点。',
      rewardIcon: '🍦',
      rewardColor: '#34c759',
      requiredPointType: 'FLOWER',
      requiredPoints: 40,
      stockRemaining: 10,
      requireApproval: 0,
      status: 'ACTIVE',
    },
    {
      id: 403,
      childId: 101,
      name: '皇冠特权',
      description: '亲子游戏时间 30 分钟。',
      rewardIcon: '♛',
      rewardColor: '#6c63ff',
      requiredPointType: 'CROWN',
      requiredPoints: 1,
      stockRemaining: 0,
      requireApproval: 1,
      status: 'ACTIVE',
    },
  ],
  ledger: [
    { id: 501, pointType: 'STAR', changeAmount: 8, sourceName: '晨读 20 分钟', reason: '任务完成入账', eventTime: `${formatDate(today)}T08:10:00` },
    { id: 502, pointType: 'FLOWER', changeAmount: 5, sourceName: '整理书包', reason: '家长审核通过', eventTime: `${formatDate(today)}T19:30:00` },
    { id: 503, pointType: 'STAR', changeAmount: -80, sourceName: '积木礼物', reason: '奖励兑换扣减', eventTime: `${formatDate(today)}T20:00:00` },
  ],
  exchanges: [
    { id: 601, rewardNameSnapshot: '积木礼物', requiredPointType: 'STAR', requiredPointsSnapshot: 80, status: 'REQUESTED', requestedAt: `${formatDate(today)}T20:00:00` },
  ],
  calendarEvents: [],
}

export function buildDemoCalendar(tasks, monthDate) {
  const events = []
  const year = monthDate.getFullYear()
  const month = monthDate.getMonth()
  const days = new Date(year, month + 1, 0).getDate()
  for (let day = 1; day <= days; day += 1) {
    const date = new Date(year, month, day)
    tasks.forEach((task) => {
      const schedule = parseSchedule(task.scheduleJson)
      const isWeekly = task.periodType === 'WEEKLY'
      const isMonthly = task.periodType === 'MONTHLY'
      const dayOfWeek = date.getDay() === 0 ? 7 : date.getDay()
      if (isWeekly && !(schedule.days || [1]).includes(dayOfWeek)) {
        return
      }
      if (isMonthly && !(schedule.days || [1]).includes(day)) {
        return
      }
      const taskDate = formatDate(date)
      const approved = day < today.getDate() && task.periodType === 'DAILY'
      events.push({
        recordId: approved ? Number(`${task.id}${day}`) : null,
        taskId: task.id,
        goalId: task.goalId,
        childId: task.childId,
        taskDate,
        taskName: task.name,
        taskColor: task.taskColor,
        pointType: task.pointType,
        pointColor: task.pointColor,
        basePoints: task.basePoints,
        periodType: task.periodType,
        scheduleJson: task.scheduleJson,
        completionProgress: approved ? 100 : 0,
        status: approved ? 'APPROVED' : 'PENDING',
        scoreAwarded: approved ? task.basePoints : 0,
        persisted: approved,
      })
    })
  }
  return events
}

function parseSchedule(scheduleJson) {
  try {
    return JSON.parse(scheduleJson || '{}')
  } catch {
    return {}
  }
}
