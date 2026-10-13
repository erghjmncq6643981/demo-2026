import { describe, expect, it } from 'vitest'
import { localDateKey, number } from '../../public/src/features/learning/scene-plan/model.js'
import { createUnitList } from '../../public/src/features/learning/scene-plan/unit-list.js'
import { parsePreviewMarkdown, suggestedSplitCorrection } from '../../public/src/features/learning/scene-plan/markdown-parser.js'
import { calendarDates, startOfWeek, isPlanEnded, resolvePlanEndDate, resolvePlanAnchorDate } from '../../public/src/features/learning/scene-plan/calendar-model.js'
import { createCalendarView } from '../../public/src/features/learning/scene-plan/calendar-view.js'
import { isWordComplete, nextAssessment, pendingChallengeWords, pendingChallengeCount } from '../../public/src/features/learning/scene-plan/challenge-model.js'

describe('scene plan model', () => {
  it('formats dates using the local calendar date', () => {
    expect(localDateKey(new Date(2026, 7, 20, 23, 30))).toBe('2026-08-20')
  })

  it('normalizes invalid business numbers to zero', () => {
    expect(number('12')).toBe(12)
    expect(number('not-a-number')).toBe(0)
  })
})

describe('Markdown vocabulary parser', () => {
  it('keeps one sequence column and detects suspicious split words', () => {
    const items = parsePreviewMarkdown(`
| 序号 | Word | 音标 | 释义 |
| --- | --- | --- | --- |
| 1 | blanke t | /ˈblæŋkɪt/ | 毯子 |
| 2 | city | /ˈsɪti/ | 城市 |
`)

    expect(items).toHaveLength(2)
    expect(items[0]).toMatchObject({ sourceOrder: 1, originalTerm: 'blanke t', suggestedTerm: 'blanket', suspicious: true })
    expect(items[1]).toMatchObject({ sourceOrder: 2, originalTerm: 'city', suspicious: false })
  })

  it('rejects duplicate source order', () => {
    expect(() => parsePreviewMarkdown(`
| 序号 | Word | 音标 | 释义 |
| --- | --- | --- | --- |
| 1 | city | /ˈsɪti/ | 城市 |
| 1 | town | /taʊn/ | 城镇 |
`)).toThrow('词表序号重复：1')
  })

  it('does not merge valid standalone articles', () => {
    expect(suggestedSplitCorrection('a blanket')).toBe('a blanket')
  })
})

describe('场景挑战规则', () => {
  it('会拼写词需按顺序完成三种检查', () => {
    const word = { masteryRequirement: 'spelling', passedAssessments: ['meaning_choice'] }
    expect(nextAssessment(word)).toBe('copy_typing')
    word.passedAssessments.push('copy_typing', 'meaning_spelling')
    expect(isWordComplete(word)).toBe(true)
  })

  it('待挑战词只包含未完成的核心词', () => {
    const unit = {
      words: [
        { term: 'one', tier: 'core', masteryRequirement: 'recognition', passedAssessments: [] },
        { term: 'two', tier: 'core', masteryRequirement: 'recognition', passedAssessments: ['meaning_choice'] },
        { term: 'three', tier: 'extended', masteryRequirement: 'recognition', passedAssessments: [] },
      ],
    }
    expect(pendingChallengeWords(unit).map((word) => word.term)).toEqual(['one'])
  })

  it('日历摘要没有词面时使用服务端待挑战数量', () => {
    expect(pendingChallengeCount({ coreWordCount: 40, completedCoreCount: 12, pendingChallengeCount: 28 })).toBe(28)
  })
})

