import { normalizeId, sameId } from '/src/shared/ids.js'
import { hideModal, showModal } from '/src/shared/modal.js'
import { escapeHtml } from '/src/shared/text.js'
import { normalizeWordbooks, resolveSelectedWordbookId, syncCurrentWordbookId } from '/src/shared/wordbook.js'
import { createWordbookDetailFeature } from '/src/features/vocabulary/wordbook-detail.js'
import { createWordbookEntryListFeature } from '/src/features/vocabulary/wordbook-entry-list.js'
import { createWordbookAudioPlayer } from '/src/features/vocabulary/wordbook-audio-player.js'

export function createWordbookProfileFeature(ctx) {
  const {
    state,
    elements,
    request,
    setLoading,
    toast,
    logEvent,
    confirmAction,
    confirmDelete,
    loadDueReviews: loadDueReviewsFromCtx,
    openEntryTransferModal,
    saveEntry,
    renderProfileMetrics,
    loadActivity,
    renderActivityHeatmap,
  } = ctx
  const detailFeature = createWordbookDetailFeature({
    ...ctx,
    sameId,
    normalizeId,
    openEntryStatusModal,
  })

  const entryListFeature = createWordbookEntryListFeature({
    state,
    elements,
    sameId,
    normalizeDefinitions: ctx.normalizeDefinitions,
    entryMatchesKeyword,
    renderWordbookFocus: (entry) => detailFeature.renderWordbookFocus(entry),
    renderNotes: (entry) => ctx.renderNotes(entry),
    openEntryStatusModal,
    generateEntryCardWithConfirm,
    deleteWordbookEntry,
    openEntryTransferModal,
    loadWordbookEntryDetail,
    selectWordbookEntry,
  })

  const audioPlayer = createWordbookAudioPlayer({
    getEntries: () => state.wordbookEntries || [],
    speakWord: (term, opts) => ctx.speak?.(term, 'us', { ...opts, silentToast: true }),
    speakMeaning: (meaning, opts) => ctx.speakChinese?.(meaning, { ...opts, silentToast: true }),
    stopSpeaking: () => ctx.stopSpeaking?.(),
    initialOptions: {
      mode: elements.wordbookAudioModeSelect?.value || 'term_only',
      intervalPace: elements.wordbookAudioPaceSelect?.value || 'normal',
      order: elements.wordbookAudioOrderSelect?.value || 'sequence',
      repeatCount: Number(elements.wordbookAudioRepeatSelect?.value || 1),
    },
    onRowChange: (entry) => {
      const rows = elements.wordbookEntryList?.querySelectorAll('tr.wordbook-row')
      rows?.forEach((r) => r.classList.remove('wordbook-row-speaking'))
      if (entry) {
        selectWordbookEntry(entry, { silent: true })
        const targetRow = elements.wordbookEntryList?.querySelector(`tr[data-entry-id="${entry.id}"]`)
        if (targetRow) {
          targetRow.classList.add('wordbook-row-speaking')
          targetRow.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
        }
        const isCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
        if (isCardModalOpen) {
          if (elements.wordbookFocus) {
            elements.wordbookFocus.scrollTop = 0
          }
          const hasParsedDetail = entry.parsed && typeof entry.parsed === 'object' && Object.keys(entry.parsed).length > 0
          if (!state.preview && !hasParsedDetail) {
            loadWordbookEntryDetail(entry)
          }
        }
      }
    },
    onStatusChange: ({ status, currentIndex, total, entry }) => {
      const isPlaying = status === 'PLAYING'
      const isPaused = status === 'PAUSED'
      const isActive = isPlaying || isPaused

      // 1. 同步顶部工具栏小按钮
      if (elements.wordbookPlayAudioBtn) {
        elements.wordbookPlayAudioBtn.classList.toggle('playing', isActive)
        if (elements.wordbookPlayAudioIcon) {
          elements.wordbookPlayAudioIcon.textContent = isPlaying ? '⏸' : '▶'
        }
        if (elements.wordbookPlayAudioText) {
          if (isPlaying) {
            elements.wordbookPlayAudioText.textContent = `暂停 (${currentIndex + 1}/${total})`
          } else if (isPaused) {
            elements.wordbookPlayAudioText.textContent = `继续 (${currentIndex + 1}/${total})`
          } else {
            elements.wordbookPlayAudioText.textContent = '顺序朗读'
          }
        }
      }

      // 2. 同步浮动常驻随身听控制栏 (Player Bar)
      if (elements.wordbookAudioPlayerBar) {
        elements.wordbookAudioPlayerBar.classList.toggle('hidden', !isActive)
      }
      if (isActive && elements.wordbookAudioPlayerBar) {
        if (elements.wordbookAudioStatusBadge) {
          elements.wordbookAudioStatusBadge.classList.toggle('is-playing', isPlaying)
          elements.wordbookAudioStatusBadge.classList.toggle('is-paused', isPaused)
        }
        if (elements.wordbookAudioStatusText) {
          elements.wordbookAudioStatusText.textContent = isPlaying ? '正在朗读' : '已暂停'
        }
        if (elements.wordbookAudioToggleBtn) {
          elements.wordbookAudioToggleBtn.textContent = isPlaying ? '⏸' : '▶'
          elements.wordbookAudioToggleBtn.title = isPlaying ? '暂停 (空格键)' : '继续播放 (空格键)'
        }
        if (elements.wordbookAudioProgress) {
          elements.wordbookAudioProgress.textContent = `${currentIndex + 1} / ${total}`
        }
        if (elements.wordbookAudioCurrentTerm) {
          elements.wordbookAudioCurrentTerm.textContent = entry?.term || entry?.normalizedTerm || '-'
        }
        if (elements.wordbookAudioCurrentMeaning) {
          const rawMeaning =
            entry?.meaningText ||
            entry?.definition ||
            entry?.parsed?.definitions?.[0]?.cn ||
            entry?.parsed?.definitions?.[0]?.meaning ||
            ''
          elements.wordbookAudioCurrentMeaning.textContent = rawMeaning ? String(rawMeaning).replace(/^[a-zA-Z]+\.\s*/, '').trim() : ''
        }
      }
    },
    toast,
  })

  function updateMissingCardBadge() {
    const entries = state.wordbookEntries || []
    const missingCount = entries.filter((item) =>
      ['missing', 'failed', 'not_required'].includes(item.cardStatus || 'missing')
    ).length
    if (elements.wordbookMissingCardBadge) {
      if (missingCount > 0) {
        elements.wordbookMissingCardBadge.classList.remove('hidden')
        elements.wordbookMissingCardBadge.textContent = String(missingCount)
      } else {
        elements.wordbookMissingCardBadge.classList.add('hidden')
      }
    }
    if (elements.wordbookBatchGenerateCardBtn) {
      elements.wordbookBatchGenerateCardBtn.title = missingCount > 0
        ? `一键为当前页 ${missingCount} 个缺省词条生成 AI 词卡`
        : '当前页词卡已全部就绪'
    }
  }

  async function batchGenerateMissingCards() {
    const entries = state.wordbookEntries || []
    const missingEntries = entries.filter((item) =>
      ['missing', 'failed', 'not_required'].includes(item.cardStatus || 'missing')
    )
    if (!missingEntries.length) {
      if (elements.wordCardStatusFilter && elements.wordCardStatusFilter.value !== 'need_card') {
        const switchAndFilter = typeof confirmAction === 'function'
          ? await confirmAction({
              title: '快捷筛选缺少词卡',
              message: '当前页所有单词的 AI 词卡均已就绪。\n是否立即为您切换到「⚡ 缺少词卡」筛选视图，直接查看并批量生成尚未就绪的单词？',
              acceptText: '切换到缺少词卡',
              cancelText: '保持当前视图',
            })
          : window.confirm('当前页所有单词的 AI 词卡均已就绪。\n是否切换到「缺少词卡」筛选视图快速补全？')
        if (switchAndFilter) {
          elements.wordCardStatusFilter.value = 'need_card'
          state.wordbookPage = 1
          await loadWordbookEntries()
          toast?.('已切换到缺少词卡筛选列表，点击「⚡ 补全词卡」即可批量生成')
        }
        return
      }
      toast?.('当前筛选条件下没有缺少词卡的单词')
      return
    }

    const count = missingEntries.length
    const confirmed = typeof confirmAction === 'function'
      ? await confirmAction({
          title: '批量生成 AI 词卡',
          message: `确定要为当前页 ${count} 个未就绪的单词调用 AI 大模型批量生成完整结构化词卡吗？`,
          acceptText: '立即生成',
          danger: false,
        })
      : window.confirm(`确定要为当前页 ${count} 个未就绪的单词生成 AI 词卡吗？`)
    if (!confirmed) return

    if (state.preview) {
      missingEntries.forEach((entry) => {
        entry.cardStatus = 'ready'
      })
      renderWordbookEntries()
      toast?.(`设计预览：已模拟为当前页 ${count} 个单词生成 AI 词卡`)
      return
    }

    const btn = elements.wordbookBatchGenerateCardBtn
    if (btn) btn.disabled = true
    missingEntries.forEach((entry) => {
      entry.cardStatus = 'generating'
    })
    renderWordbookEntries()

    const queue = [...missingEntries]
    let successCount = 0

    async function worker() {
      while (queue.length > 0) {
        const item = queue.shift()
        if (!item) break
        try {
          await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(item.id)}/generate-card/async?forceRefresh=true`, {
            method: 'POST',
          })
          successCount++
        } catch {
          item.cardStatus = 'failed'
        }
      }
    }

    try {
      await Promise.all(Array.from({ length: Math.min(3, queue.length) }, () => worker()))
      toast?.(`已为当前页 ${successCount} 个单词提交词卡任务，可在任务中心查看进度`)
      logEvent?.('ai', '批量提交词卡生成', `${successCount} 个单词`)
    } catch (err) {
      toast?.(`批量生成异常：${err.message}`)
    } finally {
      if (btn) btn.disabled = false
      renderWordbookEntries()
    }
  }

  // 绑定工具栏操作与随身听控制栏事件
  elements.wordbookBatchGenerateCardBtn?.addEventListener('click', batchGenerateMissingCards)
  elements.wordbookPlayAudioBtn?.addEventListener('click', () => audioPlayer.toggle())
  elements.wordbookAudioToggleBtn?.addEventListener('click', () => audioPlayer.toggle())
  elements.wordbookAudioPrevBtn?.addEventListener('click', () => audioPlayer.prev())
  elements.wordbookAudioNextBtn?.addEventListener('click', () => audioPlayer.next())
  elements.wordbookAudioStopBtn?.addEventListener('click', () => audioPlayer.stop())
  elements.wordbookAudioModeSelect?.addEventListener('change', (e) => {
    audioPlayer.setOptions({ mode: e.target.value })
  })
  elements.wordbookAudioPaceSelect?.addEventListener('change', (e) => {
    audioPlayer.setOptions({ intervalPace: e.target.value })
  })
  elements.wordbookAudioOrderSelect?.addEventListener('change', (e) => {
    audioPlayer.setOptions({ order: e.target.value })
  })
  elements.wordbookAudioRepeatSelect?.addEventListener('change', (e) => {
    audioPlayer.setOptions({ repeatCount: Number(e.target.value) })
  })
  if (elements.wordbookAudioCurrentTerm) {
    elements.wordbookAudioCurrentTerm.style.cursor = 'pointer'
    elements.wordbookAudioCurrentTerm.title = '点击查看当前单词卡片'
    elements.wordbookAudioCurrentTerm.addEventListener('click', () => {
      const entries = state.wordbookEntries || []
      const currentIdx = audioPlayer.getCurrentIndex()
      const currentEntry = entries[currentIdx] || state.selectedEntry
      if (currentEntry) {
        selectWordbookEntry(currentEntry, { silent: true })
        const modal = elements.wordbookCardModal || document.getElementById('wordbookCardModal')
        if (modal) showModal(modal)
        const hasParsedDetail = currentEntry.parsed && typeof currentEntry.parsed === 'object' && Object.keys(currentEntry.parsed).length > 0
        if (!state.preview && !hasParsedDetail) {
          loadWordbookEntryDetail(currentEntry)
        }
      }
    })
  }

  function handleAiTaskUpdated(event) {
    const task = event?.detail
    if (!task) return
    const isWordCardTask = task.taskType === 'vocabulary_card_single' || task.taskType === 'vocabulary_card_batch'
    if (!isWordCardTask) return
    if (task.status === 'completed' || task.status === 'failed') {
      const matchingEntry = state.wordbookEntries?.find((item) => sameId(item.id, task.businessId))
      if (matchingEntry) {
        if (task.status === 'completed') {
          loadWordbookEntryDetail(matchingEntry)
        } else {
          matchingEntry.cardStatus = 'failed'
          matchingEntry.cardErrorMessage = task.errorMessage || '生成失败'
          renderWordbookEntries()
        }
      } else {
        loadWordbookEntries()
      }
    }
  }

  window.addEventListener('learning:ai-task-updated', handleAiTaskUpdated)

  function renderWordbookFocus(entry) {
    return detailFeature.renderWordbookFocus(entry)
  }

  function renderWordbookEntries() {
    const currentlySpeakingId = elements.wordbookEntryList?.querySelector('tr.wordbook-row-speaking')?.getAttribute('data-entry-id')
    entryListFeature.render()
    updateMissingCardBadge()
    const targetSpeakingId = currentlySpeakingId || (audioPlayer && (audioPlayer.isPlaying() || audioPlayer.isPaused()) ? state.wordbookEntries?.[audioPlayer.getCurrentIndex()]?.id : null)
    if (targetSpeakingId) {
      const row = elements.wordbookEntryList?.querySelector(`tr[data-entry-id="${targetSpeakingId}"]`)
      row?.classList.add('wordbook-row-speaking')
    }
  }

  function loadWordbooks(options = {}) {
    const loadEntries = options.loadEntries !== false
    if (state.preview) {
      renderWordbooks()
      renderPublicCatalogs()
      if (loadEntries) renderWordbookEntries()
      renderProfileMetrics()
      return Promise.resolve()
    }
    if (!state.token) {
      renderWordbooks()
      renderPublicCatalogs()
      return Promise.resolve()
    }
    return Promise.allSettled([
      request('/api/v1/learning/wordbooks'),
      request('/api/v1/vocabulary-imports/public'),
    ]).then(([wordbooksRes, publicCatalogsRes]) => {
      if (wordbooksRes.status === 'fulfilled') {
        state.wordbooks = normalizeWordbooks(wordbooksRes.value)
      }
      if (publicCatalogsRes.status === 'fulfilled') {
        state.publicVocabularyCatalogs = Array.isArray(publicCatalogsRes.value) ? publicCatalogsRes.value : []
      }
      renderWordbooks()
      renderPublicCatalogs()
      return loadEntries ? loadWordbookEntries() : undefined
    }).catch((error) => {
      logEvent('error', '单词本加载失败', error.message)
      toast(`单词本加载失败：${error.message}`)
    })
  }

  function renderPublicCatalogs() {
    if (!elements.profilePublicCatalogCards) return
    const catalogs = Array.isArray(state.publicVocabularyCatalogs) ? state.publicVocabularyCatalogs : []
    if (elements.profilePublicCatalogSummary) {
      elements.profilePublicCatalogSummary.textContent = `${catalogs.length} 本公共词本`
    }
    if (!catalogs.length) {
      elements.profilePublicCatalogCards.className = 'wordbook-cards empty'
      elements.profilePublicCatalogCards.textContent = '暂无已发布的公共词本'
      return
    }
    const SOURCE_LABELS = {
      primary_to_middle: '小升初',
      ncee: '高考',
      self_study: '自考',
      toefl: '托福',
      cet4: '四级',
      cet6: '六级',
      ielts: '雅思',
    }
    elements.profilePublicCatalogCards.className = 'wordbook-cards'
    elements.profilePublicCatalogCards.innerHTML = catalogs
      .map(
        (item) => `
          <div class="wordbook-card public-catalog-card">
            <button class="wordbook-main" type="button" data-public-catalog-job="${escapeHtml(item.jobId || item.catalogVersionId || '')}">
              <strong>${escapeHtml(item.catalogName)}</strong>
              <span>${escapeHtml(item.learningPurpose || '官方精选公共词本，点击查看词条详情')}</span>
              <small>${SOURCE_LABELS[item.sourceType] || item.sourceType || '公共词本'} · 共 ${item.totalCount || 0} 个单词 · 只读详情</small>
            </button>
            <div class="row-actions">
              <button class="secondary-button compact" type="button" data-public-catalog-preview="${escapeHtml(item.jobId || item.catalogVersionId || '')}">查看词表</button>
            </div>
          </div>
        `,
      )
      .join('')

    const handlePreview = (jobId) => {
      if (!jobId) {
        toast('该公共词本暂无可查看的导入明细')
        return
      }
      ctx.openImportReview?.(jobId)
    }

    elements.profilePublicCatalogCards.querySelectorAll('[data-public-catalog-job]').forEach((button) => {
      button.addEventListener('click', () => handlePreview(button.getAttribute('data-public-catalog-job')))
    })
    elements.profilePublicCatalogCards.querySelectorAll('[data-public-catalog-preview]').forEach((button) => {
      button.addEventListener('click', () => handlePreview(button.getAttribute('data-public-catalog-preview')))
    })
  }

  function renderWordbooks() {
    elements.wordbookSelect.innerHTML = ''
    elements.reviewWordbookSelect.innerHTML = ''
    if (elements.articleWordbookSelect) elements.articleWordbookSelect.innerHTML = ''
    if (!state.wordbooks.length) {
      elements.wordbookSelect.innerHTML = '<option value="">暂无单词本</option>'
      elements.reviewWordbookSelect.innerHTML = '<option value="">暂无单词本</option>'
      if (elements.articleWordbookSelect) elements.articleWordbookSelect.innerHTML = '<option value="">暂无单词本</option>'
      elements.wordbookCards.className = 'wordbook-cards empty'
      elements.wordbookCards.textContent = state.token ? '暂无个人单词本' : '登录后查看单词本'
      renderProfileMetrics()
      renderPublicCatalogs()
      return
    }

    resolveSelectedWordbookId(state, elements)

    for (const wordbook of state.wordbooks) {
      const option = document.createElement('option')
      option.value = String(wordbook.id)
      option.textContent = `${wordbook.name} · ${wordbook.entryCount || 0}词`
      elements.wordbookSelect.appendChild(option)
      elements.reviewWordbookSelect.appendChild(option.cloneNode(true))
      if (elements.articleWordbookSelect) elements.articleWordbookSelect.appendChild(option.cloneNode(true))
    }
    elements.wordbookSelect.value = String(state.currentWordbookId)
    elements.reviewWordbookSelect.value = String(state.currentWordbookId)
    if (elements.articleWordbookSelect) elements.articleWordbookSelect.value = String(state.currentWordbookId)

    elements.wordbookCards.className = 'wordbook-cards'
    elements.wordbookCards.innerHTML = state.wordbooks
      .map(
        (item) => `
          <div class="wordbook-card ${sameId(item.id, state.currentWordbookId) ? 'active' : ''}">
            <button class="wordbook-main" type="button" data-wordbook-id="${escapeHtml(item.id)}">
              <strong>${escapeHtml(item.name)}</strong>
              <span>${escapeHtml(item.description || (item.isDefault ? '默认单词本' : '自定义单词本'))}</span>
              <small>${item.isDefault ? '默认 · ' : ''}${item.entryCount || 0} 个单词</small>
            </button>
            <div class="row-actions">
              <button class="icon-action-button" type="button" data-wordbook-edit="${escapeHtml(item.id)}" title="编辑单词本" aria-label="编辑单词本">✎</button>
              <button class="danger-icon-button" type="button" data-wordbook-delete="${escapeHtml(item.id)}" title="删除单词本">×</button>
            </div>
          </div>
        `,
      )
      .join('')
    elements.wordbookCards.querySelectorAll('[data-wordbook-id]').forEach((button) => {
      button.addEventListener('click', () => changeWordbook(button.getAttribute('data-wordbook-id')))
    })
    elements.wordbookCards.querySelectorAll('[data-wordbook-edit]').forEach((button) => {
      button.addEventListener('click', () => openWordbookModal(button.getAttribute('data-wordbook-edit')))
    })
    elements.wordbookCards.querySelectorAll('[data-wordbook-delete]').forEach((button) => {
      button.addEventListener('click', () => deleteWordbook(button.getAttribute('data-wordbook-delete')))
    })
    renderProfileMetrics()
    renderPublicCatalogs()
  }

  async function changeWordbook(wordbookId) {
    syncCurrentWordbookId(state, elements, wordbookId)
    state.wordbookPage = 1
    state.selectedArticleEntryIds = []
    state.currentArticleRecord = null
    state.articleRecords = []
    renderWordbooks()
    await Promise.allSettled([loadWordbookEntries(), loadDueReviewsFromCtx()])
    logEvent('wordbook', '切换单词本', currentWordbookName())
  }

  function openWordbookModal(id = null) {
    if (id) {
      fillWordbookForm(id)
      elements.wordbookModalTitle.textContent = '编辑单词本'
    } else {
      resetWordbookForm({ keepModalOpen: true })
      elements.wordbookModalTitle.textContent = '新增单词本'
    }
    showModal(elements.wordbookModal)
  }

  function closeWordbookModal() {
    hideModal(elements.wordbookModal)
  }

  async function createWordbook() {
    const name = elements.newWordbookInput.value.trim()
    if (!name) {
      toast('请输入新单词本名称')
      return
    }
    if (!state.token) {
      toast('请先登录')
      return
    }
    setLoading(true)
    try {
      const payload = {
        name,
        description: elements.wordbookDescriptionInput.value.trim(),
        isDefault: elements.wordbookDefaultInput.checked,
      }
      if (state.preview) {
        const id = state.currentWordbookEditId || String(Date.now())
        if (payload.isDefault) state.wordbooks.forEach((item) => (item.isDefault = false))
        const index = state.wordbooks.findIndex((item) => sameId(item.id, id))
        const existing = index >= 0 ? state.wordbooks[index] : {}
        const next = { ...existing, id, ...payload, entryCount: existing.entryCount ?? 0, dueCount: existing.dueCount ?? 0 }
        if (index >= 0) state.wordbooks.splice(index, 1, { ...state.wordbooks[index], ...next })
        else state.wordbooks.push(next)
        syncCurrentWordbookId(state, elements, id)
        resetWordbookForm()
        closeWordbookModal()
        renderWordbooks()
        toast('设计预览：单词本已保存')
        return
      }
      const path = state.currentWordbookEditId ? `/api/v1/learning/wordbooks/${state.currentWordbookEditId}` : '/api/v1/learning/wordbooks'
      const method = state.currentWordbookEditId ? 'PUT' : 'POST'
      const wordbook = await request(path, { method, body: JSON.stringify(payload) })
      resetWordbookForm()
      closeWordbookModal()
      syncCurrentWordbookId(state, elements, wordbook.id)
      await loadWordbooks()
      logEvent('wordbook', method === 'PUT' ? '更新单词本' : '创建单词本', name)
      toast('单词本已保存')
    } catch (error) {
      logEvent('error', '保存单词本失败', error.message)
      toast(`保存单词本失败：${error.message}`)
    } finally {
      setLoading(false)
    }
  }

  function fillWordbookForm(id) {
    const wordbook = state.wordbooks.find((item) => sameId(item.id, id))
    if (!wordbook) return
    state.currentWordbookEditId = wordbook.id
    elements.newWordbookInput.value = wordbook.name || ''
    elements.wordbookDescriptionInput.value = wordbook.description || ''
    elements.wordbookDefaultInput.checked = Boolean(wordbook.isDefault)
  }

  function resetWordbookForm(options = {}) {
    state.currentWordbookEditId = null
    elements.newWordbookInput.value = ''
    elements.wordbookDescriptionInput.value = ''
    elements.wordbookDefaultInput.checked = false
    if (!options.keepModalOpen) {
      closeWordbookModal()
    }
  }

  async function deleteWordbook(id) {
    const wordbook = state.wordbooks.find((item) => sameId(item.id, id))
    if (!wordbook) return
    const confirmed = await confirmDelete({
      title: '删除单词本',
      message: `确认删除单词本「${wordbook.name}」？单词本中存在单词时后端会拒绝删除。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.wordbooks = state.wordbooks.filter((item) => !sameId(item.id, id))
      if (!state.wordbooks.length) {
        state.wordbooks = [{ id: 1, name: '默认单词本', description: '日常学习沉淀', isDefault: true, entryCount: 0, dueCount: 0 }]
      }
      const fallback = state.wordbooks.find((item) => item.isDefault) || state.wordbooks[0]
      fallback.isDefault = true
      syncCurrentWordbookId(state, elements, fallback.id)
      renderWordbooks()
      renderWordbookEntries()
      renderActivityHeatmap()
      toast('设计预览：单词本已删除')
      return
    }
    try {
      await request(`/api/v1/learning/wordbooks/${encodeURIComponent(id)}`, { method: 'DELETE' })
      if (sameId(state.currentWordbookId, id)) {
        syncCurrentWordbookId(state, elements, null)
      }
      await Promise.allSettled([loadWordbooks(), loadDueReviewsFromCtx(), loadActivity()])
      toast('单词本已删除')
    } catch (error) {
      logEvent('error', '删除单词本失败', error.message)
      toast(`删除单词本失败：${error.message}`)
    }
  }

  function loadWordbookEntries() {
    if (state.preview) {
      renderWordbookEntries()
      renderProfileMetrics()
      return Promise.resolve()
    }
    if (!state.token || !state.currentWordbookId) {
      state.wordbookEntries = []
      renderWordbookEntries()
      return Promise.resolve()
    }
    const status = elements.wordStatusFilter?.value || ''
    const cardStatus = elements.wordCardStatusFilter?.value || ''
    const keyword = elements.wordPrefixInput?.value?.trim() || ''
    const params = new URLSearchParams()
    if (status) params.set('status', status)
    if (cardStatus) params.set('cardStatus', cardStatus)
    if (keyword) params.set('keyword', keyword)
    params.set('page', String(state.wordbookPage || 1))
    params.set('pageSize', String(state.wordbookPageSize || 30))
    return request(`/api/v1/learning/wordbooks/${encodeURIComponent(state.currentWordbookId)}/entries?${params.toString()}`)
      .then((result) => {
        const entries = Array.isArray(result) ? result : result?.items
        state.wordbookEntries = Array.isArray(entries) ? entries : []
        state.wordbookTotal = Number(result?.total || state.wordbookEntries.length)
        state.wordbookPage = Number(result?.page || state.wordbookPage || 1)
        renderWordbookEntries()
        renderProfileMetrics()
      })
      .catch((error) => {
        logEvent('error', '单词本加载失败', error.message)
      })
  }

  function searchWordbookEntries() {
    audioPlayer.stop()
    state.wordbookPage = 1
    return loadWordbookEntries()
  }

  function changeWordbookPage(delta) {
    audioPlayer.stop()
    const totalCount = Number(state.wordbookTotal ?? state.wordbookEntries.length)
    const maxPage = Math.max(1, Math.ceil(totalCount / (state.wordbookPageSize || 30)))
    const currentPage = Number(state.wordbookPage || 1)
    const next = Math.max(1, Math.min(maxPage, currentPage + delta))
    if (next === currentPage) return
    state.wordbookPage = next
    loadWordbookEntries()
  }

  function jumpToWordbookPage(targetPage) {
    audioPlayer.stop()
    const totalCount = Number(state.wordbookTotal ?? state.wordbookEntries.length)
    const maxPage = Math.max(1, Math.ceil(totalCount / (state.wordbookPageSize || 30)))
    const pageNum = parseInt(targetPage, 10)
    if (isNaN(pageNum) || pageNum < 1) return
    const next = Math.max(1, Math.min(maxPage, pageNum))
    if (next === state.wordbookPage) return
    state.wordbookPage = next
    loadWordbookEntries()
  }

  async function loadWordbookEntryDetail(entry) {
    if (!entry?.id || state.preview || !state.token) return entry
    try {
      const detail = await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entry.id)}`)
      if (!detail) return entry
      const index = state.wordbookEntries.findIndex((item) => sameId(item.id, entry.id))
      const merged = index >= 0 ? { ...state.wordbookEntries[index], ...detail } : detail
      if (index >= 0) state.wordbookEntries.splice(index, 1, merged)
      if (!sameId(state.selectedEntry?.id, entry.id)) return merged
      state.selectedEntry = merged
      renderWordbookFocus(merged)
      ctx.renderNotes(merged)
      renderWordbookEntries()
      return merged
    } catch (error) {
      logEvent('error', '词卡详情加载失败', error.message)
      toast(`词卡详情加载失败：${error.message}`)
      return entry
    }
  }

  function openEntryStatusModal(entryId) {
    const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
    if (!entry) return
    state.currentStatusEntryId = entry.id
    elements.entryStatusTerm.textContent = entry.term || entry.normalizedTerm || '当前单词'
    showModal(elements.entryStatusModal)
  }

  function closeEntryStatusModal() {
    hideModal(elements.entryStatusModal)
    state.currentStatusEntryId = null
  }

  async function chooseEntryStatus(status) {
    const entryId = state.currentStatusEntryId
    if (!entryId) return
    await updateEntryStatus(entryId, status)
    closeEntryStatusModal()
  }

  async function updateEntryStatus(entryId, status) {
    await saveEntry(entryId, { status })
  }

  async function deleteWordbookEntry(entryId) {
    const entry = state.wordbookEntries.find((item) => sameId(item.id, entryId)) || state.reviewEntries.find((item) => sameId(item.id, entryId))
    const term = entry?.term || entry?.normalizedTerm || '当前单词'
    const confirmed = await confirmDelete({
      title: '删除单词',
      message: `确认从单词本中删除「${term}」？删除后这个单词的笔记和复习计划会从当前单词本移除。`,
    })
    if (!confirmed) return
    if (state.preview) {
      state.wordbookEntries = state.wordbookEntries.filter((entry) => !sameId(entry.id, entryId))
      state.reviewEntries = state.reviewEntries.filter((entry) => !sameId(entry.id, entryId))
      state.selectedEntry = null
      renderWordbookEntries()
      ctx.renderReviewQueue(state.reviewEntries)
      toast('设计预览：已从单词本删除')
      return
    }
    try {
      await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entryId)}`, { method: 'DELETE' })
      await Promise.allSettled([loadWordbooks(), loadWordbookEntries(), loadDueReviewsFromCtx()])
      loadActivity()?.catch?.(() => {})
      toast('已从单词本删除')
    } catch (error) {
      logEvent('error', '删除词条失败', error.message)
      toast(`删除词条失败：${error.message}`)
    }
  }

  async function generateEntryCardWithConfirm(entry, button) {
    const term = entry.term || entry.normalizedTerm || '该单词'
    const confirmed = typeof confirmAction === 'function'
      ? await confirmAction({
          title: '生成 AI 词卡',
          message: `确定要为单词「${term}」调用 AI 大模型生成完整结构化词卡（音标、释义、权威例句、场景搭配、助记）吗？`,
          acceptText: '立即生成',
          danger: false,
        })
      : window.confirm(`确定要为单词「${term}」生成 AI 词卡吗？`)
    if (!confirmed) return

    if (state.preview) {
      entry.cardStatus = 'ready'
      renderWordbookEntries()
      toast?.(`设计预览：已模拟为「${term}」生成 AI 词卡`)
      return
    }

    if (button) {
      button.disabled = true
      button.classList.add('generating')
      button.innerHTML = `<span class="card-loading-spinner"></span>生成中...`
    }
    try {
      await request(`/api/v1/learning/wordbook-entries/${encodeURIComponent(entry.id)}/generate-card/async?forceRefresh=true`, {
        method: 'POST',
      })
      const updated = { ...entry, cardStatus: 'generating', cardErrorMessage: null }
      if (updated) {
        const idx = state.wordbookEntries.findIndex((item) => sameId(item.id, entry.id))
        if (idx >= 0) {
          state.wordbookEntries[idx] = {
            ...state.wordbookEntries[idx],
            ...updated,
            cardStatus: 'generating',
            phonetic: updated.phonetic || state.wordbookEntries[idx].phonetic,
            meaningText: updated.meaningText || state.wordbookEntries[idx].meaningText,
          }
        }
        if (sameId(state.selectedEntry?.id, entry.id)) {
          state.selectedEntry = state.wordbookEntries[idx] || updated
          renderWordbookFocus(state.selectedEntry)
        }
        renderWordbookEntries()
        logEvent?.('ai', '提交词卡生成任务', term)
        toast?.(`单词「${term}」词卡任务已提交，可在任务中心查看进度`)
      }
    } catch (err) {
      logEvent?.('error', '生成词卡失败', err.message)
      toast?.(`生成词卡失败：${err.message}`)
      renderWordbookEntries()
    }
  }

  function entryMatchesKeyword(entry, keyword) {
    const values = [
      entry.term,
      entry.normalizedTerm,
      entry.parsed?.term,
      entry.note,
      entry.meaningText,
      ...(Array.isArray(entry.parsed?.definitions) ? entry.parsed.definitions.map((d) => d.cn || d.en || '') : []),
    ]
    return values.some((value) => String(value || '').trim().toLowerCase().includes(keyword))
  }

  function updateActiveEntryRow(entryId) {
    if (!elements.wordbookEntryList) return
    elements.wordbookEntryList.querySelectorAll('.wordbook-row').forEach((row) => {
      row.classList.toggle('active', sameId(row.getAttribute('data-entry-id'), entryId))
    })
  }

  function selectWordbookEntry(entry, options = {}) {
    if (!entry) {
      renderWordbookFocus(null)
      return
    }
    state.selectedEntry = entry
    renderWordbookFocus(entry)
    ctx.renderNotes(entry)
    if (!options.silent) {
      renderWordbookEntries()
    } else {
      updateActiveEntryRow(entry.id)
    }

    const isCardModalOpen = Boolean(elements.wordbookCardModal && !elements.wordbookCardModal.classList.contains('hidden'))
    const hasParsedDetail = entry.parsed && typeof entry.parsed === 'object' && Object.keys(entry.parsed).length > 0
    if (!state.preview && !hasParsedDetail && (isCardModalOpen || options.loadDetail)) {
      loadWordbookEntryDetail(entry)
    }
  }

  let isNavigatingCard = false

  async function navigateWordbookCard(delta) {
    if (isNavigatingCard) return
    const entries = Array.isArray(state.wordbookEntries) ? state.wordbookEntries : []
    if (!entries.length) return
    const currentIndex = entries.findIndex((item) => sameId(item.id, state.selectedEntry?.id))
    const baseIndex = currentIndex >= 0 ? currentIndex : 0
    const targetIndex = baseIndex + delta

    // Within current page
    if (targetIndex >= 0 && targetIndex < entries.length) {
      const nextEntry = entries[targetIndex]
      selectWordbookEntry(nextEntry, { silent: true })
      if (elements.wordbookFocus) {
        elements.wordbookFocus.scrollTop = 0
      }
      const hasParsedDetail = nextEntry.parsed && typeof nextEntry.parsed === 'object' && Object.keys(nextEntry.parsed).length > 0
      if (!state.preview && !hasParsedDetail) {
        loadWordbookEntryDetail(nextEntry)
      }
      return
    }

    // Cross-page navigation
    const totalCount = Number(state.wordbookTotal ?? entries.length)
    const pageSize = Number(state.wordbookPageSize || 30)
    const maxPage = Math.max(1, Math.ceil(totalCount / pageSize))
    const currentPage = Number(state.wordbookPage || 1)

    // Forward cross-page (targetIndex >= entries.length)
    if (delta > 0 && targetIndex >= entries.length && currentPage < maxPage) {
      isNavigatingCard = true
      if (elements.wordbookCardNextBtn) elements.wordbookCardNextBtn.disabled = true
      try {
        state.wordbookPage = currentPage + 1
        await loadWordbookEntries()
        const newEntries = Array.isArray(state.wordbookEntries) ? state.wordbookEntries : []
        if (newEntries.length > 0) {
          const nextEntry = newEntries[0]
          selectWordbookEntry(nextEntry, { silent: true })
          if (elements.wordbookFocus) {
            elements.wordbookFocus.scrollTop = 0
          }
          const hasParsedDetail = nextEntry.parsed && typeof nextEntry.parsed === 'object' && Object.keys(nextEntry.parsed).length > 0
          if (!state.preview && !hasParsedDetail) {
            loadWordbookEntryDetail(nextEntry)
          }
        }
      } finally {
        isNavigatingCard = false
        detailFeature.updateOuterCardControls(state.selectedEntry)
      }
      return
    }

    // Backward cross-page (targetIndex < 0)
    if (delta < 0 && targetIndex < 0 && currentPage > 1) {
      isNavigatingCard = true
      if (elements.wordbookCardPrevBtn) elements.wordbookCardPrevBtn.disabled = true
      try {
        state.wordbookPage = currentPage - 1
        await loadWordbookEntries()
        const newEntries = Array.isArray(state.wordbookEntries) ? state.wordbookEntries : []
        if (newEntries.length > 0) {
          const prevEntry = newEntries[newEntries.length - 1]
          selectWordbookEntry(prevEntry, { silent: true })
          if (elements.wordbookFocus) {
            elements.wordbookFocus.scrollTop = 0
          }
          const hasParsedDetail = prevEntry.parsed && typeof prevEntry.parsed === 'object' && Object.keys(prevEntry.parsed).length > 0
          if (!state.preview && !hasParsedDetail) {
            loadWordbookEntryDetail(prevEntry)
          }
        }
      } finally {
        isNavigatingCard = false
        detailFeature.updateOuterCardControls(state.selectedEntry)
      }
      return
    }
  }

  function currentWordbookName(wordbookId = state.currentWordbookId) {
    return state.wordbooks.find((item) => sameId(item.id, wordbookId))?.name || '所选单词本'
  }

  function toggleWordbookFocusMode(forceState = null) {
    const next = typeof forceState === 'boolean' ? forceState : !state.wordbookFocusMode
    state.wordbookFocusMode = next
    elements.wordbookHeaderPanel?.classList.toggle('hidden', next)
    elements.wordbookListPanel?.classList.toggle('hidden', next)
    elements.wordbookLayout?.classList.toggle('wordbook-focus-layout', next)
    if (elements.toggleWordbookFocusModeBtn) {
      elements.toggleWordbookFocusModeBtn.textContent = next ? '退出专注' : '专注模式'
      elements.toggleWordbookFocusModeBtn.classList.toggle('active', next)
    }
  }

  return {
    loadWordbooks,
    renderWordbooks,
    changeWordbook,
    openWordbookModal,
    closeWordbookModal,
    createWordbook,
    fillWordbookForm,
    resetWordbookForm,
    deleteWordbook,
    loadWordbookEntries,
    searchWordbookEntries,
    changeWordbookPage,
    jumpToWordbookPage,
    loadWordbookEntryDetail,
    renderWordbookEntries,
    selectWordbookEntry,
    navigateWordbookCard,
    ...detailFeature,
    openEntryStatusModal,
    closeEntryStatusModal,
    chooseEntryStatus,
    updateEntryStatus,
    deleteWordbookEntry,
    currentWordbookName,
    toggleWordbookFocusMode,
    batchGenerateMissingCards,
    toggleWordbookAudio: () => audioPlayer.toggle(),
    stopWordbookAudio: () => audioPlayer.stop(),
  }
}
