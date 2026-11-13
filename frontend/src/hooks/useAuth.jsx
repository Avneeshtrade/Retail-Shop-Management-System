import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import { api, setUnauthorizedHandler, tokenStore } from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(!!tokenStore.get())

  const logout = useCallback(() => {
    tokenStore.set(null)
    setUser(null)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(logout)
    if (!tokenStore.get()) return
    api.get('/auth/me').then(setUser).catch(logout).finally(() => setLoading(false))
  }, [logout])

  const login = useCallback(async (username, password) => {
    const res = await api.post('/auth/login', { username, password })
    tokenStore.set(res.token)
    setUser(res.user)
    return res.user
  }, [])

  const value = useMemo(() => ({
    user, loading, login, logout,
    hasRole: (...roles) => !!user && roles.includes(user.role),
  }), [user, loading, login, logout])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export const useAuth = () => useContext(AuthContext)

/** Role groups mirroring the backend's Roles.java */
export const R = {
  ADMIN: ['ADMIN'],
  MANAGE: ['ADMIN', 'MANAGER'],
  SELL: ['ADMIN', 'MANAGER', 'CASHIER'],
  STOCK: ['ADMIN', 'MANAGER', 'WAREHOUSE'],
  FINANCE: ['ADMIN', 'MANAGER', 'ACCOUNTANT'],
  PROCURE: ['ADMIN', 'MANAGER', 'WAREHOUSE', 'ACCOUNTANT'],
  ALL: ['ADMIN', 'MANAGER', 'CASHIER', 'WAREHOUSE', 'ACCOUNTANT'],
}
