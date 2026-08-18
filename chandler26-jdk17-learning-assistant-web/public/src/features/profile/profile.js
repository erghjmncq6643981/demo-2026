import { createAccountProfileFeature } from '/src/features/profile/account.js'
import { createActivityProfileFeature } from '/src/features/profile/activity.js'
import { createAgentProfileFeature } from '/src/features/profile/agent.js'
import { createLearningConfigProfileFeature } from '/src/features/profile/learning-config.js'
import { createModelProfileFeature } from '/src/features/profile/model.js'
import { createTemplateProfileFeature } from '/src/features/profile/template.js'
import { createWordbookProfileFeature } from '/src/features/profile/wordbook.js'
import { createTaskCenterProfileFeature } from '/src/features/profile/task-center.js'

export function createProfileFeature(ctx) {
  const learningConfigFeature = createLearningConfigProfileFeature(ctx)
  const activityFeature = createActivityProfileFeature(ctx)
  const agentFeature = createAgentProfileFeature({
    ...ctx,
    renderLearningConfigSummary: (...args) => learningConfigFeature.renderLearningConfigSummary(...args),
  })
  const templateFeature = createTemplateProfileFeature({
    ...ctx,
    renderLearningConfigSummary: (...args) => learningConfigFeature.renderLearningConfigSummary(...args),
  })
  const modelFeature = createModelProfileFeature(ctx)
  const accountFeature = createAccountProfileFeature(ctx)
  const wordbookFeature = createWordbookProfileFeature({
    ...ctx,
    renderProfileMetrics: (...args) => activityFeature.renderProfileMetrics(...args),
    loadActivity: (...args) => activityFeature.loadActivity(...args),
    renderActivityHeatmap: (...args) => activityFeature.renderActivityHeatmap(...args),
  })
  const taskCenterFeature = createTaskCenterProfileFeature(ctx)

  return {
    ...agentFeature,
    ...templateFeature,
    ...modelFeature,
    ...accountFeature,
    ...learningConfigFeature,
    ...wordbookFeature,
    ...activityFeature,
    ...taskCenterFeature,
  }
}
