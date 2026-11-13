import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from '../api/client'

/** GET with loading/error state. Re-fetches when path or params change. */
export function useApi(path, params) {
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(!!path)
  const key = path ? path + JSON.stringify(params ?? {}) : null
  const seq = useRef(0)

  const reload = useCallback(() => {
    if (!path) return Promise.resolve()
    const id = ++seq.current
    setLoading(true)
    return api.get(path, params)
      .then((d) => { if (id === seq.current) { setData(d); setError(null) } })
      .catch((e) => { if (id === seq.current) setError(e) })
      .finally(() => { if (id === seq.current) setLoading(false) })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  useEffect(() => { reload() }, [reload])
  return { data, error, loading, reload, setData }
}
