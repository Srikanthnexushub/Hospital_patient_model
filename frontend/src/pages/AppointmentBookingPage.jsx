import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { useBookAppointment } from '../hooks/useAppointments.js'
import InlineError from '../components/common/InlineError.jsx'

const DURATION_OPTIONS = [15, 30, 45, 60, 90, 120]
const TYPE_OPTIONS = ['GENERAL_CONSULTATION', 'FOLLOW_UP', 'SPECIALIST', 'EMERGENCY', 'ROUTINE_CHECKUP', 'PROCEDURE']

const schema = z.object({
  patientId:       z.string().min(1, 'Patient ID is required'),
  doctorId:        z.string().min(1, 'Doctor ID is required'),
  appointmentDate: z.string().min(1, 'Date is required'),
  startTime:       z.string().min(1, 'Start time is required'),
  durationMinutes: z.coerce.number().int().refine(v => DURATION_OPTIONS.includes(v), 'Invalid duration'),
  type:            z.string().min(1, 'Type is required'),
  reason:          z.string().min(1, 'Reason is required').max(500),
  notes:           z.string().max(1000).optional(),
})

export default function AppointmentBookingPage() {
  const navigate = useNavigate()
  const { mutate: book, isPending, error } = useBookAppointment()

  const { register, handleSubmit, formState: { errors } } = useForm({
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
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Patient ID</label>
            <input {...register('patientId')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="e.g. P2026001" />
            <InlineError error={errors.patientId} />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Doctor ID</label>
            <input {...register('doctorId')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="e.g. U2025001" />
            <InlineError error={errors.doctorId} />
          </div>
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
          <label className="block text-sm font-medium text-gray-700 mb-1">Reason <span className="text-red-500">*</span></label>
          <input {...register('reason')} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" placeholder="Brief reason for visit" />
          <InlineError error={errors.reason} />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Notes (optional)</label>
          <textarea {...register('notes')} rows={3} className="w-full border border-gray-300 rounded px-3 py-2 text-sm" />
          <InlineError error={errors.notes} />
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
