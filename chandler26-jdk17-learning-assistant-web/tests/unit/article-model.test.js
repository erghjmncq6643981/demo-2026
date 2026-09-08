import { describe, expect, it } from 'vitest'
import {
  articleErrorSuggestion,
  normalizeArticleStage,
  scoreArticlePractice,
  splitArticleLines,
} from '../../public/src/features/reading/article-model.js'

describe('语境精读规则', () => {
  it('按题数计算阅读检测得分', () => {
    const practice = [
      { correct_answer: 'Airport' },
      { correct_answer: 'Ticket' },
      { correct_answer: 'Gate' },
    ]
    expect(scoreArticlePractice(practice, [' airport ', 'wrong', 'GATE']))
      .toEqual({ total: 3, correct: 2, score: 67 })
  })

  it('不支持的阅读阶段回退到通读', () => {
    expect(normalizeArticleStage('unknown')).toBe('reading')
  })

  it('中英文都能稳定分句', () => {
    expect(splitArticleLines('First sentence. Second sentence!', 'en')).toHaveLength(2)
    expect(splitArticleLines('第一句。第二句！', 'zh')).toHaveLength(2)
  })

  it('模型余额错误给出可执行建议', () => {
    expect(articleErrorSuggestion('AI_MODEL_BALANCE_INSUFFICIENT', 400)).toContain('余额')
  })

  it('预览材料应包含顶层文章标题以支持历史摘要渲染', async () => {
    const { buildPreviewArticleRecord } = await import('../../public/src/features/reading/article-render.js')
    const preview = buildPreviewArticleRecord({ articleEntries: [], selectedArticleEntryIds: [], wordbookEntries: [] })
    expect(preview.title).toBe('A Choice That Changed the Plan')
    expect(preview.parsed.title).toBe('A Choice That Changed the Plan')
  })
})
