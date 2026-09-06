import { describe, expect, it } from 'vitest'
import { ApiError, queryString } from './client'

describe('queryString', () => {
  it('drops undefined, null and empty values', () => {
    expect(queryString({ page: 0, size: 25, search: undefined, from: null, to: '' })).toBe(
      '?page=0&size=25',
    )
  })

  it('returns an empty string when nothing is set', () => {
    expect(queryString({ search: undefined })).toBe('')
  })

  // page=0 is meaningful and must survive: a falsy check instead of an
  // explicit undefined/null check would silently drop the first page.
  it('keeps a zero value', () => {
    expect(queryString({ page: 0 })).toBe('?page=0')
  })

  it('encodes values that need escaping', () => {
    expect(queryString({ search: 'a&b c' })).toBe('?search=a%26b+c')
  })
})

describe('ApiError', () => {
  it('flags 401 as unauthenticated', () => {
    expect(new ApiError(401, 'UNAUTHENTICATED', 'nope').isUnauthenticated).toBe(true)
    expect(new ApiError(403, 'ACCESS_DENIED', 'nope').isUnauthenticated).toBe(false)
  })

  it('exposes per-field validation messages', () => {
    const error = new ApiError(400, 'VALIDATION_ERROR', 'invalid', { amount: 'Amount is required' })
    expect(error.fieldError('amount')).toBe('Amount is required')
    expect(error.fieldError('date')).toBeUndefined()
  })
})
