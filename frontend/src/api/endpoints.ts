import { api, queryString } from './client'
import type {
  Account,
  AccountInput,
  AnalyticsQuery,
  AnalyticsResult,
  AnalyticsSummary,
  AuditEvent,
  BudgetInput,
  BudgetProgress,
  Category,
  ImportJob,
  ImportSummary,
  Page,
  PeriodType,
  RecurringExpense,
  SpendingAnomaly,
  Transaction,
  TransactionFilters,
  TransactionInput,
  User,
  Widget,
  WidgetInput,
} from './types'

/**
 * Every backend call the app can make, in one typed list.
 *
 * Keeping them here (rather than inline in components) means a URL or a shape
 * changes in exactly one place, and the query hooks below stay about caching
 * rather than about HTTP.
 */

export const authApi = {
  /** Primes the XSRF-TOKEN cookie before the first state-changing request. */
  primeCsrf: () => api.get<void>('/auth/csrf'),
  register: (email: string, password: string) => api.post<User>('/auth/register', { email, password }),
  login: (email: string, password: string) => api.post<User>('/auth/login', { email, password }),
  logout: () => api.post<void>('/auth/logout'),
  me: () => api.get<User>('/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    api.post<void>('/auth/password', { currentPassword, newPassword }),
}

export const demoApi = {
  createSession: () => api.post<User>('/demo/session'),
}

export const accountsApi = {
  list: (includeArchived = false) =>
    api.get<Account[]>(`/accounts${queryString({ includeArchived: String(includeArchived) })}`),
  create: (input: AccountInput) => api.post<Account>('/accounts', input),
  update: (id: number, input: AccountInput) => api.put<Account>(`/accounts/${id}`, input),
  archive: (id: number) => api.post<Account>(`/accounts/${id}/archive`),
  unarchive: (id: number) => api.post<Account>(`/accounts/${id}/unarchive`),
  remove: (id: number) => api.delete<void>(`/accounts/${id}`),
}

export const categoriesApi = {
  list: () => api.get<Category[]>('/categories'),
  create: (name: string, type: 'EXPENSE' | 'INCOME') => api.post<Category>('/categories', { name, type }),
  remove: (id: number) => api.delete<void>(`/categories/${id}`),
}

export const transactionsApi = {
  list: (filters: TransactionFilters) =>
    api.get<Page<Transaction>>(`/transactions${queryString({ ...filters })}`),
  get: (id: number) => api.get<Transaction>(`/transactions/${id}`),
  create: (input: TransactionInput) => api.post<Transaction>('/transactions', input),
  update: (id: number, input: TransactionInput) => api.put<Transaction>(`/transactions/${id}`, input),
  recategorise: (id: number, categoryId: number | null) =>
    api.patch<Transaction>(`/transactions/${id}/category`, { categoryId }),
  remove: (id: number) => api.delete<void>(`/transactions/${id}`),
}

export const analyticsApi = {
  summary: (period: PeriodType, start?: string, end?: string) =>
    api.get<AnalyticsSummary>(`/analytics/summary${queryString({ period, start, end })}`),
  query: (request: AnalyticsQuery) => api.post<AnalyticsResult>('/analytics/query', request),
}

export const dashboardApi = {
  list: () => api.get<Widget[]>('/dashboard/widgets'),
  create: (input: WidgetInput) => api.post<Widget>('/dashboard/widgets', input),
  update: (id: number, input: WidgetInput) => api.put<Widget>(`/dashboard/widgets/${id}`, input),
  remove: (id: number) => api.delete<void>(`/dashboard/widgets/${id}`),
  reorder: (widgetIds: number[]) => api.put<Widget[]>('/dashboard/widgets/order', { widgetIds }),
  reset: () => api.post<Widget[]>('/dashboard/widgets/reset'),
}

export const budgetsApi = {
  list: (month?: string) => api.get<BudgetProgress[]>(`/budgets${queryString({ month })}`),
  create: (input: BudgetInput) => api.post<BudgetProgress>('/budgets', input),
  update: (id: number, input: BudgetInput) => api.put<BudgetProgress>(`/budgets/${id}`, input),
  remove: (id: number) => api.delete<void>(`/budgets/${id}`),
}

export const importsApi = {
  uploadCsv: (accountId: number, file: File) => {
    const formData = new FormData()
    formData.append('file', file)
    return api.upload<ImportSummary>(`/imports/csv${queryString({ accountId })}`, formData)
  },
  list: (page = 0, size = 20) => api.get<Page<ImportJob>>(`/imports${queryString({ page, size })}`),
}

export const insightsApi = {
  recurring: () => api.get<RecurringExpense[]>('/recurring'),
  anomalies: () => api.get<SpendingAnomaly[]>('/insights/anomalies'),
}

export const securityApi = {
  events: (page = 0, size = 25) =>
    api.get<Page<AuditEvent>>(`/security/events${queryString({ page, size })}`),
}
