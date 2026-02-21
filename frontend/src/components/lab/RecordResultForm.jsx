import { useState } from 'react'
import { useRecordLabResult } from '../../api/labOrders.js'

const EMPTY_FORM = {
  value: '',
  unit: '',
  referenceRangeLow: '',
  referenceRangeHigh: '',
  interpretation: 'NORMAL',
  resultNotes: '',
}

const INTERPRETATION_COLOURS = {
  NORMAL:       'text-green-700',
  LOW:          'text-yellow-700',
  HIGH:         'text-yellow-700',
  CRITICAL_LOW:  'text-red-700 font-bold',
  CRITICAL_HIGH: 'text-red-700 font-bold',
  ABNORMAL:     'text-orange-700',
}

/**
 * Modal-style form to record a lab result for a specific order.
 * Props:
 *   orderId   – the lab order UUID
 *   patientId – needed for cache invalidation
 *   testName  – displayed in the title
 *   onClose   – callback to close the modal
 */
export default function RecordResultForm({ orderId, patientId, testName, onClose }) {
  const [form, setForm] = useState(EMPTY_FORM)
  const [err, setErr] = useState('')

  const recordMutation = useRecordLabResult(orderId, patientId)

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setErr('')
    if (!form.value.trim()) { setErr('Result value is required.'); return }
    try {
      await recordMutation.mutateAsync({
        value:               form.value.trim(),
        unit:                form.unit || undefined,
        referenceRangeLow:   form.referenceRangeLow  ? parseFloat(form.referenceRangeLow)  : undefined,
        referenceRangeHigh:  form.referenceRangeHigh ? parseFloat(form.referenceRangeHigh) : undefined,
        interpretation:      form.interpretation,
        resultNotes:         form.resultNotes || undefined,
      })
      onClose?.()
    } catch (e) {
      const msg = e?.response?.data?.message ?? 'Failed to record result.'
      setErr(msg)
    }
  }

  return (
    /* Overlay */
    <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
      <div className="bg-white rounded-xl shadow-xl w-full max-w-md">
        <div className="px-6 py-4 border-b border-gray-200">
          <h2 className="text-base font-semibold text-gray-900">Record Result: {testName}</h2>
        </div>
        <form onSubmit={handleSubmit} className="px-6 py-4 space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div className="col-span-2">
              <label className="block text-xs font-medium text-gray-600 mb-1">Result Value *</label>
              <input
                name="value" value={form.value} onChange={handleChange} required
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                placeholder="e.g. 12.5 or Positive"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Unit</label>
              <input
                name="unit" value={form.unit} onChange={handleChange}
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                placeholder="e.g. g/dL"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Interpretation *</label>
              <select name="interpretation" value={form.interpretation} onChange={handleChange}
                className={`w-full border border-gray-300 rounded px-3 py-1.5 text-sm ${INTERPRETATION_COLOURS[form.interpretation] ?? ''}`}>
                <option>NORMAL</option>
                <option>LOW</option>
                <option>HIGH</option>
                <option>CRITICAL_LOW</option>
                <option>CRITICAL_HIGH</option>
                <option>ABNORMAL</option>
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Ref Range Low</label>
              <input
                type="number" step="any" name="referenceRangeLow"
                value={form.referenceRangeLow} onChange={handleChange}
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                placeholder="e.g. 11.5"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-600 mb-1">Ref Range High</label>
              <input
                type="number" step="any" name="referenceRangeHigh"
                value={form.referenceRangeHigh} onChange={handleChange}
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
                placeholder="e.g. 16.5"
              />
            </div>
            <div className="col-span-2">
              <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
              <textarea name="resultNotes" value={form.resultNotes} onChange={handleChange} rows={2}
                className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm resize-none"
                placeholder="Optional clinical notes" />
            </div>
          </div>
          {err && <p className="text-xs text-red-600">{err}</p>}
          <div className="flex gap-2 pt-1">
            <button type="submit" disabled={recordMutation.isPending}
              className="btn-primary text-sm py-1.5 px-4">
              {recordMutation.isPending ? 'Recording…' : 'Record Result'}
            </button>
            <button type="button" onClick={onClose}
              className="btn-secondary text-sm py-1.5 px-4">
              Cancel
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}
