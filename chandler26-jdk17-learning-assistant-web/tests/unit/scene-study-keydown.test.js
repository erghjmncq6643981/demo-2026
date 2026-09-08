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
      currentLearningPlan: { id: 100 },
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
      logEvent: vi.fn(),
      toast: vi.fn(),
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
    expect(submittedAnswer).toBe('城市')
  })

  it('types letter n in copy_typing mode for aunt without interference', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'assessment',
      sceneTypingTyped: 'au',
    }
    const unit = {
      id: 1,
      words: [
        {
          id: 11,
          term: 'aunt',
          tier: 'core',
          masteryRequirement: 'spelling',
          passedAssessments: ['meaning_choice'],
        },
      ],
    }
    const sceneStudy = createSceneStudy({
      state,
      elements: {},
      activeUnit: () => unit,
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
    })

    const eventN = {
      key: 'n',
      code: 'KeyN',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventN)

    expect(eventN.preventDefault).toHaveBeenCalled()
    expect(state.sceneTypingTyped).toBe('aun')
  })

  it('toggles in-challenge-stage on sceneLearningStage during challenge and assessment stages', () => {
    const stageClasses = new Set()
    const elements = {
      scenePlanToolbar: { classList: { toggle: vi.fn() } },
      scenePlanSidebar: { classList: { toggle: vi.fn() } },
      scenePlanLayout: { classList: { toggle: vi.fn() } },
      scenePlanOverview: { classList: { toggle: vi.fn() } },
      sceneLearningStage: {
        classList: {
          toggle: vi.fn((cls, val) => {
            if (val) stageClasses.add(cls)
            else stageClasses.delete(cls)
          }),
        },
        querySelector: vi.fn().mockReturnValue({ classList: { toggle: vi.fn() } }),
      },
      sceneLearningFooter: { classList: { toggle: vi.fn() } },
      sceneChallengeStage: { classList: { toggle: vi.fn() } },
      sceneAssessmentPanel: { classList: { toggle: vi.fn() } },
    }
    const state = {
      currentLearningPlan: { id: 1 },
    }
    const sceneStudy = createSceneStudy({
      state,
      elements,
      activeUnit: () => ({ id: 10 }),
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
    })

    sceneStudy.applyStage('assessment')
    expect(elements.sceneLearningStage.classList.toggle).toHaveBeenCalledWith('in-challenge-stage', true)

    sceneStudy.applyStage('challenge')
    expect(elements.sceneLearningStage.classList.toggle).toHaveBeenCalledWith('in-challenge-stage', true)

    sceneStudy.applyStage('learning')
    expect(elements.sceneLearningStage.classList.toggle).toHaveBeenCalledWith('in-challenge-stage', false)
  })
})
