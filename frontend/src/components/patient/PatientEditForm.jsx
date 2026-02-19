import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import InlineError from '../common/InlineError.jsx'
import { checkDuplicatePhone } from '../../api/patientApi.js'
import { useUpdatePatient } from '../../hooks/usePatient.js'

const PHONE_REGEX = /^(\+1-\d{3}-\d{3}-\d{4}|\(\d{3}\) \d{3}-\d{4}|\d{3}-\d{3}-\d{4})$/

const schema = z
  .object({
    firstName: z.string().min(1, 'First name is required').max(100),
    lastName: z.string().min(1, 'Last name is required').max(100),
    dateOfBirth: z
      .string()
      .min(1, 'Date of birth is required')
      .refine(val => new Date(val) < new Date(), 'Date of birth must be in the past'),
    gender: z.enum(['MALE', 'FEMALE', 'OTHER'], { required_error: 'Gender is required' }),
    bloodGroup: z.string().optional().or(z.literal('')),
    phone: z.string().min(1, 'Phone number is required')
      .regex(PHONE_REGEX, 'Enter a valid phone number (e.g. 555-123-4567)'),
    email: z.string().email('Enter a valid email address').optional().or(z.literal('')),
    address: z.string().max(255).optional().or(z.literal('')),
    city: z.string().max(100).optional().or(z.literal('')),
    state: z.string().max(100).optional().or(z.literal('')),
    zipCode: z.string().max(20).optional().or(z.literal('')),
    emergencyContactName: z.string().max(200).optional().or(z.literal('')),
    emergencyContactPhone: z.string().optional().or(z.literal(''))
      .refine(val => !val || PHONE_REGEX.test(val), 'Enter a valid phone number'),
    emergencyContactRelationship: z.string().max(100).optional().or(z.literal('')),
    knownAllergies: z.string().max(1000).optional().or(z.literal('')),
    chronicConditions: z.string().max(1000).optional().or(z.literal('')),
  })
  .refine(
    data => !data.emergencyContactName || data.emergencyContactPhone,
    { message: 'Emergency contact phone is required when name is provided', path: ['emergencyContactPhone'] }
  )
  .refine(
    data => !data.emergencyContactPhone || data.emergencyContactName,
    { message: 'Emergency contact name is required when phone is provided', path: ['emergencyContactName'] }
  )

const BLOOD_GROUP_DISPLAY = {
  A_POS: 'A+', A_NEG: 'A−', B_POS: 'B+', B_NEG: 'B−',
  AB_POS: 'AB+', AB_NEG: 'AB−', O_POS: 'O+', O_NEG: 'O−', UNKNOWN: 'Unknown',
}

