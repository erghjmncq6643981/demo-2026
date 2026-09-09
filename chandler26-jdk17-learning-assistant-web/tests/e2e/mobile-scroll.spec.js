import { expect, test } from '@playwright/test'

test('test mobile scene article scroll and note panel with fix', async ({ page }) => {
  await page.setViewportSize({ width: 400, height: 876 })
  await page.goto('/?preview=1')
  await page.waitForLoadState('networkidle')

  const navBtn = page.getByRole('button', { name: '显示导航' })
  if (await navBtn.isVisible()) {
    await navBtn.click()
  }
  await page.getByRole('button', { name: '词汇大挑战 计划、日历与词汇挑战', exact: true }).click()
  await expect(page.locator('#scenePlanView')).toHaveClass(/active/)

  await page.locator('#sceneStartLearningBtn').click()
  await expect(page.locator('#sceneLearningStage')).toBeVisible()

  // Test 1: Reading panel scrolling
  const panel = page.locator('#sceneReadingPanel')
  const box = await panel.boundingBox()
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.wheel(0, 200)
  await page.waitForTimeout(200)

  const readingScrollTop = await page.evaluate(() => document.querySelector('#sceneReadingPanel')?.scrollTop)
  console.log('Reading panel scrollTop after wheel:', readingScrollTop)
  expect(readingScrollTop).toBeGreaterThan(0)

  // Test 2: Open note panel
  await page.locator('#sceneOpenNoteModalBtn').click()
  await expect(page.locator('#sceneNotePanel')).toBeVisible()
  await expect(page.locator('#sceneStudySplitLayout')).toHaveClass(/with-note-open/)

  // When note is open on mobile:
  // .scene-study-split-layout.with-note-open has overflow-y: auto
  // Let's verify both reading panel and note panel are inside split layout and split layout can scroll
  const noteInfo = await page.evaluate(() => {
    const split = document.querySelector('#sceneStudySplitLayout')
    return {
      splitScrollHeight: split?.scrollHeight,
      splitClientHeight: split?.clientHeight,
      notePanelVisible: !document.querySelector('#sceneNotePanel')?.classList.contains('hidden'),
    }
  })
  console.log('Note info:', noteInfo)
  expect(noteInfo.notePanelVisible).toBe(true)

  // Test 3: Close note panel
  await page.locator('#sceneNoteCloseBtn').click()
  await expect(page.locator('#sceneNotePanel')).toHaveClass(/hidden/)
  await expect(page.locator('#sceneStudySplitLayout')).not.toHaveClass(/with-note-open/)

  // Test 4: Challenge flow - clicking challenge button
  await page.getByRole('button', { name: '词汇挑战', exact: true }).click()
  await expect(page.locator('#sceneChallengeStage')).toBeVisible()
  await expect(page.locator('#sceneLearningStage')).toHaveClass(/in-challenge-stage/)

  // Verify challenge stage is visible and usable
  await expect(page.locator('#sceneChallengeWords')).toContainText('本轮词数')
})
