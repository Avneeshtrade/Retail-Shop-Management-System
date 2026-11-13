/** HTML legend so identity never depends on colour alone. */
export default function Legend({ items }) {
  return (
    <div className="legend">
      {items.map((i) => (
        <span key={i.label} className="legend-item">
          <span className={i.line ? 'line-key' : 'box-key'} style={{ background: i.color }} />
          {i.label}
        </span>
      ))}
    </div>
  )
}
