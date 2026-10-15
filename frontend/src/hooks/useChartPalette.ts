import { useEffect, useState } from 'react'

/**
 * Reads the chart palette out of the CSS custom properties.
 *
 * Resolving the CSS tokens here keeps SVG colours and HTML tooltips in sync
 * when the selected theme or the system colour preference changes.
 */

const PALETTE_SIZE = 8

const FALLBACK = [
  '#2f6df6', '#12a594', '#d4761d', '#8b5cf6',
  '#d64571', '#0e8a5f', '#b8860b', '#5b7183',
]

function readPalette(): string[] {
  if (typeof window === 'undefined') {
    return FALLBACK
  }
  const styles = getComputedStyle(document.documentElement)
  const colours = Array.from({ length: PALETTE_SIZE }, (_, index) =>
    styles.getPropertyValue(`--chart-${index + 1}`).trim(),
  )
  // A jsdom test environment resolves custom properties to empty strings.
  return colours.every(Boolean) ? colours : FALLBACK
}

export function useChartPalette(): string[] {
  const [palette, setPalette] = useState<string[]>(readPalette)

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const update = () => setPalette(readPalette())
    const observer = new MutationObserver(update)
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] })
    media.addEventListener('change', update)
    update()
    return () => {
      media.removeEventListener('change', update)
      observer.disconnect()
    }
  }, [])

  return palette
}

/** Reads a single named token, e.g. `--text-muted`. */
export function readToken(token: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim()
  return value || fallback
}
