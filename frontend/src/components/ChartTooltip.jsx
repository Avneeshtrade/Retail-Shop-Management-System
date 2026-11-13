/** Recharts tooltip content: value leads, label follows, line-key swatches. */
export default function ChartTooltip({ active, payload, label, labelFormatter, valueFormatter }) {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-title">{labelFormatter ? labelFormatter(label) : label}</div>
      {payload.map((p) => (
        <div key={p.dataKey ?? p.name} className="chart-tooltip-row">
          <span className="line-key" style={{ background: p.color || p.payload?.fill }} />
          <strong>{valueFormatter ? valueFormatter(p.value, p) : p.value}</strong>
          <span className="muted">{p.name}</span>
        </div>
      ))}
    </div>
  )
}
