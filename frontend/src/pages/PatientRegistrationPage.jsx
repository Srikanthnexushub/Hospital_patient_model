import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import PatientRegistrationForm from '../components/patient/PatientRegistrationForm.jsx'
import { useAuth } from '../hooks/useAuth.js'

export default function PatientRegistrationPage() {
  const navigate = useNavigate()
  const { role } = useAuth()
  const [successBanner, setSuccessBanner] = useState(null)

  function handleSuccess(response) {
    setSuccessBanner(`Patient registered successfully. Patient ID: ${response.patientId}`)
    setTimeout(() => navigate(`/patients/${response.patientId}`), 1500)
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">

      {/* Page header */}
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Register New Patient</h1>
          <p className="text-sm text-gray-500 mt-1">
            All fields marked with <span className="text-red-500">*</span> are required.
          </p>
        </div>
        <Link to="/" className="text-sm text-blue-600 hover:underline">
          ← Back to Patient List
        </Link>
      </div>

      {/* Success banner */}
      {successBanner && (
        <div
          role="alert"
          className="mb-6 rounded-md bg-green-50 border border-green-200 p-4 text-green-800"
        >
          {successBanner}
        </div>
      )}

      {/* Form (role guard is inside the form component) */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <PatientRegistrationForm role={role} onSuccess={handleSuccess} />
      </div>
    </div>
  )
}
