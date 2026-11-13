import { useEffect, useState } from 'react'
import { label } from '../format'

export function PageHeader({ title, subtitle, actions }) {
  return (
    <div className="page-header">
      <div>
        <h1>{title}</h1>
        {subtitle && <p className="muted">{subtitle}</p>}
      </div>
      {actions && <div className="actions">{actions}</div>}
    </div>
  )
}

export function Card({ title, actions, children, className = '', pad = true }) {
  return (
    <section className={`card ${className}`}>
      {(title || actions) && (
        <header className="card-header">
          {title && <h2>{title}</h2>}
          {actions && <div className="actions">{actions}</div>}
        </header>
      )}
      <div className={pad ? 'card-body' : ''}>{children}</div>
    </section>
  )
}

export function Kpi({ label: l, value, sub, tone }) {
  return (
    <div className="kpi">
      <div className="kpi-label">{l}</div>
      <div className="kpi-value">{value}</div>
      {sub && <div className={`kpi-sub ${tone ? 'tone-' + tone : ''}`}>{sub}</div>}
    </div>
  )
}

const STATUS_TONE = {
  COMPLETED: 'good', PAID: 'good', DELIVERED: 'good', RECEIVED: 'good', ACTIVE: 'good',
  PENDING: 'warn', PARTIAL: 'warn', PARTIALLY_RECEIVED: 'warn', PACKED: 'info', ORDERED: 'info',
  IN_TRANSIT: 'info', SHIPPED: 'info', DRAFT: 'neutral', UNPAID: 'warn',
  CANCELLED: 'bad', REFUNDED: 'bad', RETURNED: 'bad', FAILED: 'bad', INACTIVE: 'neutral',
}
const TONE_ICON = { good: '●', warn: '◐', info: '◆', bad: '✕', neutral: '○' }

/** Status pill: colour + icon + text, never colour alone. */
export function Badge({ value, tone }) {
  if (!value) return null
  const t = tone || STATUS_TONE[value] || 'neutral'
  return <span className={`badge badge-${t}`}><span aria-hidden>{TONE_ICON[t]}</span>{label(value)}</span>
}

export function Modal({ title, onClose, children, footer, wide }) {
  useEffect(() => {
    const fn = (e) => e.key === 'Escape' && onClose()
    window.addEventListener('keydown', fn)
    return () => window.removeEventListener('keydown', fn)
  }, [onClose])
  return (
    <div className="modal-backdrop" onMouseDown={(e) => e.target === e.currentTarget && onClose()}>
      <div className={`modal ${wide ? 'modal-wide' : ''}`} role="dialog" aria-modal="true" aria-label={title}>
        <header className="modal-header">
          <h2>{title}</h2>
          <button className="btn-icon" onClick={onClose} aria-label="Close">✕</button>
        </header>
        <div className="modal-body">{children}</div>
        {footer && <footer className="modal-footer">{footer}</footer>}
      </div>
    </div>
  )
}

export function Field({ label: l, children, hint, span }) {
  return (
    <label className={`field ${span ? 'span-' + span : ''}`}>
      <span className="field-label">{l}</span>
      {children}
      {hint && <span className="field-hint">{hint}</span>}
    </label>
  )
}

export function Loading({ text = 'Loading…' }) {
  return <div className="loading"><span className="spinner" aria-hidden /> {text}</div>
}

export function ErrorBox({ error, onRetry }) {
  if (!error) return null
  return (
    <div className="error-box" role="alert">
      <span>⚠ {error.message}</span>
      {onRetry && <button className="btn btn-sm" onClick={onRetry}>Retry</button>}
    </div>
  )
}

export function Empty({ children = 'Nothing here yet.' }) {
  return <div className="empty">{children}</div>
}

/** Simple table. columns: [{key, header, render?, align?}] */
export function Table({ columns, rows, onRowClick, empty, rowKey = 'id' }) {
  if (!rows?.length) return <Empty>{empty}</Empty>
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>{columns.map((c) => <th key={c.key} className={c.align === 'right' ? 'num' : ''}>{c.header}</th>)}</tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r[rowKey] ?? i} onClick={onRowClick ? () => onRowClick(r) : undefined} className={onRowClick ? 'clickable' : ''}>
              {columns.map((c) => (
                <td key={c.key} className={c.align === 'right' ? 'num' : ''}>{c.render ? c.render(r) : r[c.key]}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Tabs({ tabs, value, onChange }) {
  return (
    <div className="tabs" role="tablist">
      {tabs.map((t) => (
        <button key={t.value} role="tab" aria-selected={value === t.value}
                className={`tab ${value === t.value ? 'active' : ''}`} onClick={() => onChange(t.value)}>
          {t.label}
        </button>
      ))}
    </div>
  )
}

/** Button that runs an async action, shows busy state and reports errors via onError. */
export function AsyncButton({ onClick, children, className = 'btn', confirm, ...rest }) {
  const [busy, setBusy] = useState(false)
  return (
    <button {...rest} className={className} disabled={busy || rest.disabled}
            onClick={async (e) => {
              if (confirm && !window.confirm(confirm)) return
              setBusy(true)
              try { await onClick(e) } finally { setBusy(false) }
            }}>
      {busy ? '…' : children}
    </button>
  )
}

export function Select({ value, onChange, options, placeholder, ...rest }) {
  return (
    <select value={value ?? ''} onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)} {...rest}>
      {placeholder !== undefined && <option value="">{placeholder}</option>}
      {options.map((o) => (
        <option key={o.value} value={o.value}>{o.label}</option>
      ))}
    </select>
  )
}

export const enumOptions = (values) => values.map((v) => ({ value: v, label: label(v) }))
