/**
 * useAuth — reads the JWT from sessionStorage and returns decoded claims.
 *
 * Returns { username, role, isAuthenticated } parsed from the stored JWT.
 * Falls back to unauthenticated state if no token or if token is malformed.
 */
export function useAuth() {
  const token = sessionStorage.getItem('jwt_token')
  if (!token) {
    return { username: null, role: null, isAuthenticated: false, token: null }
  }

  try {
    // JWT is three base64url-encoded segments: header.payload.signature
    const payloadB64 = token.split('.')[1]
    const payload = JSON.parse(atob(payloadB64.replace(/-/g, '+').replace(/_/g, '/')))
    return {
      username: payload.username || payload.sub || null,
      role: payload.role || null,
      isAuthenticated: true,
      token,
    }
  } catch {
    return { username: null, role: null, isAuthenticated: false, token: null }
  }
}
