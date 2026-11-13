import { createShellElements } from './shell-elements.js'
import { createIdentityElements } from '/src/features/identity/identity-elements.js'
import { createVocabularyElements } from '/src/features/vocabulary/vocabulary-elements.js'
import { createRuminationElements } from '/src/features/rumination/rumination-elements.js'
import { createLearningElements } from '/src/features/learning/learning-elements.js'
import { createAiElements } from '/src/features/ai/ai-elements.js'
import { createSystemElements } from '/src/features/system/system-elements.js'
import { createTaskElements } from '/src/features/task/task-elements.js'

export function createElements(root = document) {
  return {
    ...createShellElements(root),
    ...createIdentityElements(root),
    ...createVocabularyElements(root),
    ...createRuminationElements(root),
    ...createLearningElements(root),
    ...createAiElements(root),
    ...createSystemElements(root),
    ...createTaskElements(root),
  }
}
