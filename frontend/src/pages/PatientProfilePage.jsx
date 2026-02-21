import { useState } from 'react'
import { useParams, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { usePatient } from '../hooks/usePatient.js'
import { usePatientAppointments } from '../hooks/useAppointments.js'
import {
  useProblems, useCreateProblem, useUpdateProblem,
  useMedications, usePrescribeMedication, useUpdateMedication,
  useAllergies, useRecordAllergy, useDeleteAllergy,
} from '../hooks/useEmr.js'
import PatientProfile from '../components/patient/PatientProfile.jsx'
import PatientEditForm from '../components/patient/PatientEditForm.jsx'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'
import Pagination from '../components/common/Pagination.jsx'

// ── Shared helpers ────────────────────────────────────────────────────────────

const SEVERITY_CLS = {
  MILD:             'bg-green-100 text-green-800',
  MODERATE:         'bg-yellow-100 text-yellow-800',
  SEVERE:           'bg-orange-100 text-orange-800',
  LIFE_THREATENING: 'bg-red-100 text-red-900',
}

function SeverityBadge({ severity }) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium ${SEVERITY_CLS[severity] ?? 'bg-gray-100 text-gray-600'}`}>
      {severity?.replace(/_/g, ' ')}
    </span>
  )
}

function InlineError({ msg }) {
  return msg ? <p className="text-xs text-red-600 mt-1">{msg}</p> : null
}

// ── Appointment history ───────────────────────────────────────────────────────

function AppointmentHistoryTab({ patientId }) {
  const [page, setPage] = useState(0)
  const { data, isLoading, isError } = usePatientAppointments(patientId, page)
  const appointments = data?.content ?? []

  if (isLoading) return <LoadingSpinner />
  if (isError)   return <p className="text-red-600 text-sm">Failed to load appointment history.</p>
  if (appointments.length === 0) return <p className="text-gray-500 text-sm">No appointment history.</p>

  return (
    <div>
      <div className="divide-y divide-gray-100">
        {appointments.map(appt => (
          <div key={appt.appointmentId} className="py-3 flex items-start justify-between gap-4">
            <div>
              <p className="text-sm font-medium text-gray-900">
                {appt.appointmentDate} · {appt.startTime?.slice(0, 5)}–{appt.endTime?.slice(0, 5)}
              </p>
              <p className="text-xs text-gray-500 mt-0.5">
                {appt.type?.replace(/_/g, ' ')} · Dr. {appt.doctorName}
              </p>
            </div>
            <div className="flex items-center gap-2">
              <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium
                ${appt.status === 'COMPLETED' ? 'bg-green-100 text-green-800'
                  : appt.status === 'CANCELLED' ? 'bg-gray-100 text-gray-600'
                  : 'bg-blue-100 text-blue-800'}`}>
                {appt.status?.replace(/_/g, ' ')}
              </span>
              <Link to={`/appointments/${appt.appointmentId}`} className="text-xs text-blue-600 hover:underline">
                View
              </Link>
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

// ── Problems tab ──────────────────────────────────────────────────────────────

const EMPTY_PROBLEM = { title: '', severity: 'MODERATE', status: 'ACTIVE', description: '', icdCode: '', onsetDate: '' }

