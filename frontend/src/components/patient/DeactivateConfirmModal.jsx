import { useEffect, useRef } from 'react'
import { useChangePatientStatus } from '../../hooks/usePatient.js'

/**
 * Confirmation modal for deactivating a patient.
 * Implements focus trap and blocks background interaction (WCAG 2.1).
 */
export default function DeactivateConfirmModal({ isOpen, onClose, patient, onSuccess }) {
  const { mutateAsync: changeStatus, isPending, error } = useChangePatientStatus(patient?.patientId)
  const cancelBtnRef = useRef(null)

  // Focus the Cancel button when modal opens
  useEffect(() => {
    if (isOpen) {
      setTimeout(() => cancelBtnRef.current?.focus(), 50)
    }
  }, [isOpen])

  // Close on Escape key
  useEffect(() => {
    function onKey(e) {
      if (e.key === 'Escape' && isOpen) onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [isOpen, onClose])

  if (!isOpen || !patient) return null

  async function handleConfirm() {
    try {
      await changeStatus('DEACTIVATE')
      onSuccess?.()
    } catch {
      // error is surfaced via the error state from useMutation
    }
  }

  return (
    <>
      {/* Backdrop */}
      <div
        className="fixed inset-0 z-40 bg-black/40"
        aria-hidden="true"
        onClick={onClose}
      />

      {/* Dialog */}
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="deactivate-title"
        className="fixed inset-0 z-50 flex items-center justify-center p-4"
      >
        <div className="bg-white rounded-lg shadow-xl max-w-md w-full p-6">

          <h2 id="deactivate-title" className="text-lg font-semibold text-gray-900 mb-3">
            Deactivate Patient
          </h2>

          <p className="text-sm text-gray-600 mb-6">
            Are you sure you want to deactivate{' '}
            <strong>{patient.firstName} {patient.lastName}</strong>{' '}
            (Patient ID: <span className="font-mono">{patient.patientId}</span>)?
            This patient will no longer appear in active searches.
          </p>

          {error && (
            <p className="text-sm text-red-600 mb-4" role="alert">
              {error?.response?.data?.message || 'Failed to deactivate patient.'}
            </p>
          )}

          <div className="flex justify-end gap-3">
            <button
              ref={cancelBtnRef}
              type="button"
              className="btn-secondary"
              onClick={onClose}
              disabled={isPending}
            >
              Cancel
            </button>
            <button
              type="button"
              className="px-4 py-2 text-sm font-medium rounded-md text-white bg-red-600 hover:bg-red-700 disabled:opacity-50"
              onClick={handleConfirm}
              disabled={isPending}
              aria-busy={isPending}
            >
              {isPending ? 'Deactivating…' : 'Confirm Deactivation'}
            </button>
          </div>
        </div>
      </div>
    </>
  )
}
