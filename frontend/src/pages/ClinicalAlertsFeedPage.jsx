import { useState } from 'react'
import { useAuth } from '../hooks/useAuth.js'
import { useGlobalAlerts } from '../api/clinicalAlerts.js'
import AlertFeed from '../components/alerts/AlertFeed.jsx'
import Pagination from '../components/common/Pagination.jsx'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

const STATUS_OPTIONS   = ['', 'ACTIVE', 'ACKNOWLEDGED', 'DISMISSED']
const SEVERITY_OPTIONS = ['', 'CRITICAL', 'WARNING', 'INFO']

export default function ClinicalAlertsFeedPage() {
  const { role } = useAuth()
  const [status,   setStatus]   = useState('ACTIVE')
  const [severity, setSeverity] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useGlobalAlerts(status || undefined, severity || undefined, page)

  const canAct = ['DOCTOR', 'ADMIN'].includes(role)
  const alerts = data?.content ?? []

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6 gap-3 flex-wrap">
        <h1 className="text-2xl font-bold text-gray-900">Clinical Alerts</h1>
        <span className="text-xs text-gray-400 italic">Auto-refreshes every 30s</span>
      </div>

      {/* Filters */}
      <div className="flex gap-3 flex-wrap mb-6">
        <div>
          <label className="block text-xs font-medium text-gray-500 mb-1">Status</label>
          <select
            value={status}
            onChange={e => { setStatus(e.target.value); setPage(0) }}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm"
          >
            {STATUS_OPTIONS.map(o => (
              <option key={o} value={o}>{o || 'All'}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-500 mb-1">Severity</label>
          <select
            value={severity}
            onChange={e => { setSeverity(e.target.value); setPage(0) }}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm"
          >
            {SEVERITY_OPTIONS.map(o => (
              <option key={o} value={o}>{o || 'All'}</option>
            ))}
          </select>
        </div>
      </div>

      {/* Alert list */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        {isLoading ? (
          <LoadingSpinner />
        ) : isError ? (
          <p className="text-red-600 text-sm">Failed to load alerts.</p>
        ) : (
          <>
            {data?.totalElements != null && (
              <p className="text-xs text-gray-400 mb-4">{data.totalElements} alert{data.totalElements !== 1 ? 's' : ''}</p>
            )}
            <AlertFeed alerts={alerts} canAct={canAct} />
            {(data?.totalPages ?? 0) > 1 && (
              <div className="mt-6">
                <Pagination page={page} totalPages={data.totalPages} onPageChange={setPage} />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  )
}
