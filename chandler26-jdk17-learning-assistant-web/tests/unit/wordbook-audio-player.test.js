import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createWordbookAudioPlayer } from '../../public/src/features/vocabulary/wordbook-audio-player.js'

describe('WordbookAudioPlayer (Walkman audio loop & state management)', () => {
  let player
  let spokeTerms
  let spokeMeanings
  let stateChanges
  let elements

  beforeEach(() => {
    vi.useFakeTimers()
    spokeTerms = []
    spokeMeanings = []
    stateChanges = []

    document.body.innerHTML = `
      <table class="wordbook-table">
        <tbody>
          <tr class="wordbook-row" data-entry-id="1"><td>vigor</td></tr>
          <tr class="wordbook-row" data-entry-id="2"><td>abandon</td></tr>
          <tr class="wordbook-row" data-entry-id="3"><td>maintain</td></tr>
        </tbody>
      </table>
    `

    elements = {
      wordbookEntryList: document.querySelector('.wordbook-table tbody'),
    }

    player = createWordbookAudioPlayer({
      elements,
      speakTerm: (term) => {
        spokeTerms.push(term)
        return Promise.resolve()
      },
      speakMeaning: (meaning) => {
        spokeMeanings.push(meaning)
        return Promise.resolve()
      },
      onStateChange: (info) => {
        stateChanges.push({ ...info })
      },
      termPauseMs: 50,
      nextWordPauseMs: 100,
    })
  })

  afterEach(() => {
    player?.stop()
    vi.clearAllTimers()
    vi.useRealTimers()
  })

  it('initializes in IDLE state', () => {
    expect(player.getStatus()).toBe('IDLE')
    expect(player.isPlaying()).toBe(false)
    expect(player.isPaused()).toBe(false)
  })

  it('handles empty entries gracefully', async () => {
    await player.play([])
    expect(player.getStatus()).toBe('IDLE')
    expect(spokeTerms.length).toBe(0)
  })

  it('plays entries sequentially with speaking row highlights', async () => {
    const entries = [
      { id: '1', term: 'vigor', parsed: { definitions: [{ cn: '活力，精力' }] } },
      { id: '2', term: 'abandon', parsed: { definitions: [{ cn: '抛弃，放弃' }] } },
    ]

    const playPromise = player.play(entries)

    expect(player.getStatus()).toBe('PLAYING')
    expect(player.isPlaying()).toBe(true)
    expect(stateChanges[0].status).toBe('PLAYING')
    expect(stateChanges[0].currentIndex).toBe(0)
    expect(stateChanges[0].total).toBe(2)

    // First row highlighted
    const row1 = document.querySelector('tr[data-entry-id="1"]')
    expect(row1.classList.contains('wordbook-row-speaking')).toBe(true)

    // Advance timers for term pause and next word pause
    await vi.runAllTimersAsync()
    await playPromise

    expect(spokeTerms).toEqual(['vigor', 'abandon'])
    expect(spokeMeanings).toEqual(['活力', '抛弃'])
    expect(player.getStatus()).toBe('IDLE')
    expect(row1.classList.contains('wordbook-row-speaking')).toBe(false)
  })

  it('pauses and resumes without losing position', async () => {
    const entries = [
      { id: '1', term: 'vigor', parsed: { definitions: [{ cn: '活力' }] } },
      { id: '2', term: 'abandon', parsed: { definitions: [{ cn: '放弃' }] } },
      { id: '3', term: 'maintain', parsed: { definitions: [{ cn: '维持' }] } },
    ]

    player.play(entries)
    await vi.advanceTimersByTimeAsync(10)

    player.pause()
    expect(player.getStatus()).toBe('PAUSED')
    expect(player.isPaused()).toBe(true)
    expect(player.isPlaying()).toBe(false)

    // Current row remains highlighted during pause
    const row1 = document.querySelector('tr[data-entry-id="1"]')
    expect(row1.classList.contains('wordbook-row-speaking')).toBe(true)

    // Resume
    player.resume()
    expect(player.getStatus()).toBe('PLAYING')
    expect(player.isPlaying()).toBe(true)

    await vi.runAllTimersAsync()
    expect(player.getStatus()).toBe('IDLE')
  })

  it('stops and clears highlighting immediately', async () => {
    const entries = [
      { id: '1', term: 'vigor', parsed: { definitions: [{ cn: '活力' }] } },
      { id: '2', term: 'abandon', parsed: { definitions: [{ cn: '放弃' }] } },
    ]

    player.play(entries)
    const row1 = document.querySelector('tr[data-entry-id="1"]')
    expect(row1.classList.contains('wordbook-row-speaking')).toBe(true)

    player.stop()
    expect(player.getStatus()).toBe('IDLE')
    expect(row1.classList.contains('wordbook-row-speaking')).toBe(false)
    expect(player.getCurrentIndex()).toBe(0)
  })

  it('toggles play/pause state correctly via togglePlay', async () => {
    const entries = [
      { id: '1', term: 'vigor', parsed: { definitions: [{ cn: '活力' }] } },
    ]

    // 1st toggle: starts playing
    player.togglePlay(entries)
    expect(player.getStatus()).toBe('PLAYING')

    // 2nd toggle: pauses
    player.togglePlay(entries)
    expect(player.getStatus()).toBe('PAUSED')

    // 3rd toggle: resumes
    player.togglePlay(entries)
    expect(player.getStatus()).toBe('PLAYING')

    await vi.runAllTimersAsync()
    expect(player.getStatus()).toBe('IDLE')
  })

  it('respects term_only mode by omitting meaning speech', async () => {
    player.setOptions({ mode: 'term_only' })
    const entries = [
      { id: '1', term: 'vigor', parsed: { definitions: [{ cn: '活力' }] } },
    ]

    const playPromise = player.play(entries)
    await vi.runAllTimersAsync()
    await playPromise

    expect(spokeTerms).toEqual(['vigor'])
    expect(spokeMeanings).toEqual([]) // Meaning omitted in term_only mode
    expect(player.getStatus()).toBe('IDLE')
  })

  it('supports next and prev manual step navigation', async () => {
    const entries = [
      { id: '1', term: 'vigor' },
      { id: '2', term: 'abandon' },
      { id: '3', term: 'maintain' },
    ]

    player.play(entries)
    expect(player.getCurrentIndex()).toBe(0)

    // Jump to next word
    player.next()
    expect(player.getCurrentIndex()).toBe(1)
    const row2 = document.querySelector('tr[data-entry-id="2"]')
    expect(row2.classList.contains('wordbook-row-speaking')).toBe(true)

    // Jump to previous word
    player.prev()
    expect(player.getCurrentIndex()).toBe(0)
    const row1 = document.querySelector('tr[data-entry-id="1"]')
    expect(row1.classList.contains('wordbook-row-speaking')).toBe(true)

    player.stop()
    expect(player.getStatus()).toBe('IDLE')
  })

  it('supports repeatCount for repeating each word multiple times', async () => {
    player.setOptions({ mode: 'term_only', repeatCount: 2 })
    const entries = [
      { id: '1', term: 'vigor' },
    ]

    const playPromise = player.play(entries)
    await vi.runAllTimersAsync()
    await playPromise

    expect(spokeTerms).toEqual(['vigor', 'vigor'])
    expect(player.getStatus()).toBe('IDLE')
  })

  it('supports random playback order and allows prev back-tracking', async () => {
    player.setOptions({ mode: 'term_only', order: 'random' })
    const entries = [
      { id: '1', term: 'word1' },
      { id: '2', term: 'word2' },
      { id: '3', term: 'word3' },
    ]

    player.play(entries)
    expect(player.getCurrentIndex()).toBe(0)

    // In random mode, next() picks a valid entry index
    player.next()
    const nextIdx = player.getCurrentIndex()
    expect(nextIdx).toBeGreaterThanOrEqual(0)
    expect(nextIdx).toBeLessThan(3)

    // Calling prev() returns to index 0
    player.prev()
    expect(player.getCurrentIndex()).toBe(0)

    player.stop()
    expect(player.getStatus()).toBe('IDLE')
  })
})