function ProblemsTab({ patientId, role }) {
  const canWrite = ['DOCTOR', 'ADMIN'].includes(role)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_PROBLEM)
  const [err, setErr] = useState('')

  const { data: problems = [], isLoading, isError } = useProblems(patientId)
  const createMutation  = useCreateProblem(patientId)
  const updateMutation  = useUpdateProblem(patientId)

  if (isLoading) return <LoadingSpinner />
  if (isError)   return <p className="text-red-600 text-sm">Failed to load problems.</p>

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setErr('')
    if (!form.title.trim()) { setErr('Title is required.'); return }
    try {
      await createMutation.mutateAsync({
        title: form.title.trim(),
        severity: form.severity,
        status: form.status,
        description: form.description || undefined,
        icdCode: form.icdCode || undefined,
        onsetDate: form.onsetDate || undefined,
      })
      setForm(EMPTY_PROBLEM)
      setShowForm(false)
    } catch {
      setErr('Failed to add problem. Please try again.')
    }
  }

  async function handleResolve(problemId) {
    await updateMutation.mutateAsync({ problemId, data: { status: 'RESOLVED' } })
  }

  return (
    <div className="space-y-4">
      {/* Add Problem form */}
      {canWrite && (
        <div>
          {!showForm ? (
            <button
              onClick={() => setShowForm(true)}
              className="text-sm font-medium text-blue-600 hover:text-blue-800"
            >
              + Add Problem
            </button>
          ) : (
            <form onSubmit={handleSubmit} className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
              <p className="text-sm font-semibold text-blue-800">New Problem</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Title *</label>
                  <input
                    name="title" value={form.title} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. Hypertension"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Severity *</label>
                  <select name="severity" value={form.severity} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
                    <option>MILD</option><option>MODERATE</option>
                    <option>SEVERE</option><option>LIFE_THREATENING</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Status *</label>
                  <select name="status" value={form.status} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
                    <option>ACTIVE</option><option>INACTIVE</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">ICD Code</label>
                  <input name="icdCode" value={form.icdCode} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. I10" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Onset Date</label>
                  <input type="date" name="onsetDate" value={form.onsetDate} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm" />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Description</label>
                  <textarea name="description" value={form.description} onChange={handleChange} rows={2}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm resize-none"
                    placeholder="Optional notes" />
                </div>
              </div>
              <InlineError msg={err} />
              <div className="flex gap-2">
                <button type="submit" disabled={createMutation.isPending}
                  className="btn-primary text-sm py-1.5 px-4">
                  {createMutation.isPending ? 'Saving…' : 'Save Problem'}
                </button>
                <button type="button" onClick={() => { setShowForm(false); setErr('') }}
                  className="btn-secondary text-sm py-1.5 px-4">
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* Problem list */}
      {problems.length === 0 ? (
        <p className="text-gray-500 text-sm">No active problems.</p>
      ) : (
        <div className="divide-y divide-gray-100">
          {problems.map(p => (
            <div key={p.id} className="py-3">
              <div className="flex items-start justify-between gap-2 flex-wrap">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="text-sm font-medium text-gray-900">{p.title}</p>
                  <SeverityBadge severity={p.severity} />
                  {p.icdCode && (
                    <span className="text-[10px] font-mono bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">
                      ICD: {p.icdCode}
                    </span>
                  )}
                </div>
                {canWrite && p.status === 'ACTIVE' && (
                  <button
                    onClick={() => handleResolve(p.id)}
                    disabled={updateMutation.isPending}
                    className="text-xs text-green-700 border border-green-300 hover:bg-green-50 px-2 py-0.5 rounded shrink-0"
                  >
                    Mark Resolved
                  </button>
                )}
              </div>
              {p.description && <p className="text-xs text-gray-500 mt-0.5">{p.description}</p>}
              {p.onsetDate   && <p className="text-xs text-gray-400 mt-0.5">Since {p.onsetDate}</p>}
              {p.status !== 'ACTIVE' && (
                <span className="inline-block mt-1 text-[10px] bg-gray-100 text-gray-500 px-1.5 py-0.5 rounded">
                  {p.status}
                </span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Medications tab ───────────────────────────────────────────────────────────

const EMPTY_MED = {
  medicationName: '', genericName: '', dosage: '', frequency: '',
  route: 'ORAL', startDate: '', endDate: '', indication: '',
}

function MedicationsTab({ patientId, role }) {
  const canWrite = ['DOCTOR', 'ADMIN'].includes(role)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_MED)
  const [err, setErr] = useState('')

  const { data: medications = [], isLoading, isError } = useMedications(patientId)
  const prescribeMutation   = usePrescribeMedication(patientId)
  const updateMutation      = useUpdateMedication(patientId)

  if (isLoading) return <LoadingSpinner />
  if (isError)   return <p className="text-red-600 text-sm">Failed to load medications.</p>

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setErr('')
    if (!form.medicationName.trim()) { setErr('Medication name is required.'); return }
    if (!form.dosage.trim())         { setErr('Dosage is required.'); return }
    if (!form.frequency.trim())      { setErr('Frequency is required.'); return }
    if (!form.startDate)             { setErr('Start date is required.'); return }
    if (form.endDate && form.endDate < form.startDate) {
      setErr('End date must be on or after start date.'); return
    }
    try {
      await prescribeMutation.mutateAsync({
        medicationName: form.medicationName.trim(),
        genericName:    form.genericName || undefined,
        dosage:         form.dosage.trim(),
        frequency:      form.frequency.trim(),
        route:          form.route,
        startDate:      form.startDate,
        endDate:        form.endDate || undefined,
        indication:     form.indication || undefined,
      })
      setForm(EMPTY_MED)
      setShowForm(false)
    } catch {
      setErr('Failed to prescribe medication. Please try again.')
    }
  }

  async function handleDiscontinue(medicationId) {
    await updateMutation.mutateAsync({ medicationId, data: { status: 'DISCONTINUED' } })
  }

  return (
    <div className="space-y-4">
      {/* Prescribe form */}
      {canWrite && (
        <div>
          {!showForm ? (
            <button
              onClick={() => setShowForm(true)}
              className="text-sm font-medium text-blue-600 hover:text-blue-800"
            >
              + Prescribe Medication
            </button>
          ) : (
            <form onSubmit={handleSubmit} className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
              <p className="text-sm font-semibold text-blue-800">New Prescription</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Medication Name *</label>
                  <input name="medicationName" value={form.medicationName} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. Metformin" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Generic Name</label>
                  <input name="genericName" value={form.genericName} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="Optional" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Dosage *</label>
                  <input name="dosage" value={form.dosage} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. 500mg" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Frequency *</label>
                  <input name="frequency" value={form.frequency} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. Twice daily" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Route *</label>
                  <select name="route" value={form.route} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
                    <option>ORAL</option><option>IV</option><option>IM</option>
                    <option>TOPICAL</option><option>INHALED</option><option>OTHER</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Start Date *</label>
                  <input type="date" name="startDate" value={form.startDate} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">End Date</label>
                  <input type="date" name="endDate" value={form.endDate} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Indication</label>
                  <input name="indication" value={form.indication} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. Type 2 Diabetes" />
                </div>
              </div>
              <InlineError msg={err} />
              <div className="flex gap-2">
                <button type="submit" disabled={prescribeMutation.isPending}
                  className="btn-primary text-sm py-1.5 px-4">
                  {prescribeMutation.isPending ? 'Prescribing…' : 'Prescribe'}
                </button>
                <button type="button" onClick={() => { setShowForm(false); setErr('') }}
                  className="btn-secondary text-sm py-1.5 px-4">
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* Medication list */}
      {medications.length === 0 ? (
        <p className="text-gray-500 text-sm">No active medications.</p>
      ) : (
        <div className="divide-y divide-gray-100">
          {medications.map(m => (
            <div key={m.id} className="py-3">
              <div className="flex items-start justify-between gap-2 flex-wrap">
                <div>
                  <p className="text-sm font-medium text-gray-900">
                    {m.medicationName}
                    {m.genericName && <span className="ml-1 text-xs text-gray-400">({m.genericName})</span>}
                  </p>
                  <p className="text-xs text-gray-600 mt-0.5">{m.dosage} · {m.frequency} · {m.route}</p>
                  {m.indication   && <p className="text-xs text-gray-400">For: {m.indication}</p>}
                  <p className="text-xs text-gray-400">Rx: {m.prescribedBy}</p>
                </div>
                {canWrite && m.status === 'ACTIVE' && (
                  <button
                    onClick={() => handleDiscontinue(m.id)}
                    disabled={updateMutation.isPending}
                    className="text-xs text-red-600 border border-red-300 hover:bg-red-50 px-2 py-0.5 rounded shrink-0"
                  >
                    Discontinue
                  </button>
                )}
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Allergies tab ─────────────────────────────────────────────────────────────

const EMPTY_ALLERGY = { substance: '', type: 'DRUG', severity: 'MODERATE', reaction: '', onsetDate: '' }

function AllergiesTab({ patientId, role }) {
  const canWrite  = ['DOCTOR', 'NURSE', 'ADMIN'].includes(role)
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_ALLERGY)
  const [err, setErr] = useState('')

  const { data: allergies = [], isLoading, isError } = useAllergies(patientId)
  const recordMutation = useRecordAllergy(patientId)
  const { mutate: remove, isPending: removing } = useDeleteAllergy(patientId)

  if (isLoading) return <LoadingSpinner />
  if (isError)   return <p className="text-red-600 text-sm">Failed to load allergies.</p>

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setErr('')
    if (!form.substance.trim()) { setErr('Substance is required.'); return }
    if (!form.reaction.trim())  { setErr('Reaction is required.'); return }
    try {
      await recordMutation.mutateAsync({
        substance: form.substance.trim(),
        type:      form.type,
        severity:  form.severity,
        reaction:  form.reaction.trim(),
        onsetDate: form.onsetDate || undefined,
      })
      setForm(EMPTY_ALLERGY)
      setShowForm(false)
    } catch {
      setErr('Failed to record allergy. Please try again.')
    }
  }

  return (
    <div className="space-y-4">
      {/* Record allergy form */}
      {canWrite && (
        <div>
          {!showForm ? (
            <button
              onClick={() => setShowForm(true)}
              className="text-sm font-medium text-blue-600 hover:text-blue-800"
            >
              + Record Allergy
            </button>
          ) : (
            <form onSubmit={handleSubmit} className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
              <p className="text-sm font-semibold text-blue-800">New Allergy</p>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Substance *</label>
                  <input name="substance" value={form.substance} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. Penicillin" />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Type *</label>
                  <select name="type" value={form.type} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
                    <option>DRUG</option><option>FOOD</option>
                    <option>ENVIRONMENTAL</option><option>OTHER</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Severity *</label>
                  <select name="severity" value={form.severity} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
                    <option>MILD</option><option>MODERATE</option>
                    <option>SEVERE</option><option>LIFE_THREATENING</option>
                  </select>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1">Onset Date</label>
                  <input type="date" name="onsetDate" value={form.onsetDate} onChange={handleChange}
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm" />
                </div>
                <div className="sm:col-span-2">
                  <label className="block text-xs font-medium text-gray-600 mb-1">Reaction *</label>
                  <input name="reaction" value={form.reaction} onChange={handleChange} required
                    className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                    placeholder="e.g. Anaphylaxis, hives, rash" />
                </div>
              </div>
              <InlineError msg={err} />
              <div className="flex gap-2">
                <button type="submit" disabled={recordMutation.isPending}
                  className="btn-primary text-sm py-1.5 px-4">
                  {recordMutation.isPending ? 'Recording…' : 'Record Allergy'}
                </button>
                <button type="button" onClick={() => { setShowForm(false); setErr('') }}
                  className="btn-secondary text-sm py-1.5 px-4">
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      )}

      {/* Allergy list */}
      {allergies.length === 0 ? (
        <p className="text-gray-500 text-sm">No known allergies.</p>
      ) : (
        <div className="divide-y divide-gray-100">
          {allergies.map(a => (
            <div key={a.id} className="py-3 flex items-start justify-between gap-3">
              <div className="flex-1">
                <div className="flex items-center gap-2 flex-wrap">
                  <p className="text-sm font-medium text-gray-900">{a.substance}</p>
                  <SeverityBadge severity={a.severity} />
                  <span className="text-xs text-gray-400">{a.type}</span>
                </div>
                <p className="text-xs text-gray-500 mt-0.5">{a.reaction}</p>
                {a.onsetDate && <p className="text-xs text-gray-400 mt-0.5">Since {a.onsetDate}</p>}
              </div>
              {canWrite && (
                <button
                  onClick={() => remove(a.id)}
                  disabled={removing}
                  className="text-xs text-red-600 hover:underline shrink-0"
                >
                  Remove
                </button>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

// ── Page ──────────────────────────────────────────────────────────────────────

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

  const canViewSummary = ['DOCTOR', 'ADMIN'].includes(role)

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">

      {/* Page header */}
      <div className="flex items-center justify-between mb-6 gap-3 flex-wrap">
        <h1 className="text-2xl font-bold text-gray-900">
          {editMode ? 'Edit Patient' : 'Patient Profile'}
        </h1>
        {!editMode && canViewSummary && (
          <Link
            to={`/patients/${patientId}/medical-summary`}
            className="inline-flex items-center gap-1.5 text-sm font-medium bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg transition-colors"
          >
            <span>🩺</span> Medical Summary
          </Link>
        )}
      </div>

      {/* Success banner */}
      {successMessage && !editMode && (
        <div role="alert" className="mb-6 rounded-md bg-green-50 border border-green-200 p-4 text-green-800">
          {successMessage}
        </div>
      )}

      {/* Tab navigation */}
      {!editMode && (
        <div className="flex gap-1 mb-4 border-b border-gray-200 overflow-x-auto">
          {[
            ['profile',      'Profile'],
            ['appointments', 'Appointments'],
            ['problems',     'Problems'],
            ['medications',  'Medications'],
            ['allergies',    'Allergies'],
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
          <ProblemsTab patientId={patientId} role={role} />
        ) : activeTab === 'medications' ? (
          <MedicationsTab patientId={patientId} role={role} />
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
