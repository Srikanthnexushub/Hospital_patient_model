import { useState } from 'react'
import { useLabOrders, useLabResults } from '../../api/labOrders.js'
import RecordResultForm from './RecordResultForm.jsx'
import Pagination from '../common/Pagination.jsx'
import LoadingSpinner from '../common/LoadingSpinner.jsx'

const STATUS_CLS = {
  PENDING:     'bg-yellow-100 text-yellow-800',
  IN_PROGRESS: 'bg-blue-100 text-blue-800',
  RESULTED:    'bg-green-100 text-green-800',
  CANCELLED:   'bg-gray-100 text-gray-600',
}

const INTERP_CLS = {
  NORMAL:        'text-green-700',
  LOW:           'text-yellow-700',
  HIGH:          'text-yellow-700',
  CRITICAL_LOW:  'text-red-700 font-bold',
  CRITICAL_HIGH: 'text-red-700 font-bold',
  ABNORMAL:      'text-orange-700',
}

const STATUS_TABS = ['ALL', 'PENDING', 'IN_PROGRESS', 'RESULTED', 'CANCELLED']

/**
 * Shows paginated lab orders with a status filter tab bar.
 * Allows NURSE/DOCTOR/ADMIN to record results on non-RESULTED orders.
 * Props:
 *   patientId – patient context
 *   canRecord – boolean (NURSE, DOCTOR, ADMIN may record results)
 */
export default function LabOrderList({ patientId, canRecord = false }) {
  const [statusFilter, setStatusFilter] = useState('ALL')
  const [ordersPage, setOrdersPage] = useState(0)
  const [resultsPage, setResultsPage] = useState(0)
  const [recordingOrder, setRecordingOrder] = useState(null) // { id, testName }
  const [activeView, setActiveView] = useState('orders') // 'orders' | 'results'

  const ordersQuery = useLabOrders(patientId, statusFilter === 'ALL' ? undefined : statusFilter, ordersPage)
  const resultsQuery = useLabResults(patientId, resultsPage)

  const orders  = ordersQuery.data?.content  ?? []
  const results = resultsQuery.data?.content ?? []

  return (
    <div className="space-y-4">
      {/* View toggle */}
      <div className="flex gap-2 text-sm">
        <button
          onClick={() => setActiveView('orders')}
          className={`px-3 py-1.5 rounded font-medium transition-colors ${activeView === 'orders' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
        >
          Orders
        </button>
        <button
          onClick={() => setActiveView('results')}
          className={`px-3 py-1.5 rounded font-medium transition-colors ${activeView === 'results' ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200'}`}
        >
          Results
        </button>
      </div>

      {activeView === 'orders' && (
        <>
          {/* Status filter tabs */}
          <div className="flex gap-1 overflow-x-auto border-b border-gray-200">
            {STATUS_TABS.map(tab => (
              <button
                key={tab}
                onClick={() => { setStatusFilter(tab); setOrdersPage(0) }}
                className={`px-3 py-1.5 text-xs font-medium border-b-2 -mb-px whitespace-nowrap transition-colors ${
                  statusFilter === tab ? 'border-blue-600 text-blue-600' : 'border-transparent text-gray-500 hover:text-gray-700'
                }`}
              >
                {tab.replace(/_/g, ' ')}
              </button>
            ))}
          </div>

          {ordersQuery.isLoading ? <LoadingSpinner /> : ordersQuery.isError ? (
            <p className="text-red-600 text-sm">Failed to load lab orders.</p>
          ) : orders.length === 0 ? (
            <p className="text-gray-500 text-sm">No lab orders found.</p>
          ) : (
            <div className="divide-y divide-gray-100">
              {orders.map(order => (
                <div key={order.id} className="py-3 flex items-start justify-between gap-3 flex-wrap">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2 flex-wrap mb-0.5">
                      <p className="text-sm font-medium text-gray-900">{order.testName}</p>
                      <span className={`inline-flex px-2 py-0.5 rounded-full text-xs font-medium ${STATUS_CLS[order.status] ?? 'bg-gray-100 text-gray-600'}`}>
                        {order.status?.replace(/_/g, ' ')}
                      </span>
                    </div>
                    <p className="text-xs text-gray-500">
                      {order.category} · {order.priority}
                      {order.hasResult && ' · Result recorded'}
                    </p>
                    <p className="text-xs text-gray-400">Ordered by {order.orderedBy} · {new Date(order.orderedAt).toLocaleDateString()}</p>
                  </div>
                  {canRecord && order.status !== 'RESULTED' && order.status !== 'CANCELLED' && (
                    <button
                      onClick={() => setRecordingOrder({ id: order.id, testName: order.testName })}
                      className="text-xs border border-blue-400 text-blue-600 hover:bg-blue-50 px-2 py-1 rounded shrink-0"
                    >
                      Record Result
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}

          {(ordersQuery.data?.totalPages ?? 0) > 1 && (
            <Pagination
              page={ordersPage}
              totalPages={ordersQuery.data.totalPages}
              onPageChange={setOrdersPage}
            />
          )}
        </>
      )}

      {activeView === 'results' && (
        <>
          {resultsQuery.isLoading ? <LoadingSpinner /> : resultsQuery.isError ? (
            <p className="text-red-600 text-sm">Failed to load lab results.</p>
          ) : results.length === 0 ? (
            <p className="text-gray-500 text-sm">No results recorded yet.</p>
          ) : (
            <div className="divide-y divide-gray-100">
              {results.map(result => (
                <div key={result.id} className="py-3">
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className={`text-sm font-semibold ${INTERP_CLS[result.interpretation] ?? 'text-gray-900'}`}>
                          {result.value}{result.unit ? ` ${result.unit}` : ''}
                        </span>
                        <span className={`text-xs font-medium ${INTERP_CLS[result.interpretation] ?? 'text-gray-500'}`}>
                          {result.interpretation?.replace(/_/g, ' ')}
                        </span>
                      </div>
                      {(result.referenceRangeLow != null || result.referenceRangeHigh != null) && (
                        <p className="text-xs text-gray-400 mt-0.5">
                          Ref: {result.referenceRangeLow ?? '—'} – {result.referenceRangeHigh ?? '—'}{result.unit ? ` ${result.unit}` : ''}
                        </p>
                      )}
                      {result.resultNotes && <p className="text-xs text-gray-500 mt-0.5">{result.resultNotes}</p>}
                      <p className="text-xs text-gray-400 mt-0.5">
                        By {result.resultedBy} · {new Date(result.resultedAt).toLocaleString()}
                      </p>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}

          {(resultsQuery.data?.totalPages ?? 0) > 1 && (
            <Pagination
              page={resultsPage}
              totalPages={resultsQuery.data.totalPages}
              onPageChange={setResultsPage}
            />
          )}
        </>
      )}

      {/* Record result modal */}
      {recordingOrder && (
        <RecordResultForm
          orderId={recordingOrder.id}
          patientId={patientId}
          testName={recordingOrder.testName}
          onClose={() => setRecordingOrder(null)}
        />
      )}
    </div>
  )
}
