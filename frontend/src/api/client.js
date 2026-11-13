const BASE = import.meta.env.VITE_API_BASE ?? ''
const TOKEN_KEY = 'retailhub.token'

export const tokenStore = {
  get: () => { try { return localStorage.getItem(TOKEN_KEY) } catch { return null } },
  set: (t) => { try { t ? localStorage.setItem(TOKEN_KEY, t) : localStorage.removeItem(TOKEN_KEY) } catch { /* ignore */ } },
}

let onUnauthorized = () => {}
export const setUnauthorizedHandler = (fn) => { onUnauthorized = fn }

export class ApiError extends Error {
  constructor(status, body) {
    const fieldErrors = body?.errors ? Object.entries(body.errors).map(([k, v]) => `${k}: ${v}`).join(', ') : ''
    super([body?.detail || body?.title || `Request failed (${status})`, fieldErrors].filter(Boolean).join(' — '))
    this.status = status
    this.body = body
  }
}

async function request(method, path, body, params) {
  let url = BASE + '/api' + path
  if (params) {
    const qs = new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== ''))
    if ([...qs].length) url += '?' + qs
  }
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(url, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined })
  if (res.status === 401 && !path.startsWith('/auth/login')) onUnauthorized()
  const text = await res.text()
  const data = text ? (() => { try { return JSON.parse(text) } catch { return text } })() : null
  if (!res.ok) throw new ApiError(res.status, typeof data === 'object' ? data : { detail: data })
  return data
}

export const api = {
  get: (p, params) => request('GET', p, undefined, params),
  post: (p, body, params) => request('POST', p, body ?? {}, params),
  put: (p, body) => request('PUT', p, body),
  del: (p) => request('DELETE', p),
}
