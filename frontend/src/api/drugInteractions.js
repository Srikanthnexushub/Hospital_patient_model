import { useMutation, useQuery } from '@tanstack/react-query'
import api from './patientApi.js'

// ── Raw API functions ─────────────────────────────────────────────────────────

export async function checkInteraction(patientId, drugName) {
  const response = await api.post(`/patients/${patientId}/interaction-check`, { drugName })
  return response.data
}

export async function getInteractionSummary(patientId) {
  const response = await api.get(`/patients/${patientId}/interaction-summary`)
  return response.data
}

// ── React Query hooks ─────────────────────────────────────────────────────────

export function useInteractionCheck(patientId) {
  return useMutation({
    mutationFn: (drugName) => checkInteraction(patientId, drugName),
  })
}

export function useInteractionSummary(patientId) {
  return useQuery({
    queryKey: ['interaction-summary', patientId],
    queryFn: () => getInteractionSummary(patientId),
    staleTime: 30_000,
    enabled: !!patientId,
  })
}
