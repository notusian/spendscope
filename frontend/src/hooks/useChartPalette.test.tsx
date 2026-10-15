import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, expect, it, vi } from 'vitest'
import { useChartPalette } from './useChartPalette'

afterEach(() => {
  vi.unstubAllGlobals()
  document.documentElement.removeAttribute('data-theme')
  for (let index = 1; index <= 8; index++) {
    document.documentElement.style.removeProperty(`--chart-${index}`)
  }
})

it('refreshes chart colours when the selected theme changes', async () => {
  vi.stubGlobal('matchMedia', vi.fn(() => ({ addEventListener: vi.fn(), removeEventListener: vi.fn() })))
  for (let index = 1; index <= 8; index++) {
    document.documentElement.style.setProperty(`--chart-${index}`, '#10294b')
  }
  const { result, unmount } = renderHook(useChartPalette)
  expect(result.current[0]).toBe('#10294b')
  await act(async () => {
    for (let index = 1; index <= 8; index++) {
      document.documentElement.style.setProperty(`--chart-${index}`, '#77cde1')
    }
    document.documentElement.dataset.theme = 'dark'
  })
  await waitFor(() => expect(result.current[0]).toBe('#77cde1'))
  unmount()
})
