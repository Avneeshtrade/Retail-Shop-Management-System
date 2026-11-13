import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Field, Kpi, Loading, Modal, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { daysAgo, fmtDate, fmtDateTime, fmtMoney, isoDate, label } from '../format'

const STATUSES = ['PENDING', 'PACKED', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'RETURNED']
const TYPES = ['OUTBOUND', 'INBOUND', 'TRANSFER']
const NEXT = { PENDING: ['PACKED', 'IN_TRANSIT', 'CANCELLED'], PACKED: ['IN_TRANSIT', 'CANCELLED'], IN_TRANSIT: ['DELIVERED', 'RETURNED'] }

export default function Shipments() {
  const [status, setStatus] = useState(null)
  const [type, setType] = useState(null)
  const { data, error, loading, reload } = useApi('/shipments', { status, type })
  const { hasRole } = useAuth()
  const [creating, setCreating] = useState(false)
  const [openId, setOpenId] = useState(null)
  const canEdit = hasRole(...R.STOCK)

  const all = data || []
  const today = isoDate(new Date())
  const active = all.filter((s) => ['PENDING', 'PACKED', 'IN_TRANSIT'].includes(s.status))
  const late = active.filter((s) => s.estimatedDelivery && s.estimatedDelivery < today)

  return (
    <>
      <PageHeader title="Logistics" subtitle="Deliveries to customers, inbound freight and transfers between locations"
                  actions={canEdit && <button className="btn btn-primary" onClick={() => setCreating(true)}>+ New shipment</button>} />
      <div className="kpis">
        <Kpi label="Awaiting dispatch" value={active.filter((s) => s.status !== 'IN_TRANSIT').length} />
        <Kpi label="In transit" value={active.filter((s) => s.status === 'IN_TRANSIT').length} />
        <Kpi label="Past due date" value={late.length} tone={late.length ? 'bad' : 'good'} sub={late.length ? 'Follow up with carriers' : 'All on schedule'} />
      </div>
      <div className="toolbar">
        <Select value={type} onChange={setType} placeholder="All types" options={enumOptions(TYPES)} />
        <Select value={status} onChange={setStatus} placeholder="All statuses" options={enumOptions(STATUSES)} />
      </div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : <Table rows={all} onRowClick={(s) => setOpenId(s.id)} empty="No shipments" columns={[
          { key: 'shipmentNumber', header: 'Shipment', render: (s) => <><strong>{s.shipmentNumber}</strong><div className="sub">{label(s.type)}</div></> },
          { key: 'ref', header: 'For', render: (s) => s.salesOrder?.orderNumber || s.purchaseOrder?.poNumber || `${s.fromWarehouse?.code} → ${s.toWarehouse?.code}` },
          { key: 'carrier', header: 'Carrier', render: (s) => <>{s.carrier || '—'}<div className="sub">{s.trackingNumber}</div></> },
          { key: 'dest', header: 'Destination', render: (s) => <span className="sub">{s.destinationAddress || '—'}</span> },
          { key: 'estimatedDelivery', header: 'ETA', render: (s) => <span className={['PENDING', 'PACKED', 'IN_TRANSIT'].includes(s.status) && s.estimatedDelivery < today ? 'tone-bad' : ''}>{fmtDate(s.estimatedDelivery)}</span> },
          { key: 'status', header: 'Status', render: (s) => <Badge value={s.status} /> },
          { key: 'shippingCost', header: 'Cost', align: 'right', render: (s) => fmtMoney(s.shippingCost) },
        ]} />}
      </Card>
      {creating && <ShipmentForm onClose={() => setCreating(false)} onSaved={() => { setCreating(false); reload() }} />}
      {openId && <ShipmentDetail id={openId} canEdit={canEdit} onClose={() => setOpenId(null)} onChanged={reload} />}
    </>
  )
}

