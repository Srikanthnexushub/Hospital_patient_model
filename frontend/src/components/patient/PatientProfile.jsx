import { useState } from 'react'
import { Link } from 'react-router-dom'
import PatientStatusBadge from './PatientStatusBadge.jsx'
import DeactivateConfirmModal from './DeactivateConfirmModal.jsx'
import { useChangePatientStatus } from '../../hooks/usePatient.js'

const BLOOD_GROUP_DISPLAY = {
  A_POS: 'A+', A_NEG: 'A−', B_POS: 'B+', B_NEG: 'B−',
  AB_POS: 'AB+', AB_NEG: 'AB−', O_POS: 'O+', O_NEG: 'O−',
  UNKNOWN: 'Unknown',
}
const GENDER_LABEL = { MALE: 'Male', FEMALE: 'Female', OTHER: 'Other' }

function fmt(val, fallback = 'Not provided') {
  return val || fallback
}

function formatDate(iso) {
  if (!iso) return '—'
  return new Date(iso).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })
}

export default function PatientProfile({ patient, role, onStatusChanged }) {
  const [deactivateOpen, setDeactivateOpen] = useState(false)
  const [statusMessage, setStatusMessage] = useState(null)

  const canEdit   = role === 'RECEPTIONIST' || role === 'ADMIN'
  const isAdmin   = role === 'ADMIN'
  const isActive  = patient.status === 'ACTIVE'

  const activateMutation = useChangePatientStatus(patient.patientId)

  async function handleActivate() {
    try {
      await activateMutation.mutateAsync('ACTIVATE')
      setStatusMessage('Patient has been activated.')
      onStatusChanged?.()
    } catch (err) {
      const msg = err?.response?.data?.message
      setStatusMessage(msg || 'Failed to activate patient.')
    }
  }

  function handleDeactivateSuccess() {
    setDeactivateOpen(false)
    setStatusMessage('Patient has been deactivated.')
    onStatusChanged?.()
  }

  const wasUpdated = patient.updatedAt !== patient.createdAt

  return (
    <div className="space-y-6">

      {/* Status message banner */}
      {statusMessage && (
        <div role="alert" className="rounded-md bg-blue-50 border border-blue-200 p-3 text-blue-800 text-sm">
          {statusMessage}
        </div>
      )}

      {/* Header row: name + status + action buttons */}
      <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
        <div>
          <div className="flex items-center gap-3 flex-wrap">
            <h2 className="text-xl font-bold text-gray-900">
              {patient.firstName} {patient.lastName}
            </h2>
            <PatientStatusBadge status={patient.status} />
          </div>
          <p className="text-sm font-mono text-gray-500 mt-0.5">{patient.patientId}</p>
        </div>

        <div className="flex flex-wrap gap-2">
          <Link to="/" className="btn-secondary text-sm">
            ← Back to Patient List
          </Link>

          {canEdit && (
            <Link
              to={`/patients/${patient.patientId}/edit`}
              className="btn-secondary text-sm"
            >
              Edit Patient
            </Link>
          )}

          {isAdmin && isActive && (
            <button
              type="button"
              className="px-3 py-1.5 text-sm font-medium rounded-md border border-red-300 text-red-700 hover:bg-red-50"
              onClick={() => setDeactivateOpen(true)}
            >
              Deactivate Patient
            </button>
          )}

          {isAdmin && !isActive && (
            <button
              type="button"
              className="px-3 py-1.5 text-sm font-medium rounded-md border border-green-300 text-green-700 hover:bg-green-50"
              onClick={handleActivate}
              disabled={activateMutation.isPending}
            >
              {activateMutation.isPending ? 'Activating…' : 'Activate Patient'}
            </button>
          )}
        </div>
      </div>

      {/* ── Section 1: Personal Demographics ─────────────────────────── */}
      <section aria-labelledby="section-personal">
        <h3 id="section-personal" className="form-section-title">Personal Demographics</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-3">
          <Field label="Date of Birth" value={formatDate(patient.dateOfBirth)} />
          <Field label="Age"           value={`${patient.age} years`} />
          <Field label="Gender"        value={GENDER_LABEL[patient.gender] || patient.gender} />
          <Field label="Blood Group"   value={BLOOD_GROUP_DISPLAY[patient.bloodGroup] || patient.bloodGroup} />
        </dl>
      </section>

      {/* ── Section 2: Contact Information ──────────────────────────────── */}
      <section aria-labelledby="section-contact">
        <h3 id="section-contact" className="form-section-title">Contact Information</h3>
        <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-3">
          <Field label="Phone"   value={patient.phone} />
          <Field label="Email"   value={fmt(patient.email)} />
          <Field label="Address" value={fmt(patient.address)} />
          <Field label="City"    value={fmt(patient.city)} />
          <Field label="State"   value={fmt(patient.state)} />
          <Field label="ZIP"     value={fmt(patient.zipCode)} />
        </dl>
      </section>

      {/* ── Section 3: Emergency Contact ─────────────────────────────────── */}
      <section aria-labelledby="section-emergency">
        <h3 id="section-emergency" className="form-section-title">Emergency Contact</h3>
        {patient.emergencyContactName ? (
          <dl className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-x-6 gap-y-3">
            <Field label="Name"         value={patient.emergencyContactName} />
            <Field label="Phone"        value={patient.emergencyContactPhone} />
            <Field label="Relationship" value={fmt(patient.emergencyContactRelationship)} />
          </dl>
        ) : (
          <p className="text-sm text-gray-500">No emergency contact on file.</p>
        )}
      </section>

      {/* ── Section 4: Medical Background ───────────────────────────────── */}
      <section aria-labelledby="section-medical">
        <h3 id="section-medical" className="form-section-title">Medical Background</h3>
        <dl className="grid grid-cols-1 gap-y-3">
          <Field label="Known Allergies"   value={patient.knownAllergies || 'None recorded'} />
          <Field label="Chronic Conditions" value={patient.chronicConditions || 'None recorded'} />
        </dl>
      </section>

      {/* ── Audit ────────────────────────────────────────────────────────── */}
      <section aria-labelledby="section-audit" className="border-t border-gray-200 pt-4">
        <h3 id="section-audit" className="text-xs font-semibold text-gray-400 uppercase tracking-wide mb-1">
          Audit Trail
        </h3>
        <p className="text-xs text-gray-500">
          Registered {formatDate(patient.createdAt)} by <span className="font-medium">{patient.createdBy}</span>
          {wasUpdated
            ? ` · Last updated ${formatDate(patient.updatedAt)} by ${patient.updatedBy}`
            : ' · Never updated'}
        </p>
      </section>

      {/* Deactivate confirmation modal */}
      <DeactivateConfirmModal
        isOpen={deactivateOpen}
        onClose={() => setDeactivateOpen(false)}
        patient={patient}
        onSuccess={handleDeactivateSuccess}
      />
    </div>
  )
}

function Field({ label, value }) {
  return (
    <div>
      <dt className="text-xs font-medium text-gray-500 uppercase tracking-wide">{label}</dt>
      <dd className="text-sm text-gray-900 mt-0.5">{value}</dd>
    </div>
  )
}
