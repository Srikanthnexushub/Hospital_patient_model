/**
 * Displays a field-level validation error message below a form field.
 */
export default function InlineError({ id, message }) {
  if (!message) return null
  return (
    <p id={id} role="alert" aria-live="polite" className="mt-1 text-sm text-red-600">
      {message}
    </p>
  )
}
