import { describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ProgressBar, errorMessage } from './ui'
import { ApiError } from '../api/client'

describe('ProgressBar', () => {
  it('exposes the percentage to assistive technology', () => {
    render(<ProgressBar percentage={84} />)
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '84')
  })

  // Over-budget must stay visible without breaking the layout: the bar caps at
  // 100% width but the accessible value keeps the true number.
  it('caps the fill width at 100% when a budget is exceeded', () => {
    const { container } = render(<ProgressBar percentage={132.4} />)
    const fill = container.querySelector('.progress__fill') as HTMLElement
    expect(fill.style.width).toBe('100%')
    expect(fill.className).toContain('progress__fill--over')
    expect(screen.getByRole('progressbar')).toHaveAttribute('aria-valuenow', '132')
  })

  it('treats a null percentage as zero rather than crashing', () => {
    const { container } = render(<ProgressBar percentage={null} />)
    const fill = container.querySelector('.progress__fill') as HTMLElement
    expect(fill.style.width).toBe('0%')
  })
})

describe('errorMessage', () => {
  it('uses the backend message from an ApiError', () => {
    const error = new ApiError(409, 'CONFLICT', 'An account with that email already exists')
    expect(errorMessage(error)).toBe('An account with that email already exists')
  })

  it('falls back to a generic message for a non-Error throw', () => {
    expect(errorMessage('boom')).toBe('An unexpected error occurred.')
  })
})
