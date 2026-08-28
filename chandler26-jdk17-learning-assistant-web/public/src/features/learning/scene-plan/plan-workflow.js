import { hideModal, showModal } from '/src/shared/modal.js'
import { initDatetimePicker } from '/src/shared/datetime-picker.js'
import { normalizeWordbookId, syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { PLAN_STATUS_LABELS, asArray, number } from '/src/features/learning/scene-plan/model.js'
import { createPreviewPlan } from '/src/features/learning/scene-plan/preview-data.js'

export function createPlanWorkflow({
  state,
  elements,
  api,
  loadSceneData,
  renderSourceOptions,
  changeSelectedPlan,
  targetPlan,
  setButtonLoading,
  toast,
  logEvent,
  confirmAction,
  escapeHtml,
  sameId,
}) {
  function renderSelectOptions(select, items, selected, label, emptyLabel) {
    if (!select) return
    select.innerHTML = ''
    if (!items.length) {
      select.innerHTML = `<option value="">${escapeHtml(emptyLabel)}</option>`
      return
    }
    items.forEach((item) => {
      const option = document.createElement('option')
      option.value = String(item.id)
      option.textContent = label(item)
      select.appendChild(option)
    })
    const normalizedSelected = String(selected || '')
    select.value = items.some((item) => String(item.id) === normalizedSelected) ? normalizedSelected : String(items[0].id)
  }

  function renderPlanList() {
    const plans = asArray(state.learningPlans)
    if (elements.scenePlanCount) elements.scenePlanCount.textContent = String(plans.length)
    renderSelectOptions(elements.scenePlanSelect, plans, state.currentLearningPlan?.id, (item) => `${item.name} · ${number(item.learnedCoreWords)}/${number(item.totalCatalogWords)}词`, '暂无学习计划')
    if (!plans.length) {
      if (elements.scenePlanList) {
        elements.scenePlanList.className = 'scene-plan-list empty'
        elements.scenePlanList.textContent = '暂无学习计划'
      }
      if (elements.profileLearningPlanList) {
        elements.profileLearningPlanList.className = 'profile-learning-plan-list empty'
        elements.profileLearningPlanList.textContent = '暂无学习计划'
      }
      return
    }
    if (elements.scenePlanList) {
      elements.scenePlanList.className = 'scene-plan-list'
      elements.scenePlanList.innerHTML = plans.map((plan) => `<button class="scene-plan-item ${sameId(plan.id, state.currentLearningPlan?.id) ? 'active' : ''}" type="button" data-scene-plan-id="${escapeHtml(plan.id)}"><span class="scene-item-topline"><strong>${escapeHtml(plan.name)}</strong><small>${escapeHtml(PLAN_STATUS_LABELS[plan.status] || plan.status || '学习中')}</small></span><span>${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)} 个核心词</span><small>已完成 ${number(plan.completedUnitCount)} 个场景</small></button>`).join('')
    }
    if (elements.profileLearningPlanList) {
      elements.profileLearningPlanList.className = 'profile-learning-plan-list'
      elements.profileLearningPlanList.innerHTML = plans.map((plan) => `<article class="profile-learning-plan-card"><div><span class="mini-pill">${escapeHtml(PLAN_STATUS_LABELS[plan.status] || plan.status || '学习中')}</span><h4>${escapeHtml(plan.name)}</h4><p>${escapeHtml(plan.learningPurpose || '未填写学习目标')}</p></div><div class="profile-plan-progress"><strong>${number(plan.learnedCoreWords)} / ${number(plan.totalCatalogWords)}</strong><span>已掌握词汇 · ${number(plan.completedUnitCount)} 个场景</span><div class="plan-actions" style="display: flex; gap: 8px; align-items: center; margin-top: 10px; flex-wrap: wrap;"><button class="secondary-button compact" type="button" data-open-scene-plan="${escapeHtml(plan.id)}">进入挑战</button>${plan.status === 'active' ? '<button class="secondary-button compact" type="button" data-profile-plan-pause="' + escapeHtml(plan.id) + '">暂停</button>' : ''}${plan.status === 'paused' || plan.status === 'not_started' ? `<button class="primary-button compact-primary" type="button" data-profile-plan-resume="${escapeHtml(plan.id)}">${plan.status === 'not_started' ? '启动' : '恢复'}</button>` : ''}${plan.status !== 'cancelled' && plan.status !== 'completed' ? `<button class="danger-button compact" type="button" data-profile-plan-cancel="${escapeHtml(plan.id)}">取消计划</button>` : ''}<button class="icon-action-button" type="button" data-scene-plan-edit="${escapeHtml(plan.id)}" title="修改计划" aria-label="修改计划">✎</button></div></div></article>`).join('')
    }
    const containers = [elements.scenePlanList, elements.profileLearningPlanList].filter(Boolean)
    containers.forEach((container) => container.querySelectorAll('[data-scene-plan-id], [data-open-scene-plan]').forEach((button) => button.addEventListener('click', async () => {
      const planId = button.dataset.scenePlanId || button.dataset.openScenePlan
      await changeSelectedPlan(planId)
      if (button.dataset.openScenePlan) document.querySelector('[data-view="scenePlanView"]')?.click()
    })))
    elements.profileLearningPlanList?.querySelectorAll('[data-profile-plan-pause]').forEach((button) => button.addEventListener('click', async (event) => { event.stopPropagation(); await pausePlan(button.dataset.profilePlanPause) }))
    elements.profileLearningPlanList?.querySelectorAll('[data-profile-plan-resume]').forEach((button) => button.addEventListener('click', async (event) => { event.stopPropagation(); await resumePlan(button.dataset.profilePlanResume) }))
    elements.profileLearningPlanList?.querySelectorAll('[data-profile-plan-cancel]').forEach((button) => button.addEventListener('click', async (event) => { event.stopPropagation(); await cancelPlan(button.dataset.profilePlanCancel) }))
    elements.profileLearningPlanList?.querySelectorAll('[data-scene-plan-edit]').forEach((button) => button.addEventListener('click', (event) => { event.stopPropagation(); openModal(button.dataset.scenePlanEdit) }))
  }

  let startPickerInited = false
  let endPickerInited = false

  function formatPlanDate(dateStr) {
    if (!dateStr) return '-'
    const date = new Date(dateStr)
    if (Number.isNaN(date.getTime())) return '-'
    const pad = (value) => String(value).padStart(2, '0')
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
  }

  function openModal(planId = null) {
    const targetPlanId = (typeof planId === 'string' || typeof planId === 'number') && String(planId).trim() ? planId : null
    renderSourceOptions()
    const startPickerEl = document.getElementById('scenePlanStartTimePicker')
    const endPickerEl = document.getElementById('scenePlanEndTimePicker')
    if (startPickerEl && !startPickerInited) { initDatetimePicker(startPickerEl); startPickerInited = true }
    if (endPickerEl && !endPickerInited) { initDatetimePicker(endPickerEl); endPickerInited = true }
    state.currentPlanEditId = targetPlanId
    if (targetPlanId) {
      const plan = asArray(state.learningPlans).find((item) => sameId(item.id, targetPlanId))
      if (!plan) return
      elements.scenePlanModalTitle && (elements.scenePlanModalTitle.textContent = '编辑学习计划')
      elements.createScenePlanBtn.textContent = '保存修改'
      if (elements.sceneCatalogSelect) { elements.sceneCatalogSelect.value = String(plan.catalogVersionId || ''); elements.sceneCatalogSelect.disabled = true }
      elements.scenePlanNameInput.value = plan.name || ''
      elements.scenePlanPurposeInput.value = plan.learningPurpose || ''
      elements.scenePlanModelSelect.value = plan.modelConfigId || ''
      if (elements.scenePlanWordbookSelect) elements.scenePlanWordbookSelect.value = String(plan.wordbookId || '')
      if (elements.scenePlanStartTimeInput) {
        elements.scenePlanStartTimeInput.value = plan.startTime || ''
        const labelEl = startPickerEl?.querySelector('.datetime-picker-label')
        if (labelEl) { labelEl.textContent = plan.startTime ? formatPlanDate(plan.startTime) : (startPickerEl.dataset.placeholder || '选择开始时间'); labelEl.classList.toggle('placeholder', !plan.startTime) }
      }
      if (elements.scenePlanEndTimeInput) {
        elements.scenePlanEndTimeInput.value = plan.endTime || ''
        const labelEl = endPickerEl?.querySelector('.datetime-picker-label')
        if (labelEl) { labelEl.textContent = plan.endTime ? formatPlanDate(plan.endTime) : (endPickerEl.dataset.placeholder || '选择结束时间'); labelEl.classList.toggle('placeholder', !plan.endTime) }
      }
      elements.scenePlanStatusField?.classList.remove('hidden')
      if (elements.scenePlanStatusSelect) elements.scenePlanStatusSelect.value = plan.status || 'active'
    } else {
      elements.scenePlanModalTitle && (elements.scenePlanModalTitle.textContent = '新建学习计划')
      elements.createScenePlanBtn.textContent = '创建计划'
      if (elements.sceneCatalogSelect) elements.sceneCatalogSelect.disabled = false
      const selected = state.publicVocabularyCatalogs.find((item) => String(item.catalogVersionId) === elements.sceneCatalogSelect?.value)
      elements.scenePlanNameInput.value = selected ? `${selected.catalogName}学习计划` : ''
      elements.scenePlanPurposeInput.value = selected?.learningPurpose || ''
      elements.scenePlanModelSelect.value = ''
      if (elements.scenePlanWordbookSelect) elements.scenePlanWordbookSelect.value = String(state.currentWordbookId || '')
      if (elements.scenePlanStartTimeInput) { elements.scenePlanStartTimeInput.value = ''; const labelEl = startPickerEl?.querySelector('.datetime-picker-label'); if (labelEl) { labelEl.textContent = startPickerEl.dataset.placeholder || '选择开始时间'; labelEl.classList.add('placeholder') } }
      if (elements.scenePlanEndTimeInput) { elements.scenePlanEndTimeInput.value = ''; const labelEl = endPickerEl?.querySelector('.datetime-picker-label'); if (labelEl) { labelEl.textContent = endPickerEl.dataset.placeholder || '选择结束时间'; labelEl.classList.add('placeholder') } }
      elements.scenePlanStatusField?.classList.add('hidden')
    }
    showModal(elements.scenePlanModal)
  }

  function closeModal() {
    hideModal(elements.scenePlanModal)
  }

  function changeCatalog() {
    const selected = state.publicVocabularyCatalogs.find((item) => sameId(item.catalogVersionId, elements.sceneCatalogSelect.value))
    if (!selected) return
    elements.scenePlanNameInput.value = `${selected.catalogName}学习计划`
    elements.scenePlanPurposeInput.value = selected.learningPurpose || ''
  }

  async function savePlan() {
    const catalogVersionId = elements.sceneCatalogSelect.value
    const name = elements.scenePlanNameInput.value.trim()
    const learningPurpose = elements.scenePlanPurposeInput.value.trim()
    if (!name || !learningPurpose) { toast('请填写计划名称和学习目标'); return }
    if (!state.currentPlanEditId && !catalogVersionId) { toast('请选择公共词本'); return }
    const startTime = elements.scenePlanStartTimeInput?.value || null
    const endTime = elements.scenePlanEndTimeInput?.value || null
    const wordbookId = elements.scenePlanWordbookSelect?.value || null
    if (state.currentPlanEditId) {
      setButtonLoading(elements.createScenePlanBtn, true, '保存中...')
      try {
        const modelConfigId = elements.scenePlanModelSelect.value
        const status = elements.scenePlanStatusSelect?.value || null
        let plan
        if (state.preview) {
          plan = state.learningPlans.find((item) => sameId(item.id, state.currentPlanEditId))
          if (plan) Object.assign(plan, { name, learningPurpose, startTime, endTime, wordbookId: wordbookId || plan.wordbookId, modelConfigId: modelConfigId || null, ...(status ? { status } : {}) })
        } else {
          plan = await api.updatePlan(state.currentPlanEditId, { name, learningPurpose, wordbookId: wordbookId || null, modelConfigId: modelConfigId || null, startTime, endTime, status })
        }
        if (plan) {
          if (sameId(state.currentLearningPlan?.id, plan.id)) state.currentLearningPlan = plan
          await loadSceneData({ planId: plan.id })
        }
        closeModal()
        toast('学习计划已更新')
      } catch (error) {
        logEvent('error', '更新学习计划失败', error.message)
        toast(`更新计划失败：${error.message}`)
      } finally { setButtonLoading(elements.createScenePlanBtn, false) }
      return
    }
    const isFuture = startTime && new Date(startTime) > new Date()
    setButtonLoading(elements.createScenePlanBtn, true, isFuture ? '创建中...' : '生成首个场景中...')
    try {
      const modelConfigId = elements.scenePlanModelSelect.value
      let plan
      if (state.preview) {
        const catalog = state.publicVocabularyCatalogs.find((item) => sameId(item.catalogVersionId, catalogVersionId))
        plan = createPreviewPlan({ id: `preview-${Date.now()}`, catalog, name, learningPurpose, wordbookId: wordbookId || null, startTime, endTime, status: isFuture ? 'not_started' : 'active', learnedCoreWords: 0, completedUnitCount: 0 })
        state.learningPlans.unshift(plan)
      } else {
        plan = await api.createPlan({ catalogVersionId, wordbookId: wordbookId || null, name, learningPurpose, modelConfigId: modelConfigId || null, generateFirstUnit: !isFuture, startTime, endTime })
      }
      state.currentLearningPlan = plan
      await loadSceneData({ planId: plan.id })
      closeModal()
      logEvent('learning', '创建学习计划', name)
      toast(isFuture ? '学习计划已成功创建（未开始）' : '学习计划和首个场景已生成')
    } catch (error) {
      logEvent('error', '创建场景学习计划失败', error.message)
      toast(`创建计划失败：${error.message}`)
    } finally { setButtonLoading(elements.createScenePlanBtn, false) }
  }

  function changeWordbook() {
    const wordbookId = normalizeWordbookId(elements.sceneWordbookSelect.value)
    syncCurrentWordbookId(state, elements, wordbookId)
    elements.sceneWordbookSelect.value = wordbookId
  }

  async function pausePlan(planId) {
    const plan = targetPlan(planId)
    if (!plan) return
    try {
      if (state.preview) { plan.status = 'paused'; toast('设计预览：计划已暂停') }
      else { const result = await api.updatePlanStatus(plan.id, 'pause'); if (sameId(plan.id, state.currentLearningPlan?.id)) state.currentLearningPlan = result }
      await loadSceneData({ planId: plan.id })
      toast('学习计划已暂停')
    } catch (error) { logEvent('error', '暂停学习计划失败', error.message); toast(`暂停失败：${error.message}`) }
  }

  async function resumePlan(planId) {
    const plan = targetPlan(planId)
    if (!plan) return
    try {
      if (state.preview) { plan.status = 'active'; toast('设计预览：计划已启动/恢复') }
      else { const result = await api.updatePlanStatus(plan.id, 'resume'); if (sameId(plan.id, state.currentLearningPlan?.id)) state.currentLearningPlan = result }
      await loadSceneData({ planId: plan.id })
      toast('学习计划已启动/恢复')
    } catch (error) { logEvent('error', '恢复学习计划失败', error.message); toast(`恢复失败：${error.message}`) }
  }

  async function cancelPlan(planId) {
    const plan = targetPlan(planId)
    if (!plan) return
    const confirmed = await confirmAction({ title: '取消学习计划', message: `确认取消学习计划「${plan.name}」？取消后将不能继续学习或恢复。` })
    if (!confirmed) return
    try {
      if (state.preview) { plan.status = 'cancelled'; toast('设计预览：计划已取消') }
      else { const result = await api.updatePlanStatus(plan.id, 'cancel'); if (sameId(plan.id, state.currentLearningPlan?.id)) state.currentLearningPlan = result }
      await loadSceneData()
      toast('学习计划已取消')
    } catch (error) { logEvent('error', '取消学习计划失败', error.message); toast(`取消失败：${error.message}`) }
  }

  return { renderPlanList, openModal, closeModal, changeCatalog, savePlan, changeWordbook, pausePlan, resumePlan, cancelPlan }
}
