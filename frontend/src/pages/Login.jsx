import { useState } from 'react'
import { useAuth } from '../hooks/useAuth.jsx'

const DEMO = ['admin', 'manager', 'cashier', 'warehouse', 'accountant']

export default function Login() {
  const { login } = useAuth()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await login(username, password) // App redirects /login to the role's home page
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-page">
      <form className="card login-card stack" onSubmit={submit}>
        <div className="brand-mark" style={{ color: '#fff' }}>◆</div>
        <div>
          <h1>RetailHub</h1>
          <p className="muted" style={{ margin: 0 }}>Sign in to manage your shop</p>
        </div>
        {error && <div className="error-box" role="alert">{error}</div>}
        <label className="field"><span className="field-label">Username</span>
          <input value={username} onChange={(e) => setUsername(e.target.value)} autoFocus autoComplete="username" required />
        </label>
        <label className="field"><span className="field-label">Password</span>
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required />
        </label>
        <button className="btn btn-primary btn-block btn-lg" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
        <div className="demo-users">
          Demo accounts (password = username + “123”):
          <div>{DEMO.map((u) => <button type="button" key={u} onClick={() => { setUsername(u); setPassword(u + '123') }}>{u}</button>)}</div>
        </div>
      </form>
    </div>
  )
}
