/**
 * Centered loading spinner with optional label.
 */
export default function LoadingSpinner({ label = 'Loading...' }) {
  return (
    <div className="flex flex-col items-center justify-center py-12" role="status" aria-label={label}>
      <div className="h-10 w-10 animate-spin rounded-full border-4 border-blue-500 border-t-transparent" />
      <p className="mt-3 text-sm text-gray-500">{label}</p>
    </div>
  )
}
