import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { useSearchPatients } from '../hooks/usePatients.js'
import { useListState } from '../hooks/useListState.js'
import { searchPatients } from '../api/patientApi.js'
import { downloadCsv } from '../utils/exportCsv.js'
import SearchBox from '../components/common/SearchBox.jsx'
import FilterBar from '../components/common/FilterBar.jsx'
import PatientList from '../components/patient/PatientList.jsx'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

export default function PatientListPage() {
  const { role } = useAuth()
  const {
    query, status, gender, bloodGroup, page,
    setQuery, setStatus, setGender, setBloodGroup, setPage, reset,
  } = useListState()

  const canRegister = role === 'RECEPTIONIST' || role === 'ADMIN'
  const [exporting, setExporting] = useState(false)

  const { data, isLoading, isError } = useSearchPatients({
    query: query || undefined,
    status,
    gender,
    bloodGroup,
    page,
    size: 20,
  })

  const patients = data?.content ?? []
  const pagination = data
    ? { number: data.page, size: data.size, totalElements: data.totalElements,
        totalPages: data.totalPages, first: data.first, last: data.last }
    : null
  const hasQuery = !!(query || status !== 'ACTIVE' || gender !== 'ALL' || bloodGroup !== 'ALL')

  async function handleExport() {
    setExporting(true)
    try {
      const all = await searchPatients({
        query: query || undefined,
        status,
        gender,
        bloodGroup,
        page: 0,
        size: 1000,
      })
      const headers = ['Patient ID', 'First Name', 'Last Name', 'Age', 'Gender', 'Phone', 'Status']
      const rows = all.content.map(p => [
        p.patientId,
        p.firstName,
        p.lastName,
        p.age,
        p.gender,
        p.phone,
        p.status,
      ])
      downloadCsv('patients', headers, rows)
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">

      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Patients</h1>
        <div className="flex gap-2 self-start sm:self-auto">
          <button
            onClick={handleExport}
            disabled={exporting || !data || data.totalElements === 0}
            className="btn-secondary text-sm disabled:opacity-50"
          >
            {exporting ? 'Exporting…' : '↓ Export CSV'}
          </button>
          {canRegister && (
            <Link to="/patients/new" className="btn-primary">
              + Register New Patient
            </Link>
          )}
        </div>
      </div>

      {/* Search + Filters */}
      <div className="flex flex-col sm:flex-row gap-3 mb-4 items-start sm:items-center">
        <SearchBox
          value={query}
          onChange={q => { setQuery(q); setPage(0) }}
          onClear={() => { reset() }}
        />
        <FilterBar
          status={status}
          gender={gender}
          bloodGroup={bloodGroup}
          onStatusChange={v => { setStatus(v); setPage(0) }}
          onGenderChange={v => { setGender(v); setPage(0) }}
          onBloodGroupChange={v => { setBloodGroup(v); setPage(0) }}
        />
      </div>

      {/* Result count hint */}
      {data && (
        <p className="text-sm text-gray-500 mb-3" aria-live="polite" aria-atomic="true">
          {data.totalElements === 0
            ? 'No patients found'
            : `${data.totalElements} patient${data.totalElements !== 1 ? 's' : ''} found`}
        </p>
      )}

      {/* Content */}
      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <div className="text-red-600 text-sm p-4 bg-red-50 rounded-md border border-red-200">
          Failed to load patients. Please try again.
        </div>
      ) : (
        <PatientList
          patients={patients}
          pagination={pagination}
          role={role}
          hasQuery={hasQuery}
          onPageChange={setPage}
        />
      )}
    </div>
  )
}
