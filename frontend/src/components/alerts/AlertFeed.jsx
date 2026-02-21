import { useState } from 'react'
import { useAcknowledgeAlert, useDismissAlert } from '../../api/clinicalAlerts.js'
import AlertBadge from './AlertBadge.jsx'

const ALERT_TYPE_LABELS = {
  LAB_CRITICAL:           'Lab Critical',
  LAB_ABNORMAL:           'Lab Abnormal',
  NEWS2_HIGH:             'NEWS2 High',
  NEWS2_CRITICAL:         'NEWS2 Critical',
  DRUG_INTERACTION:       'Drug Interaction',
  ALLERGY_CONTRAINDICATION: 'Allergy Contraindication',
}

const STATUS_CLS = {
  ACTIVE:       'bg-red-50 border-l-4 border-red-400',
  ACKNOWLEDGED: 'bg-yellow-50 border-l-4 border-yellow-400',
  DISMISSED:    'bg-gray-50 border-l-4 border-gray-300',
}

/**
 * Filterable alert list with inline acknowledge/dismiss actions.
 * Props:
 *   alerts   – array of alert objects
 *   canAct   – boolean (DOCTOR or ADMIN can acknowledge/dismiss)
 *   onFilter – optional callback for external filter state
 */
export default function AlertFeed({ alerts = [], canAct = false }) {
  const [dismissingId, setDismissingId] = useState(null)
  const [dismissReason, setDismissReason] = useState('')
  const [dismissErr, setDismissErr] = useState('')

  const acknowledgeMutation = useAcknowledgeAlert()
  const dismissMutation = useDismissAlert()

  async function handleAcknowledge(alertId) {
    await acknowledgeMutation.mutateAsync(alertId)
  }

  function openDismiss(alertId) {
    setDismissingId(alertId)
    setDismissReason('')
    setDismissErr('')
  }

  async function handleDismiss(e) {
    e.preventDefault()
    if (!dismissReason.trim()) { setDismissErr('Reason is required.'); return }
    await dismissMutation.mutateAsync({ alertId: dismissingId, reason: dismissReason.trim() })
    setDismissingId(null)
    setDismissReason('')
  }

  if (alerts.length === 0) {
    return <p className="text-gray-500 text-sm">No alerts found.</p>
  }

  return (
    <div className="space-y-2">
      {alerts.map(alert => (
        <div
          key={alert.id}
          className={`rounded-lg p-4 ${STATUS_CLS[alert.status] ?? 'bg-white border border-gray-200'}`}
        >
          <div className="flex items-start justify-between gap-3 flex-wrap">
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap mb-1">
                <AlertBadge severity={alert.severity} count={1} label={alert.severity} />
                <span className="text-xs font-medium text-gray-500">
                  {ALERT_TYPE_LABELS[alert.alertType] ?? alert.alertType}
                </span>
                {alert.patientName && (
                  <span className="text-xs text-gray-400">— {alert.patientName}</span>
                )}
              </div>
              <p className="text-sm font-semibold text-gray-900">{alert.title}</p>
              {alert.description && (
                <p className="text-xs text-gray-600 mt-0.5">{alert.description}</p>
              )}
              {alert.triggerValue && (
                <p className="text-xs text-gray-400 mt-0.5">Value: {alert.triggerValue}</p>
              )}
              <p className="text-xs text-gray-400 mt-1">
                {new Date(alert.createdAt).toLocaleString()}
                {alert.acknowledgedBy && ` · Ack by ${alert.acknowledgedBy}`}
                {alert.dismissReason && ` · Dismissed: ${alert.dismissReason}`}
              </p>
            </div>

            {/* Actions for ACTIVE alerts */}
            {canAct && alert.status === 'ACTIVE' && (
              <div className="flex gap-2 shrink-0">
                <button
                  onClick={() => handleAcknowledge(alert.id)}
                  disabled={acknowledgeMutation.isPending}
                  className="text-xs border border-yellow-400 text-yellow-700 hover:bg-yellow-50 px-2 py-1 rounded"
                >
                  Acknowledge
                </button>
                <button
                  onClick={() => openDismiss(alert.id)}
                  className="text-xs border border-gray-400 text-gray-600 hover:bg-gray-50 px-2 py-1 rounded"
                >
                  Dismiss
                </button>
              </div>
            )}
          </div>

          {/* Inline dismiss form */}
          {dismissingId === alert.id && (
            <form onSubmit={handleDismiss} className="mt-3 flex gap-2 items-start">
              <div className="flex-1">
                <textarea
                  value={dismissReason}
                  onChange={e => setDismissReason(e.target.value)}
                  placeholder="Reason for dismissal…"
                  rows={2}
                  className="w-full border border-gray-300 rounded px-2 py-1 text-xs resize-none"
                />
                {dismissErr && <p className="text-xs text-red-600 mt-0.5">{dismissErr}</p>}
              </div>
              <div className="flex flex-col gap-1">
                <button
                  type="submit"
                  disabled={dismissMutation.isPending}
                  className="text-xs bg-gray-600 hover:bg-gray-700 text-white px-3 py-1.5 rounded"
                >
                  {dismissMutation.isPending ? 'Saving…' : 'Confirm'}
                </button>
                <button
                  type="button"
                  onClick={() => setDismissingId(null)}
                  className="text-xs text-gray-500 hover:underline px-3 py-1.5"
                >
                  Cancel
                </button>
              </div>
            </form>
          )}
        </div>
      ))}
    </div>
  )
}
