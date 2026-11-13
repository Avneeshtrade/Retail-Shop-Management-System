import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Field, Loading, Modal, PageHeader, Select, Table, Tabs } from '../components/ui.jsx'
import { fmtDateTime, fmtMoney } from '../format'

export default function Inventory() {
  const [tab, setTab] = useState('stock')
  const { hasRole } = useAuth()
  const canMove = hasRole(...R.STOCK) || hasRole(...R.FINANCE)
  return (
    <>
      <PageHeader title="Inventory" subtitle="Stock on hand across all locations, adjustments and audit trail" />
      <Tabs value={tab} onChange={setTab} tabs={[{ value: 'stock', label: 'Stock levels' }, ...(canMove ? [{ value: 'moves', label: 'Movement history' }] : [])]} />
      {tab === 'stock' ? <StockLevels canAdjust={hasRole(...R.STOCK)} /> : <Movements />}
    </>
  )
}

function StockLevels({ canAdjust }) {
  const [warehouseId, setWarehouseId] = useState(null)
  const [lowOnly, setLowOnly] = useState(false)
  const [q, setQ] = useState('')
  const { data, error, loading, reload } = useApi('/inventory', { warehouseId, lowOnly })
  const { data: warehouses } = useApi('/warehouses')
  const [adjust, setAdjust] = useState(null)

  const rows = (data || []).filter((r) => !q || r.product.name.toLowerCase().includes(q.toLowerCase()) || r.product.sku.toLowerCase().includes(q.toLowerCase()))
  const value = rows.reduce((a, r) => a + r.totalQuantity * Number(r.product.costPrice), 0)

  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search product" value={q} onChange={(e) => setQ(e.target.value)} />
        <Select value={warehouseId} onChange={setWarehouseId} placeholder="All locations" options={(warehouses || []).map((w) => ({ value: w.id, label: w.name }))} />
        <label className="check"><input type="checkbox" checked={lowOnly} onChange={(e) => setLowOnly(e.target.checked)} /> Low stock only</label>
        <span className="spacer" />
        <span className="muted">{rows.length} products · {fmtMoney(value)} at cost</span>
        {canAdjust && <button className="btn btn-primary" onClick={() => setAdjust({})}>Stock adjustment</button>}
      </div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : (
          <Table rowKey="sku" rows={rows.map((r) => ({ ...r, sku: r.product.sku }))} columns={[
            { key: 'p', header: 'Product', render: (r) => <><strong>{r.product.name}</strong><div className="sub">{r.product.sku}</div></> },
            { key: 'by', header: 'By location', render: (r) => <span className="sub">{r.byWarehouse.map((w) => `${w.warehouseCode}: ${w.quantity}`).join(' · ') || '—'}</span> },
            { key: 'reorder', header: 'Reorder at', align: 'right', render: (r) => r.product.reorderLevel },
            { key: 'totalQuantity', header: 'On hand', align: 'right', render: (r) => <strong>{r.totalQuantity}</strong> },
            { key: 'value', header: 'Value (cost)', align: 'right', render: (r) => fmtMoney(r.totalQuantity * r.product.costPrice) },
            { key: 'st', header: 'Status', render: (r) => r.totalQuantity <= 0 ? <Badge value="Out of stock" tone="bad" /> : r.lowStock ? <Badge value="Low" tone="warn" /> : <Badge value="OK" tone="good" /> },
            ...(canAdjust ? [{ key: 'a', header: '', align: 'right', render: (r) => <button className="btn btn-sm" onClick={() => setAdjust({ productId: r.product.id })}>Adjust</button> }] : []),
          ]} />
        )}
      </Card>
      {adjust && <AdjustForm initial={adjust} warehouses={warehouses || []} onClose={() => setAdjust(null)} onSaved={() => { setAdjust(null); reload() }} />}
    </>
  )
}

function AdjustForm({ initial, warehouses, onClose, onSaved }) {
  const { data: products } = useApi('/products')
  const toast = useToast()
  const [mode, setMode] = useState('count')
  const [f, setF] = useState({ productId: initial.productId ?? null, warehouseId: warehouses[0]?.id ?? null, value: '', reason: 'Stock count' })
  const { data: current } = useApi(f.productId && f.warehouseId ? '/inventory/available' : null, { productId: f.productId, warehouseId: f.warehouseId })
  const save = async () => {
    try {
      await api.post('/inventory/adjustments', {
        productId: Number(f.productId), warehouseId: Number(f.warehouseId), reason: f.reason,
        ...(mode === 'count' ? { countedQuantity: Number(f.value) } : { quantityChange: Number(f.value) }),
      })
      toast('Stock updated')
      onSaved()
    } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal title="Stock adjustment" onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" disabled={!f.productId || f.value === '' || !f.reason} onClick={save}>Apply</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Product" span={2}><Select value={f.productId} onChange={(v) => setF({ ...f, productId: v })} placeholder="Select…" options={(products || []).map((p) => ({ value: p.id, label: `${p.name} (${p.sku})` }))} /></Field>
        <Field label="Location"><Select value={f.warehouseId} onChange={(v) => setF({ ...f, warehouseId: v })} options={warehouses.map((w) => ({ value: w.id, label: w.name }))} /></Field>
        <Field label="Current quantity"><input disabled value={current ?? '—'} /></Field>
        <Field label="Type">
          <div className="segmented">
            <button type="button" className={mode === 'count' ? 'active' : ''} onClick={() => setMode('count')}>Physical count</button>
            <button type="button" className={mode === 'delta' ? 'active' : ''} onClick={() => setMode('delta')}>Add / remove</button>
          </div>
        </Field>
        <Field label={mode === 'count' ? 'Counted quantity' : 'Change (+/−)'} hint={mode === 'delta' ? 'Negative to remove (damage, theft, expiry)' : undefined}>
          <input type="number" value={f.value} onChange={(e) => setF({ ...f, value: e.target.value })} />
        </Field>
        <Field label="Reason *" span={2}><input value={f.reason} onChange={(e) => setF({ ...f, reason: e.target.value })} placeholder="Stock count, damaged, expired…" /></Field>
      </div>
    </Modal>
  )
}

function Movements() {
  const { data, error, loading, reload } = useApi('/inventory/movements')
  return (
    <>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : (
          <Table rows={data} columns={[
            { key: 'createdAt', header: 'When', render: (m) => fmtDateTime(m.createdAt) },
            { key: 'p', header: 'Product', render: (m) => <>{m.product.name}<div className="sub">{m.product.sku}</div></> },
            { key: 'w', header: 'Location', render: (m) => m.warehouse.code },
            { key: 'type', header: 'Type', render: (m) => <Badge value={m.type} tone={m.quantity > 0 ? 'good' : 'info'} /> },
            { key: 'quantity', header: 'Qty', align: 'right', render: (m) => <strong className={m.quantity > 0 ? 'tone-good' : ''}>{m.quantity > 0 ? '+' : ''}{m.quantity}</strong> },
            { key: 'balanceAfter', header: 'Balance', align: 'right' },
            { key: 'reference', header: 'Reference', render: (m) => <>{m.reference}<div className="sub">{m.note}</div></> },
            { key: 'performedBy', header: 'By' },
          ]} />
        )}
      </Card>
      <p className="muted">Showing the latest 200 movements.</p>
    </>
  )
}
