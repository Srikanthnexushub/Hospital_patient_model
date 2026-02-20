import api from './patientApi.js'

// ── US1: Generate Invoice ───────────────────────────────────────────────────

export async function createInvoice(data) {
  const response = await api.post('/invoices', data)
  return response.data
}

// ── US2: View & Search Invoices ─────────────────────────────────────────────

export async function listInvoices({ patientId, appointmentId, status, dateFrom, dateTo, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (patientId)     params.patientId     = patientId
  if (appointmentId) params.appointmentId = appointmentId
  if (status)        params.status        = status
  if (dateFrom)      params.dateFrom      = dateFrom
  if (dateTo)        params.dateTo        = dateTo
  const response = await api.get('/invoices', { params })
  return response.data
}

export async function getInvoice(invoiceId) {
  const response = await api.get(`/invoices/${invoiceId}`)
  return response.data
}

export async function getPatientInvoices(patientId, { page = 0, size = 20 } = {}) {
  const response = await api.get(`/patients/${patientId}/invoices`, { params: { page, size } })
  return response.data
}

// ── US3: Record Payment ──────────────────────────────────────────────────────

export async function recordPayment(invoiceId, data) {
  const response = await api.post(`/invoices/${invoiceId}/payments`, data)
  return response.data
}

// ── US4: Cancel / Write-off ──────────────────────────────────────────────────

export async function changeInvoiceStatus(invoiceId, action, reason) {
  const response = await api.patch(`/invoices/${invoiceId}/status`, { action, reason })
  return response.data
}

// ── US5: Financial Summary Report ───────────────────────────────────────────

export async function getFinancialReport(dateFrom, dateTo) {
  const response = await api.get('/reports/financial', { params: { dateFrom, dateTo } })
  return response.data
}
