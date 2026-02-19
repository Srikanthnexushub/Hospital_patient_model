import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import PatientProfile from '../components/patient/PatientProfile.jsx'

const mockNavigate = vi.fn()
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom')
  return { ...actual, useNavigate: () => mockNavigate }
})

const basePatient = {
  patientId: 'P2026001',
  firstName: 'Jane',
  lastName: 'Smith',
  dateOfBirth: '1985-06-15',
  age: 40,
  gender: 'FEMALE',
  bloodGroup: 'A_POS',
  phone: '555-123-4567',
  email: 'jane@example.com',
  address: '123 Main St',
  city: 'Springfield',
  state: 'IL',
  zipCode: '62701',
  emergencyContactName: 'John Smith',
  emergencyContactPhone: '555-987-6543',
  emergencyContactRelationship: 'Spouse',
  knownAllergies: 'Penicillin',
  chronicConditions: 'Diabetes',
  status: 'ACTIVE',
  createdAt: '2026-01-01T10:00:00Z',
  createdBy: 'receptionist1',
  updatedAt: '2026-01-01T10:00:00Z',
  updatedBy: 'receptionist1',
  version: 0,
}

function renderProfile(patient, role = 'RECEPTIONIST', onStatusChanged = vi.fn()) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>
        <PatientProfile patient={patient} role={role} onStatusChanged={onStatusChanged} />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('PatientProfile', () => {
  it('renders 4 labeled sections', () => {
    renderProfile(basePatient)
    expect(screen.getByText(/Personal Demographics/i)).toBeInTheDocument()
    expect(screen.getByText(/Contact Information/i)).toBeInTheDocument()
    expect(screen.getByText(/Emergency Contact/i)).toBeInTheDocument()
    expect(screen.getByText(/Medical Background/i)).toBeInTheDocument()
  })

  it('shows green Active badge for ACTIVE patient', () => {
    renderProfile(basePatient)
    const badge = screen.getByText('Active')
    expect(badge).toHaveClass('bg-green-100')
  })

  it('shows red Inactive badge for INACTIVE patient', () => {
    renderProfile({ ...basePatient, status: 'INACTIVE' })
    const badge = screen.getByText('Inactive')
    expect(badge).toHaveClass('bg-red-100')
  })

  it('shows Edit Patient button for RECEPTIONIST', () => {
    renderProfile(basePatient, 'RECEPTIONIST')
    expect(screen.getByRole('link', { name: /Edit Patient/i })).toBeInTheDocument()
  })

  it('does not render Edit Patient button for DOCTOR', () => {
    renderProfile(basePatient, 'DOCTOR')
    expect(screen.queryByRole('link', { name: /Edit Patient/i })).not.toBeInTheDocument()
  })

  it('shows Deactivate button for ADMIN + ACTIVE patient', () => {
    renderProfile(basePatient, 'ADMIN')
    expect(screen.getByRole('button', { name: /Deactivate/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Activate/i })).not.toBeInTheDocument()
  })

  it('shows Activate button for ADMIN + INACTIVE patient', () => {
    renderProfile({ ...basePatient, status: 'INACTIVE' }, 'ADMIN')
    expect(screen.getByRole('button', { name: /Activate/i })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Deactivate/i })).not.toBeInTheDocument()
  })

  it('shows "No emergency contact on file" when missing', () => {
    const noContact = { ...basePatient, emergencyContactName: null, emergencyContactPhone: null }
    renderProfile(noContact)
    expect(screen.getByText(/No emergency contact on file/i)).toBeInTheDocument()
  })

  it('shows "None recorded" for null allergies', () => {
    renderProfile({ ...basePatient, knownAllergies: null })
    expect(screen.getByText(/None recorded/i)).toBeInTheDocument()
  })

  it('shows "Back to List" link', () => {
    renderProfile(basePatient)
    expect(screen.getByRole('link', { name: /Back to (Patient )?List/i })).toBeInTheDocument()
  })

  it('shows audit information', () => {
    renderProfile(basePatient)
    expect(screen.getByText(/receptionist1/i)).toBeInTheDocument()
  })
})
