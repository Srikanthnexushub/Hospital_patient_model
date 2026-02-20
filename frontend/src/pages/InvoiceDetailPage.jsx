import { useState } from 'react'
import { useParams, useLocation, Link } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { useInvoice, useRecordPayment, useChangeInvoiceStatus } from '../hooks/useInvoices.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

const STATUS_COLOURS = {
  DRAFT:          'bg-gray-100 text-gray-600',
  ISSUED:         'bg-blue-100 text-blue-800',
  PARTIALLY_PAID: 'bg-amber-100 text-amber-800',
  PAID:           'bg-green-100 text-green-800',
  CANCELLED:      'bg-red-100 text-red-700',
  WRITTEN_OFF:    'bg-purple-100 text-purple-800',
}

const PAYMENT_METHODS = ['CASH', 'CARD', 'INSURANCE', 'BANK_TRANSFER', 'CHEQUE']

function StatusBadge({ status }) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_COLOURS[status] ?? 'bg-gray-100 text-gray-600'}`}>
      {status?.replace(/_/g, ' ')}
    </span>
  )
}

function InfoRow({ label, value }) {
  return (
    <div className="flex flex-col sm:flex-row sm:gap-4">
      <dt className="text-sm font-medium text-gray-500 sm:w-36">{label}</dt>
      <dd className="text-sm text-gray-900 mt-0.5 sm:mt-0">{value ?? '—'}</dd>
    </div>
  )
}

function AmountRow({ label, value, highlight, indent }) {
  return (
    <div className={`flex justify-between text-sm ${indent ? 'pl-4' : ''} ${highlight ? 'font-semibold text-gray-900' : 'text-gray-600'}`}>
      <span>{label}</span>
      <span>${Number(value ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}</span>
    </div>
  )
}

// ── Record Payment form ──────────────────────────────────────────────────────
function RecordPaymentSection({ invoiceId, amountDue }) {
  const [open, setOpen] = useState(false)
  const [form, setForm] = useState({ amount: '', paymentMethod: 'CASH', referenceNumber: '', notes: '' })
  const [formError, setFormError] = useState('')

  const { mutate: record, isPending } = useRecordPayment(invoiceId)

  function handleSubmit(e) {
    e.preventDefault()
    const amt = Number(form.amount)
    if (!amt || amt <= 0) { setFormError('Amount must be greater than 0'); return }
    setFormError('')
    record({
      amount:          amt,
      paymentMethod:   form.paymentMethod,
      referenceNumber: form.referenceNumber || undefined,
      notes:           form.notes           || undefined,
    }, {
      onSuccess: () => {
        setOpen(false)
        setForm({ amount: '', paymentMethod: 'CASH', referenceNumber: '', notes: '' })
      },
    })
  }

  if (!open) {
    return (
      <button onClick={() => setOpen(true)} className="btn-primary text-sm">
        Record Payment
      </button>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="mt-4 bg-blue-50 border border-blue-200 rounded-lg p-4 space-y-3">
      <h3 className="text-sm font-semibold text-blue-900">Record Payment</h3>
      {formError && <p className="text-red-600 text-xs">{formError}</p>}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Amount ($) *</label>
          <input
            type="number" min="0.01" step="0.01"
            value={form.amount}
            onChange={e => setForm(f => ({ ...f, amount: e.target.value }))}
            placeholder={`Max due: $${Number(amountDue).toFixed(2)}`}
            className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Payment Method *</label>
          <select
            value={form.paymentMethod}
            onChange={e => setForm(f => ({ ...f, paymentMethod: e.target.value }))}
            className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
          >
            {PAYMENT_METHODS.map(m => <option key={m} value={m}>{m.replace(/_/g, ' ')}</option>)}
          </select>
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Reference No.</label>
          <input
            value={form.referenceNumber}
            onChange={e => setForm(f => ({ ...f, referenceNumber: e.target.value }))}
            className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
            placeholder="Cheque no., card last 4, etc."
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">Notes</label>
          <input
            value={form.notes}
            onChange={e => setForm(f => ({ ...f, notes: e.target.value }))}
            className="w-full border border-gray-300 rounded px-2 py-1.5 text-sm"
          />
        </div>
      </div>
      <div className="flex gap-2">
        <button type="submit" disabled={isPending} className="btn-primary text-sm py-1.5">
          {isPending ? 'Recording…' : 'Record Payment'}
        </button>
        <button type="button" onClick={() => setOpen(false)} className="btn-secondary text-sm py-1.5">
          Cancel
        </button>
      </div>
    </form>
  )
}

// ── Status action buttons (ADMIN) ────────────────────────────────────────────
function StatusActions({ invoiceId, status }) {
  const [action, setAction] = useState(null)   // 'CANCEL' | 'WRITE_OFF'
  const [reason, setReason] = useState('')
  const [reasonError, setReasonError] = useState('')

  const { mutate: changeStatus, isPending, error } = useChangeInvoiceStatus(invoiceId)

  const canCancel    = status === 'DRAFT' || status === 'ISSUED'
  const canWriteOff  = status === 'ISSUED' || status === 'PARTIALLY_PAID'

  if (!canCancel && !canWriteOff) return null

  function handleConfirm() {
    if (!reason.trim()) { setReasonError('Reason is required'); return }
    setReasonError('')
    changeStatus({ action, reason }, {
      onSuccess: () => { setAction(null); setReason('') },
    })
  }

  return (
    <div className="mt-4">
      <div className="flex flex-wrap gap-2">
        {canCancel && !action && (
          <button
            onClick={() => setAction('CANCEL')}
            className="px-3 py-1.5 rounded text-sm font-medium bg-red-600 hover:bg-red-700 text-white"
          >
            Cancel Invoice
          </button>
        )}
        {canWriteOff && !action && (
          <button
            onClick={() => setAction('WRITE_OFF')}
            className="px-3 py-1.5 rounded text-sm font-medium bg-purple-700 hover:bg-purple-800 text-white"
          >
            Write Off
          </button>
        )}
      </div>

      {action && (
        <div className="mt-3 space-y-2">
          <p className="text-sm font-medium text-gray-700">
            {action === 'CANCEL' ? 'Cancel this invoice?' : 'Write off this invoice?'} Provide a reason:
          </p>
          {reasonError && <p className="text-red-600 text-xs">{reasonError}</p>}
          {error && <p className="text-red-600 text-xs">{error?.response?.data?.message ?? error.message}</p>}
          <div className="flex gap-2 items-start">
            <textarea
              value={reason}
              onChange={e => setReason(e.target.value)}
              rows={2}
              className="flex-1 border border-gray-300 rounded px-3 py-1.5 text-sm"
              placeholder="Reason (required)"
            />
            <div className="flex flex-col gap-1">
              <button
                onClick={handleConfirm}
                disabled={isPending}
                className={`px-3 py-1.5 rounded text-sm font-medium text-white ${action === 'CANCEL' ? 'bg-red-600 hover:bg-red-700' : 'bg-purple-700 hover:bg-purple-800'}`}
              >
                {isPending ? 'Processing…' : 'Confirm'}
              </button>
              <button
                onClick={() => { setAction(null); setReason(''); setReasonError('') }}
                className="px-3 py-1.5 rounded text-sm border border-gray-300 hover:bg-gray-50"
              >
                Back
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

// ── Main page ────────────────────────────────────────────────────────────────
export default function InvoiceDetailPage() {
  const { invoiceId } = useParams()
  const { role } = useAuth()
  const location = useLocation()
  const successMessage = location.state?.message

  const { data: inv, isLoading, isError, error } = useInvoice(invoiceId)

  const canRecordPayment = (role === 'RECEPTIONIST' || role === 'ADMIN')
    && (inv?.status === 'ISSUED' || inv?.status === 'PARTIALLY_PAID')
  const canChangeStatus  = role === 'ADMIN'

  if (isLoading) return <div className="max-w-3xl mx-auto px-4 py-8"><LoadingSpinner /></div>
  if (isError) {
    const is404 = error?.response?.status === 404
    return (
      <div className="max-w-3xl mx-auto px-4 py-8">
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700">
          {is404 ? 'Invoice not found.' : 'Failed to load invoice.'}
        </div>
        <Link to="/invoices" className="inline-block mt-4 text-sm text-blue-600 hover:underline">← Back to Invoices</Link>
      </div>
    )
  }

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <div className="mb-4">
        <Link to="/invoices" className="text-sm text-blue-600 hover:underline">← Invoices</Link>
      </div>

      <h1 className="text-2xl font-bold text-gray-900 mb-6">Invoice Detail</h1>

      {successMessage && (
        <div className="mb-4 rounded-md bg-green-50 border border-green-200 p-4 text-green-800 text-sm">
          {successMessage}
        </div>
      )}

      {/* Header card */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-4">
        <div className="flex items-start justify-between mb-4">
          <div>
            <p className="text-xs font-mono text-gray-500 mb-1">{inv.invoiceId}</p>
            <StatusBadge status={inv.status} />
          </div>
          <div className="text-right text-xs text-gray-400">
            <p>Created {new Date(inv.createdAt).toLocaleDateString()} by {inv.createdBy}</p>
            {inv.updatedAt && <p>Updated {new Date(inv.updatedAt).toLocaleDateString()} by {inv.updatedBy}</p>}
          </div>
        </div>

        <dl className="space-y-2">
          <InfoRow label="Patient"     value={`${inv.patientName} (${inv.patientId})`} />
          <InfoRow label="Doctor"      value={inv.doctorName ? `${inv.doctorName} (${inv.doctorId})` : inv.doctorId} />
          <InfoRow label="Appointment" value={`${inv.appointmentId}${inv.appointmentDate ? ` — ${inv.appointmentDate}` : ''}`} />
          {inv.notes       && <InfoRow label="Notes"         value={inv.notes} />}
          {inv.cancelReason && <InfoRow label="Cancel Reason" value={inv.cancelReason} />}
        </dl>

        {/* Status actions (ADMIN) */}
        {canChangeStatus && <StatusActions invoiceId={invoiceId} status={inv.status} />}
      </div>

      {/* Amounts card */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-4">
        <h2 className="text-base font-semibold text-gray-900 mb-4">Amounts</h2>
        <div className="space-y-1.5">
          <AmountRow label="Total (before discount)" value={inv.totalAmount} />
          {Number(inv.discountPercent) > 0 && (
            <AmountRow label={`Discount (${inv.discountPercent}%)`} value={inv.discountAmount} indent />
          )}
          <AmountRow label="Net Amount"   value={inv.netAmount}   />
          {Number(inv.taxRate) > 0 && (
            <AmountRow label={`Tax (${inv.taxRate}%)`} value={inv.taxAmount} indent />
          )}
          <div className="border-t border-gray-200 pt-2 mt-2 space-y-1.5">
            <AmountRow label="Amount Paid" value={inv.amountPaid} />
            <AmountRow label="Amount Due"  value={inv.amountDue}  highlight />
          </div>
        </div>

        {/* Record payment (RECEPTIONIST / ADMIN) */}
        {canRecordPayment && (
          <div className="mt-4 pt-4 border-t border-gray-100">
            <RecordPaymentSection invoiceId={invoiceId} amountDue={inv.amountDue} />
          </div>
        )}
      </div>

      {/* Line Items */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6 mb-4">
        <h2 className="text-base font-semibold text-gray-900 mb-4">Line Items</h2>
        {inv.lineItems?.length === 0 ? (
          <p className="text-sm text-gray-500">No line items.</p>
        ) : (
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Code</th>
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Description</th>
                <th className="pb-2 text-right text-xs font-medium text-gray-500">Qty</th>
                <th className="pb-2 text-right text-xs font-medium text-gray-500">Unit Price</th>
                <th className="pb-2 text-right text-xs font-medium text-gray-500">Total</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {inv.lineItems.map(item => (
                <tr key={item.id}>
                  <td className="py-2 font-mono text-xs text-gray-600">{item.serviceCode}</td>
                  <td className="py-2 text-gray-800">{item.description}</td>
                  <td className="py-2 text-right text-gray-700">{item.quantity}</td>
                  <td className="py-2 text-right text-gray-700">${Number(item.unitPrice).toFixed(2)}</td>
                  <td className="py-2 text-right font-medium text-gray-900">${Number(item.lineTotal).toFixed(2)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Payment History */}
      <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
        <h2 className="text-base font-semibold text-gray-900 mb-4">Payment History</h2>
        {inv.payments?.length === 0 ? (
          <p className="text-sm text-gray-500">No payments recorded.</p>
        ) : (
          <table className="min-w-full text-sm">
            <thead>
              <tr className="border-b border-gray-200">
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Date</th>
                <th className="pb-2 text-right text-xs font-medium text-gray-500">Amount</th>
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Method</th>
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Reference</th>
                <th className="pb-2 text-left text-xs font-medium text-gray-500">Recorded By</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {inv.payments.map(pay => (
                <tr key={pay.id}>
                  <td className="py-2 text-gray-600 text-xs">{new Date(pay.paidAt).toLocaleString()}</td>
                  <td className="py-2 text-right font-medium text-green-700">${Number(pay.amount).toFixed(2)}</td>
                  <td className="py-2 text-gray-700">{pay.paymentMethod?.replace(/_/g, ' ')}</td>
                  <td className="py-2 text-gray-500 font-mono text-xs">{pay.referenceNumber ?? '—'}</td>
                  <td className="py-2 text-gray-500">{pay.recordedBy}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
