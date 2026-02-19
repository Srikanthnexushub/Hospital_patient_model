/**
 * WCAG-AA accessible status badge — uses BOTH color AND text label.
 * Never rely on color alone; screen readers read the text.
 */
export default function PatientStatusBadge({ status }) {
  if (status === 'ACTIVE') {
    return (
      <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-green-100 text-green-800">
        Active
      </span>
    )
  }
  return (
    <span className="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-red-100 text-red-800">
      Inactive
    </span>
  )
}
