import { Routes, Route, Navigate } from 'react-router-dom'
import PatientListPage from './pages/PatientListPage.jsx'
import PatientRegistrationPage from './pages/PatientRegistrationPage.jsx'
import PatientProfilePage from './pages/PatientProfilePage.jsx'

export default function App() {
  return (
    <div className="min-h-screen bg-gray-50">
      <Routes>
        {/* Patient list (default home) */}
        <Route path="/" element={<PatientListPage />} />

        {/* Register new patient */}
        <Route path="/patients/new" element={<PatientRegistrationPage />} />

        {/* View patient profile */}
        <Route path="/patients/:patientId" element={<PatientProfilePage />} />

        {/* Edit patient (renders PatientProfilePage in edit mode) */}
        <Route path="/patients/:patientId/edit" element={<PatientProfilePage editMode />} />

        {/* Fallback */}
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </div>
  )
}
