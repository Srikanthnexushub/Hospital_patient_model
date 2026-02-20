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
import InvoiceListPage from './pages/InvoiceListPage.jsx'
import InvoiceCreatePage from './pages/InvoiceCreatePage.jsx'
import InvoiceDetailPage from './pages/InvoiceDetailPage.jsx'
import FinancialReportPage from './pages/FinancialReportPage.jsx'

function ProtectedRoute({ children }) {
  const { isAuthenticated } = useAuth()
  return isAuthenticated ? children : <Navigate to="/login" replace />
}

// Redirects to "/" if the current role is not in allowedRoles.
// Backend enforces the same rules; this just prevents showing a broken form.
function RoleRoute({ allowedRoles, children }) {
  const { role } = useAuth()
  return allowedRoles.includes(role) ? children : <Navigate to="/" replace />
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
        {role !== 'NURSE' && (
          <NavLink to="/invoices" className={linkCls}>Billing</NavLink>
        )}
        {role === 'ADMIN' && (
          <NavLink to="/reports/financial" className={linkCls}>Reports</NavLink>
        )}
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
                  <Route path="/patients/new" element={
                    <RoleRoute allowedRoles={['RECEPTIONIST', 'ADMIN']}>
                      <PatientRegistrationPage />
                    </RoleRoute>
                  } />
                  <Route path="/patients/:patientId" element={<PatientProfilePage />} />
                  <Route path="/patients/:patientId/edit" element={
                    <RoleRoute allowedRoles={['RECEPTIONIST', 'ADMIN']}>
                      <PatientProfilePage editMode />
                    </RoleRoute>
                  } />
                  <Route path="/appointments" element={<AppointmentListPage />} />
                  <Route path="/appointments/new" element={
                    <RoleRoute allowedRoles={['RECEPTIONIST', 'ADMIN']}>
                      <AppointmentBookingPage />
                    </RoleRoute>
                  } />
                  <Route path="/appointments/:appointmentId" element={<AppointmentDetailPage />} />
                  <Route path="/doctors/:doctorId/availability" element={<DoctorAvailabilityPage />} />
                  {/* Billing */}
                  <Route path="/invoices" element={
                    <RoleRoute allowedRoles={['RECEPTIONIST', 'ADMIN', 'DOCTOR']}>
                      <InvoiceListPage />
                    </RoleRoute>
                  } />
                  <Route path="/invoices/new" element={
                    <RoleRoute allowedRoles={['RECEPTIONIST', 'ADMIN']}>
                      <InvoiceCreatePage />
                    </RoleRoute>
                  } />
                  <Route path="/invoices/:invoiceId" element={
                    <RoleRoute allowedRoles={['RECEPTIONIST', 'ADMIN', 'DOCTOR']}>
                      <InvoiceDetailPage />
                    </RoleRoute>
                  } />
                  <Route path="/reports/financial" element={
                    <RoleRoute allowedRoles={['ADMIN']}>
                      <FinancialReportPage />
                    </RoleRoute>
                  } />
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
