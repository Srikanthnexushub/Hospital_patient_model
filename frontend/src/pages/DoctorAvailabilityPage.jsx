import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useDoctorAvailability } from '../hooks/useAppointments.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

export default function DoctorAvailabilityPage() {
  const today = new Date().toISOString().slice(0, 10)
  const [doctorId, setDoctorId] = useState('')
  const [date, setDate] = useState(today)
  const [query, setQuery] = useState({ doctorId: '', date: today })

  const { data, isLoading, isError } = useDoctorAvailability(query.doctorId, query.date)

  function handleSearch() {
    if (doctorId && date) setQuery({ doctorId, date })
  }

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="mb-4">
        <Link to="/appointments" className="text-sm text-blue-600 hover:underline">← Appointments</Link>
      </div>
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Doctor Availability</h1>

      <div className="flex flex-wrap gap-3 mb-6 items-end">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Doctor ID</label>
          <input
            value={doctorId}
            onChange={e => setDoctorId(e.target.value)}
            placeholder="e.g. U2025001"
            className="border border-gray-300 rounded px-3 py-2 text-sm w-44"
          />
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">Date</label>
          <input
            type="date"
            value={date}
            onChange={e => setDate(e.target.value)}
            className="border border-gray-300 rounded px-3 py-2 text-sm"
          />
        </div>
        <button onClick={handleSearch} className="btn-primary py-2">
          Check Availability
        </button>
      </div>

      {!query.doctorId ? (
        <p className="text-gray-500 text-sm">Enter a Doctor ID and date to check availability.</p>
      ) : isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <div className="text-red-600 text-sm p-4 bg-red-50 rounded-md border border-red-200">
          Doctor not found or failed to load availability.
        </div>
      ) : data ? (
        <>
          <p className="text-sm text-gray-600 mb-4">
            <strong>{data.doctorName}</strong> ({data.doctorId}) — {data.date}
          </p>
          <div className="grid grid-cols-2 sm:grid-cols-4 md:grid-cols-5 gap-2">
            {data.slots?.map(slot => (
              <div
                key={slot.startTime}
                className={`rounded-md border p-2 text-center text-sm ${
                  slot.available
                    ? 'bg-green-50 border-green-200 text-green-800'
                    : 'bg-red-50 border-red-200 text-red-700'
                }`}
              >
                <p className="font-medium">{slot.startTime?.slice(0, 5)}</p>
                <p className="text-xs mt-0.5">{slot.available ? 'Available' : 'Booked'}</p>
                {!slot.available && slot.appointmentId && (
                  <Link to={`/appointments/${slot.appointmentId}`} className="text-xs underline mt-1 block" onClick={e => e.stopPropagation()}>
                    View
                  </Link>
                )}
              </div>
            ))}
          </div>
        </>
      ) : null}
    </div>
  )
}
