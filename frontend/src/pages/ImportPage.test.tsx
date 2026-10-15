import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { fireEvent, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { accountsApi, importsApi } from '../api/endpoints'
import ImportPage from './ImportPage'

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(<QueryClientProvider client={client}><ImportPage /></QueryClientProvider>)
  return userEvent.setup()
}

beforeEach(() => {
  vi.restoreAllMocks()
  vi.spyOn(accountsApi, 'list').mockResolvedValue([
    { id: 1, name: 'Current account', type: 'CHECKING', currency: 'SGD', archived: false, createdAt: '2026-01-01' },
  ])
  vi.spyOn(importsApi, 'list').mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false })
  vi.spyOn(importsApi, 'uploadCsv').mockResolvedValue({ importJobId: 1, rowsProcessed: 1, rowsImported: 1, rowsRejected: 0, duplicatesDetected: 0, errors: [], errorsTruncated: false })
})

describe('CSV imports', () => {
  it.each([0, 5 * 1024 * 1024 + 1])('rejects a file of %i bytes before uploading', async (size) => {
    const browser = renderPage()
    await screen.findByRole('option', { name: 'Current account' })
    await browser.selectOptions(screen.getByRole('combobox', { name: 'Import into' }), '1')
    const file = new File(['date,description,amount'], 'transactions.csv', { type: 'text/csv' })
    Object.defineProperty(file, 'size', { value: size })
    const input = screen.getByLabelText('CSV file') as HTMLInputElement
    await browser.upload(input, file)
    expect(input.files?.[0]).toBe(file)
    // jsdom does not include user-event's FileList in native required validation.
    fireEvent.submit(input.form!)
    expect(await screen.findByRole('alert')).toHaveTextContent('Choose a non-empty CSV file no larger than 5 MB.')
    expect(importsApi.uploadCsv).not.toHaveBeenCalled()
  })

  it('lets the user reach imports beyond the first page', async () => {
    vi.mocked(importsApi.list).mockImplementation(async (page = 0) => ({
      content: [{ id: page + 1, accountId: 1, originalFilename: page === 0 ? 'recent.csv' : 'older.csv', status: 'COMPLETED', rowsProcessed: 1, rowsImported: 1, rowsRejected: 0, duplicatesDetected: 0, createdAt: '2026-10-01', completedAt: '2026-10-01' }],
      page, size: 20, totalElements: 21, totalPages: 2, hasNext: page === 0,
    }))
    const browser = renderPage()
    await screen.findByText('recent.csv')
    await browser.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('older.csv')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeDisabled()
    await browser.click(screen.getByRole('button', { name: 'Previous' }))
    expect(await screen.findByText('recent.csv')).toBeInTheDocument()
  })
})
