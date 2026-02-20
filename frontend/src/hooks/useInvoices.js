import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  createInvoice,
  listInvoices,
  getInvoice,
  getPatientInvoices,
  recordPayment,
  changeInvoiceStatus,
  getFinancialReport,
} from '../api/billingApi.js'

// ── US1: Create Invoice ─────────────────────────────────────────────────────

export function useCreateInvoice() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createInvoice,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}

// ── US2: List / Search ──────────────────────────────────────────────────────

export function useInvoices(params = {}) {
  return useQuery({
    queryKey: ['invoices', params],
    queryFn: () => listInvoices(params),
    staleTime: 30_000,
    placeholderData: prev => prev,
  })
}

export function useInvoice(invoiceId) {
  return useQuery({
    queryKey: ['invoice', invoiceId],
    queryFn: () => getInvoice(invoiceId),
    staleTime: 10_000,
    enabled: !!invoiceId,
  })
}

export function usePatientInvoices(patientId, page = 0) {
  return useQuery({
    queryKey: ['patient-invoices', patientId, page],
    queryFn: () => getPatientInvoices(patientId, { page }),
    staleTime: 30_000,
    enabled: !!patientId,
    placeholderData: prev => prev,
  })
}

// ── US3: Record Payment ─────────────────────────────────────────────────────

export function useRecordPayment(invoiceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: data => recordPayment(invoiceId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}

// ── US4: Cancel / Write-off ─────────────────────────────────────────────────

export function useChangeInvoiceStatus(invoiceId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ action, reason }) => changeInvoiceStatus(invoiceId, action, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoice', invoiceId] })
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
  })
}

// ── US5: Financial Report ───────────────────────────────────────────────────

export function useFinancialReport(dateFrom, dateTo) {
  return useQuery({
    queryKey: ['financial-report', dateFrom, dateTo],
    queryFn: () => getFinancialReport(dateFrom, dateTo),
    staleTime: 60_000,
    enabled: !!(dateFrom && dateTo),
  })
}
