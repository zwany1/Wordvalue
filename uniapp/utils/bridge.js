let native = null
let probed = false

function plugin() {
  if (probed) return native
  probed = true
  try {
    native = uni.requireNativePlugin('QingshengIME')
  } catch (e) {
    native = null
  }
  return native
}

export function isNativeAvailable() {
  return plugin() !== null
}

export function getImeStatus() {
  const p = plugin()
  if (p) return p.getImeStatus()
  return { enabled: false }
}

export function getSettings() {
  const p = plugin()
  if (p) return p.getSettings()
  return { defaultStyle: 'NATURAL', maxReplyLength: 30, replyCount: 3, saveHistory: true }
}

export function setStyle(style) {
  const p = plugin()
  if (p) return p.setStyle(style)
  return false
}

export function setMaxReplyLength(length) {
  const p = plugin()
  if (p) p.setMaxReplyLength(length)
}

export function setSaveHistory(value) {
  const p = plugin()
  if (p) p.setSaveHistory(value)
}

export function listTargets() {
  const p = plugin()
  if (p) return p.listTargets()
  return []
}

export function getHistory() {
  const p = plugin()
  if (p) return p.getHistory()
  return []
}

export function openNativeSettings() {
  const p = plugin()
  if (p) p.openNativeSettings()
}

export function openImeSettings() {
  const p = plugin()
  if (p) p.openImeSettings()
}