describe('学习日历规则', () => {
  it('周视角始终从周一开始', () => {
    expect(startOfWeek(new Date(2026, 7, 23, 12)).getDay()).toBe(1)
    expect(calendarDates('week', '2026-08-23')).toHaveLength(7)
  })

  it('月视角给出稳定的六周网格', () => {
    expect(calendarDates('month', '2026-08-17')).toHaveLength(42)
  })

  it('合并日历数据时保留已加载的词汇详情，不被概要中的 null 覆盖', () => {
    const unitList = createUnitList({
      state: {},
      api: {},
      sameId: (a, b) => String(a) === String(b),
    })

    const plan = {
      id: '100',
      units: [
        {
          id: '200',
          unitNo: 1,
          status: 'ready',
          words: [{ id: '1', term: 'apple', tier: 'core' }],
          relatedWords: [{ id: '10', term: 'fruit' }],
          learningText: 'This is an apple.',
        },
      ],
    }

    const calendarData = [
      {
        date: '2026-08-31',
        units: [
          {
            id: '200',
            unitNo: 1,
            status: 'ready',
            coreWordCount: 1,
            completedCoreCount: 0,
            words: null,
            relatedWords: null,
            learningText: null,
          },
        ],
      },
    ]

    unitList.mergeCalendarUnits(plan, calendarData)

    expect(plan.units[0].words).toHaveLength(1)
    expect(plan.units[0].words[0].term).toBe('apple')
    expect(plan.units[0].relatedWords).toHaveLength(1)
    expect(plan.units[0].relatedWords[0].term).toBe('fruit')
    expect(plan.units[0].learningText).toBe('This is an apple.')
    expect(plan.units[0].coreWordCount).toBe(1)
  })

  it('判断计划是否已结束（completed 或 cancelled）', () => {
    expect(isPlanEnded({ status: 'completed' })).toBe(true)
    expect(isPlanEnded({ status: 'cancelled' })).toBe(true)
    expect(isPlanEnded({ status: 'active' })).toBe(false)
    expect(isPlanEnded({ status: 'paused' })).toBe(false)
    expect(isPlanEnded({ status: 'not_started' })).toBe(false)
    expect(isPlanEnded(null)).toBe(false)
  })

  it('解析已结束计划的最后一天（优先取单元中最大日期，其次 endTime）', () => {
    const planWithUnits = {
      status: 'completed',
      startTime: '2026-08-01T09:00:00',
      endTime: '2026-08-31T23:59:59',
      units: [
        { id: 1, recommendedDate: '2026-08-10' },
        { id: 2, recommendedDate: '2026-08-25' },
        { id: 3, recommendedDate: '2026-08-18' },
      ],
    }
    expect(resolvePlanEndDate(planWithUnits)).toBe('2026-08-25')

    const planNoUnits = {
      status: 'completed',
      startTime: '2026-08-01T09:00:00',
      endTime: '2026-08-31T23:59:59',
      units: [],
    }
    expect(resolvePlanEndDate(planNoUnits)).toBe('2026-08-31')
  })

  it('已结束计划的锚点日期固定为最后一天，不随自然日变化', () => {
    const completedPlan = {
      status: 'completed',
      startTime: '2026-08-01T09:00:00',
      endTime: '2026-08-20T22:00:00',
      units: [{ id: 1, recommendedDate: '2026-08-20' }],
    }
    // 无论当前是哪一天，已完成计划的锚点始终是 2026-08-20
    expect(resolvePlanAnchorDate(completedPlan)).toBe('2026-08-20')

    const cancelledPlan = {
      status: 'cancelled',
      endTime: '2026-07-15T12:00:00',
      units: [],
    }
    expect(resolvePlanAnchorDate(cancelledPlan)).toBe('2026-07-15')
  })

  it('进行中的计划在计划期内默认锚定为今天', () => {
    const today = localDateKey()
    const activePlan = {
      status: 'active',
      startTime: '2020-01-01T00:00:00',
      endTime: '2030-12-31T23:59:59',
    }
    expect(resolvePlanAnchorDate(activePlan)).toBe(today)
  })

  it('日历视图重置时，已完成计划重置到最后一天而非自然日', () => {
    const completedPlan = {
      status: 'completed',
      endTime: '2026-08-25T18:00:00',
      units: [{ id: 1, recommendedDate: '2026-08-25' }],
    }
    const state = {
      currentLearningPlan: completedPlan,
      sceneCalendarRange: 'week',
      sceneCalendarCursorDate: null,
      sceneCalendarData: null,
    }
    let rendered = false
    let refreshed = false
    const view = createCalendarView({
      state,
      render: () => { rendered = true },
      refresh: () => { refreshed = true },
    })

    view.reset()
    expect(state.sceneCalendarCursorDate).toBe('2026-08-25')
    expect(rendered).toBe(true)
    expect(refreshed).toBe(true)
  })
})
