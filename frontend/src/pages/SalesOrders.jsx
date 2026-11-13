import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import DateRange from '../components/DateRange.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Field, Loading, Modal, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { daysAgo, fmtDateTime, fmtMoney, label, today } from '../format'

const STATUSES = ['PENDING', 'COMPLETED', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'REFUNDED']
const METHODS = ['CASH', 'CARD', 'UPI', 'WALLET', 'BANK_TRANSFER', 'CHEQUE']

export default function SalesOrders() {
  const [range, setRange] = useState({ from: daysAgo(6), to: today() })
  const [status, setStatus] = useState(null)
  const [q, setQ] = useState('')
  const { data, error, loading, reload } = useApi('/sales-orders', { ...range, status })
  const [openId, setOpenId] = useState(null)

  const rows = (data || []).filter((o) => !q || o.orderNumber.toLowerCase().includes(q.toLowerCase()) || o.customer?.name?.toLowerCase().includes(q.toLowerCase()))
  const total = rows.filter((o) => !['CANCELLED'].includes(o.status)).reduce((a, o) => a + Number(o.total), 0)

  return (
    <>
      <PageHeader title="Sales Orders" subtitle={`${rows.length} orders · ${fmtMoney(total)}`} />
      <div className="toolbar">
        <DateRange {...range} onChange={setRange} />
        <Select value={status} onChange={setStatus} placeholder="All statuses" options={enumOptions(STATUSES)} />
        <input type="search" placeholder="Order # or customer" value={q} onChange={(e) => setQ(e.target.value)} />
      </div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : (
          <Table onRowClick={(o) => setOpenId(o.id)} rows={rows} empty="No orders in this period" columns={[
            { key: 'orderNumber', header: 'Order', render: (o) => <><strong>{o.orderNumber}</strong><div className="sub">{label(o.channel)} · {o.cashier}</div></> },
            { key: 'orderDate', header: 'Date', render: (o) => fmtDateTime(o.orderDate) },
            { key: 'customer', header: 'Customer', render: (o) => o.customer?.name || <span className="muted">Walk-in</span> },
            { key: 'items', header: 'Items', align: 'right', render: (o) => o.items.reduce((a, i) => a + i.quantity, 0) },
            { key: 'status', header: 'Status', render: (o) => <Badge value={o.status} /> },
            { key: 'paymentStatus', header: 'Payment', render: (o) => <Badge value={o.paymentStatus} /> },
            { key: 'total', header: 'Total', align: 'right', render: (o) => fmtMoney(o.total) },
          ]} />
        )}
      </Card>
      {openId && <OrderDetail id={openId} onClose={() => setOpenId(null)} onChanged={reload} />}
    </>
  )
}

