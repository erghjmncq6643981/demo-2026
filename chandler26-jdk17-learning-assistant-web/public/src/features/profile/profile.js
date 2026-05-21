import { createAccountProfileFeature } from '/src/features/profile/account.js'
import { createActivityProfileFeature } from '/src/features/profile/activity.js'
import { createAgentProfileFeature } from '/src/features/profile/agent.js'
import { createModelProfileFeature } from '/src/features/profile/model.js'
import { createTemplateProfileFeature } from '/src/features/profile/template.js'
import { createWordbookProfileFeature } from '/src/features/profile/wordbook.js'

export function createProfileFeature(ctx) {
  const activityFeature = createActivityProfileFeature(ctx)
  const agentFeature = createAgentProfileFeature(ctx)
  const templateFeature = createTemplateProfileFeature(ctx)
  const modelFeature = createModelProfileFeature(ctx)
  const accountFeature = createAccountProfileFeature(ctx)
  const wordbookFeature = createWordbookProfileFeature({
    ...ctx,
    renderProfileMetrics: (...args) => activityFeature.renderProfileMetrics(...args),
    loadActivity: (...args) => activityFeature.loadActivity(...args),
    renderActivityHeatmap: (...args) => activityFeature.renderActivityHeatmap(...args),
  })

  return {
    ...agentFeature,
    ...templateFeature,
    ...modelFeature,
    ...accountFeature,
    ...wordbookFeature,
    ...activityFeature,
  }
}
