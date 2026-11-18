import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createActivityProfileFeature } from '../../public/src/features/identity/activity.js'
import { createPreviewActivity } from '../../public/src/shared/vocabulary.js'

describe('Learning Activity Heatmap Enhancements', () => {
  let root
  let elements
  let state
  let mockRequest

  beforeEach(() => {
    root = document.createElement('div')
    root.innerHTML = `
      <div class="activity-section">
        <div class="activity-range-tabs" id="activityRangeTabs">
          <button type="button" class="activity-range-tab" data-days="90" aria-selected="false">近 90 天</button>
          <button type="button" class="activity-range-tab" data-days="180" aria-selected="false">近半年</button>
          <button type="button" class="activity-range-tab active" data-days="365" aria-selected="true">近 1 年</button>
        </div>
        <div class="activity-stats-bar">
          <span id="activityTotalPill"></span>
          <span id="activityActiveDaysPill"></span>
          <span id="activityStreakPill"></span>
          <span id="activitySummary"></span>
        </div>
        <div id="activityHeatmapStage" class="activity-heatmap-stage">
          <div id="activityHeatmap" class="activity-heatmap empty"></div>
          <div id="activityTooltip" class="activity-tooltip hidden" aria-hidden="true"></div>
        </div>
        <span id="activitySelectedHint"></span>
      </div>
    `
    document.body.innerHTML = ''
    document.body.appendChild(root)

    elements = {
      activityRangeTabs: root.querySelector('#activityRangeTabs'),
      activityTotalPill: root.querySelector('#activityTotalPill'),
      activityActiveDaysPill: root.querySelector('#activityActiveDaysPill'),
      activityStreakPill: root.querySelector('#activityStreakPill'),
      activitySummary: root.querySelector('#activitySummary'),
      activityHeatmapStage: root.querySelector('#activityHeatmapStage'),
      activityHeatmap: root.querySelector('#activityHeatmap'),
      activityTooltip: root.querySelector('#activityTooltip'),
      activitySelectedHint: root.querySelector('#activitySelectedHint'),
    }

    state = {
      token: 'test-token',
      preview: false,
      activity: null,
      wordbooks: [],
      systemLogs: [],
    }

    mockRequest = vi.fn(async () => ({
      days: 90,
      learnedTotal: 12,
      wordAddedTotal: 4,
      reviewTotal: 20,
      sceneCompletedTotal: 3,
      articleCompletedTotal: 0,
      ruminationTotal: 15,
      totalActivityCount: 54,
      activeDays: 9,
      currentStreak: 4,
      longestStreak: 6,
      studySecondsTotal: 900,
      items: [
        { date: '2026-11-15', learnedCount: 2, wordAddedCount: 0, reviewCount: 4, sceneCompletedCount: 0, articleCompletedCount: 0, ruminationCount: 3, studySeconds: 185, totalCount: 9 },
        { date: '2026-11-16', learnedCount: 0, wordAddedCount: 0, reviewCount: 0, sceneCompletedCount: 0, articleCompletedCount: 0, ruminationCount: 0, studySeconds: 0, totalCount: 0 },
        { date: '2026-11-17', learnedCount: 5, wordAddedCount: 2, reviewCount: 6, sceneCompletedCount: 1, articleCompletedCount: 0, ruminationCount: 7, studySeconds: 420, totalCount: 21 },
        { date: '2026-11-18', learnedCount: 5, wordAddedCount: 2, reviewCount: 10, sceneCompletedCount: 2, articleCompletedCount: 0, ruminationCount: 5, studySeconds: 295, totalCount: 24 },
      ],
    }))
  })

  it('renders totals, active days, streak, and rumination breakdown consistently', async () => {
    const feature = createActivityProfileFeature({
      state,
      elements,
      request: mockRequest,
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmDelete: vi.fn(),
      createPreviewActivity,
    })

    await feature.loadActivity(365)

    expect(mockRequest).toHaveBeenCalledWith('/api/v1/learning/activity?days=365')
    expect(elements.activityTotalPill.textContent).toBe('共 54 次活跃')
    expect(elements.activityActiveDaysPill.textContent).toBe('活跃 9 天')
    expect(elements.activityStreakPill.textContent).toBe('连续 4 天')
    expect(elements.activityStreakPill.classList.contains('is-streak')).toBe(true)
    expect(elements.activityStreakPill.title).toContain('6 天')
    expect(elements.activitySummary.textContent).toBe('12 学习 / 20 复习 / 3 场景 / 15 反刍 / 4 加词')
  })

  it('switches date range tabs and refetches activity data', async () => {
    const feature = createActivityProfileFeature({
      state,
      elements,
      request: mockRequest,
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmDelete: vi.fn(),
      createPreviewActivity,
    })

    await feature.loadActivity(365)
    const tab90 = elements.activityRangeTabs.querySelector('[data-days="90"]')
    tab90.click()
    await new Promise((r) => setTimeout(r, 10))

    expect(mockRequest).toHaveBeenLastCalledWith('/api/v1/learning/activity?days=90')
    expect(tab90.classList.contains('active')).toBe(true)
    expect(tab90.getAttribute('aria-selected')).toBe('true')
  })

  it('uses quantile thresholds so single outlier spike does not flatten daily color levels', () => {
    const feature = createActivityProfileFeature({
      state,
      elements,
      request: mockRequest,
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmDelete: vi.fn(),
      createPreviewActivity,
    })

    const days = [
      { totalCount: 2 },
      { totalCount: 5 },
      { totalCount: 8 },
      { totalCount: 12 },
      { totalCount: 15 },
      { totalCount: 18 },
      { totalCount: 22 },
      { totalCount: 200 }, // single huge outlier
    ]
    const thresholds = feature.buildActivityThresholds(days)

    expect(feature.activityLevel(0, thresholds)).toBe(0)
    expect(feature.activityLevel(2, thresholds)).toBe(1)
    expect(feature.activityLevel(12, thresholds)).toBeGreaterThanOrEqual(2)
    expect(feature.activityLevel(18, thresholds)).toBeGreaterThanOrEqual(3)
    expect(feature.activityLevel(200, thresholds)).toBe(4)
  })

  it('shows non-zero breakdown and duration in custom tooltip on hover/click', async () => {
    const feature = createActivityProfileFeature({
      state,
      elements,
      request: mockRequest,
      toast: vi.fn(),
      logEvent: vi.fn(),
      confirmDelete: vi.fn(),
      createPreviewActivity,
    })

    await feature.loadActivity(365)
    const activeCell = elements.activityHeatmap.querySelector('.activity-day[data-date="2026-11-15"]')
    expect(activeCell).not.toBeNull()

    activeCell.click()

    expect(elements.activityTooltip.classList.contains('hidden')).toBe(false)
    expect(elements.activityTooltip.textContent).toContain('2026-11-15')
    expect(elements.activityTooltip.textContent).toContain('9 次活跃')
    expect(elements.activityTooltip.textContent).toContain('学习 2')
    expect(elements.activityTooltip.textContent).toContain('复习 4')
    expect(elements.activityTooltip.textContent).toContain('反刍 3')
    expect(elements.activityTooltip.textContent).not.toContain('精读')
    expect(elements.activityTooltip.textContent).not.toContain('场景')
    expect(elements.activityTooltip.textContent).toContain('3 分钟')
    expect(elements.activitySelectedHint.textContent).toContain('2026-11-15')
    expect(elements.activitySelectedHint.textContent).toContain('反刍 3')

    const emptyCell = elements.activityHeatmap.querySelector('.activity-day[data-date="2026-11-16"]')
    emptyCell.click()
    expect(elements.activityTooltip.textContent).toContain('当日暂无学习记录')
  })
})
