import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import api from './patientApi.js'

// ── Raw API functions ─────────────────────────────────────────────────────────

export async function getPatientAlerts(patientId, { status, severity, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (status) params.status = status
  if (severity) params.severity = severity
  const response = await api.get(`/patients/${patientId}/alerts`, { params })
  return response.data
}

export async function getGlobalAlerts({ status, severity, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (status) params.status = status
  if (severity) params.severity = severity
  const response = await api.get('/alerts', { params })
  return response.data
}

export async function acknowledgeAlert(alertId) {
  const response = await api.patch(`/alerts/${alertId}/acknowledge`)
  return response.data
}

export async function dismissAlert(alertId, reason) {
  const response = await api.patch(`/alerts/${alertId}/dismiss`, { reason })
  return response.data
}

// ── React Query hooks ─────────────────────────────────────────────────────────

export function usePatientAlerts(patientId, status, severity, page = 0) {
  return useQuery({
    queryKey: ['patient-alerts', patientId, status, severity, page],
    queryFn: () => getPatientAlerts(patientId, { status, severity, page }),
    staleTime: 30_000,
    enabled: !!patientId,
    placeholderData: prev => prev,
    refetchInterval: 30_000,
  })
}

export function useGlobalAlerts(status, severity, page = 0) {
  return useQuery({
    queryKey: ['global-alerts', status, severity, page],
    queryFn: () => getGlobalAlerts({ status, severity, page }),
    staleTime: 30_000,
    placeholderData: prev => prev,
    refetchInterval: 30_000,
  })
}

export function useAcknowledgeAlert() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (alertId) => acknowledgeAlert(alertId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['global-alerts'] })
      queryClient.invalidateQueries({ queryKey: ['patient-alerts'] })
    },
  })
}

export function useDismissAlert() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ alertId, reason }) => dismissAlert(alertId, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['global-alerts'] })
      queryClient.invalidateQueries({ queryKey: ['patient-alerts'] })
    },
  })
}
