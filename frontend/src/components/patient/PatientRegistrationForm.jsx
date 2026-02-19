import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import InlineError from '../common/InlineError.jsx'
import { checkDuplicatePhone } from '../../api/patientApi.js'
import { useRegisterPatient } from '../../hooks/usePatients.js'

// ── Zod schema mirroring backend validation rules ─────────────────────────

const PHONE_REGEX = /^(\+1-\d{3}-\d{3}-\d{4}|\(\d{3}\) \d{3}-\d{4}|\d{3}-\d{3}-\d{4})$/

const schema = z
  .object({
    firstName: z.string().min(1, 'First name is required').max(100),
    lastName: z.string().min(1, 'Last name is required').max(100),
    dateOfBirth: z
      .string()
      .min(1, 'Date of birth is required')
      .refine(val => {
        const d = new Date(val)
        const today = new Date()
        today.setHours(0, 0, 0, 0)
        return d < today
      }, 'Date of birth must be in the past')
      .refine(val => {
        const d = new Date(val)
        const minDate = new Date()
        minDate.setFullYear(minDate.getFullYear() - 150)
        return d > minDate
      }, 'Date of birth cannot be more than 150 years ago'),
    gender: z.enum(['MALE', 'FEMALE', 'OTHER'], { required_error: 'Gender is required' }),
    bloodGroup: z
      .enum(['A_POS', 'A_NEG', 'B_POS', 'B_NEG', 'AB_POS', 'AB_NEG', 'O_POS', 'O_NEG', 'UNKNOWN', ''])
      .optional(),
    phone: z
      .string()
      .min(1, 'Phone number is required')
      .regex(PHONE_REGEX, 'Enter a valid phone number (e.g. 555-123-4567)'),
    email: z.string().email('Enter a valid email address').optional().or(z.literal('')),
    address: z.string().max(255).optional().or(z.literal('')),
    city: z.string().max(100).optional().or(z.literal('')),
    state: z.string().max(100).optional().or(z.literal('')),
    zipCode: z.string().max(20).optional().or(z.literal('')),
    emergencyContactName: z.string().max(200).optional().or(z.literal('')),
    emergencyContactPhone: z
      .string()
      .optional()
      .or(z.literal(''))
      .refine(val => !val || PHONE_REGEX.test(val), 'Enter a valid phone number'),
    emergencyContactRelationship: z.string().max(100).optional().or(z.literal('')),
    knownAllergies: z.string().max(1000).optional().or(z.literal('')),
    chronicConditions: z.string().max(1000).optional().or(z.literal('')),
  })
  .refine(
    data =>
      !data.emergencyContactName ||
      (data.emergencyContactName && data.emergencyContactPhone),
    {
      message: 'Emergency contact phone is required when name is provided',
      path: ['emergencyContactPhone'],
    }
  )
  .refine(
    data =>
      !data.emergencyContactPhone ||
      (data.emergencyContactPhone && data.emergencyContactName),
    {
      message: 'Emergency contact name is required when phone is provided',
      path: ['emergencyContactName'],
    }
  )

// ── Helper: compute age from ISO date string ──────────────────────────────

function computeAge(dob) {
  if (!dob) return null
  const birth = new Date(dob)
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const m = today.getMonth() - birth.getMonth()
  if (m < 0 || (m === 0 && today.getDate() < birth.getDate())) age--
  return age >= 0 ? age : null
}

// ── Component ─────────────────────────────────────────────────────────────

