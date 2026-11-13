import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Field, Loading, Modal, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { fmtDate, fmtDateTime, fmtMoney, label } from '../format'

const STATUSES = ['DRAFT', 'ORDERED', 'PARTIALLY_RECEIVED', 'RECEIVED', 'CANCELLED']
const METHODS = ['BANK_TRANSFER', 'CHEQUE', 'CASH', 'CARD', 'UPI']

export default function PurchaseOrders() {
  const [status, setStatus] = useState(null)
  const { data, error, loading, reload } = useApi('/purchase-orders', { status })
  const { data: warehouses } = useApi('/warehouses')
  const { hasRole } = useAuth()
  const toast = useToast()
  const [edit, setEdit] = useState(null)
  const [openId, setOpenId] = useState(null)

  const autoReorder = async () => {
    const wh = warehouses?.find((w) => w.type === 'WAREHOUSE') || warehouses?.[0]
    try {
      const pos = await api.post('/purchase-orders/auto-reorder', null, { warehouseId: wh.id })
      toast(pos.length ? `Created ${pos.length} draft PO(s) for ${wh.name}` : 'Nothing needs reordering right now')
      reload()
    } catch (e) { toast(e.message, 'error') }
  }

  return (
    <>
      <PageHeader title="Purchase Orders" subtitle="Order from suppliers, receive goods and settle invoices"
                  actions={<>
                    {hasRole(...R.MANAGE, 'WAREHOUSE') && <AsyncButton className="btn" onClick={autoReorder}>⚡ Auto-reorder low stock</AsyncButton>}
                    <button className="btn btn-primary" onClick={() => setEdit({})}>+ New PO</button>
                  </>} />
      <div className="toolbar"><Select value={status} onChange={setStatus} placeholder="All statuses" options={enumOptions(STATUSES)} /></div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : <Table rows={data} onRowClick={(p) => setOpenId(p.id)} empty="No purchase orders" columns={[
          { key: 'poNumber', header: 'PO', render: (p) => <><strong>{p.poNumber}</strong><div className="sub">{p.createdBy}</div></> },
          { key: 'supplier', header: 'Supplier', render: (p) => p.supplier.name },
          { key: 'warehouse', header: 'Deliver to', render: (p) => p.warehouse.code },
          { key: 'orderDate', header: 'Ordered', render: (p) => fmtDate(p.orderDate) },
          { key: 'expectedDate', header: 'Expected', render: (p) => fmtDate(p.expectedDate) },
          { key: 'status', header: 'Status', render: (p) => <Badge value={p.status} /> },
          { key: 'paymentStatus', header: 'Payment', render: (p) => <Badge value={p.paymentStatus} /> },
          { key: 'total', header: 'Total', align: 'right', render: (p) => fmtMoney(p.total) },
        ]} />}
      </Card>
      {edit && <PoForm po={edit} onClose={() => setEdit(null)} onSaved={(po) => { setEdit(null); reload(); setOpenId(po.id) }} />}
      {openId && <PoDetail id={openId} onClose={() => setOpenId(null)} onChanged={reload} onEdit={(po) => { setOpenId(null); setEdit(po) }} />}
    </>
  )
}

