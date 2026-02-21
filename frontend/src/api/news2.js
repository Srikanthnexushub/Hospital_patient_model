import { useQuery } from '@tanstack/react-query'
import api from './patientApi.js'

// ── Raw API function ──────────────────────────────────────────────────────────

export async function getNews2(patientId) {
  const response = await api.get(`/patients/${patientId}/news2`)
  return response.data
}

// ── React Query hook ──────────────────────────────────────────────────────────

export function useNews2(patientId) {
  return useQuery({
    queryKey: ['news2', patientId],
    queryFn: () => getNews2(patientId),
    staleTime: 30_000,
    enabled: !!patientId,
    refetchInterval: 30_000,
  })
}
