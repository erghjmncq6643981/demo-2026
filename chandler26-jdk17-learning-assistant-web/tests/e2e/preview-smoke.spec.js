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
