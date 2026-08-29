import { asArray } from '/src/features/learning/scene-plan/model.js'

export function requiredAssessments(word) {
  return word?.masteryRequirement === 'spelling'
    ? ['meaning_choice', 'copy_typing', 'meaning_spelling']
    : ['meaning_choice']
}

export function nextAssessment(word) {
  if (word?.completed === true) return null
  const passed = new Set(asArray(word?.passedAssessments))
  return requiredAssessments(word).find((type) => !passed.has(type)) || null
}

export function isWordComplete(word) {
  if (word?.completed === true) return true
  if (word?.completed === false && !word?.passedAssessments?.length) return false
  return !nextAssessment(word)
}

export function pendingChallengeWords(unit) {
  const detailedWords = asArray(unit?.words).filter((word) => word.tier === 'core')
  if (detailedWords.length) return detailedWords.filter((word) => !isWordComplete(word))
  return asArray(unit?.pendingChallengeWords).filter((word) => !isWordComplete(word))
}
