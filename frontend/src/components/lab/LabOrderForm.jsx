import { useState } from 'react'
import { useCreateLabOrder } from '../../api/labOrders.js'

const EMPTY_FORM = {
  testName: '',
  testCode: '',
  category: 'HEMATOLOGY',
  priority: 'ROUTINE',
  appointmentId: '',
  notes: '',
}

/**
 * Create lab order form — visible to DOCTOR and ADMIN only.
 * Props:
 *   patientId – the patient to order the lab for
 *   onCreated – optional callback after successful creation
 */
export default function LabOrderForm({ patientId, onCreated }) {
  const [showForm, setShowForm] = useState(false)
  const [form, setForm] = useState(EMPTY_FORM)
  const [err, setErr] = useState('')

  const createMutation = useCreateLabOrder(patientId)

  function handleChange(e) {
    setForm(f => ({ ...f, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e) {
    e.preventDefault()
    setErr('')
    if (!form.testName.trim()) { setErr('Test name is required.'); return }
    try {
      await createMutation.mutateAsync({
        testName:      form.testName.trim(),
        testCode:      form.testCode || undefined,
        category:      form.category,
        priority:      form.priority,
        appointmentId: form.appointmentId || undefined,
        notes:         form.notes || undefined,
      })
      setForm(EMPTY_FORM)
      setShowForm(false)
      onCreated?.()
    } catch {
      setErr('Failed to create lab order. Please try again.')
    }
  }

  if (!showForm) {
    return (
      <button
        onClick={() => setShowForm(true)}
        className="text-sm font-medium text-blue-600 hover:text-blue-800"
      >
        + Order Lab Test
      </button>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
      <p className="text-sm font-semibold text-blue-800">New Lab Order</p>
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div className="sm:col-span-2">
          <label className="block text-xs font-medium text-gray-600 mb-1">Test Name *</label>
          <input
            name="testName" value={form.testName} onChange={handleChange} required
            className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
            placeholder="e.g. Complete Blood Count"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Test Code</label>
          <input
            name="testCode" value={form.testCode} onChange={handleChange}
            className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
            placeholder="e.g. CBC"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Category *</label>
          <select name="category" value={form.category} onChange={handleChange}
            className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
            <option>HEMATOLOGY</option>
            <option>CHEMISTRY</option>
            <option>MICROBIOLOGY</option>
            <option>IMMUNOLOGY</option>
            <option>URINALYSIS</option>
            <option>OTHER</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Priority *</label>
          <select name="priority" value={form.priority} onChange={handleChange}
            className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm">
            <option>ROUTINE</option>
            <option>URGENT</option>
            <option>STAT</option>
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Appointment ID</label>
          <input
            name="appointmentId" value={form.appointmentId} onChange={handleChange}
            className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm"
            placeholder="Optional link"
          />
        </div>
        <div className="sm:col-span-2">
          <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
          <textarea name="notes" value={form.notes} onChange={handleChange} rows={2}
            className="w-full border border-gray-300 rounded px-3 py-1.5 text-sm resize-none"
            placeholder="Optional clinical notes" />
        </div>
      </div>
      {err && <p className="text-xs text-red-600">{err}</p>}
      <div className="flex gap-2">
        <button type="submit" disabled={createMutation.isPending}
          className="btn-primary text-sm py-1.5 px-4">
          {createMutation.isPending ? 'Ordering…' : 'Place Order'}
        </button>
        <button type="button" onClick={() => { setShowForm(false); setErr('') }}
          className="btn-secondary text-sm py-1.5 px-4">
          Cancel
        </button>
      </div>
    </form>
  )
}