function ShipmentForm({ onClose, onSaved }) {
  const toast = useToast()
  const [f, setF] = useState({ type: 'TRANSFER', carrier: '', trackingNumber: '', shippingCost: 0, estimatedDelivery: '', notes: '', items: [{ productId: null, quantity: 1 }] })
  const { data: warehouses } = useApi('/warehouses')
  const { data: products } = useApi(f.type === 'TRANSFER' ? '/products' : null)
  const { data: orders } = useApi(f.type === 'OUTBOUND' ? '/sales-orders' : null, { from: daysAgo(30) })
  const { data: pos } = useApi(f.type === 'INBOUND' ? '/purchase-orders' : null)
  const set = (k) => (v) => setF({ ...f, [k]: v?.target ? v.target.value : v })

  const save = async () => {
    const body = { ...f, shippingCost: Number(f.shippingCost) || 0, estimatedDelivery: f.estimatedDelivery || null,
      salesOrderId: f.salesOrderId ? Number(f.salesOrderId) : null, purchaseOrderId: f.purchaseOrderId ? Number(f.purchaseOrderId) : null,
      fromWarehouseId: f.fromWarehouseId ? Number(f.fromWarehouseId) : null, toWarehouseId: f.toWarehouseId ? Number(f.toWarehouseId) : null,
      items: f.type === 'TRANSFER' ? f.items.filter((i) => i.productId).map((i) => ({ productId: Number(i.productId), quantity: Number(i.quantity) })) : null }
    try { await api.post('/shipments', body); toast('Shipment created'); onSaved() } catch (e) { toast(e.message, 'error') }
  }
  const whOpts = (warehouses || []).map((w) => ({ value: w.id, label: w.name }))

  return (
    <Modal wide title="New shipment" onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" onClick={save}>Create</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Type" span={2}>
          <div className="segmented">
            {TYPES.map((t) => <button key={t} type="button" className={f.type === t ? 'active' : ''} onClick={() => setF({ ...f, type: t })}>
              {{ OUTBOUND: 'Customer delivery', INBOUND: 'Inbound from supplier', TRANSFER: 'Stock transfer' }[t]}</button>)}
          </div>
        </Field>
        {f.type === 'OUTBOUND' && <Field label="Sales order *" span={2}><Select value={f.salesOrderId} onChange={set('salesOrderId')} placeholder="Select…"
          options={(orders || []).filter((o) => ['PENDING', 'COMPLETED'].includes(o.status)).map((o) => ({ value: o.id, label: `${o.orderNumber} · ${o.customer?.name || 'Walk-in'} · ${fmtMoney(o.total)}` }))} /></Field>}
        {f.type === 'INBOUND' && <Field label="Purchase order *" span={2}><Select value={f.purchaseOrderId} onChange={set('purchaseOrderId')} placeholder="Select…"
          options={(pos || []).filter((p) => ['ORDERED', 'PARTIALLY_RECEIVED'].includes(p.status)).map((p) => ({ value: p.id, label: `${p.poNumber} · ${p.supplier.name}` }))} /></Field>}
        {f.type === 'TRANSFER' && <>
          <Field label="From *"><Select value={f.fromWarehouseId} onChange={set('fromWarehouseId')} placeholder="Select…" options={whOpts} /></Field>
          <Field label="To *"><Select value={f.toWarehouseId} onChange={set('toWarehouseId')} placeholder="Select…" options={whOpts} /></Field>
        </>}
        <Field label="Carrier"><input value={f.carrier} onChange={set('carrier')} placeholder="e.g. FastShip Express / Own fleet" /></Field>
        <Field label="Tracking number"><input value={f.trackingNumber} onChange={set('trackingNumber')} /></Field>
        <Field label="Shipping cost"><input type="number" value={f.shippingCost} onChange={set('shippingCost')} /></Field>
        <Field label="Estimated delivery"><input type="date" value={f.estimatedDelivery} onChange={set('estimatedDelivery')} /></Field>
      </div>
      {f.type === 'TRANSFER' && <>
        <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>Items to transfer</h3>
        <p className="muted" style={{ marginTop: 0 }}>Stock leaves the source when marked In transit and arrives when Delivered.</p>
        <div className="stack">
          {f.items.map((it, idx) => (
            <div key={idx} className="row">
              <Select style={{ flex: 1 }} value={it.productId} placeholder="Product…" options={(products || []).map((p) => ({ value: p.id, label: `${p.name} (${p.sku})` }))}
                      onChange={(v) => setF({ ...f, items: f.items.map((x, i) => i === idx ? { ...x, productId: v } : x) })} />
              <input type="number" min="1" style={{ width: 90 }} value={it.quantity} aria-label="Quantity"
                     onChange={(e) => setF({ ...f, items: f.items.map((x, i) => i === idx ? { ...x, quantity: e.target.value } : x) })} />
              <button className="btn-icon" onClick={() => setF({ ...f, items: f.items.filter((_, i) => i !== idx) })} aria-label="Remove">✕</button>
            </div>
          ))}
          <div><button className="btn btn-sm" onClick={() => setF({ ...f, items: [...f.items, { productId: null, quantity: 1 }] })}>+ Add item</button></div>
        </div>
      </>}
      <Field label="Notes"><textarea value={f.notes} onChange={set('notes')} /></Field>
    </Modal>
  )
}

