import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { accountsApi, categoriesApi, transactionsApi } from '../api/endpoints'
import type { Page, Transaction } from '../api/types'
import TransactionsPage from './TransactionsPage'

const transactions: Transaction[] = [
  {
    id: 1, accountId: 1, accountName: 'Current account', categoryId: null, categoryName: null,
    date: '2026-10-14', description: 'Lunch', merchant: null, amount: -12.5, currency: 'USD',
    createdAt: '2026-10-14T12:00:00Z',
  },
  {
    id: 2, accountId: 2, accountName: 'Archived account', categoryId: null, categoryName: null,
    date: '2026-10-13', description: 'Train', merchant: null, amount: -3.2, currency: 'SGD',
    createdAt: '2026-10-13T12:00:00Z',
  },
]

function page(content = transactions, number = 0, totalElements = content.length): Page<Transaction> {
  return { content, page: number, size: 25, totalElements, totalPages: 1, hasNext: false }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  render(<QueryClientProvider client={client}><TransactionsPage /></QueryClientProvider>)
  return userEvent.setup()
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(accountsApi, 'list').mockResolvedValue([
    { id: 1, name: 'Current account', type: 'CHECKING', currency: 'SGD', archived: false, createdAt: '2026-01-01' },
    { id: 2, name: 'Archived account', type: 'CHECKING', currency: 'SGD', archived: true, createdAt: '2026-01-01' },
  ])
  vi.spyOn(categoriesApi, 'list').mockResolvedValue([])
  vi.spyOn(transactionsApi, 'list').mockResolvedValue(page())
})

describe('transaction controls', () => {
  it('clears both the visible search and the API filter', async () => {
    const user = renderPage()
    await screen.findByRole('button', { name: 'Edit Lunch' })
    const search = screen.getByRole('searchbox', { name: 'Search' })
    await user.type(search, 'Lunch')
    await user.click(screen.getByRole('button', { name: 'Clear' }))
    expect(search).toHaveValue('')
    await waitFor(() => expect(transactionsApi.list).toHaveBeenLastCalledWith({
      page: 0, size: 25, sort: 'date', direction: 'desc',
    }))
  })

  it('loads the selected transaction when switching between open edit forms', async () => {
    const user = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Edit Lunch' }))
    await user.clear(screen.getByRole('textbox', { name: 'Description' }))
    await user.type(screen.getByRole('textbox', { name: 'Description' }), 'Unsaved draft')
    await user.click(screen.getByRole('button', { name: 'Edit Train' }))
    expect(screen.getByRole('textbox', { name: 'Description' })).toHaveValue('Train')
    expect(screen.getByRole('spinbutton', { name: 'Amount' })).toHaveValue(-3.2)
    expect(document.getElementById('form-account')).toHaveValue('2')
    expect(document.getElementById('form-category')).toHaveDisplayValue('Uncategorised')
  })

  it('preserves the transaction currency when editing an account with another currency', async () => {
    vi.spyOn(transactionsApi, 'update').mockResolvedValue(transactions[0]!)
    const user = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Edit Lunch' }))
    await user.click(screen.getByRole('button', { name: 'Save changes' }))
    await waitFor(() => expect(transactionsApi.update).toHaveBeenCalledWith(1, expect.objectContaining({
      currency: 'USD', amount: -12.5, accountId: 1,
    })))
  })

  it('returns to the previous page after deleting the last transaction on a page', async () => {
    let deleted = false
    vi.mocked(transactionsApi.list).mockImplementation(async (filters) => {
      if (filters.page === 1) return { ...page(deleted ? [] : [transactions[1]!], 1, deleted ? 1 : 2), totalPages: deleted ? 1 : 2 }
      return { ...page([transactions[0]!], 0, deleted ? 1 : 2), totalPages: deleted ? 1 : 2, hasNext: !deleted }
    })
    vi.spyOn(transactionsApi, 'remove').mockImplementation(async () => { deleted = true })
    const user = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Next' }))
    await user.click(await screen.findByRole('button', { name: 'Delete Train' }))
    expect(await screen.findByRole('button', { name: 'Edit Lunch' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeDisabled()
  })

  it('shows a failed deletion while retaining the transaction', async () => {
    vi.spyOn(transactionsApi, 'remove').mockRejectedValue(new Error('The transaction could not be deleted.'))
    const user = renderPage()
    await user.click(await screen.findByRole('button', { name: 'Delete Lunch' }))
    expect(await screen.findByRole('alert')).toHaveTextContent('The transaction could not be deleted.')
    expect(screen.getByRole('button', { name: 'Edit Lunch' })).toBeInTheDocument()
  })
})
