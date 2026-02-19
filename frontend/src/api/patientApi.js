import axios from 'axios'

// ── Axios instance ─────────────────────────────────────────────────────────
const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api/v1',
  headers: { 'Content-Type': 'application/json' },
  timeout: 10_000,
})

// Request interceptor: attach JWT from sessionStorage
api.interceptors.request.use(config => {
  const token = sessionStorage.getItem('jwt_token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Response error interceptor: surface HTTP errors
api.interceptors.response.use(
  response => response,
  error => {
    // Let callers handle error details (status, message, fieldErrors)
    return Promise.reject(error)
  }
)

// ── US1: Patient Registration ──────────────────────────────────────────────

export async function registerPatient(data) {
  const response = await api.post('/patients', data)
  return response.data
}

export async function checkDuplicatePhone(phone, excludePatientId = null) {
  const params = { phone }
  if (excludePatientId) params.excludePatientId = excludePatientId
  const response = await api.get('/patients/check-phone', { params })
  return response.data
}

// ── US2: Search & List ─────────────────────────────────────────────────────

export async function searchPatients({ query = '', status = 'ACTIVE', gender = 'ALL',
                                       bloodGroup = 'ALL', page = 0, size = 20 } = {}) {
  const params = { page, size, sort: 'createdAt,desc' }
  if (query) params.query = query
  if (status && status !== 'ALL') params.status = status
  if (gender && gender !== 'ALL') params.gender = gender
  if (bloodGroup && bloodGroup !== 'ALL') params.bloodGroup = bloodGroup
  const response = await api.get('/patients', { params })
  return response.data
}

// ── US3: Profile View ──────────────────────────────────────────────────────

export async function getPatient(patientId) {
  const response = await api.get(`/patients/${patientId}`)
  return response.data
}

// ── US4: Update ────────────────────────────────────────────────────────────

export async function updatePatient(patientId, data, version) {
  const response = await api.put(`/patients/${patientId}`, data, {
    headers: { 'If-Match': version },
  })
  return response.data
}

// ── US5: Status Management ─────────────────────────────────────────────────

export async function changePatientStatus(patientId, action) {
  const response = await api.patch(`/patients/${patientId}/status`, { action })
  return response.data
}

export default api
