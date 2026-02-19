import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { registerPatient, searchPatients } from '../api/patientApi.js'

/**
 * US1: Mutation hook to register a new patient.
 * On success, invalidates the 'patients' query cache.
 */
export function useRegisterPatient() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: registerPatient,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['patients'] })
    },
  })
}

/**
 * US2: Query hook to search/list patients with filters.
 * Keeps previous data during page transitions for a smoother UX.
 */
export function useSearchPatients(params = {}) {
  return useQuery({
    queryKey: ['patients', params],
    queryFn: () => searchPatients(params),
    staleTime: 30_000,
    gcTime: 300_000,
    placeholderData: previousData => previousData, // replaces keepPreviousData in v5
  })
}
