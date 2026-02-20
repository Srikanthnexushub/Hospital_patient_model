import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth.js'
import { useInvoices } from '../hooks/useInvoices.js'
import LoadingSpinner from '../components/common/LoadingSpinner.jsx'
import Pagination from '../components/common/Pagination.jsx'

const STATUS_OPTIONS = ['DRAFT', 'ISSUED', 'PARTIALLY_PAID', 'PAID', 'CANCELLED', 'WRITTEN_OFF']

const STATUS_COLOURS = {
  DRAFT:          'bg-gray-100 text-gray-600',
  ISSUED:         'bg-blue-100 text-blue-800',
  PARTIALLY_PAID: 'bg-amber-100 text-amber-800',
  PAID:           'bg-green-100 text-green-800',
  CANCELLED:      'bg-red-100 text-red-700',
  WRITTEN_OFF:    'bg-purple-100 text-purple-800',
}

function StatusBadge({ status }) {
  return (
    <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${STATUS_COLOURS[status] ?? 'bg-gray-100 text-gray-600'}`}>
      {status?.replace(/_/g, ' ')}
    </span>
  )
}

function fmt(val) {
  if (val == null) return '—'
  return Number(val).toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

export default function InvoiceListPage() {
  const { role } = useAuth()
  const navigate = useNavigate()
  const canCreate = role === 'RECEPTIONIST' || role === 'ADMIN'

  const [page, setPage] = useState(0)
  const [filters, setFilters] = useState({ status: '', dateFrom: '', dateTo: '' })

  const { data, isLoading, isError } = useInvoices({ ...filters, page, size: 20 })
  const invoices     = data?.content      ?? []
  const totalPages   = data?.totalPages   ?? 0
  const totalElements = data?.totalElements ?? 0

  function handleFilter(key, val) {
    setFilters(f => ({ ...f, [key]: val }))
    setPage(0)
  }

  function clearFilters() {
    setFilters({ status: '', dateFrom: '', dateTo: '' })
    setPage(0)
  }

  const hasFilters = filters.status || filters.dateFrom || filters.dateTo

  return (
    <div className="max-w-7xl mx-auto px-4 py-8">
      <div className="flex flex-col sm:flex-row sm:items-center justify-between mb-6 gap-4">
        <h1 className="text-2xl font-bold text-gray-900">Invoices</h1>
        {canCreate && (
          <Link to="/invoices/new" className="btn-primary self-start sm:self-auto">
            + Create Invoice
          </Link>
        )}
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-2 mb-4">
        <select
          value={filters.status}
          onChange={e => handleFilter('status', e.target.value)}
          className="border border-gray-300 rounded px-2 py-1.5 text-sm"
        >
          <option value="">All Statuses</option>
          {STATUS_OPTIONS.map(s => (
            <option key={s} value={s}>{s.replace(/_/g, ' ')}</option>
          ))}
        </select>
        <input
          type="date"
          value={filters.dateFrom}
          onChange={e => handleFilter('dateFrom', e.target.value)}
          className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          placeholder="From"
        />
        <input
          type="date"
          value={filters.dateTo}
          onChange={e => handleFilter('dateTo', e.target.value)}
          className="border border-gray-300 rounded px-2 py-1.5 text-sm"
          placeholder="To"
        />
        {hasFilters && (
          <button onClick={clearFilters} className="text-sm text-gray-500 hover:text-gray-700 px-2">
            ✕ Clear
          </button>
        )}
      </div>

      {data && (
        <p className="text-sm text-gray-500 mb-3">
          {totalElements === 0
            ? 'No invoices found'
            : `${totalElements} invoice${totalElements !== 1 ? 's' : ''} found`}
        </p>
      )}

      {isLoading ? (
        <LoadingSpinner />
      ) : isError ? (
        <div className="text-red-600 text-sm p-4 bg-red-50 rounded-md border border-red-200">
          Failed to load invoices. Please try again.
        </div>
      ) : invoices.length === 0 ? (
        <div className="text-center py-12 text-gray-500">No invoices found.</div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow-sm border border-gray-200 overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200 text-sm">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Invoice ID</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Patient</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Doctor</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-500">Total</th>
                  <th className="px-4 py-3 text-right font-medium text-gray-500">Amount Due</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Status</th>
                  <th className="px-4 py-3 text-left font-medium text-gray-500">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {invoices.map(inv => (
                  <tr
                    key={inv.invoiceId}
                    className="hover:bg-gray-50 cursor-pointer"
                    onClick={() => navigate(`/invoices/${inv.invoiceId}`)}
                  >
                    <td className="px-4 py-3 font-mono text-xs text-gray-600">{inv.invoiceId}</td>
                    <td className="px-4 py-3 font-medium text-gray-900">{inv.patientName}</td>
                    <td className="px-4 py-3 text-gray-700">{inv.doctorId}</td>
                    <td className="px-4 py-3 text-right text-gray-900">${fmt(inv.totalAmount)}</td>
                    <td className="px-4 py-3 text-right font-medium text-gray-900">${fmt(inv.amountDue)}</td>
                    <td className="px-4 py-3"><StatusBadge status={inv.status} /></td>
                    <td className="px-4 py-3 text-gray-500 text-xs">
                      {inv.createdAt ? new Date(inv.createdAt).toLocaleDateString() : '—'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {totalPages > 1 && (
            <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
          )}
        </>
      )}
    </div>
  )
}
