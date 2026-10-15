import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  accountsApi,
  analyticsApi,
  budgetsApi,
  categoriesApi,
  dashboardApi,
  importsApi,
  insightsApi,
  securityApi,
  transactionsApi,
} from '../api/endpoints'
import type {
  AccountInput,
  AnalyticsQuery,
  BudgetInput,
  PeriodType,
  TransactionFilters,
  TransactionInput,
  WidgetInput,
} from '../api/types'

/** Query hooks own cached server data; mutations invalidate every affected view. */

export const queryKeys = {
  accounts: (includeArchived: boolean) => ['accounts', { includeArchived }] as const,
  categories: () => ['categories'] as const,
  transactions: (filters: TransactionFilters) => ['transactions', filters] as const,
  analyticsSummary: (period: PeriodType) => ['analytics', 'summary', period] as const,
  analyticsQuery: (request: AnalyticsQuery | null) => ['analytics', 'query', request] as const,
  widgets: () => ['dashboard', 'widgets'] as const,
  budgets: (month: string | undefined) => ['budgets', month ?? 'current'] as const,
  recurring: () => ['insights', 'recurring'] as const,
  anomalies: () => ['insights', 'anomalies'] as const,
  importJobs: (page: number) => ['imports', page] as const,
  securityEvents: (page: number) => ['security', 'events', page] as const,
}

/**
 * Query keys whose data a transaction change invalidates.
 *
 * Editing one transaction moves analytics totals, budget progress, recurring
 * detection and anomalies. Listing them once here means no future mutation
 * forgets one of them.
 */
const TRANSACTION_DEPENDENT_KEYS = ['transactions', 'analytics', 'budgets', 'insights']

function invalidateTransactionDependents(queryClient: ReturnType<typeof useQueryClient>) {
  TRANSACTION_DEPENDENT_KEYS.forEach((key) => {
    void queryClient.invalidateQueries({ queryKey: [key] })
  })
}

// ---------------------------------------------------------------- accounts

export function useAccounts(includeArchived = false) {
  return useQuery({
    queryKey: queryKeys.accounts(includeArchived),
    queryFn: () => accountsApi.list(includeArchived),
  })
}

export function useCreateAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: AccountInput) => accountsApi.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

export function useUpdateAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: AccountInput }) => accountsApi.update(id, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      invalidateTransactionDependents(queryClient)
    },
  })
}

export function useArchiveAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, archived }: { id: number; archived: boolean }) =>
      archived ? accountsApi.archive(id) : accountsApi.unarchive(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['accounts'] }),
  })
}

export function useDeleteAccount() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => accountsApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['accounts'] })
      invalidateTransactionDependents(queryClient)
    },
  })
}

// -------------------------------------------------------------- categories

export function useCategories() {
  return useQuery({
    queryKey: queryKeys.categories(),
    queryFn: () => categoriesApi.list(),
    // Categories barely change; refetching them on every screen is wasted work.
    staleTime: 5 * 60_000,
  })
}

export function useCreateCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ name, type }: { name: string; type: 'EXPENSE' | 'INCOME' }) =>
      categoriesApi.create(name, type),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['categories'] }),
  })
}

export function useDeleteCategory() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => categoriesApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['categories'] })
      invalidateTransactionDependents(queryClient)
    },
  })
}

// ------------------------------------------------------------ transactions

export function useTransactions(filters: TransactionFilters) {
  return useQuery({
    queryKey: queryKeys.transactions(filters),
    queryFn: () => transactionsApi.list(filters),
    // Keeps the previous page visible while the next one loads, so paging does
    // not flash an empty table.
    placeholderData: (previous) => previous,
  })
}

export function useCreateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: TransactionInput) => transactionsApi.create(input),
    onSuccess: () => invalidateTransactionDependents(queryClient),
  })
}

export function useUpdateTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: TransactionInput }) =>
      transactionsApi.update(id, input),
    onSuccess: () => invalidateTransactionDependents(queryClient),
  })
}

export function useRecategoriseTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, categoryId }: { id: number; categoryId: number | null }) =>
      transactionsApi.recategorise(id, categoryId),
    onSuccess: () => invalidateTransactionDependents(queryClient),
  })
}

export function useDeleteTransaction() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => transactionsApi.remove(id),
    onSuccess: () => invalidateTransactionDependents(queryClient),
  })
}

// --------------------------------------------------------------- analytics

export function useAnalyticsSummary(period: PeriodType) {
  return useQuery({
    queryKey: queryKeys.analyticsSummary(period),
    queryFn: () => analyticsApi.summary(period),
  })
}

/**
 * Runs a generalised analytics query.
 *
 * Accepts `null` so a caller can say "this widget has no query" without
 * building a fake request or casting. A null request simply disables the query.
 */
export function useAnalyticsQuery(request: AnalyticsQuery | null, options?: { enabled?: boolean }) {
  return useQuery({
    queryKey: queryKeys.analyticsQuery(request),
    queryFn: () => {
      if (!request) {
        throw new Error('useAnalyticsQuery ran without a request')
      }
      return analyticsApi.query(request)
    },
    enabled: (options?.enabled ?? true) && request !== null,
  })
}

// --------------------------------------------------------------- dashboard

export function useWidgets() {
  return useQuery({
    queryKey: queryKeys.widgets(),
    queryFn: () => dashboardApi.list(),
  })
}

export function useCreateWidget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: WidgetInput) => dashboardApi.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}

export function useUpdateWidget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: WidgetInput }) => dashboardApi.update(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}

export function useDeleteWidget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => dashboardApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}

export function useReorderWidgets() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (widgetIds: number[]) => dashboardApi.reorder(widgetIds),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}

export function useResetDashboard() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => dashboardApi.reset(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  })
}

// ----------------------------------------------------------------- budgets

export function useBudgets(month?: string) {
  return useQuery({
    queryKey: queryKeys.budgets(month),
    queryFn: () => budgetsApi.list(month),
  })
}

export function useCreateBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: BudgetInput) => budgetsApi.create(input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}

export function useUpdateBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, input }: { id: number; input: BudgetInput }) => budgetsApi.update(id, input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}

export function useDeleteBudget() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => budgetsApi.remove(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['budgets'] }),
  })
}

// ---------------------------------------------------------------- insights

export function useRecurringExpenses() {
  return useQuery({
    queryKey: queryKeys.recurring(),
    queryFn: () => insightsApi.recurring(),
  })
}

export function useAnomalies() {
  return useQuery({
    queryKey: queryKeys.anomalies(),
    queryFn: () => insightsApi.anomalies(),
  })
}

// ----------------------------------------------------------------- imports

export function useImportJobs(page = 0) {
  return useQuery({
    queryKey: queryKeys.importJobs(page),
    queryFn: () => importsApi.list(page),
  })
}

export function useImportCsv() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ accountId, file }: { accountId: number; file: File }) =>
      importsApi.uploadCsv(accountId, file),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['imports'] })
      invalidateTransactionDependents(queryClient)
    },
  })
}

// ---------------------------------------------------------------- security

export function useSecurityEvents(page = 0) {
  return useQuery({
    queryKey: queryKeys.securityEvents(page),
    queryFn: () => securityApi.events(page),
  })
}
