import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  recordVitals,
  getVitalsByAppointment,
  getVitalsByPatient,
  createProblem,
  listProblems,
  updateProblem,
  prescribeMedication,
  listMedications,
  updateMedication,
  recordAllergy,
  listAllergies,
  deleteAllergy,
  getMedicalSummary,
} from '../api/emrApi.js'

// ── US1: Vitals ──────────────────────────────────────────────────────────────

export function useRecordVitals(appointmentId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data) => recordVitals(appointmentId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['vitals', 'appointment', appointmentId] })
    },
  })
}

export function useVitalsByAppointment(appointmentId) {
  return useQuery({
    queryKey: ['vitals', 'appointment', appointmentId],
    queryFn: () => getVitalsByAppointment(appointmentId),
    staleTime: 30_000,
    enabled: !!appointmentId,
    retry: (count, error) => error?.response?.status !== 404 && count < 2,
  })
}

export function useVitalsByPatient(patientId, page = 0) {
  return useQuery({
    queryKey: ['vitals', 'patient', patientId, page],
    queryFn: () => getVitalsByPatient(patientId, { page }),
    staleTime: 30_000,
    enabled: !!patientId,
    placeholderData: prev => prev,
  })
}

// ── US2: Problems ─────────────────────────────────────────────────────────────

export function useCreateProblem(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data) => createProblem(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['problems', patientId] })
    },
  })
}

export function useProblems(patientId, status = 'ACTIVE') {
  return useQuery({
    queryKey: ['problems', patientId, status],
    queryFn: () => listProblems(patientId, { status }),
    staleTime: 30_000,
    enabled: !!patientId,
  })
}

export function useUpdateProblem(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ problemId, data }) => updateProblem(patientId, problemId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['problems', patientId] })
    },
  })
}

// ── US3: Medications ──────────────────────────────────────────────────────────

export function usePrescribeMedication(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data) => prescribeMedication(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['medications', patientId] })
    },
  })
}

export function useMedications(patientId, status = 'ACTIVE') {
  return useQuery({
    queryKey: ['medications', patientId, status],
    queryFn: () => listMedications(patientId, { status }),
    staleTime: 30_000,
    enabled: !!patientId,
  })
}

export function useUpdateMedication(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ medicationId, data }) => updateMedication(patientId, medicationId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['medications', patientId] })
    },
  })
}

// ── US4: Allergies ────────────────────────────────────────────────────────────

export function useRecordAllergy(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data) => recordAllergy(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['allergies', patientId] })
    },
  })
}

export function useAllergies(patientId) {
  return useQuery({
    queryKey: ['allergies', patientId],
    queryFn: () => listAllergies(patientId),
    staleTime: 60_000,
    enabled: !!patientId,
  })
}

export function useDeleteAllergy(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (allergyId) => deleteAllergy(patientId, allergyId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['allergies', patientId] })
    },
  })
}

// ── US5: Medical Summary ──────────────────────────────────────────────────────

export function useMedicalSummary(patientId) {
  return useQuery({
    queryKey: ['medical-summary', patientId],
    queryFn: () => getMedicalSummary(patientId),
    staleTime: 30_000,
    enabled: !!patientId,
  })
}