export default function PatientRegistrationForm({ role, onSuccess }) {
  const [age, setAge] = useState(null)
  const [phoneWarning, setPhoneWarning] = useState(null)
  const { mutateAsync: register, isPending } = useRegisterPatient()

  const {
    register: rhf,
    handleSubmit,
    formState: { errors },
    getValues,
  } = useForm({
    resolver: zodResolver(schema),
    mode: 'onBlur',
    defaultValues: {
      bloodGroup: '',
      email: '',
      address: '',
      city: '',
      state: '',
      zipCode: '',
      emergencyContactName: '',
      emergencyContactPhone: '',
      emergencyContactRelationship: '',
      knownAllergies: '',
      chronicConditions: '',
    },
  })

  // Role guard — form is not rendered for DOCTOR/NURSE
  if (role === 'DOCTOR' || role === 'NURSE') {
    return (
      <div className="rounded-md bg-red-50 border border-red-200 p-4">
        <p className="text-red-700">You do not have permission to register patients.</p>
      </div>
    )
  }

  async function handleDobBlur(e) {
    const val = e.target.value
    setAge(computeAge(val))
  }

  async function handlePhoneBlur(e) {
    const phone = e.target.value
    if (!phone || !PHONE_REGEX.test(phone)) {
      setPhoneWarning(null)
      return
    }
    try {
      const result = await checkDuplicatePhone(phone)
      if (result.duplicate) {
        setPhoneWarning(
          `⚠ Phone already registered to patient ${result.patientId} (${result.patientName})`
        )
      } else {
        setPhoneWarning(null)
      }
    } catch {
      setPhoneWarning(null)
    }
  }

  async function onSubmit(data) {
    // Convert empty strings to null for optional fields
    const payload = Object.fromEntries(
      Object.entries(data).map(([k, v]) => [k, v === '' ? null : v])
    )
    const response = await register(payload)
    onSuccess?.(response)
  }

  return (
    <form onSubmit={handleSubmit(onSubmit)} noValidate className="space-y-8">

      {/* ── Section 1: Personal Demographics ───────────────────────────── */}
      <section aria-labelledby="section-personal">
        <h2 id="section-personal" className="form-section-title">
          Personal Demographics
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

          <div>
            <label htmlFor="firstName" className="form-label">
              First Name <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="firstName"
              type="text"
              className="form-input"
              aria-required="true"
              aria-describedby={errors.firstName ? 'firstName-error' : undefined}
              {...rhf('firstName')}
            />
            <InlineError id="firstName-error" message={errors.firstName?.message} />
          </div>

          <div>
            <label htmlFor="lastName" className="form-label">
              Last Name <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="lastName"
              type="text"
              className="form-input"
              aria-required="true"
              aria-describedby={errors.lastName ? 'lastName-error' : undefined}
              {...rhf('lastName')}
            />
            <InlineError id="lastName-error" message={errors.lastName?.message} />
          </div>

          <div>
            <label htmlFor="dateOfBirth" className="form-label">
              Date of Birth <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="dateOfBirth"
              type="date"
              className="form-input"
              aria-required="true"
              aria-describedby={errors.dateOfBirth ? 'dob-error' : undefined}
              {...rhf('dateOfBirth', { onBlur: handleDobBlur })}
            />
            <InlineError id="dob-error" message={errors.dateOfBirth?.message} />
            {age !== null && (
              <p className="text-sm text-gray-500 mt-1">Age: {age} years</p>
            )}
          </div>

          <div>
            <label htmlFor="gender" className="form-label">
              Gender <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <select
              id="gender"
              className="form-input"
              aria-required="true"
              aria-describedby={errors.gender ? 'gender-error' : undefined}
              {...rhf('gender')}
            >
              <option value="">Select gender</option>
              <option value="MALE">Male</option>
              <option value="FEMALE">Female</option>
              <option value="OTHER">Other</option>
            </select>
            <InlineError id="gender-error" message={errors.gender?.message} />
          </div>

          <div>
            <label htmlFor="bloodGroup" className="form-label">Blood Group</label>
            <select id="bloodGroup" className="form-input" {...rhf('bloodGroup')}>
              <option value="">Unknown / Not specified</option>
              <option value="A_POS">A+</option>
              <option value="A_NEG">A−</option>
              <option value="B_POS">B+</option>
              <option value="B_NEG">B−</option>
              <option value="AB_POS">AB+</option>
              <option value="AB_NEG">AB−</option>
              <option value="O_POS">O+</option>
              <option value="O_NEG">O−</option>
            </select>
          </div>
        </div>
      </section>

      {/* ── Section 2: Contact Information ──────────────────────────────── */}
      <section aria-labelledby="section-contact">
        <h2 id="section-contact" className="form-section-title">
          Contact Information
        </h2>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

          <div>
            <label htmlFor="phone" className="form-label">
              Phone Number <span className="text-red-500" aria-hidden="true">*</span>
            </label>
            <input
              id="phone"
              type="tel"
              className="form-input"
              placeholder="555-123-4567"
              aria-required="true"
              aria-describedby={errors.phone ? 'phone-error' : undefined}
              aria-live="polite"
              {...rhf('phone', { onBlur: handlePhoneBlur })}
            />
            <InlineError id="phone-error" message={errors.phone?.message} />
            {phoneWarning && (
              <p className="text-sm text-yellow-700 mt-1" role="alert" aria-live="polite">
                {phoneWarning}
              </p>
            )}
          </div>

          <div>
            <label htmlFor="email" className="form-label">Email</label>
            <input
              id="email"
              type="email"
              className="form-input"
              aria-describedby={errors.email ? 'email-error' : undefined}
              {...rhf('email')}
            />
            <InlineError id="email-error" message={errors.email?.message} />
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

      {/* ── Section 3: Emergency Contact ─────────────────────────────────── */}
      <section aria-labelledby="section-emergency">
        <h2 id="section-emergency" className="form-section-title">
          Emergency Contact
        </h2>
        <p className="text-sm text-gray-500 mb-3">
          If provided, both name and phone number are required together.
        </p>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">

          <div>
            <label htmlFor="emergencyContactName" className="form-label">Contact Name</label>
            <input
              id="emergencyContactName"
              type="text"
              className="form-input"
              aria-describedby={errors.emergencyContactName ? 'ec-name-error' : undefined}
              {...rhf('emergencyContactName')}
            />
            <InlineError id="ec-name-error" message={errors.emergencyContactName?.message} />
          </div>

          <div>
            <label htmlFor="emergencyContactPhone" className="form-label">Contact Phone</label>
            <input
              id="emergencyContactPhone"
              type="tel"
              className="form-input"
              placeholder="555-123-4567"
              aria-describedby={errors.emergencyContactPhone ? 'ec-phone-error' : undefined}
              {...rhf('emergencyContactPhone')}
            />
            <InlineError id="ec-phone-error" message={errors.emergencyContactPhone?.message} />
          </div>

          <div>
            <label htmlFor="emergencyContactRelationship" className="form-label">Relationship</label>
            <input
              id="emergencyContactRelationship"
              type="text"
              className="form-input"
              placeholder="e.g. Spouse, Parent, Child"
              {...rhf('emergencyContactRelationship')}
            />
          </div>
        </div>
      </section>

      {/* ── Section 4: Medical Background ───────────────────────────────── */}
      <section aria-labelledby="section-medical">
        <h2 id="section-medical" className="form-section-title">
          Medical Background
        </h2>
        <div className="grid grid-cols-1 gap-4">

          <div>
            <label htmlFor="knownAllergies" className="form-label">Known Allergies</label>
            <textarea
              id="knownAllergies"
              rows={3}
              className="form-input"
              placeholder="List any known allergies (e.g. Penicillin, Peanuts)"
              {...rhf('knownAllergies')}
            />
          </div>

          <div>
            <label htmlFor="chronicConditions" className="form-label">Chronic Conditions</label>
            <textarea
              id="chronicConditions"
              rows={3}
              className="form-input"
              placeholder="List any chronic conditions (e.g. Diabetes, Hypertension)"
              {...rhf('chronicConditions')}
            />
          </div>
        </div>
      </section>

      {/* ── Submit ─────────────────────────────────────────────────────── */}
      <div className="flex justify-end gap-3 pt-4 border-t border-gray-200">
        <button
          type="submit"
          className="btn-primary"
          disabled={isPending}
          aria-busy={isPending}
        >
          {isPending ? 'Registering…' : 'Register Patient'}
        </button>
      </div>
    </form>
  )
}
