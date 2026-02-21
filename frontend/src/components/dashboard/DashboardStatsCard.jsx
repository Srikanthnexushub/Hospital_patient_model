/**
 * Single stat card with label and count.
 * Props:
 *   label     – display label
 *   value     – numeric or string value to display
 *   highlight – optional colour class (e.g. 'text-red-600')
 */
export default function DashboardStatsCard({ label, value, highlight }) {
  return (
    <div className="bg-white rounded-lg border border-gray-200 shadow-sm p-4 flex flex-col gap-1">
      <p className="text-xs text-gray-500 font-medium uppercase tracking-wide">{label}</p>
      <p className={`text-3xl font-bold ${highlight ?? 'text-gray-900'}`}>
        {value ?? '—'}
      </p>
    </div>
  )
}
