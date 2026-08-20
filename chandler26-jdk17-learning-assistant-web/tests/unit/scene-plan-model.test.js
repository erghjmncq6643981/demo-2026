import { describe, expect, it } from 'vitest'
import { localDateKey, number } from '../../public/src/features/learning/scene-plan/model.js'
import { parsePreviewMarkdown, suggestedSplitCorrection } from '../../public/src/features/learning/scene-plan/markdown-parser.js'
import { calendarDates, startOfWeek } from '../../public/src/features/learning/scene-plan/calendar-model.js'
import { isWordComplete, nextAssessment, pendingChallengeWords } from '../../public/src/features/learning/scene-plan/challenge-model.js'

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
})

describe('学习日历规则', () => {
  it('周视角始终从周一开始', () => {
    expect(startOfWeek(new Date(2026, 7, 23, 12)).getDay()).toBe(1)
    expect(calendarDates('week', '2026-08-23')).toHaveLength(7)
  })

  it('月视角给出稳定的六周网格', () => {
    expect(calendarDates('month', '2026-08-17')).toHaveLength(42)
  })
})
