function splitMarkdownRow(line) {
  let value = String(line || '').trim()
  if (value.startsWith('|')) value = value.slice(1)
  if (value.endsWith('|')) value = value.slice(0, -1)
  return value.split(/(?<!\\)\|/).map((cell) => cell.trim().replace(/\\\|/g, '|'))
}

function cleanMarkdownCell(value) {
  const trimmed = String(value || '').trim()
  return trimmed.startsWith('`') && trimmed.endsWith('`') ? trimmed.slice(1, -1).trim() : trimmed
}

export function suggestedSplitCorrection(term) {
  const tokens = String(term || '').trim().split(/\s+/)
  for (let index = 0; index < tokens.length; index += 1) {
    const token = tokens[index]
    if (!/^[a-z]$/i.test(token)) continue
    if (index === tokens.length - 1 && index > 0 && (tokens[index - 1].match(/[a-z]/gi) || []).length >= 4) {
      return [...tokens.slice(0, index - 1), `${tokens[index - 1]}${token}`].join(' ')
    }
    if (index < tokens.length - 1 && !/^[ai]$/i.test(token) && (tokens[index + 1].match(/[a-z]/gi) || []).length >= 4) {
      return [...tokens.slice(0, index), `${token}${tokens[index + 1]}`, ...tokens.slice(index + 2)].join(' ')
    }
  }
  return term
}

export function parsePreviewMarkdown(markdown) {
  const lines = String(markdown || '').replace(/\r\n?/g, '\n').split('\n')
  let columns = null
  const items = []
  for (const line of lines) {
    if (!line.includes('|')) continue
    const cells = splitMarkdownRow(line)
    if (!columns) {
      const names = cells.map((cell) => cleanMarkdownCell(cell).toLowerCase())
      const candidate = {
        order: names.indexOf('序号'),
        word: names.indexOf('word'),
        phonetic: names.indexOf('音标'),
        definition: names.indexOf('释义'),
      }
      if (Object.values(candidate).every((index) => index >= 0)) columns = candidate
      continue
    }
    if (cells.every((cell) => /^:?-{3,}:?$/.test(cell))) continue
    const originalTerm = cleanMarkdownCell(cells[columns.word])
    const orderText = cleanMarkdownCell(cells[columns.order])
    if (!originalTerm && !orderText) continue
    const sourceOrder = Number(orderText)
    if (!Number.isInteger(sourceOrder)) throw new Error(`词表序号不是有效整数：${orderText}`)
    if (!originalTerm) throw new Error(`序号 ${sourceOrder} 的 Word 为空`)
    const suggestedTerm = suggestedSplitCorrection(originalTerm)
    const suspicious = suggestedTerm !== originalTerm
    items.push({
      id: items.length + 1,
      sourceOrder,
      originalTerm,
      suggestedTerm,
      approvedTerm: null,
      effectiveTerm: originalTerm,
      phonetic: cleanMarkdownCell(cells[columns.phonetic]),
      definition: cleanMarkdownCell(cells[columns.definition]),
      suspicious,
      reviewStatus: suspicious ? 'pending' : 'not_required',
      warnings: suspicious ? ['疑似断词'] : [],
    })
  }
  if (!columns) throw new Error('未找到包含“序号、Word、音标、释义”的 Markdown 表头')
  if (!items.length) throw new Error('Markdown 表格中没有可导入词条')
  const orders = new Set()
  const duplicate = items.find((item) => orders.has(item.sourceOrder) || !orders.add(item.sourceOrder))
  if (duplicate) throw new Error(`词表序号重复：${duplicate.sourceOrder}`)
  return items
}
