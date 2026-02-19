import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../api/patientApi.js'

const DEV_USERS = [
  { label: 'Receptionist — registers & edits patients', username: 'receptionist1', role: 'RECEPTIONIST' },
  { label: 'Admin — full access + status management',   username: 'admin1',        role: 'ADMIN'        },
  { label: 'Doctor — read-only view',                  username: 'doctor1',       role: 'DOCTOR'       },
  { label: 'Nurse — read-only view',                   username: 'nurse1',        role: 'NURSE'        },
]

export default function LoginPage() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('receptionist1')
  const [error, setError]       = useState(null)
  const [loading, setLoading]   = useState(false)

  async function handleLogin(e) {
    e.preventDefault()
    setLoading(true)
    setError(null)
    try {
      const { data } = await api.post('/auth/dev-login', { username, password: 'password' })
      sessionStorage.setItem('jwt_token', data.token)
      navigate('/', { replace: true })
    } catch {
      setError('Sign-in failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center px-4">
      <div className="bg-white rounded-xl shadow-md p-8 w-full max-w-sm space-y-6">

        {/* Header */}
        <div className="text-center">
          <div className="text-3xl mb-2">🏥</div>
          <h1 className="text-xl font-bold text-gray-900">Hospital Patient System</h1>
          <p className="text-sm text-gray-500 mt-1">Development Login</p>
        </div>

        {/* Form */}
        <form onSubmit={handleLogin} className="space-y-4">
          <div>
            <label htmlFor="role-select" className="block text-sm font-medium text-gray-700 mb-1">
              Sign in as
            </label>
            <select
              id="role-select"
              value={username}
              onChange={e => setUsername(e.target.value)}
              className="w-full border border-gray-300 rounded-md px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500"
            >
              {DEV_USERS.map(u => (
                <option key={u.username} value={u.username}>
                  {u.label}
                </option>
              ))}
            </select>
          </div>

          {error && (
            <p className="text-sm text-red-600 bg-red-50 border border-red-200 rounded-md px-3 py-2">
              {error}
            </p>
          )}

          <button
            type="submit"
            disabled={loading}
            className="w-full bg-blue-600 hover:bg-blue-700 disabled:opacity-60 text-white font-medium rounded-md py-2 text-sm transition-colors"
          >
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>

        <p className="text-xs text-gray-400 text-center">
          Dev-only — password is <code className="font-mono">"password"</code> for all users
        </p>
      </div>
    </div>
  )
}
