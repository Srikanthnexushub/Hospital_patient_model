import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  bookAppointment,
  listAppointments,
  getAppointment,
  getTodayAppointments,
  getDoctorSchedule,
  changeAppointmentStatus,
  updateAppointment,
  getDoctorAvailability,
  addClinicalNotes,
  getClinicalNotes,
  getPatientAppointments,
} from '../api/appointmentApi.js'

// ── US1: Book Appointment ───────────────────────────────────────────────────

export function useBookAppointment() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: bookAppointment,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
    },
  })
}

// ── US2: List / Search ─────────────────────────────────────────────────────

export function useAppointments(params = {}) {
  return useQuery({
    queryKey: ['appointments', params],
    queryFn: () => listAppointments(params),
    staleTime: 30_000,
    placeholderData: prev => prev,
  })
}

export function useAppointment(appointmentId) {
  return useQuery({
    queryKey: ['appointment', appointmentId],
    queryFn: () => getAppointment(appointmentId),
    staleTime: 10_000,
    enabled: !!appointmentId,
  })
}

export function useTodayAppointments(page = 0) {
  return useQuery({
    queryKey: ['appointments', 'today', page],
    queryFn: () => getTodayAppointments({ page }),
    staleTime: 30_000,
    placeholderData: prev => prev,
  })
}

export function useDoctorSchedule(doctorId, date) {
  return useQuery({
    queryKey: ['doctor-schedule', doctorId, date],
    queryFn: () => getDoctorSchedule(doctorId, date),
    staleTime: 30_000,
    enabled: !!(doctorId && date),
  })
}

// ── US3: Status Lifecycle ──────────────────────────────────────────────────

export function useChangeAppointmentStatus(appointmentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ action, reason }) => changeAppointmentStatus(appointmentId, action, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointment', appointmentId] })
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
    },
  })
}

// ── US4: Update Details ─────────────────────────────────────────────────────

export function useUpdateAppointment(appointmentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ data, version }) => updateAppointment(appointmentId, data, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointment', appointmentId] })
      queryClient.invalidateQueries({ queryKey: ['appointments'] })
    },
  })
}

// ── US5: Doctor Availability ────────────────────────────────────────────────

export function useDoctorAvailability(doctorId, date) {
  return useQuery({
    queryKey: ['doctor-availability', doctorId, date],
    queryFn: () => getDoctorAvailability(doctorId, date),
    staleTime: 30_000,
    enabled: !!(doctorId && date),
  })
}

// ── US6: Clinical Notes ─────────────────────────────────────────────────────

export function useAddClinicalNotes(appointmentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: data => addClinicalNotes(appointmentId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['appointment-notes', appointmentId] })
      queryClient.invalidateQueries({ queryKey: ['appointment', appointmentId] })
    },
  })
}

export function useClinicalNotes(appointmentId) {
  return useQuery({
    queryKey: ['appointment-notes', appointmentId],
    queryFn: () => getClinicalNotes(appointmentId),
    staleTime: 10_000,
    enabled: !!appointmentId,
    retry: (count, error) => error?.response?.status !== 404 && count < 2,
  })
}

// ── US7: Patient Appointment History ───────────────────────────────────────

export function usePatientAppointments(patientId, page = 0) {
  return useQuery({
    queryKey: ['patient-appointments', patientId, page],
    queryFn: () => getPatientAppointments(patientId, { page }),
    staleTime: 30_000,
    enabled: !!patientId,
    placeholderData: prev => prev,
  })
}
