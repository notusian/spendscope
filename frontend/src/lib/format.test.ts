import { describe, expect, it } from 'vitest'
import {
  formatCurrency,
  formatMonth,
  formatPercentage,
  formatPercentageChange,
  humaniseEnum,
} from './format'

describe('formatCurrency', () => {
  it('formats a positive amount with the currency symbol', () => {
    expect(formatCurrency(1234.5, 'SGD')).toContain('1,234.50')
  })

  it('renders a dash rather than "$NaN" for missing values', () => {
    expect(formatCurrency(null)).toBe('—')
    expect(formatCurrency(undefined)).toBe('—')
    expect(formatCurrency(Number.NaN)).toBe('—')
  })
})

describe('formatPercentageChange', () => {
  it('adds a plus sign to increases', () => {
    expect(formatPercentageChange(18.2)).toBe('+18.2%')
  })

  it('keeps the minus sign on decreases', () => {
    expect(formatPercentageChange(-9.05)).toBe('-9.1%')
  })

  // A null change means the backend could not compute one. Showing 0% would
  // claim spending was flat, which is a different and wrong statement.
  it('renders a dash when no comparison is available', () => {
    expect(formatPercentageChange(null)).toBe('—')
  })
})

describe('formatPercentage', () => {
  it('formats a share to one decimal', () => {
    expect(formatPercentage(84)).toBe('84.0%')
  })

  it('handles values above 100 for exceeded budgets', () => {
    expect(formatPercentage(132.4)).toBe('132.4%')
  })
})

describe('formatMonth', () => {
  it('expands YYYY-MM into a readable month', () => {
    expect(formatMonth('2026-09')).toBe('September 2026')
  })

  it('returns the input unchanged when it is not a month', () => {
    expect(formatMonth('nonsense')).toBe('nonsense')
  })
})

describe('humaniseEnum', () => {
  it('sentence-cases screaming snake case', () => {
    expect(humaniseEnum('CREDIT_CARD')).toBe('Credit card')
  })
})
