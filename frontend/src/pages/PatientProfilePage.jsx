import { useState } from 'react'
import { useParams, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { usePatient } from '../hooks/usePatient.js'
import { usePatientAppointments } from '../hooks/useAppointments.js'
import { useProblems, useMedications, useAllergies, useDeleteAllergy } from '../hooks/useEmr.js'
import PatientProfile from '../components/patient/PatientProfile.jsx'
import PatientEditForm from '../components/patient/PatientEditForm.jsx'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'
import Pagination from '../components/common/Pagination.jsx'

function AppointmentHistoryTab({ patientId }) {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError } = usePatientAppointments(patientId, page)
  const appointments = data?.content ?? []

  if (isLoading) return <LoadingSpinner />
  if (isError) return <p className="text-red-600 text-sm">Failed to load appointment history.</p>
  if (appointments.length === 0) return <p className="text-gray-500 text-sm">No appointment history.</p>

  return (
    <div>
      <div className="divide-y divide-gray-100">
        {appointments.map(appt => (
          <div key={appt.appointmentId} className="py-3 flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-gray-900">{appt.appointmentDate} · {appt.startTime?.slice(0, 5)}–{appt.endTime?.slice(0, 5)}</p>
              <p className="text-xs text-gray-500 mt-0.5">{appt.type?.replace(/_/g, ' ')} · Dr. {appt.doctorName}</p>
            </div>
            <div className="flex items-center gap-2">
              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium
                ${appt.status === 'COMPLETED' ? 'bg-green-100 text-green-800'
                  : appt.status === 'CANCELLED' ? 'bg-gray-100 text-gray-600'
                  : 'bg-blue-100 text-blue-800'}`}>
                {appt.status?.replace(/_/g, ' ')}
              </span>
              <Link to={`/appointments/${appt.appointmentId}`} className="text-xs text-blue-600 hover:underline">View</Link>
            </div>
          </div>
        ))}
      </div>
      {(data?.totalPages ?? 0) > 1 && (
        <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
      )}
    </div>
  )
}

function ProblemsTab({ patientId }) {
  const { data: problems = [], isLoading, isError } = useProblems(patientId)
  if (isLoading) return <LoadingSpinner />
  if (isError) return <p className="text-red-600 text-sm">Failed to load problems.</p>
  if (problems.length === 0) return <p className="text-gray-500 text-sm">No active problems.</p>
  return (
    <div className="divide-y divide-gray-100">
      {problems.map(p => (
        <div key={p.id} className="py-3">
          <div className="flex items-start justify-between">
            <p className="text-sm font-medium text-gray-900">{p.title}</p>
            <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">{p.severity}</span>
          </div>
          {p.description && <p className="text-xs text-gray-500 mt-0.5">{p.description}</p>}
          {p.icdCode && <p className="text-xs text-gray-400">ICD: {p.icdCode}</p>}
        </div>
      ))}
    </div>
  )
}

function MedicationsTab({ patientId }) {
  const { data: medications = [], isLoading, isError } = useMedications(patientId)
  if (isLoading) return <LoadingSpinner />
  if (isError) return <p className="text-red-600 text-sm">Failed to load medications.</p>
  if (medications.length === 0) return <p className="text-gray-500 text-sm">No active medications.</p>
  return (
    <div className="divide-y divide-gray-100">
      {medications.map(m => (
        <div key={m.id} className="py-3">
          <div className="flex items-start justify-between">
            <p className="text-sm font-medium text-gray-900">{m.medicationName}</p>
            <span className="text-xs text-gray-500">{m.route}</span>
          </div>
          <p className="text-xs text-gray-600 mt-0.5">{m.dosage} · {m.frequency}</p>
          {m.indication && <p className="text-xs text-gray-400">Indication: {m.indication}</p>}
          <p className="text-xs text-gray-400">Prescribed by: {m.prescribedBy}</p>
        </div>
      ))}
    </div>
  )
}

function AllergiesTab({ patientId, role }) {
  const canDelete = ['DOCTOR', 'NURSE', 'ADMIN'].includes(role)
  const { data: allergies = [], isLoading, isError } = useAllergies(patientId)
  const { mutate: remove } = useDeleteAllergy(patientId)
  if (isLoading) return <LoadingSpinner />
  if (isError) return <p className="text-red-600 text-sm">Failed to load allergies.</p>
  if (allergies.length === 0) return <p className="text-gray-500 text-sm">No known allergies.</p>
  const severityColours = { MILD: 'bg-green-100 text-green-800', MODERATE: 'bg-yellow-100 text-yellow-800', SEVERE: 'bg-red-100 text-red-800', LIFE_THREATENING: 'bg-red-200 text-red-900' }
  return (
    <div className="divide-y divide-gray-100">
      {allergies.map(a => (
        <div key={a.id} className="py-3 flex items-start justify-between gap-3">
          <div className="flex-1">
            <div className="flex items-center gap-2">
              <p className="text-sm font-medium text-gray-900">{a.substance}</p>
              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${severityColours[a.severity] ?? 'bg-gray-100 text-gray-600'}`}>{a.severity}</span>
              <span className="text-xs text-gray-400">{a.type}</span>
            </div>
            <p className="text-xs text-gray-500 mt-0.5">{a.reaction}</p>
          </div>
          {canDelete && (
            <button onClick={() => remove(a.id)} className="text-xs text-red-600 hover:underline shrink-0">Remove</button>
          )}
        </div>
      ))}
    </div>
  )
}

export default function PatientProfilePage({ editMode = false }) {
  const { patientId } = useParams()
  const { role } = useAuth()
  const location = useLocation()
  const successMessage = location.state?.message
  const [activeTab, setActiveTab] = useState('profile')

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

      {/* Tab navigation (only in view mode) */}
      {!editMode && (
        <div className="flex gap-1 mb-4 border-b border-gray-200 overflow-x-auto">
          {[
            ['profile', 'Profile'],
            ['appointments', 'Appointments'],
            ['problems', 'Problems'],
            ['medications', 'Medications'],
            ['allergies', 'Allergies'],
          ].map(([tab, label]) => (
            <button
              key={tab}
              onClick={() => setActiveTab(tab)}
              className={`px-4 py-2 text-sm font-medium border-b-2 -mb-px transition-colors whitespace-nowrap ${
                activeTab === tab
                  ? 'border-blue-600 text-blue-600'
                  : 'border-transparent text-gray-500 hover:text-gray-700'
              }`}
            >
              {label}
            </button>
          ))}
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        {editMode ? (
          <PatientEditForm patient={patient} />
        ) : activeTab === 'appointments' ? (
          <AppointmentHistoryTab patientId={patientId} />
        ) : activeTab === 'problems' ? (
          <ProblemsTab patientId={patientId} />
        ) : activeTab === 'medications' ? (
          <MedicationsTab patientId={patientId} />
        ) : activeTab === 'allergies' ? (
          <AllergiesTab patientId={patientId} role={role} />
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
