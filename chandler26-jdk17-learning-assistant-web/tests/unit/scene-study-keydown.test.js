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

  it('toggles scene audio play/pause with Space or KeyP during learning stage and prevents default scrolling', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'learning',
    }
    const elements = {
      sceneLearningStage: { classList: { contains: () => false } },
    }
    const toggleAudio = vi.fn()
    const replayAudio = vi.fn()
    const sceneStudy = createSceneStudy({
      state,
      elements,
      activeUnit: () => ({ id: 10 }),
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      toggleAudio,
      replayAudio,
    })

    const eventSpace = {
      key: ' ',
      code: 'Space',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventSpace)
    expect(eventSpace.preventDefault).toHaveBeenCalled()
    expect(toggleAudio).toHaveBeenCalledTimes(1)

    const eventP = {
      key: 'p',
      code: 'KeyP',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventP)
    expect(eventP.preventDefault).toHaveBeenCalled()
    expect(toggleAudio).toHaveBeenCalledTimes(2)
  })

  it('replays scene audio with KeyR or Shift+Space during learning stage', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'learning',
    }
    const elements = {
      sceneLearningStage: { classList: { contains: () => false } },
    }
    const toggleAudio = vi.fn()
    const replayAudio = vi.fn()
    const sceneStudy = createSceneStudy({
      state,
      elements,
      activeUnit: () => ({ id: 10 }),
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      toggleAudio,
      replayAudio,
    })

    const eventR = {
      key: 'r',
      code: 'KeyR',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventR)
    expect(eventR.preventDefault).toHaveBeenCalled()
    expect(replayAudio).toHaveBeenCalledTimes(1)

    const eventShiftSpace = {
      key: ' ',
      code: 'Space',
      shiftKey: true,
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventShiftSpace)
    expect(eventShiftSpace.preventDefault).toHaveBeenCalled()
    expect(replayAudio).toHaveBeenCalledTimes(2)
  })

  it('does not trigger audio shortcuts when typing inside an input or textarea', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'learning',
    }
    const elements = {
      sceneLearningStage: { classList: { contains: () => false } },
    }
    const toggleAudio = vi.fn()
    const replayAudio = vi.fn()
    const sceneStudy = createSceneStudy({
      state,
      elements,
      activeUnit: () => ({ id: 10 }),
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      toggleAudio,
      replayAudio,
    })

    const mockInput = document.createElement('textarea')
    document.body.appendChild(mockInput)
    mockInput.focus()

    const eventSpace = {
      key: ' ',
      code: 'Space',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventSpace)
    expect(eventSpace.preventDefault).not.toHaveBeenCalled()
    expect(toggleAudio).not.toHaveBeenCalled()

    const eventR = {
      key: 'r',
      code: 'KeyR',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventR)
    expect(eventR.preventDefault).not.toHaveBeenCalled()
    expect(replayAudio).not.toHaveBeenCalled()

    document.body.removeChild(mockInput)
  })

  it('does not trigger audio shortcuts when metaKey, ctrlKey or altKey is held (e.g. Cmd+R, Ctrl+R, Cmd+P)', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'learning',
    }
    const elements = {
      sceneLearningStage: { classList: { contains: () => false } },
    }
    const toggleAudio = vi.fn()
    const replayAudio = vi.fn()
    const sceneStudy = createSceneStudy({
      state,
      elements,
      activeUnit: () => ({ id: 10 }),
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      toggleAudio,
      replayAudio,
    })

    const eventCmdR = {
      key: 'r',
      code: 'KeyR',
      metaKey: true,
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventCmdR)
    expect(eventCmdR.preventDefault).not.toHaveBeenCalled()
    expect(replayAudio).not.toHaveBeenCalled()

    const eventCtrlP = {
      key: 'p',
      code: 'KeyP',
      ctrlKey: true,
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventCtrlP)
    expect(eventCtrlP.preventDefault).not.toHaveBeenCalled()
    expect(toggleAudio).not.toHaveBeenCalled()
  })

  it('pauses and resumes speech synthesis if speechSynthesis is speaking during learning stage', () => {
    const state = {
      activeView: 'scenePlanView',
      sceneChallengeStage: 'learning',
    }
    const elements = {
      sceneLearningStage: { classList: { contains: () => false } },
    }
    const toast = vi.fn()
    const origSynthesis = window.speechSynthesis
    window.speechSynthesis = {
      speaking: true,
      paused: false,
      pause: vi.fn(),
      resume: vi.fn(),
      cancel: vi.fn(),
    }

    const sceneStudy = createSceneStudy({
      state,
      elements,
      activeUnit: () => ({ id: 10 }),
      renderCurrentScene: () => {},
      sameId: (a, b) => String(a) === String(b),
      toast,
    })

    const eventSpace = {
      key: ' ',
      code: 'Space',
      preventDefault: vi.fn(),
    }
    sceneStudy.handleChallengeKeydown(eventSpace)
    expect(window.speechSynthesis.pause).toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('已暂停朗读')

    window.speechSynthesis.paused = true
    sceneStudy.handleChallengeKeydown(eventSpace)
    expect(window.speechSynthesis.resume).toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('继续朗读')

    if (origSynthesis) window.speechSynthesis = origSynthesis
    else delete window.speechSynthesis
  })
})
