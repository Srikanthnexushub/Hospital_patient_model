import { useState } from 'react'
import { useRiskDashboard, useDashboardStats } from '../api/dashboard.js'
import DashboardStatsCard from '../components/dashboard/DashboardStatsCard.jsx'
import RiskDashboardTable from '../components/dashboard/RiskDashboardTable.jsx'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

export default function PatientRiskDashboardPage() {
  const [page, setPage] = useState(0)

  const { data: dashboardData, isLoading: dashLoading, isError: dashError } = useRiskDashboard(page)
  const { data: stats, isLoading: statsLoading, isError: statsError } = useDashboardStats()

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex items-center justify-between mb-6 gap-3 flex-wrap">
        <h1 className="text-2xl font-bold text-gray-900">Patient Risk Dashboard</h1>
        <span className="text-xs text-gray-400 italic">Auto-refreshes every 30s</span>
      </div>

      {/* Stats cards */}
      {statsLoading ? (
        <LoadingSpinner />
      ) : statsError ? (
        <p className="text-red-600 text-sm mb-6">Failed to load stats.</p>
      ) : stats && (
        <>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-8">
            <DashboardStatsCard
              label="Active Patients"
              value={stats.totalActivePatients}
            />
            <DashboardStatsCard
              label="With Critical Alerts"
              value={stats.patientsWithCriticalAlerts}
              highlight={stats.patientsWithCriticalAlerts > 0 ? 'text-red-600' : undefined}
            />
            <DashboardStatsCard
              label="High NEWS2"
              value={stats.patientsWithHighNews2}
              highlight={stats.patientsWithHighNews2 > 0 ? 'text-orange-600' : undefined}
            />
            <DashboardStatsCard
              label="Total Active Alerts"
              value={stats.totalActiveAlerts}
              highlight={stats.totalActiveAlerts > 0 ? 'text-yellow-600' : undefined}
            />
          </div>

          {/* Alert breakdown row */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-8">
            <DashboardStatsCard
              label="Critical Alerts"
              value={stats.totalCriticalAlerts}
              highlight={stats.totalCriticalAlerts > 0 ? 'text-red-600' : undefined}
            />
            <DashboardStatsCard
              label="Warning Alerts"
              value={stats.totalWarningAlerts}
              highlight={stats.totalWarningAlerts > 0 ? 'text-yellow-600' : undefined}
            />
            {/* Alert types breakdown */}
            <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-4">
              <p className="text-xs text-gray-500 font-medium uppercase tracking-wide mb-2">By Type</p>
              {stats.alertsByType?.length > 0 ? (
                <div className="space-y-1">
                  {stats.alertsByType.map(({ type, count }) => (
                    <div key={type} className="flex justify-between items-center text-xs">
                      <span className="text-gray-600">{type?.replace(/_/g, ' ')}</span>
                      <span className="font-semibold text-gray-900">{count}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <p className="text-xs text-gray-400">No active alerts</p>
              )}
            </div>
          </div>
        </>
      )}

      {/* Risk-ranked patient table */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <h2 className="text-base font-semibold text-gray-900 mb-4">
          Risk-Ranked Patients
          <span className="ml-2 text-xs font-normal text-gray-400">sorted by critical alerts → NEWS2 score → warning alerts</span>
        </h2>

        {dashLoading ? (
          <LoadingSpinner />
        ) : dashError ? (
          <p className="text-red-600 text-sm">Failed to load patient risk data.</p>
        ) : (
          <RiskDashboardTable
            data={dashboardData}
            page={page}
            onPageChange={setPage}
          />
        )}
      </div>
    </div>
  )
}
