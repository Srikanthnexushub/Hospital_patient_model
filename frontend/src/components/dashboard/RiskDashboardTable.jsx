import { Link } from 'react-router-dom'
import Pagination from '../common/Pagination.jsx'

const NEWS2_COLOUR = {
  LOW:        'bg-green-100 text-green-800',
  LOW_MEDIUM: 'bg-yellow-100 text-yellow-800',
  MEDIUM:     'bg-orange-100 text-orange-800',
  HIGH:       'bg-red-100 text-red-800 font-bold',
  NO_DATA:    'bg-gray-100 text-gray-500',
}

/**
 * Paginated, risk-ranked patient table for the dashboard.
 * Props:
 *   data        – Page<PatientRiskRow> from the API
 *   page        – current page index
 *   onPageChange – callback(newPage)
 */
export default function RiskDashboardTable({ data, page, onPageChange }) {
  const rows = data?.content ?? []

  if (rows.length === 0) {
    return <p className="text-gray-500 text-sm">No patients to display.</p>
  }

  return (
    <div>
      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table className="min-w-full divide-y divide-gray-200 text-sm">
          <thead className="bg-gray-50">
            <tr>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Patient</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">NEWS2</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Critical</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Warning</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Meds</th>
              <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase tracking-wide">Last Visit</th>
              <th className="px-4 py-3"></th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100 bg-white">
            {rows.map(row => {
              const riskLevel = row.news2RiskLevel ?? 'NO_DATA'
              const riskCls = NEWS2_COLOUR[riskLevel] ?? NEWS2_COLOUR.NO_DATA
              const hasCritical = row.criticalAlertCount > 0
              return (
                <tr key={row.patientId} className={hasCritical ? 'bg-red-50/40' : ''}>
                  <td className="px-4 py-3">
                    <p className="font-medium text-gray-900">{row.patientName}</p>
                    <p className="text-xs text-gray-400">{row.patientId} · {row.bloodGroup ?? '—'}</p>
                  </td>
                  <td className="px-4 py-3">
                    <div className="flex items-center gap-1.5">
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${riskCls}`}>
                        {riskLevel.replace(/_/g, ' ')}
                      </span>
                      {row.news2Score != null && (
                        <span className="text-xs text-gray-500">({row.news2Score})</span>
                      )}
                    </div>
                  </td>
                  <td className="px-4 py-3">
                    {row.criticalAlertCount > 0 ? (
                      <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-bold bg-red-100 text-red-800">
                        {row.criticalAlertCount}
                      </span>
                    ) : (
                      <span className="text-xs text-gray-400">0</span>
                    )}
                  </td>
                  <td className="px-4 py-3">
                    {row.warningAlertCount > 0 ? (
                      <span className="inline-flex px-2 py-0.5 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800">
                        {row.warningAlertCount}
                      </span>
                    ) : (
                      <span className="text-xs text-gray-400">0</span>
                    )}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-600">
                    {row.activeMedicationCount ?? 0}
                  </td>
                  <td className="px-4 py-3 text-xs text-gray-500">
                    {row.lastVisitDate ?? '—'}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <Link
                      to={`/patients/${row.patientId}`}
                      className="text-xs text-blue-600 hover:underline"
                    >
                      View
                    </Link>
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>

      {(data?.totalPages ?? 0) > 1 && (
        <div className="mt-4">
          <Pagination page={page} totalPages={data.totalPages} onPageChange={onPageChange} />
        </div>
      )}
    </div>
  )
}
