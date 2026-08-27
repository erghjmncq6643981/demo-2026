export function createStudyEngine({
  state,
  elements,
  applyStage,
  renderChallengeWords,
  renderAssessment,
  prepareUnit,
  coreWords,
  isWordComplete,
  onChallengeStart,
}) {
  async function startLearning() {
    const unit = await prepareUnit?.()
    if (!unit) return null
    state.sceneChallengeStage = 'learning'
    applyStage?.('learning')
    elements?.sceneDetailSection?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    elements?.sceneLearningStage?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return unit
  }

  async function showChallengeWords() {
    const unit = await prepareUnit?.()
    if (!unit) return null
    state.sceneChallengeStage = 'challenge'
    applyStage?.('challenge')
    renderChallengeWords?.(coreWords?.(unit) || [])
    return unit
  }

  async function startChallenge() {
    const unit = await prepareUnit?.()
    const words = coreWords?.(unit) || []
    if (!unit || !words.length) return null
    const firstIncomplete = words.find((word) => !isWordComplete?.(word)) || words[0]
    onChallengeStart?.(firstIncomplete)
    state.sceneChallengeStage = 'assessment'
    applyStage?.('assessment')
    renderAssessment?.(unit)
    return unit
  }

  function backToReading() {
    state.sceneChallengeStage = 'learning'
    applyStage?.('learning')
  }

  return {
    startLearning,
    showChallengeWords,
    startChallenge,
    backToReading,
  }
}
