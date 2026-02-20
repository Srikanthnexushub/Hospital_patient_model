import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { useAppointments, useTodayAppointments } from '../hooks/useAppointments.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'
import Pagination from '../components/common/Pagination.jsx'

const STATUS_OPTIONS = ['', 'SCHEDULED', 'CONFIRMED', 'CHECKED_IN', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED', 'NO_SHOW']
const TYPE_OPTIONS = ['', 'GENERAL_CONSULTATION', 'FOLLOW_UP', 'SPECIALIST', 'EMERGENCY', 'ROUTINE_CHECKUP', 'PROCEDURE']

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
      {status?.replace('_', ' ')}
    </span>
  )
}

export default function AppointmentListPage() {
  const { role } = useAuth()
  const [showToday, setShowToday] = useState(false)
  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState({ date: '', status: '', type: '' })
  const canBook = role === 'RECEPTIONIST' || role === 'ADMIN'

  const todayQuery = useTodayAppointments(page)
  const allQuery = useAppointments({ ...filters, page, size: 20 })
  const { data, isLoading, isError } = showToday ? todayQuery : allQuery

  const appointments = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  function handleFilter(key, value) {
    setFilters(f => ({ ...f, [key]: value }))
    setPage(0)
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Appointments</h1>
        {canBook && (
          <Link to="/appointments/new" className="btn-primary self-start sm:self-auto">
            + Book Appointment
          </Link>
        )}
      </div>

      {/* Today / All toggle */}
      <div className="flex gap-2 mb-4">
        <button
          onClick={() => { setShowToday(false); setPage(0) }}
          className={`px-3 py-1.5 rounded text-sm font-medium ${!showToday ? 'bg-blue-600 text-white' : 'bg-white border border-gray-300 text-gray-700 hover:bg-gray-50'}`}
        >
          All
        </button>
        <button
          onClick={() => { setShowToday(true); setPage(0) }}
          className={`px-3 py-1.5 rounded text-sm font-medium ${showToday ? 'bg-blue-600 text-white' : 'bg-white border border-gray-300 text-gray-700 hover:bg-gray-50'}`}
        >
          Today
        </button>
      </div>

      {/* Filters (only for all view) */}
      {!showToday && (
        <div className="flex flex-wrap gap-2 mb-4">
          <input
            type="date"
            value={filters.date}
            onChange={e => handleFilter('date', e.target.value)}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          />
          <select
            value={filters.status}
            onChange={e => handleFilter('status', e.target.value)}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            <option value="">All Statuses</option>
            {STATUS_OPTIONS.filter(Boolean).map(s => (
              <option key={s} value={s}>{s.replace('_', ' ')}</option>
            ))}
          </select>
          <select
            value={filters.type}
            onChange={e => handleFilter('type', e.target.value)}
            className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            <option value="">All Types</option>
            {TYPE_OPTIONS.filter(Boolean).map(t => (
              <option key={t} value={t}>{t.replace(/_/g, ' ')}</option>
            ))}
          </select>
        </div>
      )}

      {data && (
        <p className="text-sm text-gray-500 mb-3">
          {totalElements === 0 ? 'No appointments found' : `${totalElements} appointment${totalElements !== 1 ? 's' : ''} found`}
        </p>
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <div className="text-red-600 text-sm p-4 bg-red-50 rounded-md border border-red-200">
          Failed to load appointments. Please try again.
        </div>
      ) : appointments.length === 0 ? (
        <div className="text-center py-12 text-gray-500">No appointments found.</div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">ID</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Patient</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Doctor</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Date & Time</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Type</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {appointments.map(appt => (
                  <tr key={appt.appointmentId} className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => window.location.assign(`/appointments/${appt.appointmentId}`)}>
                    <td className="px-4 py-3 font-mono text-xs text-gray-600">{appt.appointmentId}</td>
                    <td className="px-4 py-3 font-medium text-gray-900">{appt.patientName}</td>
                    <td className="px-4 py-3 text-gray-700">{appt.doctorName}</td>
                    <td className="px-4 py-3 text-gray-700">
                      {appt.appointmentDate}<br />
                      <span className="text-xs text-gray-500">{appt.startTime} – {appt.endTime}</span>
                    </td>
                    <td className="px-4 py-3 text-gray-700">{appt.type?.replace(/_/g, ' ')}</td>
                    <td className="px-4 py-3"><StatusBadge status={appt.status} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <Pagination
              page={page}
              totalPages={totalPages}
              onPageChange={setPage}
            />
          )}
        </>
      )}
    </div>
  )
}
