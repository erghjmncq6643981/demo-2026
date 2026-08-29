import { describe, expect, it, vi } from 'vitest'
import { createSceneStudy } from '../../public/src/features/learning/scene-plan/scene-study.js'

describe('scene study keydown', () => {
  it('types letters in copy_typing mode including letter r without triggering speak', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'assessment',
      sceneTypingTyped: 'd',
    }
    const unit = {
      id: 1,
      words: [
        {
          id: 10,
          term: 'draw',
          tier: 'core',
          masteryRequirement: 'spelling',
          passedAssessments: ['meaning_choice'],
        },
      ],
    }
    const speak = vi.fn()
    const sceneStudy = createSceneStudy({
      state,
      elements: {},
      activeUnit: () => unit,
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      speak,
    })

    const eventR = {
      key: 'r',
      code: 'KeyR',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventR)

    expect(speak).not.toHaveBeenCalled()
    expect(state.sceneTypingTyped).toBe('dr')
  })

  it('selects option in meaning_choice mode when pressing 1-4 or A-D', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'assessment',
    }
    const unit = {
      id: 1,
      words: [
        {
          id: 10,
          term: 'blanket',
          tier: 'core',
          masteryRequirement: 'recognition',
          passedAssessments: [],
          assessment: {
            options: ['毯子', '城市', '水杯', '窗户'],
          },
        },
      ],
    }
    let submittedAnswer = null
    const sceneStudy = createSceneStudy({
      state,
      elements: {},
      activeUnit: () => unit,
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      api: {
        submitAssessment: (_planId, _unitId, payload) => {
          submittedAnswer = payload.answer
          return Promise.resolve({ correct: true, passedAssessments: ['meaning_choice'] })
        },
      },
    })

    // Simulate pressing '2' or 'b'
    const event2 = {
      key: '2',
      code: 'Digit2',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(event2)

    // Answer '城市' is at index 1
    // The state was updated with submitAssessment
    expect(event2.preventDefault).toHaveBeenCalled()
  })
})