export function OrderDetail({ id, onClose, onChanged }) {
  const { data: o, reload } = useApi(`/sales-orders/${id}`)
  const { data: payments, reload: reloadPayments } = useApi(`/sales-orders/${id}/payments`)
  const { hasRole } = useAuth()
  const toast = useToast()
  const [mode, setMode] = useState(null)
  const [form, setForm] = useState({})

  if (!o) return <Modal title="Order" onClose={onClose}><Loading /></Modal>
  const due = Number(o.total) - Number(o.amountPaid)
  const refundable = Number(o.amountPaid) - Number(o.amountRefunded)
  const refresh = () => { reload(); reloadPayments(); onChanged() }
  const run = async (fn, msg) => {
    try { await fn(); toast(msg); setMode(null); refresh() } catch (e) { toast(e.message, 'error') }
  }

  return (
    <Modal wide title={`Order ${o.orderNumber}`} onClose={onClose} footer={<>
      {due > 0 && !['CANCELLED', 'REFUNDED'].includes(o.status) && hasRole(...R.SELL, 'ACCOUNTANT') &&
        <button className="btn btn-primary" onClick={() => { setMode('pay'); setForm({ method: 'CASH', amount: due.toFixed(2) }) }}>Record payment</button>}
      {['PENDING', 'COMPLETED'].includes(o.status) && hasRole(...R.STOCK) && o.channel !== 'IN_STORE' &&
        <button className="btn" onClick={() => { setMode('ship'); setForm({ carrier: '', trackingNumber: '', shippingCost: 0 }) }}>🚚 Create shipment</button>}
      {refundable > 0 && o.status !== 'REFUNDED' && hasRole(...R.MANAGE) &&
        <button className="btn btn-danger" onClick={() => { setMode('refund'); setForm({ method: 'CASH', amount: refundable.toFixed(2), restock: true, reason: '' }) }}>Refund</button>}
      {['PENDING', 'COMPLETED'].includes(o.status) && refundable <= 0 && hasRole(...R.MANAGE) &&
        <AsyncButton className="btn btn-danger" confirm="Cancel this order and return stock?"
                     onClick={() => run(() => api.post(`/sales-orders/${id}/cancel`, { reason: 'Cancelled by staff' }), 'Order cancelled')}>Cancel order</AsyncButton>}
      <button className="btn" onClick={onClose}>Close</button>
    </>}>
      <div className="grid grid-2">
        <dl className="dl">
          <dt>Status</dt><dd><Badge value={o.status} /> <Badge value={o.paymentStatus} /></dd>
          <dt>Date</dt><dd>{fmtDateTime(o.orderDate)}</dd>
          <dt>Channel</dt><dd>{label(o.channel)}</dd>
          <dt>Location</dt><dd>{o.warehouse.name}</dd>
          <dt>Cashier</dt><dd>{o.cashier}</dd>
        </dl>
        <dl className="dl">
          <dt>Customer</dt><dd>{o.customer?.name || 'Walk-in'}</dd>
          {o.customer?.phone && <><dt>Phone</dt><dd>{o.customer.phone}</dd></>}
          {o.shippingAddress && <><dt>Ship to</dt><dd>{o.shippingAddress}</dd></>}
          {o.notes && <><dt>Notes</dt><dd style={{ whiteSpace: 'pre-wrap' }}>{o.notes}</dd></>}
        </dl>
      </div>

      <Table rows={o.items} columns={[
        { key: 'p', header: 'Product', render: (i) => <>{i.product.name}<div className="sub">{i.product.sku}</div></> },
        { key: 'quantity', header: 'Qty', align: 'right' },
        { key: 'unitPrice', header: 'Price', align: 'right', render: (i) => fmtMoney(i.unitPrice) },
        { key: 'taxAmount', header: 'Tax', align: 'right', render: (i) => fmtMoney(i.taxAmount) },
        { key: 'lineTotal', header: 'Total', align: 'right', render: (i) => fmtMoney(i.lineTotal) },
      ]} />
      <div className="totals" style={{ marginTop: 10 }}>
        <div><span>Subtotal</span><span>{fmtMoney(o.subtotal)}</span></div>
        <div><span>Tax</span><span>{fmtMoney(o.taxAmount)}</span></div>
        {Number(o.discountAmount) > 0 && <div><span>Discount</span><span>−{fmtMoney(o.discountAmount)}</span></div>}
        {Number(o.shippingCharge) > 0 && <div><span>Shipping</span><span>{fmtMoney(o.shippingCharge)}</span></div>}
        <div className="grand"><span>Total</span><span>{fmtMoney(o.total)}</span></div>
        <div><span>Paid</span><span>{fmtMoney(o.amountPaid)}</span></div>
        {Number(o.amountRefunded) > 0 && <div><span>Refunded</span><span>−{fmtMoney(o.amountRefunded)}</span></div>}
        {due > 0 && <div className="tone-warn"><span>Balance due</span><span>{fmtMoney(due)}</span></div>}
      </div>

      <h3 style={{ margin: '16px 0 8px', fontSize: 14 }}>Payments</h3>
      <Table rows={payments || []} empty="No payments yet" columns={[
        { key: 'paymentNumber', header: 'Ref' },
        { key: 'type', header: 'Type', render: (p) => <Badge value={p.type === 'CUSTOMER_REFUND' ? 'REFUNDED' : 'PAID'} tone={p.type === 'CUSTOMER_REFUND' ? 'bad' : 'good'} /> },
        { key: 'method', header: 'Method', render: (p) => label(p.method) },
        { key: 'paidAt', header: 'When', render: (p) => fmtDateTime(p.paidAt) },
        { key: 'amount', header: 'Amount', align: 'right', render: (p) => fmtMoney(p.amount) },
      ]} />

      {mode && (
        <Card title={{ pay: 'Record payment', refund: 'Issue refund', ship: 'Create outbound shipment' }[mode]} className="stack" >
          <div className="form-grid">
            {mode !== 'ship' && <Field label="Method"><Select value={form.method} onChange={(v) => setForm({ ...form, method: v })} options={enumOptions(METHODS)} /></Field>}
            {mode !== 'ship' && <Field label="Amount"><input type="number" step="0.01" value={form.amount} onChange={(e) => setForm({ ...form, amount: e.target.value })} /></Field>}
            {mode === 'pay' && <Field label="Transaction reference" span={2}><input value={form.transactionReference || ''} onChange={(e) => setForm({ ...form, transactionReference: e.target.value })} /></Field>}
            {mode === 'refund' && <>
              <Field label="Reason" span={2}><input value={form.reason} onChange={(e) => setForm({ ...form, reason: e.target.value })} required /></Field>
              <label className="check span-2"><input type="checkbox" checked={form.restock} onChange={(e) => setForm({ ...form, restock: e.target.checked })} /> Return all items to stock</label>
            </>}
            {mode === 'ship' && <>
              <Field label="Carrier"><input value={form.carrier} onChange={(e) => setForm({ ...form, carrier: e.target.value })} /></Field>
              <Field label="Tracking number"><input value={form.trackingNumber} onChange={(e) => setForm({ ...form, trackingNumber: e.target.value })} /></Field>
              <Field label="Shipping cost"><input type="number" value={form.shippingCost} onChange={(e) => setForm({ ...form, shippingCost: e.target.value })} /></Field>
              <Field label="Estimated delivery"><input type="date" value={form.estimatedDelivery || ''} onChange={(e) => setForm({ ...form, estimatedDelivery: e.target.value })} /></Field>
            </>}
          </div>
          <div className="row" style={{ marginTop: 12 }}>
            <AsyncButton className="btn btn-primary" onClick={() => {
              if (mode === 'pay') return run(() => api.post(`/sales-orders/${id}/payments`, { ...form, amount: Number(form.amount) }), 'Payment recorded')
              if (mode === 'refund') return run(() => api.post(`/sales-orders/${id}/refund`, { ...form, amount: Number(form.amount) }), 'Refund issued')
              return run(() => api.post('/shipments', { type: 'OUTBOUND', salesOrderId: id, ...form, shippingCost: Number(form.shippingCost) || 0, estimatedDelivery: form.estimatedDelivery || null }), 'Shipment created')
            }}>Confirm</AsyncButton>
            <button className="btn" onClick={() => setMode(null)}>Cancel</button>
          </div>
        </Card>
      )}
    </Modal>
  )
}

