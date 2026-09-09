import { expect, test } from '@playwright/test'

test('preview loads without horizontal overflow', async ({ page }) => {
  const errors = []
  page.on('pageerror', (error) => errors.push(error.message))
  await page.goto('/?preview=1')
  await expect(page.locator('body')).toBeVisible()
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true)
  expect(errors).toEqual([])
})

test('preview keeps the learning flow usable from navigation to challenge', async ({ page }) => {
  await page.goto('/?preview=1')
  if ((page.viewportSize()?.width || 1000) < 800) {
    await page.getByRole('button', { name: '显示导航' }).click()
  }
  await page.getByRole('button', { name: '词汇大挑战 计划、日历与词汇挑战', exact: true }).click()
  await expect(page.locator('#scenePlanView')).toHaveClass(/active/)
  await expect(page.locator('#sceneCalendar')).toBeVisible()
  await page.locator('[data-calendar-range="month"]').click()
  await expect(page.locator('[data-calendar-range="month"]')).toHaveClass(/active/)

  await page.locator('#sceneStartLearningBtn').click()
  await expect(page.locator('#sceneLearningStage')).toBeVisible()
  await page.getByRole('button', { name: '词汇挑战', exact: true }).click()
  await expect(page.locator('#sceneChallengeStage')).toBeVisible()
  await expect(page.locator('#sceneChallengeWords')).toContainText('本轮词数')
})

test('modals close smoothly when pressing Escape', async ({ page }) => {
  await page.goto('/?preview=1')
  if ((page.viewportSize()?.width || 1000) < 800) {
    await page.getByRole('button', { name: '显示导航' }).click()
  }

  // Navigate to scenePlanView
  await page.getByRole('button', { name: '词汇大挑战 计划、日历与词汇挑战', exact: true }).click()
  await expect(page.locator('#scenePlanView')).toHaveClass(/active/)
  await page.locator('#sceneStartLearningBtn').click()
  await expect(page.locator('#sceneLearningStage')).toBeVisible()

  // 1. Core words modal (待挑战核心词汇)
  await page.locator('#sceneOpenCoreWordsBtn').click()
  await expect(page.locator('#sceneCoreWordsModal')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('#sceneCoreWordsModal')).toBeHidden()

  // 2. Related words modal (场景相关词汇)
  await page.locator('#sceneOpenRelatedWordsBtn').click()
  await expect(page.locator('#sceneRelatedWordsModal')).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.locator('#sceneRelatedWordsModal')).toBeHidden()
})

test('admin public vocabulary management displays public catalogs', async ({ page }) => {
  await page.goto('/?preview=1')
  if ((page.viewportSize()?.width || 1000) < 800) {
    await page.getByRole('button', { name: '显示导航' }).click()
  }

  // Navigate to systemAdminView
  await page.getByRole('button', { name: '系统管理 用户、词本与 AI 治理', exact: true }).click()
  await expect(page.locator('#systemAdminView')).toHaveClass(/active/)

  // Click on "公共词本" tab
  await page.locator('[data-system-tab="adminVocabularyPanel"]').click()
  await expect(page.locator('[data-system-tab="adminVocabularyPanel"]')).toHaveClass(/active/)
  await expect(page.locator('#adminVocabularyPanel')).toHaveClass(/active/)

  // Check that public wordbook cards are visible in #sceneImportList
  await expect(page.locator('#sceneImportList')).not.toHaveClass(/empty/)
  await expect(page.locator('#sceneImportList .scene-import-card')).toHaveCount(2)
  await expect(page.locator('#sceneImportList .scene-import-card').first()).toBeVisible()
  await expect(page.locator('#sceneImportList')).toContainText('自考英语（二）全部词汇')
  await expect(page.locator('#vocabularyImportHistoryPageInfo')).toContainText('第 1 / 1 页')
})
