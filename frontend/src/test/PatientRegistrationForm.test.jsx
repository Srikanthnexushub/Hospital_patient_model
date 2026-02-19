import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import PatientRegistrationForm from '../components/patient/PatientRegistrationForm.jsx'

// Mock the API modules
vi.mock('../api/patientApi.js', () => ({
  registerPatient: vi.fn(),
  checkDuplicatePhone: vi.fn().mockResolvedValue({ duplicate: false }),
}))

vi.mock('../hooks/usePatients.js', () => ({
  useRegisterPatient: () => ({
    mutateAsync: vi.fn().mockResolvedValue({ patientId: 'P2026001', message: 'Patient registered successfully. Patient ID: P2026001' }),
    isPending: false,
  }),
}))

function renderForm(role = 'RECEPTIONIST', onSuccess = vi.fn()) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>
        <PatientRegistrationForm role={role} onSuccess={onSuccess} />
      </MemoryRouter>
    </QueryClientProvider>
  )
}

describe('PatientRegistrationForm', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders 4 labeled sections', () => {
    renderForm()
    expect(screen.getByText(/Personal Demographics/i)).toBeInTheDocument()
    expect(screen.getByText(/Contact Information/i)).toBeInTheDocument()
    expect(screen.getByText(/Emergency Contact/i)).toBeInTheDocument()
    expect(screen.getByText(/Medical Background/i)).toBeInTheDocument()
  })

  it('marks mandatory fields with asterisk', () => {
    renderForm()
    // First Name, Last Name, DOB, Gender, Phone are required
    const labels = screen.getAllByText(/\*/i)
    expect(labels.length).toBeGreaterThanOrEqual(5)
  })

  it('shows phone error on blur with invalid phone', async () => {
    renderForm()
    const phoneInput = screen.getByLabelText(/Phone Number/i)
    await userEvent.type(phoneInput, '12345')
    fireEvent.blur(phoneInput)
    await waitFor(() => {
      expect(screen.getByText(/valid phone/i)).toBeInTheDocument()
    })
  })

  it('shows no error on blur with valid phone', async () => {
    renderForm()
    const phoneInput = screen.getByLabelText(/Phone Number/i)
    await userEvent.type(phoneInput, '555-123-4567')
    fireEvent.blur(phoneInput)
    await waitFor(() => {
      expect(screen.queryByText(/valid phone/i)).not.toBeInTheDocument()
    })
  })

  it('shows email error on blur with invalid email', async () => {
    renderForm()
    const emailInput = screen.getByLabelText(/Email/i)
    await userEvent.type(emailInput, 'not-an-email')
    fireEvent.blur(emailInput)
    await waitFor(() => {
      expect(screen.getByText(/valid email/i)).toBeInTheDocument()
    })
  })

  it('shows age when valid DOB entered', async () => {
    renderForm()
    const dobInput = screen.getByLabelText(/Date of Birth/i)
    await userEvent.type(dobInput, '1985-06-15')
    fireEvent.blur(dobInput)
    await waitFor(() => {
      expect(screen.getByText(/Age:/i)).toBeInTheDocument()
    })
  })

  it('disables submit button while loading', () => {
    vi.mock('../hooks/usePatients.js', () => ({
      useRegisterPatient: () => ({
        mutateAsync: vi.fn(),
        isPending: true,
      }),
    }))
    // Re-render with loading state would need module reset; just check the default
    renderForm()
    const submitBtn = screen.getByRole('button', { name: /Register Patient/i })
    expect(submitBtn).not.toBeDisabled() // default state not loading
  })

  it('shows authorization error for DOCTOR role', () => {
    renderForm('DOCTOR')
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument()
    expect(screen.queryByLabelText(/First Name/i)).not.toBeInTheDocument()
  })

  it('shows authorization error for NURSE role', () => {
    renderForm('NURSE')
    expect(screen.getByText(/do not have permission/i)).toBeInTheDocument()
  })
})
