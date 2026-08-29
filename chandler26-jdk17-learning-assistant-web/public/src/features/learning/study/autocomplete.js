import { escapeHtml } from '/src/shared/text.js'

const RECENT_SEARCHES_KEY = 'learning.recentSearches'
const MAX_RECENT = 8

export function createStudyAutocomplete({
  inputElement,
  dropdownElement,
  recentBarElement,
  recentChipsElement,
  clearRecentBtn,
  api,
  onSelectTerm,
}) {
  let activeIndex = -1
  let currentSuggestions = []
  let debounceTimer = null

  function getRecentSearches() {
    try {
      const data = localStorage.getItem(RECENT_SEARCHES_KEY)
      return data ? JSON.parse(data) : []
    } catch {
      return []
    }
  }

  function saveRecentSearch(term) {
    const clean = String(term || '').trim().toLowerCase()
    if (!clean) return
    const list = getRecentSearches().filter((t) => t.toLowerCase() !== clean)
    list.unshift(clean)
    try {
      localStorage.setItem(RECENT_SEARCHES_KEY, JSON.stringify(list.slice(0, MAX_RECENT)))
    } catch {}
    renderRecentSearches()
  }

  function clearRecentSearches() {
    try {
      localStorage.removeItem(RECENT_SEARCHES_KEY)
    } catch {}
    renderRecentSearches()
  }

  function renderRecentSearches() {
    if (!recentBarElement || !recentChipsElement) return
    const list = getRecentSearches()
    if (!list.length) {
      recentBarElement.classList.add('hidden')
      return
    }
    recentBarElement.classList.remove('hidden')
    recentChipsElement.innerHTML = list
      .map((term) => `<button type="button" class="recent-chip" data-recent-term="${escapeHtml(term)}">${escapeHtml(term)}</button>`)
      .join('')

    recentChipsElement.querySelectorAll('[data-recent-term]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const term = btn.getAttribute('data-recent-term')
        if (term) {
          if (inputElement) inputElement.value = term
          hideDropdown()
          onSelectTerm(term)
        }
      })
    })
  }

  function hideDropdown() {
    if (dropdownElement) {
      dropdownElement.classList.add('hidden')
      dropdownElement.innerHTML = ''
    }
    activeIndex = -1
    currentSuggestions = []
  }

  function showDropdown(items) {
    if (!dropdownElement) return
    currentSuggestions = items
    activeIndex = -1
    if (!items.length) {
      dropdownElement.classList.add('hidden')
      dropdownElement.innerHTML = ''
      return
    }
    dropdownElement.classList.remove('hidden')
    dropdownElement.innerHTML = items
      .map(
        (item, index) => `
          <div class="autocomplete-item" data-index="${index}" data-term="${escapeHtml(item.term || item.normalizedTerm)}">
            <div class="autocomplete-term">
              <strong>${escapeHtml(item.term || item.normalizedTerm)}</strong>
              ${item.partOfSpeech ? `<span class="autocomplete-pos">${escapeHtml(item.partOfSpeech)}</span>` : ''}
            </div>
            <div class="autocomplete-meaning">${escapeHtml(item.meaning || '查看详细释义')}</div>
          </div>
        `,
      )
      .join('')

    dropdownElement.querySelectorAll('.autocomplete-item').forEach((el) => {
      el.addEventListener('mousedown', (event) => {
        event.preventDefault()
        const term = el.getAttribute('data-term')
        if (term) {
          if (inputElement) inputElement.value = term
          hideDropdown()
          onSelectTerm(term)
        }
      })
    })
  }

  function updateHighlight() {
    if (!dropdownElement) return
    const items = dropdownElement.querySelectorAll('.autocomplete-item')
    items.forEach((item, index) => {
      item.classList.toggle('highlighted', index === activeIndex)
      if (index === activeIndex) {
        item.scrollIntoView({ block: 'nearest' })
      }
    })
  }

  async function fetchSuggestions(keyword) {
    const clean = String(keyword || '').trim()
    if (!clean) {
      hideDropdown()
      return
    }
    try {
      const res = await api.getSuggestions(clean)
      showDropdown(Array.isArray(res) ? res : [])
    } catch {
      hideDropdown()
    }
  }

  function bind() {
    renderRecentSearches()
    clearRecentBtn?.addEventListener('click', clearRecentSearches)

    if (inputElement) {
      inputElement.addEventListener('input', () => {
        const val = inputElement.value
        clearTimeout(debounceTimer)
        if (!val.trim()) {
          hideDropdown()
          return
        }
        debounceTimer = setTimeout(() => {
          fetchSuggestions(val)
        }, 120)
      })

      inputElement.addEventListener('keydown', (event) => {
        if (dropdownElement && !dropdownElement.classList.contains('hidden') && currentSuggestions.length) {
          if (event.key === 'ArrowDown') {
            event.preventDefault()
            activeIndex = (activeIndex + 1) % currentSuggestions.length
            updateHighlight()
            return
          }
          if (event.key === 'ArrowUp') {
            event.preventDefault()
            activeIndex = (activeIndex - 1 + currentSuggestions.length) % currentSuggestions.length
            updateHighlight()
            return
          }
          if (event.key === 'Enter' && activeIndex >= 0 && activeIndex < currentSuggestions.length) {
            event.preventDefault()
            const selected = currentSuggestions[activeIndex]
            const term = selected.term || selected.normalizedTerm
            inputElement.value = term
            hideDropdown()
            onSelectTerm(term)
            return
          }
          if (event.key === 'Escape') {
            hideDropdown()
            return
          }
        }
      })

      inputElement.addEventListener('blur', () => {
        setTimeout(hideDropdown, 200)
      })
    }
  }

  return {
    bind,
    saveRecentSearch,
    renderRecentSearches,
    hideDropdown,
  }
}
