/**
 * Light or dark, remembered on this device.
 *
 * <p>Three states, not two: "system" follows the operating system and is the
 * default, and choosing light or dark stamps `data-theme` on the root element so
 * the explicit choice wins over the OS in both directions. The stylesheet does the
 * rest - it defines the dark tokens under both scopes.</p>
 *
 * <p>Every read and write is wrapped, because localStorage throws rather than
 * returning null in a private window or when a browser is set to block site data,
 * and a theme preference is not worth a blank screen.</p>
 */
const KEY = 'pom.theme'

export function readTheme() {
  try {
    const saved = localStorage.getItem(KEY)
    return saved === 'light' || saved === 'dark' ? saved : 'system'
  } catch {
    return 'system'
  }
}

export function applyTheme(theme) {
  const root = document.documentElement
  if (theme === 'light' || theme === 'dark') {
    root.setAttribute('data-theme', theme)
  } else {
    root.removeAttribute('data-theme')
  }
  try {
    if (theme === 'system') localStorage.removeItem(KEY)
    else localStorage.setItem(KEY, theme)
  } catch {
    // A preference that cannot be saved still applies for this visit.
  }
}

/** Called once at start-up, before React renders, so there is no flash of light. */
export function initTheme() {
  applyTheme(readTheme())
}
