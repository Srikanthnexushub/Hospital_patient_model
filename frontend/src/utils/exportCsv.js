/**
 * Converts an array of objects to a CSV string and triggers a browser download.
 *
 * @param {string}   filename  - Desired filename (without .csv extension)
 * @param {string[]} headers   - Column header labels in order
 * @param {Array}    rows      - Array of arrays (each inner array = one row of values)
 */
export function downloadCsv(filename, headers, rows) {
  const escape = val => {
    if (val === null || val === undefined) return ''
    const str = String(val)
    // Wrap in quotes if value contains comma, newline, or double-quote
    if (str.includes(',') || str.includes('\n') || str.includes('"')) {
      return '"' + str.replace(/"/g, '""') + '"'
    }
    return str
  }

  const lines = [
    headers.map(escape).join(','),
    ...rows.map(row => row.map(escape).join(',')),
  ]

  const blob = new Blob([lines.join('\r\n')], { type: 'text/csv;charset=utf-8;' })
  const url  = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href     = url
  link.download = `${filename}_${new Date().toISOString().slice(0, 10)}.csv`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}
