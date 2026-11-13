import { daysAgo, isoDate, today } from '../format'

const PRESETS = [
  { label: '7 days', from: () => daysAgo(6) },
  { label: '30 days', from: () => daysAgo(29) },
  { label: '90 days', from: () => daysAgo(89) },
  { label: 'Month to date', from: () => isoDate(new Date(new Date().getFullYear(), new Date().getMonth(), 1)) },
]

export default function DateRange({ from, to, onChange }) {
  const t = today()
  return (
    <div className="date-range">
      <div className="segmented">
        {PRESETS.map((p) => {
          const f = p.from()
          const active = from === f && to === t
          return (
            <button key={p.label} className={active ? 'active' : ''} onClick={() => onChange({ from: f, to: t })}>
              {active && <span aria-hidden>✓ </span>}{p.label}
            </button>
          )
        })}
      </div>
      <input type="date" value={from} max={to} onChange={(e) => onChange({ from: e.target.value, to })} aria-label="From date" />
      <span className="muted">to</span>
      <input type="date" value={to} min={from} max={t} onChange={(e) => onChange({ from, to: e.target.value })} aria-label="To date" />
    </div>
  )
}
