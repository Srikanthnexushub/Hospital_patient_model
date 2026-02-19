import { useParams, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { usePatient } from '../hooks/usePatient.js'
import PatientProfile from '../components/patient/PatientProfile.jsx'
import PatientEditForm from '../components/patient/PatientEditForm.jsx'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

export default function PatientProfilePage({ editMode = false }) {
  const { patientId } = useParams()
  const { role } = useAuth()
  const location = useLocation()
  const successMessage = location.state?.message

  const { data: patient, isLoading, isError, error, refetch } = usePatient(patientId)

  if (isLoading) return <div className="max-w-3xl mx-auto px-4 py-8"><LoadingSpinner /></div>

  if (isError) {
    const is404 = error?.response?.status === 404
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700">
          {is404 ? 'Patient not found.' : 'Failed to load patient. Please try again.'}
        </div>
        <Link to="/" className="inline-block mt-4 text-sm text-blue-600 hover:underline">
          ← Back to Patient List
        </Link>
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">

      {/* Page header */}
      <div className="mb-6">
        <h1 className="text-2xl font-bold text-gray-900">
          {editMode ? 'Edit Patient' : 'Patient Profile'}
        </h1>
      </div>

      {/* Success banner (from navigation state after save) */}
      {successMessage && !editMode && (
        <div role="alert" className="mb-6 rounded-md bg-green-50 border border-green-200 p-4 text-green-800">
          {successMessage}
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        {editMode ? (
          <PatientEditForm patient={patient} />
        ) : (
          <PatientProfile
            patient={patient}
            role={role}
            onStatusChanged={() => refetch()}
          />
        )}
      </div>
    </div>
  )
}
