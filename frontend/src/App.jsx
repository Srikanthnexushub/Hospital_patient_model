import { Routes, Route, Navigate, NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth.js'
import LoginPage from './pages/LoginPage.jsx'
import PatientListPage from './pages/PatientListPage.jsx'
import PatientRegistrationPage from './pages/PatientRegistrationPage.jsx'
import PatientProfilePage from './pages/PatientProfilePage.jsx'
import AppointmentListPage from './pages/AppointmentListPage.jsx'
import AppointmentBookingPage from './pages/AppointmentBookingPage.jsx'
import AppointmentDetailPage from './pages/AppointmentDetailPage.jsx'
import DoctorAvailabilityPage from './pages/DoctorAvailabilityPage.jsx'

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

function NavBar() {
  const { role } = useAuth()
  const navigate = useNavigate()

  function handleLogout() {
    sessionStorage.removeItem('jwt_token')
    navigate('/login', { replace: true })
  }

  const linkCls = ({ isActive }) =>
    `text-sm font-medium px-1 py-0.5 border-b-2 transition-colors ${
      isActive ? 'border-white text-white' : 'border-transparent text-blue-100 hover:text-white'
    }`

  return (
    <nav className="bg-blue-700 text-white shadow-sm">
      <div className="max-w-7xl mx-auto px-4 flex items-center gap-6 h-12">
        <span className="font-bold text-white mr-2">HMS</span>
        <NavLink to="/" end className={linkCls}>Patients</NavLink>
        <NavLink to="/appointments" className={linkCls}>Appointments</NavLink>
        <div className="ml-auto flex items-center gap-3">
          <span className="text-xs text-blue-200">{role}</span>
          <button onClick={handleLogout} className="text-xs text-blue-200 hover:text-white">
            Sign out
          </button>
        </div>
      </div>
    </nav>
  )
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

        {/* Protected routes — wrapped with NavBar */}
        <Route
          path="/*"
          element={
            <ProtectedRoute>
              <>
                <NavBar />
                <Routes>
                  <Route path="/" element={<PatientListPage />} />
                  <Route path="/patients/new" element={<PatientRegistrationPage />} />
                  <Route path="/patients/:patientId" element={<PatientProfilePage />} />
                  <Route path="/patients/:patientId/edit" element={<PatientProfilePage editMode />} />
                  <Route path="/appointments" element={<AppointmentListPage />} />
                  <Route path="/appointments/new" element={<AppointmentBookingPage />} />
                  <Route path="/appointments/:appointmentId" element={<AppointmentDetailPage />} />
                  <Route path="/doctors/:doctorId/availability" element={<DoctorAvailabilityPage />} />
                  <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
              </>
            </ProtectedRoute>
          }
        />
      </Routes>
    </div>
  )
}
