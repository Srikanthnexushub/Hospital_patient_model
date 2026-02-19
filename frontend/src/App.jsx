import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth.js'
import LoginPage from './pages/LoginPage.jsx'
import PatientListPage from './pages/PatientListPage.jsx'
import PatientRegistrationPage from './pages/PatientRegistrationPage.jsx'
import PatientProfilePage from './pages/PatientProfilePage.jsx'

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

export default function App() {
  const { isAuthenticated } = useAuth()

  return (
    <div className="min-h-screen bg-gray-50">
      <Routes>
        {/* Public — login */}
        <Route
          path="/login"
          element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
        />

        {/* Protected routes */}
        <Route path="/" element={<ProtectedRoute><PatientListPage /></ProtectedRoute>} />
        <Route path="/patients/new" element={<ProtectedRoute><PatientRegistrationPage /></ProtectedRoute>} />
        <Route path="/patients/:patientId" element={<ProtectedRoute><PatientProfilePage /></ProtectedRoute>} />
        <Route path="/patients/:patientId/edit" element={<ProtectedRoute><PatientProfilePage editMode /></ProtectedRoute>} />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}