function ShipmentDetail({ id, canEdit, onClose, onChanged }) {
  const { data: s, reload } = useApi(`/shipments/${id}`)
  const toast = useToast()
  const [tracking, setTracking] = useState('')
  if (!s) return <Modal title="Shipment" onClose={onClose}><Loading /></Modal>
  const move = async (status) => {
    try {
      await api.post(`/shipments/${id}/status`, { status, trackingNumber: tracking || null })
      toast(`Marked ${label(status).toLowerCase()}`)
      reload(); onChanged()
    } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal wide title={`${s.shipmentNumber} · ${label(s.type)}`} onClose={onClose} footer={<>
      {canEdit && (NEXT[s.status] || []).map((n) => (
        <AsyncButton key={n} className={`btn ${['CANCELLED', 'RETURNED'].includes(n) ? 'btn-danger' : 'btn-primary'}`}
                     confirm={['CANCELLED', 'RETURNED'].includes(n) ? `Mark as ${label(n)}?` : undefined} onClick={() => move(n)}>
          Mark {label(n).toLowerCase()}
        </AsyncButton>))}
      <button className="btn" onClick={onClose}>Close</button>
    </>}>
      <div className="grid grid-2">
        <dl className="dl">
          <dt>Status</dt><dd><Badge value={s.status} /></dd>
          <dt>Carrier</dt><dd>{s.carrier || '—'}</dd>
          <dt>Tracking</dt><dd>{s.trackingNumber || (canEdit && NEXT[s.status] ? <input placeholder="Add tracking #" value={tracking} onChange={(e) => setTracking(e.target.value)} /> : '—')}</dd>
          <dt>Cost</dt><dd>{fmtMoney(s.shippingCost)}</dd>
          {s.salesOrder && <><dt>Sales order</dt><dd>{s.salesOrder.orderNumber}</dd></>}
          {s.purchaseOrder && <><dt>Purchase order</dt><dd>{s.purchaseOrder.poNumber}</dd></>}
        </dl>
        <dl className="dl">
          <dt>From</dt><dd>{s.fromWarehouse?.name || s.originAddress || '—'}</dd>
          <dt>To</dt><dd>{s.toWarehouse?.name || s.destinationAddress || '—'}</dd>
          <dt>Created</dt><dd>{fmtDateTime(s.createdAt)} by {s.createdBy}</dd>
          <dt>ETA</dt><dd>{fmtDate(s.estimatedDelivery)}</dd>
          <dt>Shipped</dt><dd>{fmtDateTime(s.shippedAt)}</dd>
          <dt>Delivered</dt><dd>{fmtDateTime(s.deliveredAt)}</dd>
        </dl>
      </div>
      <Table rows={s.items} empty="No items" columns={[
        { key: 'p', header: 'Product', render: (i) => <>{i.product.name}<div className="sub">{i.product.sku}</div></> },
        { key: 'quantity', header: 'Qty', align: 'right' },
      ]} />
      {s.notes && <><h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>Notes & history</h3><ul className="timeline">{s.notes.split('\n').map((n, i) => <li key={i}>{n}</li>)}</ul></>}
    </Modal>
  )
}
