export function createStudyEngine({
  state,
  elements,
  activeUnit,
  applyStage,
  renderChallengeWords,
  renderAssessment,
}) {
  function startLearning() {
    state.sceneStage = 'study'
    applyStage?.('study')
    elements?.sceneDetailSection?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function showChallengeWords() {
    state.sceneStage = 'challenge_words'
    applyStage?.('challenge_words')
    renderChallengeWords?.(activeUnit?.())
  }

  function startChallenge() {
    state.sceneStage = 'challenge'
    applyStage?.('challenge')
    renderAssessment?.(activeUnit?.())
  }

  function backToReading() {
    state.sceneStage = 'study'
    applyStage?.('study')
  }

  return {
    startLearning,
    showChallengeWords,
    startChallenge,
    backToReading,
  }
}
