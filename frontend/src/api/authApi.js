import api from './patientApi.js'

/**
 * Auth API functions.
 * All calls use the shared Axios instance (patientApi) which injects
 * the Bearer token from sessionStorage on every request.
 */

/**
 * POST /api/v1/auth/login
 * @param {string} username
 * @param {string} password
 * @returns {Promise<{token, userId, username, role, expiresAt}>}
 */
export async function login(username, password) {
  const { data } = await api.post('/auth/login', { username, password })
  return data
}

/**
 * POST /api/v1/auth/logout
 * Revokes the current token. No request body needed.
 * @returns {Promise<void>}
 */
export async function logout() {
  await api.post('/auth/logout')
  sessionStorage.removeItem('jwt_token')
}

/**
 * POST /api/v1/auth/refresh
 * Issues a new JWT using the current valid token.
 * @returns {Promise<{token, userId, username, role, expiresAt}>}
 */
export async function refresh() {
  const { data } = await api.post('/auth/refresh')
  return data
}

/**
 * GET /api/v1/auth/me
 * Returns the authenticated user's own profile.
 * @returns {Promise<{userId, username, role, email, department, lastLoginAt}>}
 */
export async function getMe() {
  const { data } = await api.get('/auth/me')
  return data
}
