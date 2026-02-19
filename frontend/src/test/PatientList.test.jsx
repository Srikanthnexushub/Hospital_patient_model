import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Routes, Route } from 'react-router-dom'
import PatientList from '../components/patient/PatientList.jsx'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

const makeSummary = (id, firstName, lastName, status = 'ACTIVE') => ({
  patientId: id,
  firstName,
  lastName,
  age: 40,
  gender: 'FEMALE',
  phone: '555-000-0001',
  status,
})

const defaultPagination = {
  number: 0,
  size: 20,
  totalElements: 1,
  totalPages: 1,
  first: true,
  last: true,
}

function renderList({ patients = [], pagination = defaultPagination, role = 'RECEPTIONIST',
                       hasQuery = false, onPageChange = vi.fn() } = {}) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <PatientList
          patients={patients}
          pagination={pagination}
          role={role}
          hasQuery={hasQuery}
          onPageChange={onPageChange}
        />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('PatientList', () => {
  beforeEach(() => vi.clearAllMocks())

  it('renders table with 6 column headers', () => {
    // Table only renders when patients are present; empty list shows the empty-state
    renderList({ patients: [makeSummary('P2026001', 'Jane', 'Smith')] })
    expect(screen.getByText(/Patient ID/i)).toBeInTheDocument()
    expect(screen.getByText(/Name/i)).toBeInTheDocument()
    expect(screen.getByText(/Age/i)).toBeInTheDocument()
    expect(screen.getByText(/Gender/i)).toBeInTheDocument()
    expect(screen.getByText(/Phone/i)).toBeInTheDocument()
    expect(screen.getByText(/Status/i)).toBeInTheDocument()
  })

  it('shows pagination summary with correct counts', () => {
    const patients = [makeSummary('P2026001', 'Jane', 'Smith')]
    const pagination = { number: 0, size: 20, totalElements: 25, totalPages: 2, first: true, last: false }
    const { container } = renderList({ patients, pagination })
    // Pagination text is split across <span> elements — check container text directly
    expect(container.textContent.replace(/\s+/g, ' ')).toContain('Showing 1–1 of 25 patients')
  })

  it('shows "No patients found" when no results with active query', () => {
    renderList({ patients: [], hasQuery: true })
    expect(screen.getByText(/No patients found matching your search/i)).toBeInTheDocument()
  })

  it('shows "No patients registered yet" when no patients and no query', () => {
    renderList({ patients: [], hasQuery: false, role: 'RECEPTIONIST' })
    expect(screen.getByText(/No patients registered yet/i)).toBeInTheDocument()
  })

  it('shows register link for RECEPTIONIST when no patients', () => {
    renderList({ patients: [], hasQuery: false, role: 'RECEPTIONIST' })
    expect(screen.getByText(/Register the first patient/i)).toBeInTheDocument()
  })

  it('does not show register link for DOCTOR when no patients', () => {
    renderList({ patients: [], hasQuery: false, role: 'DOCTOR' })
    expect(screen.queryByText(/Register the first patient/i)).not.toBeInTheDocument()
  })

  it('renders patient row with correct data', () => {
    const patients = [makeSummary('P2026001', 'Jane', 'Smith', 'ACTIVE')]
    renderList({ patients })
    expect(screen.getByText('P2026001')).toBeInTheDocument()
    expect(screen.getByText('Jane Smith')).toBeInTheDocument()
    expect(screen.getByText('555-000-0001')).toBeInTheDocument()
  })

  it('navigates to patient profile on row click', async () => {
    const patients = [makeSummary('P2026001', 'Jane', 'Smith')]
    renderList({ patients })
    await userEvent.click(screen.getByText('P2026001').closest('tr'))
    expect(mockNavigate).toHaveBeenCalledWith('/patients/P2026001')
  })

  it('Previous button is disabled on page 0', () => {
    const pagination = { ...defaultPagination, first: true, last: false, totalElements: 25, totalPages: 2 }
    renderList({ patients: [makeSummary('P2026001', 'A', 'B')], pagination })
    expect(screen.getByRole('button', { name: /Previous/i })).toBeDisabled()
  })

  it('Next button is disabled on last page', () => {
    renderList({ patients: [makeSummary('P2026001', 'A', 'B')], pagination: defaultPagination })
    expect(screen.getByRole('button', { name: /Next/i })).toBeDisabled()
  })
})
