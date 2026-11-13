/** Ranked horizontal bars in plain HTML (top-N lists). Single series = one colour, labels in text ink. */
export default function BarList({ items, color, format, sub }) {
  const max = Math.max(1, ...items.map((i) => Number(i.value) || 0))
  return (
    <div className="bar-list">
      {items.map((i) => (
        <div key={i.name} className="bar-list-row" title={`${i.name}: ${format(i.value)}`}>
          <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{i.name}</span>
          <strong className="tabular">{format(i.value)}{sub && <span className="muted" style={{ fontWeight: 400 }}> · {sub(i)}</span>}</strong>
          <div className="bar-list-track"><div className="bar-list-fill" style={{ width: `${(Number(i.value) / max) * 100}%`, background: color }} /></div>
        </div>
      ))}
    </div>
  )
}
