import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import api from './patientApi.js'

// ── Raw API functions ─────────────────────────────────────────────────────────

export async function getLabOrders(patientId, { status, page = 0, size = 20 } = {}) {
  const params = { page, size }
  if (status) params.status = status
  const response = await api.get(`/patients/${patientId}/lab-orders`, { params })
  return response.data
}

export async function getLabResults(patientId, { page = 0, size = 20 } = {}) {
  const response = await api.get(`/patients/${patientId}/lab-results`, { params: { page, size } })
  return response.data
}

export async function createLabOrder(patientId, data) {
  const response = await api.post(`/patients/${patientId}/lab-orders`, data)
  return response.data
}

export async function recordLabResult(orderId, data) {
  const response = await api.post(`/lab-orders/${orderId}/result`, data)
  return response.data
}

// ── React Query hooks ─────────────────────────────────────────────────────────

export function useLabOrders(patientId, status, page = 0) {
  return useQuery({
    queryKey: ['lab-orders', patientId, status, page],
    queryFn: () => getLabOrders(patientId, { status, page }),
    staleTime: 30_000,
    enabled: !!patientId,
    placeholderData: prev => prev,
    refetchInterval: 30_000,
  })
}

export function useLabResults(patientId, page = 0) {
  return useQuery({
    queryKey: ['lab-results', patientId, page],
    queryFn: () => getLabResults(patientId, { page }),
    staleTime: 30_000,
    enabled: !!patientId,
    placeholderData: prev => prev,
    refetchInterval: 30_000,
  })
}

export function useCreateLabOrder(patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data) => createLabOrder(patientId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lab-orders', patientId] })
    },
  })
}

export function useRecordLabResult(orderId, patientId) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (data) => recordLabResult(orderId, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['lab-orders', patientId] })
      queryClient.invalidateQueries({ queryKey: ['lab-results', patientId] })
      queryClient.invalidateQueries({ queryKey: ['patient-alerts', patientId] })
    },
  })
}
