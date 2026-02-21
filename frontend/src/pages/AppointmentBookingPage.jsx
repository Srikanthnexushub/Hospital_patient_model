import { useState, useRef, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm, Controller } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useBookAppointment, useDoctors } from '../hooks/useAppointments.js'
import { searchPatients } from '../api/patientApi.js'
import InlineError from '../components/common/InlineError.jsx'

const DURATION_OPTIONS = [15, 30, 45, 60, 90, 120]
const TYPE_OPTIONS = ['GENERAL_CONSULTATION', 'FOLLOW_UP', 'SPECIALIST', 'EMERGENCY', 'ROUTINE_CHECKUP', 'PROCEDURE']

const schema = z.object({
  patientId:       z.string().min(1, 'Select a patient'),
  doctorId:        z.string().min(1, 'Select a doctor'),
  appointmentDate: z.string().min(1, 'Date is required'),
  startTime:       z.string().min(1, 'Start time is required'),
  durationMinutes: z.coerce.number().int().refine(v => DURATION_OPTIONS.includes(v), 'Invalid duration'),
  type:            z.string().min(1, 'Type is required'),
  reason:          z.string().min(1, 'Reason is required').max(500),
  notes:           z.string().max(1000).optional(),
})

// ── Patient live-search combobox ─────────────────────────────────────────────
function PatientPicker({ value, onChange, error }) {
  const [query, setQuery] = useState('')
  const [results, setResults] = useState([])
  const [loading, setLoading] = useState(false)
  const [open, setOpen] = useState(false)
  const [selected, setSelected] = useState(null)
  const debounceRef = useRef(null)
  const wrapperRef = useRef(null)

  // Close dropdown when clicking outside
  useEffect(() => {
    function handleClick(e) {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', handleClick)
    return () => document.removeEventListener('mousedown', handleClick)
  }, [])

  function handleInput(e) {
    const q = e.target.value
    setQuery(q)
    if (selected && q !== `${selected.firstName} ${selected.lastName} (${selected.patientId})`) {
      setSelected(null)
      onChange('')
    }
    clearTimeout(debounceRef.current)
    if (q.length < 2) { setResults([]); setOpen(false); return }
    debounceRef.current = setTimeout(async () => {
      setLoading(true)
      try {
        const data = await searchPatients({ query: q, status: 'ACTIVE', page: 0, size: 8 })
        setResults(data.content ?? [])
        setOpen(true)
      } finally {
        setLoading(false)
      }
    }, 300)
  }

  function handleSelect(patient) {
    setSelected(patient)
    setQuery(`${patient.firstName} ${patient.lastName} (${patient.patientId})`)
    onChange(patient.patientId)
    setOpen(false)
  }

  return (
    <div ref={wrapperRef} className="relative">
      <input
        type="text"
        value={query}
        onChange={handleInput}
        onFocus={() => results.length > 0 && setOpen(true)}
        className={`w-full border rounded px-3 py-2 text-sm ${error ? 'border-red-400' : 'border-gray-300'}`}
        placeholder="Search by name or patient ID…"
        autoComplete="off"
      />
      {loading && (
        <span className="absolute right-3 top-2.5 text-xs text-gray-400">Searching…</span>
      )}
      {open && results.length > 0 && (
        <ul className="absolute z-20 w-full bg-white border border-gray-200 rounded shadow-lg mt-1 max-h-48 overflow-y-auto">
          {results.map(p => (
            <li
              key={p.patientId}
              className="px-3 py-2 text-sm hover:bg-blue-50 cursor-pointer flex justify-between"
              onMouseDown={() => handleSelect(p)}
            >
              <span className="font-medium text-gray-900">{p.firstName} {p.lastName}</span>
              <span className="text-gray-400 text-xs ml-2">{p.patientId} · {p.gender}</span>
            </li>
          ))}
        </ul>
      )}
      {open && !loading && query.length >= 2 && results.length === 0 && (
        <div className="absolute z-20 w-full bg-white border border-gray-200 rounded shadow-lg mt-1 px-3 py-2 text-sm text-gray-500">
          No active patients found
        </div>
      )}
    </div>
  )
}

// ── Main page ────────────────────────────────────────────────────────────────
export default function AppointmentBookingPage() {
  const navigate = useNavigate()
  const { mutate: book, isPending, error } = useBookAppointment()
  const { data: doctors = [], isLoading: doctorsLoading } = useDoctors()

  const { register, handleSubmit, control, formState: { errors } } = useForm({
    resolver: zodResolver(schema),
    defaultValues: { durationMinutes: 30, type: 'GENERAL_CONSULTATION' },
  })

  const serverError = error?.response?.data?.message ?? error?.message

  function onSubmit(data) {
    book(data, {
      onSuccess: appt => navigate(`/appointments/${appt.appointmentId}`, { state: { message: 'Appointment booked successfully.' } }),
    })
  }

  return (
    <div className="max-w-2xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Book Appointment</h1>

      {serverError && (
        <div className="mb-4 rounded-md bg-red-50 border border-red-200 p-4 text-red-700 text-sm">{serverError}</div>
      )}

      <form onSubmit={handleSubmit(onSubmit)} className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 space-y-4">

        {/* Patient search */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Patient <span className="text-red-500">*</span>
          </label>
          <Controller
            name="patientId"
            control={control}
            render={({ field }) => (
              <PatientPicker
                value={field.value}
                onChange={field.onChange}
                error={errors.patientId}
              />
            )}
          />
          <InlineError error={errors.patientId} />
        </div>

        {/* Doctor dropdown */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Doctor <span className="text-red-500">*</span>
          </label>
          <select
            {...register('doctorId')}
            disabled={doctorsLoading}
            className={`w-full border rounded px-3 py-2 text-sm ${errors.doctorId ? 'border-red-400' : 'border-gray-300'}`}
          >
            <option value="">{doctorsLoading ? 'Loading doctors…' : '— Select a doctor —'}</option>
            {doctors.map(d => (
              <option key={d.userId} value={d.userId}>
                {d.username}{d.department ? ` — ${d.department}` : ''}
              </option>
            ))}
          </select>
          <InlineError error={errors.doctorId} />
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
            <input type="date" {...register('appointmentDate')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" />
            <InlineError error={errors.appointmentDate} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Start Time</label>
            <input type="time" step="1800" {...register('startTime')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" />
            <InlineError error={errors.startTime} />
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Duration (minutes)</label>
            <select {...register('durationMinutes')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm">
              {DURATION_OPTIONS.map(d => <option key={d} value={d}>{d} min</option>)}
            </select>
            <InlineError error={errors.durationMinutes} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Type</label>
            <select {...register('type')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm">
              {TYPE_OPTIONS.map(t => <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>)}
            </select>
            <InlineError error={errors.type} />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            Reason <span className="text-red-500">*</span>
          </label>
          <input {...register('reason')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="Brief reason for visit" />
          <InlineError error={errors.reason} />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Notes (optional)</label>
          <textarea {...register('notes')} rows={3} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" />
        </div>

        <div className="flex gap-3 pt-2">
          <button type="submit" disabled={isPending} className="btn-primary">
            {isPending ? 'Booking…' : 'Book Appointment'}
          </button>
          <button type="button" onClick={() => navigate(-1)} className="btn-secondary">
            Cancel
          </button>
        </div>
      </form>
    </div>
  )
}
