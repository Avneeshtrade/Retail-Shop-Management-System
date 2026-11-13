import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useToast } from '../components/Toast.jsx'
import { Card, Field, Loading, Modal, PageHeader, Select } from '../components/ui.jsx'
import { fmtDateTime, fmtMoney, label } from '../format'

const METHODS = ['CASH', 'CARD', 'UPI', 'WALLET', 'BANK_TRANSFER', 'CHEQUE']
const round2 = (n) => Math.round(n * 100) / 100

export default function Pos() {
  const toast = useToast()
  const { data: warehouses } = useApi('/warehouses')
  const { data: categories } = useApi('/categories')
  const { data: products } = useApi('/products')
  const [warehouseId, setWarehouseId] = useState(null)
  const { data: stock, reload: reloadStock } = useApi(warehouseId ? '/inventory' : null, { warehouseId })
  const [q, setQ] = useState('')
  const [categoryId, setCategoryId] = useState(null)
  const [cart, setCart] = useState([])
  const [customer, setCustomer] = useState(null)
  const [discount, setDiscount] = useState(0)
  const [payments, setPayments] = useState([{ method: 'CASH', amount: '' }])
  const [receipt, setReceipt] = useState(null)
  const [busy, setBusy] = useState(false)
  const scanRef = useRef(null)

  useEffect(() => {
    if (!warehouseId && warehouses?.length) setWarehouseId(String((warehouses.find((w) => w.type === 'STORE') || warehouses[0]).id))
  }, [warehouses, warehouseId])

  const available = useMemo(() => {
    const m = {}
    stock?.forEach((s) => { m[s.product.id] = s.byWarehouse.reduce((a, w) => a + w.quantity, 0) })
    return m
  }, [stock])

  const shown = useMemo(() => (products || []).filter((p) => p.active
    && (!categoryId || String(p.category?.id) === categoryId)
    && (!q || p.name.toLowerCase().includes(q.toLowerCase()) || p.sku.toLowerCase().includes(q.toLowerCase()) || p.barcode === q)), [products, q, categoryId])

  const add = (p, qty = 1) => {
    const have = cart.find((l) => l.product.id === p.id)?.quantity || 0
    if (have + qty > (available[p.id] ?? 0)) return toast(`Only ${available[p.id] ?? 0} × ${p.name} in stock here`, 'error')
    setCart((c) => have ? c.map((l) => l.product.id === p.id ? { ...l, quantity: l.quantity + qty } : l) : [...c, { product: p, quantity: qty }])
  }
  const setQty = (id, qty) => setCart((c) => c.map((l) => l.product.id === id ? { ...l, quantity: Math.max(1, Math.min(qty, available[id] ?? qty)) } : l))
  const remove = (id) => setCart((c) => c.filter((l) => l.product.id !== id))

  const onScan = async (e) => {
    if (e.key !== 'Enter' || !q.trim()) return
    if (shown.length === 1) { add(shown[0]); setQ(''); return }
    try { add(await api.get(`/products/lookup/${encodeURIComponent(q.trim())}`)); setQ('') } catch { /* keep text as a filter */ }
  }

  const totals = useMemo(() => {
    let subtotal = 0, tax = 0
    cart.forEach((l) => {
      const net = Number(l.product.sellingPrice) * l.quantity
      subtotal += net
      tax += round2(net * Number(l.product.taxRate) / 100)
    })
    const d = Math.min(Number(discount) || 0, subtotal)
    return { subtotal: round2(subtotal), tax: round2(tax), discount: d, total: round2(subtotal + tax - d) }
  }, [cart, discount])

  const tendered = payments.reduce((a, p) => a + (Number(p.amount) || 0), 0)
  const effectiveTendered = payments.length === 1 && payments[0].amount === '' ? totals.total : tendered
  const change = round2(effectiveTendered - totals.total)
  const nonCashOver = payments.some((p) => p.method !== 'CASH') && payments.filter((p) => p.method !== 'CASH').reduce((a, p) => a + (Number(p.amount) || 0), 0) > totals.total

  const reset = () => { setCart([]); setCustomer(null); setDiscount(0); setPayments([{ method: 'CASH', amount: '' }]) }

  const checkout = async (payNow) => {
    setBusy(true)
    try {
      const pays = !payNow ? [] : payments.length === 1 && payments[0].amount === ''
        ? [{ method: payments[0].method, amount: totals.total }]
        : payments.filter((p) => Number(p.amount) > 0).map((p) => ({ method: p.method, amount: Number(p.amount) }))
      const order = await api.post('/sales-orders', {
        warehouseId: Number(warehouseId), customerId: customer?.id, channel: 'IN_STORE', discountAmount: totals.discount,
        items: cart.map((l) => ({ productId: l.product.id, quantity: l.quantity })), payments: pays,
      })
      setReceipt({ order, change: payNow && change > 0 ? change : 0 })
      reset()
      reloadStock()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  if (!products || !warehouses) return <Loading />

  return (
    <>
      <PageHeader title="Point of Sale" subtitle="Scan a barcode or click products to add them to the cart."
                  actions={<Field label="Selling from"><Select value={warehouseId} onChange={(v) => { setWarehouseId(v); setCart([]) }}
                    options={warehouses.filter((w) => w.active).map((w) => ({ value: String(w.id), label: `${w.name} (${w.code})` }))} /></Field>} />
      <div className="pos">
        <div>
          <div className="toolbar">
            <input ref={scanRef} type="search" placeholder="🔍 Search or scan barcode, then Enter" value={q}
                   onChange={(e) => setQ(e.target.value)} onKeyDown={onScan} autoFocus style={{ flex: 1 }} />
            <Select value={categoryId} onChange={setCategoryId} placeholder="All categories"
                    options={(categories || []).map((c) => ({ value: String(c.id), label: c.name }))} />
          </div>
          <div className="product-grid">
            {shown.map((p) => {
              const avail = available[p.id] ?? 0
              return (
                <button key={p.id} className="product-tile" disabled={avail <= 0} onClick={() => add(p)}>
                  <span className="name">{p.name}</span>
                  <span className="muted" style={{ fontSize: 12 }}>{p.sku} · {avail <= 0 ? 'Out of stock' : `${avail} in stock`}</span>
                  <span className="price">{fmtMoney(p.sellingPrice)}</span>
                </button>
              )
            })}
          </div>
        </div>

        <Card title={`Cart (${cart.reduce((a, l) => a + l.quantity, 0)})`} className="cart"
              actions={cart.length > 0 && <button className="btn btn-sm" onClick={reset}>Clear</button>}>
          <CustomerPicker value={customer} onChange={setCustomer} />
          <div style={{ margin: '10px 0' }}>
            {cart.length === 0 && <div className="empty">Cart is empty</div>}
            {cart.map((l) => (
              <div key={l.product.id} className="cart-line">
                <div>
                  <div style={{ fontWeight: 600 }}>{l.product.name}</div>
                  <div className="muted" style={{ fontSize: 12 }}>{fmtMoney(l.product.sellingPrice)} · tax {Number(l.product.taxRate)}%</div>
                </div>
                <div className="qty">
                  <button onClick={() => l.quantity === 1 ? remove(l.product.id) : setQty(l.product.id, l.quantity - 1)} aria-label="Decrease">−</button>
                  <input value={l.quantity} onChange={(e) => setQty(l.product.id, parseInt(e.target.value) || 1)} aria-label="Quantity" />
                  <button onClick={() => add(l.product)} aria-label="Increase">+</button>
                </div>
                <strong className="tabular">{fmtMoney(l.product.sellingPrice * l.quantity)}</strong>
              </div>
            ))}
          </div>
          <div className="totals">
            <div><span>Subtotal</span><span>{fmtMoney(totals.subtotal)}</span></div>
            <div><span>Tax</span><span>{fmtMoney(totals.tax)}</span></div>
            <div><span>Discount</span>
              <input type="number" min="0" step="1" value={discount} onChange={(e) => setDiscount(e.target.value)} style={{ width: 100, textAlign: 'right', minHeight: 28 }} aria-label="Discount" />
            </div>
            <div className="grand"><span>Total</span><span>{fmtMoney(totals.total)}</span></div>
          </div>

          <div className="stack" style={{ marginTop: 12 }}>
            {payments.map((p, i) => (
              <div key={i} className="row">
                <Select value={p.method} onChange={(v) => setPayments((ps) => ps.map((x, j) => j === i ? { ...x, method: v } : x))}
                        options={METHODS.map((m) => ({ value: m, label: label(m) }))} />
                <input type="number" min="0" step="0.01" placeholder={i === 0 && payments.length === 1 ? `${totals.total} (exact)` : 'Amount'}
                       value={p.amount} onChange={(e) => setPayments((ps) => ps.map((x, j) => j === i ? { ...x, amount: e.target.value } : x))} style={{ flex: 1 }} />
                {payments.length > 1 && <button className="btn-icon" onClick={() => setPayments((ps) => ps.filter((_, j) => j !== i))} aria-label="Remove payment">✕</button>}
              </div>
            ))}
            <div className="row">
              <button className="btn btn-sm" onClick={() => setPayments((ps) => [...ps, { method: 'CARD', amount: '' }])}>+ Split payment</button>
              <span className="spacer" />
              {change > 0 && <strong>Change due: {fmtMoney(change)}</strong>}
              {change < 0 && <span className="tone-warn">Remaining: {fmtMoney(-change)}</span>}
            </div>
            <button className="btn btn-primary btn-lg btn-block" disabled={!cart.length || busy || change < 0 || nonCashOver} onClick={() => checkout(true)}>
              {busy ? 'Processing…' : `Charge ${fmtMoney(totals.total)}`}
            </button>
            {customer && <button className="btn btn-block" disabled={!cart.length || busy} onClick={() => checkout(false)}>Save as unpaid (on account)</button>}
          </div>
        </Card>
      </div>
      {receipt && <Receipt {...receipt} onClose={() => { setReceipt(null); scanRef.current?.focus() }} />}
    </>
  )
}

function CustomerPicker({ value, onChange }) {
  const [q, setQ] = useState('')
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ name: '', phone: '' })
  const { data } = useApi(q.length >= 2 ? '/customers' : null, { q })
  const toast = useToast()

  if (value) return (
    <div className="row" style={{ background: 'var(--surface-2)', padding: '8px 10px', borderRadius: 8 }}>
      <div><strong>{value.name}</strong><div className="muted" style={{ fontSize: 12 }}>{value.phone} · {value.loyaltyPoints} pts</div></div>
      <span className="spacer" /><button className="btn btn-sm" onClick={() => onChange(null)}>Change</button>
    </div>
  )
  if (adding) return (
    <div className="stack">
      <input placeholder="Customer name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
      <input placeholder="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      <div className="row">
        <button className="btn btn-sm btn-primary" disabled={!form.name} onClick={async () => {
          try { onChange(await api.post('/customers', form)); setAdding(false) } catch (e) { toast(e.message, 'error') }
        }}>Add customer</button>
        <button className="btn btn-sm" onClick={() => setAdding(false)}>Cancel</button>
      </div>
    </div>
  )
  return (
    <div style={{ position: 'relative' }}>
      <div className="row">
        <input type="search" placeholder="Customer (name / phone) — optional" value={q} onChange={(e) => setQ(e.target.value)} style={{ flex: 1 }} />
        <button className="btn btn-sm" onClick={() => setAdding(true)}>+ New</button>
      </div>
      {q.length >= 2 && data?.length > 0 && (
        <div className="card" style={{ position: 'absolute', zIndex: 5, left: 0, right: 0, top: 40, maxHeight: 220, overflowY: 'auto' }}>
          {data.slice(0, 8).map((c) => (
            <button key={c.id} className="btn" style={{ width: '100%', border: 0, borderRadius: 0, justifyContent: 'space-between' }}
                    onClick={() => { onChange(c); setQ('') }}>
              <span>{c.name}</span><span className="muted">{c.phone}</span>
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function Receipt({ order, change, onClose }) {
  return (
    <Modal title="Sale complete" onClose={onClose}
           footer={<><button className="btn" onClick={() => window.print()}>🖨 Print receipt</button><button className="btn btn-primary" onClick={onClose} autoFocus>New sale</button></>}>
      <div className="receipt printable">
        <div style={{ textAlign: 'center', marginBottom: 8 }}><strong>RetailHub</strong><br />{order.warehouse.name}<br />{order.warehouse.address}</div>
        <div className="line"><span>{order.orderNumber}</span><span>{fmtDateTime(order.orderDate)}</span></div>
        <div className="line"><span>Cashier: {order.cashier}</span><span>{order.customer?.name || 'Walk-in'}</span></div>
        <hr />
        {order.items.map((i) => (
          <div key={i.id}>
            <div>{i.product.name}</div>
            <div className="line"><span>  {i.quantity} × {fmtMoney(i.unitPrice)}</span><span>{fmtMoney(i.lineTotal)}</span></div>
          </div>
        ))}
        <hr />
        <div className="line"><span>Subtotal</span><span>{fmtMoney(order.subtotal)}</span></div>
        <div className="line"><span>Tax</span><span>{fmtMoney(order.taxAmount)}</span></div>
        {Number(order.discountAmount) > 0 && <div className="line"><span>Discount</span><span>-{fmtMoney(order.discountAmount)}</span></div>}
        <div className="line"><strong>TOTAL</strong><strong>{fmtMoney(order.total)}</strong></div>
        <div className="line"><span>Paid</span><span>{fmtMoney(order.amountPaid)}</span></div>
        {change > 0 && <div className="line"><span>Change</span><span>{fmtMoney(change)}</span></div>}
        {Number(order.total) > Number(order.amountPaid) && <div className="line"><span>Balance due</span><span>{fmtMoney(order.total - order.amountPaid)}</span></div>}
        <div style={{ textAlign: 'center', marginTop: 10 }}>Thank you for shopping with us!</div>
      </div>
    </Modal>
  )
}
