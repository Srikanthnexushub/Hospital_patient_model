import { useState, useEffect, useCallback } from 'react'

const STORAGE_KEY = 'patientListState'

const defaultState = {
  query: '',
  status: 'ACTIVE',
  gender: 'ALL',
  bloodGroup: 'ALL',
  page: 0,
}

/**
 * US2: Persistent list filter/pagination state backed by sessionStorage.
 * State survives navigation (e.g. visit profile, press Back) but not full reloads.
 */
export function useListState() {
  const [state, setState] = useState(() => {
    try {
      const stored = sessionStorage.getItem(STORAGE_KEY)
      return stored ? { ...defaultState, ...JSON.parse(stored) } : defaultState
    } catch {
      return defaultState
    }
  })

  // Persist to sessionStorage whenever state changes
  useEffect(() => {
    try {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify(state))
    } catch {
      // sessionStorage may be unavailable in some environments; silently ignore
    }
  }, [state])

  const setQuery = useCallback(query => setState(s => ({ ...s, query, page: 0 })), [])
  const setStatus = useCallback(status => setState(s => ({ ...s, status, page: 0 })), [])
  const setGender = useCallback(gender => setState(s => ({ ...s, gender, page: 0 })), [])
  const setBloodGroup = useCallback(bloodGroup => setState(s => ({ ...s, bloodGroup, page: 0 })), [])
  const setPage = useCallback(page => setState(s => ({ ...s, page })), [])
  const reset = useCallback(() => setState(defaultState), [])

  return { ...state, setQuery, setStatus, setGender, setBloodGroup, setPage, reset }
}
