const SEVERITY_CLS = {
  CRITICAL: 'bg-red-100 text-red-800 border border-red-300',
  WARNING:  'bg-yellow-100 text-yellow-800 border border-yellow-300',
  INFO:     'bg-blue-100 text-blue-800 border border-blue-300',
}

/**
 * Severity-coloured chip showing alert count.
 * Props:
 *   count    – number of alerts
 *   severity – 'CRITICAL' | 'WARNING' | 'INFO'
 *   label    – optional label prefix (default: severity)
 */
export default function AlertBadge({ count, severity = 'INFO', label }) {
  if (!count) return null
  const cls = SEVERITY_CLS[severity] ?? 'bg-gray-100 text-gray-600'
  return (
    <span className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium ${cls}`}>
      {label ?? severity}
      <span className="font-bold">{count}</span>
    </span>
  )
}
