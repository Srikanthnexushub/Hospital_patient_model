import { Link } from 'react-router-dom'
import PatientListRow from './PatientListRow.jsx'
import Pagination from '../common/Pagination.jsx'

export default function PatientList({ patients, pagination, role, hasQuery, onPageChange }) {
  const canRegister = role === 'RECEPTIONIST' || role === 'ADMIN'

  // Empty-state content
  if (!patients || patients.length === 0) {
    return (
      <div className="text-center py-12 text-gray-500">
        {hasQuery ? (
          <p>No patients found matching your search.</p>
        ) : (
          <div>
            <p className="mb-2">No patients registered yet.</p>
            {canRegister && (
              <Link to="/patients/new" className="text-blue-600 hover:underline text-sm">
                Register the first patient →
              </Link>
            )}
          </div>
        )}
      </div>
    )
  }

  return (
    <div>
      <div className="overflow-x-auto rounded-lg border border-gray-200">
        <table
          className="w-full text-left border-collapse"
          role="table"
          aria-label="Patient list"
        >
          <thead className="bg-gray-50 border-b border-gray-200">
            <tr role="row">
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Patient ID</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Name</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Age</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Gender</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Phone</th>
              <th className="px-4 py-3 text-xs font-semibold uppercase tracking-wide text-gray-500">Status</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-100">
            {patients.map(p => (
              <PatientListRow key={p.patientId} patient={p} />
            ))}
          </tbody>
        </table>
      </div>

      {pagination && (
        <div className="mt-4">
          <Pagination
            page={pagination.number}
            size={pagination.size}
            totalElements={pagination.totalElements}
            totalPages={pagination.totalPages}
            first={pagination.first}
            last={pagination.last}
            onPageChange={onPageChange}
            itemsOnPage={patients.length}
          />
        </div>
      )}
    </div>
  )
}
