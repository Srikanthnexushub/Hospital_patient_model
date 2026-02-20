import { useState } from 'react'
import { useParams, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { useAppointment, useChangeAppointmentStatus, useClinicalNotes, useAddClinicalNotes } from '../hooks/useAppointments.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

function StatusBadge({ status }) {
  const colours = {
    SCHEDULED: 'bg-blue-100 text-blue-800',
    CONFIRMED: 'bg-indigo-100 text-indigo-800',
    CHECKED_IN: 'bg-yellow-100 text-yellow-800',
    IN_PROGRESS: 'bg-orange-100 text-orange-800',
    COMPLETED: 'bg-green-100 text-green-800',
    CANCELLED: 'bg-gray-100 text-gray-600',
    NO_SHOW: 'bg-red-100 text-red-800',
  }
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${colours[status] ?? 'bg-gray-100 text-gray-600'}`}>
      {status?.replace(/_/g, ' ')}
    </span>
  )
}

function InfoRow({ label, value }) {
  return (
    <div className="flex flex-col sm:flex-row sm:gap-4">
      <dt className="text-sm font-medium text-gray-500 sm:w-36">{label}</dt>
      <dd className="text-sm text-gray-900 mt-0.5 sm:mt-0">{value ?? '—'}</dd>
    </div>
  )
}

/** Available actions per status, filtered by role. */
function availableActions(status, role) {
  const matrix = {
    SCHEDULED: { CONFIRM: ['RECEPTIONIST', 'ADMIN'], CANCEL: ['RECEPTIONIST', 'ADMIN', 'DOCTOR'] },
    CONFIRMED: { CHECK_IN: ['RECEPTIONIST', 'NURSE', 'ADMIN'], NO_SHOW: ['RECEPTIONIST', 'ADMIN'], CANCEL: ['RECEPTIONIST', 'ADMIN', 'DOCTOR'] },
    CHECKED_IN: { START: ['DOCTOR', 'ADMIN'], CANCEL: ['ADMIN'] },
    IN_PROGRESS: { COMPLETE: ['DOCTOR', 'ADMIN'], CANCEL: ['DOCTOR', 'ADMIN'] },
  }
  const allowed = matrix[status] ?? {}
  // ADMIN can always CANCEL
  if (status && !['COMPLETED', 'CANCELLED', 'NO_SHOW'].includes(status)) {
    allowed.CANCEL = allowed.CANCEL ?? ['ADMIN']
  }
  return Object.entries(allowed)
    .filter(([, roles]) => roles.includes(role))
    .map(([action]) => action)
}

function ActionButtons({ status, role, onAction }) {
  const [cancelReason, setCancelReason] = useState('')
  const [showCancelInput, setShowCancelInput] = useState(false)
  const actions = availableActions(status, role)

  if (actions.length === 0) return null

  function handleAction(action) {
    if (action === 'CANCEL') {
      setShowCancelInput(true)
    } else {
      onAction(action, null)
    }
  }

  function submitCancel() {
    if (!cancelReason.trim()) return
    onAction('CANCEL', cancelReason)
    setShowCancelInput(false)
    setCancelReason('')
  }

  const actionLabels = { CONFIRM: 'Confirm', CHECK_IN: 'Check In', START: 'Start', COMPLETE: 'Complete', CANCEL: 'Cancel', NO_SHOW: 'No Show' }
  const actionColours = {
    CONFIRM: 'bg-indigo-600 hover:bg-indigo-700 text-white',
    CHECK_IN: 'bg-yellow-500 hover:bg-yellow-600 text-white',
    START: 'bg-orange-500 hover:bg-orange-600 text-white',
    COMPLETE: 'bg-green-600 hover:bg-green-700 text-white',
    CANCEL: 'bg-red-600 hover:bg-red-700 text-white',
    NO_SHOW: 'bg-gray-600 hover:bg-gray-700 text-white',
  }

  return (
    <div className="mt-4">
      <div className="flex flex-wrap gap-2">
        {actions.map(action => (
          <button
            key={action}
            onClick={() => handleAction(action)}
            className={`px-3 py-1.5 rounded text-sm font-medium ${actionColours[action] ?? 'bg-gray-500 text-white'}`}
          >
            {actionLabels[action] ?? action}
          </button>
        ))}
      </div>
      {showCancelInput && (
        <div className="mt-3 flex gap-2 items-start">
          <input
            value={cancelReason}
            onChange={e => setCancelReason(e.target.value)}
            placeholder="Cancellation reason (required)"
            className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm"
          />
          <button onClick={submitCancel} className="bg-red-600 text-white px-3 py-1.5 rounded text-sm hover:bg-red-700">
            Confirm Cancel
          </button>
          <button onClick={() => setShowCancelInput(false)} className="border border-gray-300 px-3 py-1.5 rounded text-sm hover:bg-gray-50">
            Back
          </button>
        </div>
      )}
    </div>
  )
}

function ClinicalNotesSection({ appointmentId, role, status }) {
  const canView = role === 'DOCTOR' || role === 'ADMIN'
  const canAdd = (role === 'DOCTOR' || role === 'ADMIN') && (status === 'IN_PROGRESS' || status === 'COMPLETED')
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState({ chiefComplaint: '', diagnosis: '', treatment: '', prescription: '', followUpRequired: false, followUpDays: '', privateNotes: '' })

  const { data: notes, isLoading } = useClinicalNotes(appointmentId)
  const { mutate: save, isPending, error } = useAddClinicalNotes(appointmentId)

  if (!canView) return null

  function handleSave() {
    save({ ...form, followUpDays: form.followUpDays ? Number(form.followUpDays) : null }, {
      onSuccess: () => setShowForm(false),
    })
  }

  return (
    <div className="mt-6 border-t border-gray-200 pt-6">
      <div className="flex items-center justify-between mb-3">
        <h2 className="text-base font-semibold text-gray-900">Clinical Notes</h2>
        {canAdd && !showForm && (
          <button onClick={() => setShowForm(true)} className="text-sm text-blue-600 hover:underline">
            {notes ? 'Update Notes' : 'Add Notes'}
          </button>
        )}
      </div>

      {isLoading ? <LoadingSpinner /> : notes ? (
        <dl className="space-y-2">
          <InfoRow label="Chief Complaint" value={notes.chiefComplaint} />
          <InfoRow label="Diagnosis" value={notes.diagnosis} />
          <InfoRow label="Treatment" value={notes.treatment} />
          <InfoRow label="Prescription" value={notes.prescription} />
          <InfoRow label="Follow-up" value={notes.followUpRequired ? `Yes — ${notes.followUpDays ?? '?'} days` : 'No'} />
          {(role === 'DOCTOR' || role === 'ADMIN') && notes.privateNotes && (
            <InfoRow label="Private Notes" value={notes.privateNotes} />
          )}
        </dl>
      ) : (
        <p className="text-sm text-gray-500">No clinical notes recorded.</p>
      )}

      {showForm && (
        <div className="mt-4 space-y-3 bg-gray-50 p-4 rounded border border-gray-200">
          {error && <p className="text-red-600 text-sm">{error?.response?.data?.message ?? error.message}</p>}
          {[['chiefComplaint', 'Chief Complaint'], ['diagnosis', 'Diagnosis'], ['treatment', 'Treatment'], ['prescription', 'Prescription']].map(([key, label]) => (
            <div key={key}>
              <label className="block text-xs font-medium text-gray-600 mb-1">{label}</label>
              <textarea value={form[key]} onChange={e => setForm(f => ({ ...f, [key]: e.target.value }))} rows={2} className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm" />
            </div>
          ))}
          <div className="flex items-center gap-3">
            <label className="flex items-center gap-1.5 text-sm text-gray-700">
              <input type="checkbox" checked={form.followUpRequired} onChange={e => setForm(f => ({ ...f, followUpRequired: e.target.checked }))} />
              Follow-up Required
            </label>
            {form.followUpRequired && (
              <input type="number" min={1} value={form.followUpDays} onChange={e => setForm(f => ({ ...f, followUpDays: e.target.value }))} placeholder="Days" className="w-20 border border-gray-300 rounded px-2 py-1 text-sm" />
            )}
          </div>
          {(role === 'DOCTOR' || role === 'ADMIN') && (
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Private Notes</label>
              <textarea value={form.privateNotes} onChange={e => setForm(f => ({ ...f, privateNotes: e.target.value }))} rows={2} className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm" />
            </div>
          )}
          <div className="flex gap-2">
            <button onClick={handleSave} disabled={isPending} className="btn-primary text-sm py-1.5">
              {isPending ? 'Saving…' : 'Save Notes'}
            </button>
            <button onClick={() => setShowForm(false)} className="btn-secondary text-sm py-1.5">Cancel</button>
          </div>
        </div>
      )}
    </div>
  )
}

export default function AppointmentDetailPage() {
  const { appointmentId } = useParams()
  const { role } = useAuth()
  const location = useLocation()
  const successMessage = location.state?.message

  const { data: appt, isLoading, isError, error, refetch } = useAppointment(appointmentId)
  const { mutate: changeStatus, isPending: changing, error: statusError } = useChangeAppointmentStatus(appointmentId)

  if (isLoading) return <div className="max-w-3xl mx-auto px-4 py-8"><LoadingSpinner /></div>
  if (isError) {
    const is404 = error?.response?.status === 404
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700">
          {is404 ? 'Appointment not found.' : 'Failed to load appointment.'}
        </div>
        <Link to="/appointments" className="inline-block mt-4 text-sm text-blue-600 hover:underline">← Back to Appointments</Link>
      </div>
    )
  }

  function handleAction(action, reason) {
    changeStatus({ action, reason }, { onSuccess: () => refetch() })
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-4">
        <Link to="/appointments" className="text-sm text-blue-600 hover:underline">← Appointments</Link>
      </div>

      <h1 className="text-2xl font-bold text-gray-900 mb-6">Appointment Detail</h1>

      {successMessage && (
        <div role="alert" className="mb-4 rounded-md bg-green-50 border border-green-200 p-4 text-green-800 text-sm">{successMessage}</div>
      )}
      {statusError && (
        <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-3 text-red-700 text-sm">
          {statusError?.response?.data?.message ?? statusError.message}
        </div>
      )}

      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <p className="text-xs font-mono text-gray-500 mb-1">{appt.appointmentId}</p>
            <StatusBadge status={appt.status} />
          </div>
          {changing && <span className="text-xs text-gray-400">Updating…</span>}
        </div>

        <dl className="space-y-2">
          <InfoRow label="Patient" value={`${appt.patientName} (${appt.patientId})`} />
          <InfoRow label="Doctor" value={`${appt.doctorName} (${appt.doctorId})`} />
          <InfoRow label="Date" value={appt.appointmentDate} />
          <InfoRow label="Time" value={`${appt.startTime} – ${appt.endTime} (${appt.durationMinutes} min)`} />
          <InfoRow label="Type" value={appt.type?.replace(/_/g, ' ')} />
          <InfoRow label="Reason" value={appt.reason} />
          {appt.notes && <InfoRow label="Notes" value={appt.notes} />}
          {appt.cancelReason && <InfoRow label="Cancel Reason" value={appt.cancelReason} />}
          <InfoRow label="Version" value={appt.version} />
        </dl>

        <ActionButtons status={appt.status} role={role} onAction={handleAction} />

        <ClinicalNotesSection appointmentId={appointmentId} role={role} status={appt.status} />
      </div>
    </div>
  )
}
