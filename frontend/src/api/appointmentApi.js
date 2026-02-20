import api from './patientApi.js'

// ── US1: Book Appointment ───────────────────────────────────────────────────

export async function bookAppointment(data) {
  const response = await api.post('/appointments', data)
  return response.data
}

// ── US2: View & Search ─────────────────────────────────────────────────────

export async function listAppointments({ doctorId, patientId, date, dateFrom, dateTo,
                                         status, type, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (doctorId) params.doctorId = doctorId
  if (patientId) params.patientId = patientId
  if (date) params.date = date
  if (dateFrom) params.dateFrom = dateFrom
  if (dateTo) params.dateTo = dateTo
  if (status) params.status = status
  if (type) params.type = type
  const response = await api.get('/appointments', { params })
  return response.data
}

export async function getAppointment(appointmentId) {
  const response = await api.get(`/appointments/${appointmentId}`)
  return response.data
}

export async function getTodayAppointments({ page = 0, size = 20 } = {}) {
  const response = await api.get('/appointments/today', { params: { page, size } })
  return response.data
}

export async function getDoctorSchedule(doctorId, date, { page = 0, size = 20 } = {}) {
  const response = await api.get(`/doctors/${doctorId}/schedule`, { params: { date, page, size } })
  return response.data
}

// ── US3: Status Lifecycle ──────────────────────────────────────────────────

export async function changeAppointmentStatus(appointmentId, action, reason) {
  const body = { action }
  if (reason) body.reason = reason
  const response = await api.patch(`/appointments/${appointmentId}/status`, body)
  return response.data
}

// ── US4: Update Details ─────────────────────────────────────────────────────

export async function updateAppointment(appointmentId, data, version) {
  const response = await api.patch(`/appointments/${appointmentId}`, data, {
    headers: { 'If-Match': version },
  })
  return response.data
}

// ── Doctor list (for booking form picker) ───────────────────────────────────

export async function listDoctors() {
  const response = await api.get('/doctors')
  return response.data
}

// ── US5: Doctor Availability ────────────────────────────────────────────────

export async function getDoctorAvailability(doctorId, date) {
  const response = await api.get(`/doctors/${doctorId}/availability`, { params: { date } })
  return response.data
}

// ── US6: Clinical Notes ─────────────────────────────────────────────────────

export async function addClinicalNotes(appointmentId, data) {
  const response = await api.post(`/appointments/${appointmentId}/notes`, data)
  return response.data
}

export async function getClinicalNotes(appointmentId) {
  const response = await api.get(`/appointments/${appointmentId}/notes`)
  return response.data
}

// ── US7: Patient Appointment History ───────────────────────────────────────

export async function getPatientAppointments(patientId, { page = 0, size = 20 } = {}) {
  const response = await api.get(`/patients/${patientId}/appointments`, { params: { page, size } })
  return response.data
}
