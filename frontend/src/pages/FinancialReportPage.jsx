import { useState } from 'react'
import { useFinancialReport } from '../hooks/useInvoices.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'

const PAYMENT_METHOD_LABELS = {
  CASH:          'Cash',
  CARD:          'Card',
  INSURANCE:     'Insurance',
  BANK_TRANSFER: 'Bank Transfer',
  CHEQUE:        'Cheque',
}

function fmt(val) {
  return Number(val ?? 0).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function StatCard({ label, value, colour }) {
  const colourMap = {
    green:  'bg-green-50 border-green-200 text-green-800',
    blue:   'bg-blue-50 border-blue-200 text-blue-800',
    amber:  'bg-amber-50 border-amber-200 text-amber-800',
    purple: 'bg-purple-50 border-purple-200 text-purple-800',
    red:    'bg-red-50 border-red-200 text-red-800',
    gray:   'bg-gray-50 border-gray-200 text-gray-700',
  }
  return (
    <div className={`rounded-lg border p-4 ${colourMap[colour] ?? colourMap.gray}`}>
      <p className="text-xs font-medium uppercase tracking-wide opacity-70">{label}</p>
      <p className="text-2xl font-bold mt-1">${fmt(value)}</p>
    </div>
  )
}

function CountCard({ label, value, colour }) {
  const colourMap = {
    blue:   'bg-blue-50 border-blue-200 text-blue-800',
    green:  'bg-green-50 border-green-200 text-green-800',
    amber:  'bg-amber-50 border-amber-200 text-amber-800',
    red:    'bg-red-50 border-red-200 text-red-800',
    gray:   'bg-gray-50 border-gray-200 text-gray-700',
  }
  return (
    <div className={`rounded-lg border p-4 ${colourMap[colour] ?? colourMap.gray}`}>
      <p className="text-xs font-medium uppercase tracking-wide opacity-70">{label}</p>
      <p className="text-3xl font-bold mt-1">{value ?? 0}</p>
    </div>
  )
}

// Default to current month
function defaultDates() {
  const now = new Date()
  const y   = now.getFullYear()
  const m   = String(now.getMonth() + 1).padStart(2, '0')
  const d   = String(now.getDate()).padStart(2, '0')
  return {
    dateFrom: `${y}-${m}-01`,
    dateTo:   `${y}-${m}-${d}`,
  }
}

export default function FinancialReportPage() {
  const [dates, setDates] = useState(defaultDates)
  const [submitted, setSubmitted] = useState(defaultDates)

  const { data, isLoading, isError, error } = useFinancialReport(submitted.dateFrom, submitted.dateTo)

  function handleRun(e) {
    e.preventDefault()
    setSubmitted({ ...dates })
  }

  const byMethod = data?.byPaymentMethod ?? {}

  return (
    <div className="max-w-4xl mx-auto px-4 py-8">
      <h1 className="text-2xl font-bold text-gray-900 mb-6">Financial Summary Report</h1>

      {/* Date range picker */}
      <form onSubmit={handleRun} className="bg-white rounded-lg shadow-sm border border-gray-200 p-4 mb-6 flex flex-wrap gap-3 items-end">
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">From</label>
          <input
            type="date"
            value={dates.dateFrom}
            onChange={e => setDates(d => ({ ...d, dateFrom: e.target.value }))}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm"
          />
        </div>
        <div>
          <label className="block text-xs font-medium text-gray-600 mb-1">To</label>
          <input
            type="date"
            value={dates.dateTo}
            onChange={e => setDates(d => ({ ...d, dateTo: e.target.value }))}
            className="border border-gray-300 rounded px-3 py-1.5 text-sm"
          />
        </div>
        <button type="submit" className="btn-primary text-sm py-1.5">
          Run Report
        </button>
      </form>

      {isLoading && <LoadingSpinner />}

      {isError && (
        <div className="rounded-md bg-red-50 border border-red-200 p-4 text-red-700 text-sm">
          {error?.response?.data?.message ?? 'Failed to load report. Check your date range.'}
        </div>
      )}

      {data && (
        <>
          {/* Revenue cards */}
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-4 mb-6">
            <StatCard label="Total Invoiced"    value={data.totalInvoiced}    colour="blue"   />
            <StatCard label="Total Collected"   value={data.totalCollected}   colour="green"  />
            <StatCard label="Total Outstanding" value={data.totalOutstanding} colour="amber"  />
            <StatCard label="Total Written Off" value={data.totalWrittenOff}  colour="purple" />
            <StatCard label="Total Cancelled"   value={data.totalCancelled}   colour="red"    />
          </div>

          {/* Count cards */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-4 mb-6">
            <CountCard label="Total Invoices" value={data.invoiceCount} colour="blue"  />
            <CountCard label="Paid"           value={data.paidCount}    colour="green" />
            <CountCard label="Partial"        value={data.partialCount} colour="amber" />
            <CountCard label="Overdue"        value={data.overdueCount} colour="red"   />
          </div>

          {/* By payment method */}
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 p-6">
            <h2 className="text-base font-semibold text-gray-900 mb-4">Collections by Payment Method</h2>
            <table className="min-w-full text-sm">
              <thead>
                <tr className="border-b border-gray-200">
                  <th className="pb-2 text-left text-xs font-medium text-gray-500">Method</th>
                  <th className="pb-2 text-right text-xs font-medium text-gray-500">Amount Collected</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {Object.entries(PAYMENT_METHOD_LABELS).map(([key, label]) => (
                  <tr key={key}>
                    <td className="py-2 text-gray-700">{label}</td>
                    <td className="py-2 text-right font-medium text-gray-900">
                      ${fmt(byMethod[key] ?? 0)}
                    </td>
                  </tr>
                ))}
                <tr className="border-t-2 border-gray-300">
                  <td className="py-2 font-semibold text-gray-900">Total</td>
                  <td className="py-2 text-right font-semibold text-green-700">
                    ${fmt(data.totalCollected)}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>

          <p className="mt-4 text-xs text-gray-400 text-right">
            Report period: {submitted.dateFrom} to {submitted.dateTo}
          </p>
        </>
      )}
    </div>
  )
}
