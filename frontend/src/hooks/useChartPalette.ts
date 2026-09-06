import { useEffect, useState } from 'react'

/**
 * Reads the chart palette out of the CSS custom properties.
 *
 * Recharts needs real colour strings: `fill="var(--chart-1)"` does not resolve,
 * because SVG presentation attributes are not parsed as CSS values. Rather than
 * duplicating the palette in TypeScript and letting the two drift, this reads
 * the computed values from the stylesheet - so light and dark mode stay in sync
 * with one definition in global.css.
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
    media.addEventListener('change', update)
    return () => media.removeEventListener('change', update)
  }, [])

  return palette
}

/** Reads a single named token, e.g. `--text-muted`. */
export function readToken(token: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(token).trim()
  return value || fallback
}