export default function PatientEditForm({ patient }) {
  const navigate = useNavigate()
  const [phoneWarning, setPhoneWarning] = useState(null)
  const [conflictError, setConflictError] = useState(null)

  const { mutateAsync: update, isPending } = useUpdatePatient(patient.patientId)

  const {
    register: rhf,
    handleSubmit,
    formState: { errors },
  } = useForm({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      firstName: patient.firstName || '',
      lastName: patient.lastName || '',
      dateOfBirth: patient.dateOfBirth || '',
      gender: patient.gender || '',
      bloodGroup: patient.bloodGroup || '',
      phone: patient.phone || '',
      email: patient.email || '',
      address: patient.address || '',
      city: patient.city || '',
      state: patient.state || '',
      zipCode: patient.zipCode || '',
      emergencyContactName: patient.emergencyContactName || '',
      emergencyContactPhone: patient.emergencyContactPhone || '',
      emergencyContactRelationship: patient.emergencyContactRelationship || '',
      knownAllergies: patient.knownAllergies || '',
      chronicConditions: patient.chronicConditions || '',
    },
  })

  async function handlePhoneBlur(e) {
    const phone = e.target.value
    if (!phone || !PHONE_REGEX.test(phone)) { setPhoneWarning(null); return }
    try {
      const result = await checkDuplicatePhone(phone, patient.patientId)
      setPhoneWarning(result.duplicate
        ? `⚠ Phone already registered to patient ${result.patientId} (${result.patientName})`
        : null)
    } catch { setPhoneWarning(null) }
  }

  async function onSubmit(data) {
    setConflictError(null)
    const payload = Object.fromEntries(Object.entries(data).map(([k, v]) => [k, v === '' ? null : v]))
    try {
      await update({ data: payload, version: patient.version })
      navigate(`/patients/${patient.patientId}`, {
        state: { message: 'Patient updated successfully.' }
      })
    } catch (err) {
      const status = err?.response?.status
      const msg = err?.response?.data?.message
      if (status === 409) {
        setConflictError(msg || 'Patient record was modified by another user. Please reload and try again.')
      } else {
        setConflictError(msg || 'Failed to update patient.')
      }
    }
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-8">

      {/* Read-only header fields */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 p-4 bg-gray-50 rounded-md border border-gray-200">
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">Patient ID</span>
          <p className="text-sm font-mono text-gray-700 mt-0.5">{patient.patientId}</p>
        </div>
        <div>
          <span className="text-xs font-medium text-gray-500 uppercase tracking-wide">Registration Date</span>
          <p className="text-sm text-gray-700 mt-0.5">
            {new Date(patient.createdAt).toLocaleDateString('en-US', { year: 'numeric', month: 'long', day: 'numeric' })}
          </p>
        </div>
      </div>

      {conflictError && (
        <div role="alert" className="rounded-md bg-red-50 border border-red-200 p-3 text-red-700 text-sm">
          {conflictError}
        </div>
      )}

      {/* Personal Demographics */}
      <section aria-labelledby="edit-personal">
        <h2 id="edit-personal" className="form-section-title">Personal Demographics</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label htmlFor="firstName" className="form-label">
              First Name <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input id="firstName" type="text" className="form-input" aria-required="true" {...rhf('firstName')} />
            <InlineError message={errors.firstName?.message} />
          </div>
          <div>
            <label htmlFor="lastName" className="form-label">
              Last Name <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input id="lastName" type="text" className="form-input" aria-required="true" {...rhf('lastName')} />
            <InlineError message={errors.lastName?.message} />
          </div>
          <div>
            <label htmlFor="dateOfBirth" className="form-label">
              Date of Birth <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input id="dateOfBirth" type="date" className="form-input" aria-required="true" {...rhf('dateOfBirth')} />
            <InlineError message={errors.dateOfBirth?.message} />
          </div>
          <div>
            <label htmlFor="gender" className="form-label">
              Gender <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <select id="gender" className="form-input" aria-required="true" {...rhf('gender')}>
              <option value="">Select gender</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
            <InlineError message={errors.gender?.message} />
          </div>
          <div>
            <label htmlFor="bloodGroup" className="form-label">Blood Group</label>
            <select id="bloodGroup" className="form-input" {...rhf('bloodGroup')}>
              <option value="">Unknown / Not specified</option>
              {Object.entries(BLOOD_GROUP_DISPLAY).filter(([k]) => k !== 'UNKNOWN').map(([k, v]) => (
                <option key={k} value={k}>{v}</option>
              ))}
            </select>
          </div>
        </div>
      </section>

      {/* Contact Information */}
      <section aria-labelledby="edit-contact">
        <h2 id="edit-contact" className="form-section-title">Contact Information</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label htmlFor="phone" className="form-label">
              Phone Number <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input id="phone" type="tel" className="form-input" aria-required="true"
              {...rhf('phone', { onBlur: handlePhoneBlur })} />
            <InlineError message={errors.phone?.message} />
            {phoneWarning && <p className="text-sm text-yellow-700 mt-1" role="alert">{phoneWarning}</p>}
          </div>
          <div>
            <label htmlFor="email" className="form-label">Email</label>
            <input id="email" type="email" className="form-input" {...rhf('email')} />
            <InlineError message={errors.email?.message} />
          </div>
          <div className="md:col-span-2">
            <label htmlFor="address" className="form-label">Street Address</label>
            <input id="address" type="text" className="form-input" {...rhf('address')} />
          </div>
          <div>
            <label htmlFor="city" className="form-label">City</label>
            <input id="city" type="text" className="form-input" {...rhf('city')} />
          </div>
          <div>
            <label htmlFor="state" className="form-label">State</label>
            <input id="state" type="text" className="form-input" {...rhf('state')} />
          </div>
          <div>
            <label htmlFor="zipCode" className="form-label">ZIP Code</label>
            <input id="zipCode" type="text" className="form-input" {...rhf('zipCode')} />
          </div>
        </div>
      </section>

      {/* Emergency Contact */}
      <section aria-labelledby="edit-emergency">
        <h2 id="edit-emergency" className="form-section-title">Emergency Contact</h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div>
            <label htmlFor="emergencyContactName" className="form-label">Contact Name</label>
            <input id="emergencyContactName" type="text" className="form-input" {...rhf('emergencyContactName')} />
            <InlineError message={errors.emergencyContactName?.message} />
          </div>
          <div>
            <label htmlFor="emergencyContactPhone" className="form-label">Contact Phone</label>
            <input id="emergencyContactPhone" type="tel" className="form-input"
              placeholder="555-123-4567" {...rhf('emergencyContactPhone')} />
            <InlineError message={errors.emergencyContactPhone?.message} />
          </div>
          <div>
            <label htmlFor="emergencyContactRelationship" className="form-label">Relationship</label>
            <input id="emergencyContactRelationship" type="text" className="form-input"
              placeholder="e.g. Spouse, Parent, Child" {...rhf('emergencyContactRelationship')} />
          </div>
        </div>
      </section>

      {/* Medical Background */}
      <section aria-labelledby="edit-medical">
        <h2 id="edit-medical" className="form-section-title">Medical Background</h2>
        <div className="grid grid-cols-1 gap-4">
          <div>
            <label htmlFor="knownAllergies" className="form-label">Known Allergies</label>
            <textarea id="knownAllergies" rows={3} className="form-input" {...rhf('knownAllergies')} />
          </div>
          <div>
            <label htmlFor="chronicConditions" className="form-label">Chronic Conditions</label>
            <textarea id="chronicConditions" rows={3} className="form-input" {...rhf('chronicConditions')} />
          </div>
        </div>
      </section>

      <div className="flex justify-end gap-3 pt-4 border-t border-gray-200">
        <button
          type="button"
          className="btn-secondary"
          onClick={() => navigate(`/patients/${patient.patientId}`)}
          disabled={isPending}
        >
          Cancel
        </button>
        <button type="submit" className="btn-primary" disabled={isPending} aria-busy={isPending}>
          {isPending ? 'Saving…' : 'Save Changes'}
        </button>
      </div>
    </form>
  )
}
