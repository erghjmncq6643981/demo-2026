import { createAccountProfileFeature } from '/src/features/profile/account.js'
import { createActivityProfileFeature } from '/src/features/profile/activity.js'
import { createAgentManagementFeature } from '/src/features/ai-agent/agent-management.js'
import { createLearningConfigProfileFeature } from '/src/features/profile/learning-config.js'
import { createModelManagementFeature } from '/src/features/ai-model/model-management.js'
import { createPromptTemplateManagementFeature } from '/src/features/ai-prompt/template-management.js'
import { createWordbookProfileFeature } from '/src/features/profile/wordbook.js'
import { createTaskCenterProfileFeature } from '/src/features/profile/task-center.js'

export function createProfileFeature(ctx) {
  const learningConfigFeature = createLearningConfigProfileFeature(ctx)
  const activityFeature = createActivityProfileFeature(ctx)
  const agentFeature = createAgentManagementFeature({
    ...ctx,
    renderLearningConfigSummary: (...args) => learningConfigFeature.renderLearningConfigSummary(...args),
  })
  const templateFeature = createPromptTemplateManagementFeature({
    ...ctx,
    renderLearningConfigSummary: (...args) => learningConfigFeature.renderLearningConfigSummary(...args),
  })
  const modelFeature = createModelManagementFeature({
    ...ctx,
    renderAgentConfigs: (...args) => agentFeature.renderAgentConfigs(...args),
  })
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
