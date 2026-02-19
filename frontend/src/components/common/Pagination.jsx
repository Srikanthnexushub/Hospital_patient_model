/**
 * Pagination controls for the patient list.
 * Displays "Showing X–Y of Z patients" + Previous/Next buttons.
 */
export default function Pagination({ page, size, totalElements, totalPages,
                                     first, last, itemsOnPage, onPageChange }) {
  if (totalElements === 0) return null

  const from = page * size + 1
  // If itemsOnPage is provided use it; otherwise compute from size
  const to = from + (itemsOnPage != null ? itemsOnPage - 1 : Math.min(size, totalElements - page * size) - 1)
  const isFirst = first != null ? first : page === 0
  const isLast = last != null ? last : page >= totalPages - 1

  return (
    <div className="flex items-center justify-between border-t border-gray-200 bg-white px-4 py-3 sm:px-6">
      <div className="text-sm text-gray-600">
        Showing <span className="font-medium">{from}</span>–<span className="font-medium">{to}</span> of{' '}
        <span className="font-medium">{totalElements}</span> patients
      </div>

      <div className="flex gap-2">
        <button
          onClick={() => onPageChange(page - 1)}
          disabled={isFirst}
          aria-label="Previous page"
          className="btn-secondary disabled:opacity-40"
        >
          Previous
        </button>
        <button
          onClick={() => onPageChange(page + 1)}
          disabled={isLast}
          aria-label="Next page"
          className="btn-secondary disabled:opacity-40"
        >
          Next
        </button>
      </div>
    </div>
  )
}
