export type Theme = 'light' | 'dark'

const STORAGE_KEY = 'loldatahub-theme'

export function loadTheme(): Theme {
  if (typeof window === 'undefined') return 'light'
  const saved = window.localStorage.getItem(STORAGE_KEY)
  if (saved === 'light' || saved === 'dark') return saved
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

export function applyTheme(theme: Theme) {
  if (typeof document === 'undefined') return
  document.documentElement.dataset.theme = theme
  if (typeof window !== 'undefined') window.localStorage.setItem(STORAGE_KEY, theme)
}

export function initTheme() {
  applyTheme(loadTheme())
}
