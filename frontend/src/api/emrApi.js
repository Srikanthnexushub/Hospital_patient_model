import api from './patientApi.js'

// ── US1: Vitals ──────────────────────────────────────────────────────────────

export async function recordVitals(appointmentId, data) {
  const response = await api.post(`/appointments/${appointmentId}/vitals`, data)
  return response.data
}

export async function getVitalsByAppointment(appointmentId) {
  const response = await api.get(`/appointments/${appointmentId}/vitals`)
  return response.data
}

export async function getVitalsByPatient(patientId, { page = 0, size = 10 } = {}) {
  const response = await api.get(`/patients/${patientId}/vitals`, { params: { page, size } })
  return response.data
}

// ── US2: Problems ─────────────────────────────────────────────────────────────

export async function createProblem(patientId, data) {
  const response = await api.post(`/patients/${patientId}/problems`, data)
  return response.data
}

export async function listProblems(patientId, { status = 'ACTIVE' } = {}) {
  const response = await api.get(`/patients/${patientId}/problems`, { params: { status } })
  return response.data
}

export async function updateProblem(patientId, problemId, data) {
  const response = await api.patch(`/patients/${patientId}/problems/${problemId}`, data)
  return response.data
}

// ── US3: Medications ──────────────────────────────────────────────────────────

export async function prescribeMedication(patientId, data) {
  const response = await api.post(`/patients/${patientId}/medications`, data)
  return response.data
}

export async function listMedications(patientId, { status = 'ACTIVE' } = {}) {
  const response = await api.get(`/patients/${patientId}/medications`, { params: { status } })
  return response.data
}

export async function updateMedication(patientId, medicationId, data) {
  const response = await api.patch(`/patients/${patientId}/medications/${medicationId}`, data)
  return response.data
}

// ── US4: Allergies ────────────────────────────────────────────────────────────

export async function recordAllergy(patientId, data) {
  const response = await api.post(`/patients/${patientId}/allergies`, data)
  return response.data
}

export async function listAllergies(patientId) {
  const response = await api.get(`/patients/${patientId}/allergies`)
  return response.data
}

export async function deleteAllergy(patientId, allergyId) {
  await api.delete(`/patients/${patientId}/allergies/${allergyId}`)
}

// ── US5: Medical Summary ──────────────────────────────────────────────────────

export async function getMedicalSummary(patientId) {
  const response = await api.get(`/patients/${patientId}/medical-summary`)
  return response.data
}
