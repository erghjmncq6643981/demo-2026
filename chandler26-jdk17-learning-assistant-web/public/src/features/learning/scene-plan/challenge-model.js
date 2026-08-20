import { asArray } from '/src/features/learning/scene-plan/model.js'

export function requiredAssessments(word) {
  return word?.masteryRequirement === 'spelling'
    ? ['meaning_choice', 'copy_typing', 'meaning_spelling']
    : ['meaning_choice']
}

export function nextAssessment(word) {
  const passed = new Set(asArray(word?.passedAssessments))
  return requiredAssessments(word).find((type) => !passed.has(type)) || null
}

export function isWordComplete(word) {
  return !nextAssessment(word)
}

export function pendingChallengeWords(unit) {
  return asArray(unit?.words).filter((word) => word.tier === 'core' && !isWordComplete(word))
}
