import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { getPatient, updatePatient, changePatientStatus } from '../api/patientApi.js'

/**
 * US3: Query hook to fetch a single patient profile.
 */
export function usePatient(patientId) {
  return useQuery({
    queryKey: ['patient', patientId],
    queryFn: () => getPatient(patientId),
    staleTime: 10_000,
    enabled: !!patientId,
  })
}

/**
 * US4: Mutation hook to update a patient's record.
 * Invalidates the patient cache on success.
 */
export function useUpdatePatient(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ data, version }) => updatePatient(patientId, data, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patient', patientId] })
      queryClient.invalidateQueries({ queryKey: ['patients'] })
    },
  })
}

/**
 * US5: Mutation hook to change a patient's active/inactive status.
 * Invalidates the patient cache and the list cache on success.
 */
export function useChangePatientStatus(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: action => changePatientStatus(patientId, action),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patient', patientId] })
      queryClient.invalidateQueries({ queryKey: ['patients'] })
    },
  })
}
