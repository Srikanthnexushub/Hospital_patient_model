import { useState, useEffect, useRef, useCallback } from 'react'

/**
 * Debounced search input.
 * - Change fires after 300 ms idle
 * - Enter key fires immediately
 * - Clear button resets query and calls onClear
 */
export default function SearchBox({ value, onChange, onClear, placeholder = 'Search patients…' }) {
  const [localValue, setLocalValue] = useState(value || '')
  const timerRef = useRef(null)

  // Sync when parent resets value (e.g. reset())
  useEffect(() => {
    setLocalValue(value || '')
  }, [value])

  function handleChange(e) {
    const val = e.target.value
    setLocalValue(val)
    clearTimeout(timerRef.current)
    timerRef.current = setTimeout(() => onChange(val), 300)
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter') {
      clearTimeout(timerRef.current)
      onChange(localValue)
    }
  }

  function handleClear() {
    setLocalValue('')
    clearTimeout(timerRef.current)
    onClear?.()
  }

  return (
    <div className="relative flex-1 max-w-sm">
      <span className="absolute inset-y-0 left-3 flex items-center text-gray-400 pointer-events-none">
        {/* magnifying glass icon */}
        <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
            d="M21 21l-4.35-4.35M17 11A6 6 0 1 1 5 11a6 6 0 0 1 12 0z" />
        </svg>
      </span>
      <input
        type="search"
        className="form-input pl-9 pr-8"
        value={localValue}
        onChange={handleChange}
        onKeyDown={handleKeyDown}
        placeholder={placeholder}
        aria-label="Search patients"
        autoComplete="off"
      />
      {localValue && (
        <button
          type="button"
          onClick={handleClear}
          className="absolute inset-y-0 right-2 flex items-center px-1 text-gray-400 hover:text-gray-600"
          aria-label="Clear search"
        >
          ×
        </button>
      )}
    </div>
  )
}