function PoForm({ po, onClose, onSaved }) {
  const { data: suppliers } = useApi('/suppliers')
  const { data: warehouses } = useApi('/warehouses')
  const { data: products } = useApi('/products')
  const toast = useToast()
  const [f, setF] = useState({
    supplierId: po.supplier?.id ?? null, warehouseId: po.warehouse?.id ?? null, expectedDate: po.expectedDate || '',
    shippingCost: po.shippingCost ?? 0, notes: po.notes || '',
    items: po.items?.map((i) => ({ productId: i.product.id, quantity: i.quantity, unitPrice: i.unitCost })) || [{ productId: null, quantity: 1, unitPrice: '' }],
  })
  const setItem = (idx, patch) => setF({ ...f, items: f.items.map((it, i) => i === idx ? { ...it, ...patch } : it) })
  const supplierProducts = (products || []).filter((p) => !f.supplierId || !p.preferredSupplier || String(p.preferredSupplier.id) === String(f.supplierId))
  const subtotal = f.items.reduce((a, i) => a + (Number(i.quantity) || 0) * (Number(i.unitPrice) || 0), 0)

  const save = async () => {
    const body = { ...f, supplierId: Number(f.supplierId), warehouseId: Number(f.warehouseId), expectedDate: f.expectedDate || null,
      shippingCost: Number(f.shippingCost) || 0,
      items: f.items.filter((i) => i.productId).map((i) => ({ productId: Number(i.productId), quantity: Number(i.quantity), unitPrice: i.unitPrice === '' ? null : Number(i.unitPrice) })) }
    try { onSaved(await (po.id ? api.put(`/purchase-orders/${po.id}`, body) : api.post('/purchase-orders', body))); toast('Purchase order saved') } catch (e) { toast(e.message, 'error') }
  }

  return (
    <Modal wide title={po.id ? `Edit ${po.poNumber}` : 'New purchase order'} onClose={onClose}
           footer={<><span className="muted" style={{ marginRight: 'auto' }}>Subtotal (excl. tax): {fmtMoney(subtotal)}</span><button className="btn" onClick={onClose}>Cancel</button>
             <AsyncButton className="btn btn-primary" onClick={save} disabled={!f.supplierId || !f.warehouseId}>Save draft</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Supplier *"><Select value={f.supplierId} onChange={(v) => setF({ ...f, supplierId: v })} placeholder="Select…" options={(suppliers || []).map((s) => ({ value: s.id, label: s.name }))} /></Field>
        <Field label="Deliver to *"><Select value={f.warehouseId} onChange={(v) => setF({ ...f, warehouseId: v })} placeholder="Select…" options={(warehouses || []).map((w) => ({ value: w.id, label: w.name }))} /></Field>
        <Field label="Expected date" hint="Defaults to supplier lead time"><input type="date" value={f.expectedDate} onChange={(e) => setF({ ...f, expectedDate: e.target.value })} /></Field>
        <Field label="Freight / shipping cost"><input type="number" value={f.shippingCost} onChange={(e) => setF({ ...f, shippingCost: e.target.value })} /></Field>
      </div>
      <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>Items</h3>
      <div className="stack">
        {f.items.map((it, idx) => (
          <div key={idx} className="row">
            <Select style={{ flex: 3, minWidth: 200 }} value={it.productId} placeholder="Product…" options={supplierProducts.map((p) => ({ value: p.id, label: `${p.name} (${p.sku})` }))}
                    onChange={(v) => { const p = products.find((x) => String(x.id) === String(v)); setItem(idx, { productId: v, unitPrice: p?.costPrice ?? '' }) }} />
            <input style={{ width: 90 }} type="number" min="1" value={it.quantity} onChange={(e) => setItem(idx, { quantity: e.target.value })} aria-label="Quantity" />
            <input style={{ width: 120 }} type="number" step="0.01" placeholder="Unit cost" value={it.unitPrice} onChange={(e) => setItem(idx, { unitPrice: e.target.value })} aria-label="Unit cost" />
            <button className="btn-icon" onClick={() => setF({ ...f, items: f.items.filter((_, i) => i !== idx) })} aria-label="Remove">✕</button>
          </div>
        ))}
        <div><button className="btn btn-sm" onClick={() => setF({ ...f, items: [...f.items, { productId: null, quantity: 1, unitPrice: '' }] })}>+ Add item</button></div>
      </div>
      <Field label="Notes"><textarea value={f.notes} onChange={(e) => setF({ ...f, notes: e.target.value })} /></Field>
    </Modal>
  )
}

function PoDetail({ id, onClose, onChanged, onEdit }) {
  const { data: po, reload } = useApi(`/purchase-orders/${id}`)
  const { data: payments, reload: reloadPay } = useApi(`/purchase-orders/${id}/payments`)
  const { hasRole } = useAuth()
  const toast = useToast()
  const [receiving, setReceiving] = useState(null)
  const [paying, setPaying] = useState(null)
  if (!po) return <Modal title="Purchase order" onClose={onClose}><Loading /></Modal>

  const refresh = () => { reload(); reloadPay(); onChanged() }
  const act = async (fn, msg) => { try { await fn(); toast(msg); refresh() } catch (e) { toast(e.message, 'error') } }
  const due = Number(po.total) - Number(po.amountPaid)
  const canReceive = ['ORDERED', 'PARTIALLY_RECEIVED'].includes(po.status) && hasRole(...R.STOCK)

  return (
    <Modal wide title={`${po.poNumber} · ${po.supplier.name}`} onClose={onClose} footer={<>
      {po.status === 'DRAFT' && <button className="btn" onClick={() => onEdit(po)}>Edit</button>}
      {po.status === 'DRAFT' && hasRole(...R.FINANCE) && <AsyncButton className="btn btn-primary" onClick={() => act(() => api.post(`/purchase-orders/${id}/submit`), 'Sent to supplier')}>Submit to supplier</AsyncButton>}
      {canReceive && <button className="btn btn-primary" onClick={() => setReceiving(Object.fromEntries(po.items.map((i) => [i.id, i.quantity - i.receivedQuantity])))}>📥 Receive goods</button>}
      {due > 0 && !['DRAFT', 'CANCELLED'].includes(po.status) && hasRole(...R.FINANCE) && <button className="btn" onClick={() => setPaying({ method: 'BANK_TRANSFER', amount: due.toFixed(2), transactionReference: '' })}>Pay supplier</button>}
      {['DRAFT', 'ORDERED'].includes(po.status) && hasRole(...R.MANAGE) && <AsyncButton className="btn btn-danger" confirm="Cancel this purchase order?" onClick={() => act(() => api.post(`/purchase-orders/${id}/cancel`), 'Cancelled')}>Cancel PO</AsyncButton>}
      <button className="btn" onClick={onClose}>Close</button>
    </>}>
      <div className="grid grid-2">
        <dl className="dl">
          <dt>Status</dt><dd><Badge value={po.status} /> <Badge value={po.paymentStatus} /></dd>
          <dt>Deliver to</dt><dd>{po.warehouse.name}</dd>
          <dt>Ordered</dt><dd>{fmtDate(po.orderDate)}</dd>
          <dt>Expected</dt><dd>{fmtDate(po.expectedDate)}</dd>
          {po.receivedDate && <><dt>Received</dt><dd>{fmtDate(po.receivedDate)}</dd></>}
        </dl>
        <dl className="dl">
          <dt>Supplier</dt><dd>{po.supplier.name}</dd>
          <dt>Contact</dt><dd>{[po.supplier.contactName, po.supplier.phone].filter(Boolean).join(' · ')}</dd>
          <dt>Terms</dt><dd>Net {po.supplier.paymentTermsDays} days</dd>
          {po.notes && <><dt>Notes</dt><dd>{po.notes}</dd></>}
        </dl>
      </div>
      <Table rows={po.items} columns={[
        { key: 'p', header: 'Product', render: (i) => <>{i.product.name}<div className="sub">{i.product.sku}</div></> },
        { key: 'quantity', header: 'Ordered', align: 'right' },
        { key: 'receivedQuantity', header: 'Received', align: 'right', render: (i) => receiving
            ? <input type="number" min="0" max={i.quantity - i.receivedQuantity} style={{ width: 80 }} value={receiving[i.id]} onChange={(e) => setReceiving({ ...receiving, [i.id]: e.target.value })} aria-label="Receive quantity" />
            : <span className={i.receivedQuantity >= i.quantity ? 'tone-good' : ''}>{i.receivedQuantity}</span> },
        { key: 'unitCost', header: 'Unit cost', align: 'right', render: (i) => fmtMoney(i.unitCost) },
        { key: 'lineTotal', header: 'Total (incl. tax)', align: 'right', render: (i) => fmtMoney(i.lineTotal) },
      ]} />
      {receiving && (
        <div className="row" style={{ marginTop: 10 }}>
          <span className="muted">Enter quantities received now (partial deliveries are fine).</span><span className="spacer" />
          <button className="btn" onClick={() => setReceiving(null)}>Cancel</button>
          <AsyncButton className="btn btn-primary" onClick={() => act(async () => {
            await api.post(`/purchase-orders/${id}/receive`, { lines: Object.entries(receiving).map(([itemId, q]) => ({ itemId: Number(itemId), quantity: Number(q) || 0 })) })
            setReceiving(null)
          }, 'Goods received into stock')}>Confirm receipt</AsyncButton>
        </div>
      )}
      <div className="totals" style={{ marginTop: 10 }}>
        <div><span>Subtotal</span><span>{fmtMoney(po.subtotal)}</span></div>
        <div><span>Tax</span><span>{fmtMoney(po.taxAmount)}</span></div>
        <div><span>Freight</span><span>{fmtMoney(po.shippingCost)}</span></div>
        <div className="grand"><span>Total</span><span>{fmtMoney(po.total)}</span></div>
        <div><span>Paid</span><span>{fmtMoney(po.amountPaid)}</span></div>
        {due > 0 && po.status !== 'DRAFT' && <div className="tone-warn"><span>Balance due</span><span>{fmtMoney(due)}</span></div>}
      </div>
      {payments?.length > 0 && <>
        <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>Payments to supplier</h3>
        <Table rows={payments} columns={[
          { key: 'paymentNumber', header: 'Ref' },
          { key: 'method', header: 'Method', render: (p) => label(p.method) },
          { key: 'transactionReference', header: 'Txn ref' },
          { key: 'paidAt', header: 'When', render: (p) => fmtDateTime(p.paidAt) },
          { key: 'amount', header: 'Amount', align: 'right', render: (p) => fmtMoney(p.amount) },
        ]} />
      </>}
      {paying && (
        <Card title="Pay supplier">
          <div className="form-grid">
            <Field label="Method"><Select value={paying.method} onChange={(v) => setPaying({ ...paying, method: v })} options={enumOptions(METHODS)} /></Field>
            <Field label="Amount"><input type="number" step="0.01" value={paying.amount} onChange={(e) => setPaying({ ...paying, amount: e.target.value })} /></Field>
            <Field label="Transaction / cheque ref" span={2}><input value={paying.transactionReference} onChange={(e) => setPaying({ ...paying, transactionReference: e.target.value })} /></Field>
          </div>
          <div className="row" style={{ marginTop: 10 }}>
            <AsyncButton className="btn btn-primary" onClick={() => act(async () => { await api.post(`/purchase-orders/${id}/payments`, { ...paying, amount: Number(paying.amount) }); setPaying(null) }, 'Payment recorded')}>Record payment</AsyncButton>
            <button className="btn" onClick={() => setPaying(null)}>Cancel</button>
          </div>
        </Card>
      )}
    </Modal>
  )
}
