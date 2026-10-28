export function createTaskElements(root = document) {
  const $ = (id) => (root.getElementById ? root.getElementById(id) : root.querySelector(`#${id}`))
  return {
    aiTaskPanel: $('aiTaskPanel'),
    aiTaskList: $('aiTaskList'),
    aiTaskSummary: $('aiTaskSummary'),
    aiTaskStatusFilter: $('aiTaskStatusFilter'),
    reloadAiTasksBtn: $('reloadAiTasksBtn'),
    aiTaskPageInfo: $('aiTaskPageInfo'),
    aiTaskPrevBtn: $('aiTaskPrevBtn'),
    aiTaskNextBtn: $('aiTaskNextBtn'),
    aiTaskDetailModal: $('aiTaskDetailModal'),
    aiTaskDetailCloseBtn: $('aiTaskDetailCloseBtn'),
    aiTaskDetailTitle: $('aiTaskDetailTitle'),
    aiTaskDetailMeta: $('aiTaskDetailMeta'),
    aiTaskDetailSteps: $('aiTaskDetailSteps'),
  }
}
