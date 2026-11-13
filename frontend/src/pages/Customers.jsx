import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Field, Kpi, Loading, Modal, PageHeader, Table } from '../components/ui.jsx'
import { fmtDateTime, fmtMoney, fmtNum } from '../format'
import { OrderDetail } from './SalesOrders.jsx'

export default function Customers() {
  const [q, setQ] = useState('')
  const { data, error, loading, reload } = useApi('/customers', { q })
  const { hasRole } = useAuth()
  const [edit, setEdit] = useState(null)
  const [view, setView] = useState(null)

  return (
    <>
      <PageHeader title="Customers" subtitle="Profiles, loyalty points and purchase history"
                  actions={hasRole(...R.SELL) && <button className="btn btn-primary" onClick={() => setEdit({})}>+ New customer</button>} />
      <div className="toolbar"><input type="search" placeholder="Search name, phone or email" value={q} onChange={(e) => setQ(e.target.value)} /></div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : (
          <Table rows={data} onRowClick={setView} columns={[
            { key: 'name', header: 'Name', render: (c) => <><strong>{c.name}</strong><div className="sub">{c.email}</div></> },
            { key: 'phone', header: 'Phone' },
            { key: 'orderCount', header: 'Orders', align: 'right' },
            { key: 'totalSpent', header: 'Lifetime spend', align: 'right', render: (c) => fmtMoney(c.totalSpent) },
            { key: 'loyaltyPoints', header: 'Points', align: 'right', render: (c) => fmtNum(c.loyaltyPoints) },
          ]} />
        )}
      </Card>
      {edit && <CustomerForm customer={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); reload() }} />}
      {view && <CustomerView customer={view} onClose={() => setView(null)} onEdit={hasRole(...R.SELL) ? () => { setEdit(view); setView(null) } : null} />}
    </>
  )
}

function CustomerForm({ customer, onClose, onSaved }) {
  const [f, setF] = useState({ name: customer.name || '', phone: customer.phone || '', email: customer.email || '', address: customer.address || '' })
  const toast = useToast()
  const save = async () => {
    try {
      await (customer.id ? api.put(`/customers/${customer.id}`, f) : api.post('/customers', f))
      toast('Customer saved')
      onSaved()
    } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal title={customer.id ? 'Edit customer' : 'New customer'} onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" onClick={save} disabled={!f.name}>Save</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Name *" span={2}><input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Phone"><input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
        <Field label="Email"><input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Address" span={2}><textarea value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} /></Field>
      </div>
    </Modal>
  )
}

function CustomerView({ customer: c, onClose, onEdit }) {
  const { data: orders } = useApi('/sales-orders', { customerId: c.id })
  const [order, setOrder] = useState(null)
  return (
    <Modal wide title={c.name} onClose={onClose} footer={<>{onEdit && <button className="btn" onClick={onEdit}>Edit</button>}<button className="btn" onClick={onClose}>Close</button></>}>
      <div className="kpis">
        <Kpi label="Lifetime spend" value={fmtMoney(c.totalSpent)} />
        <Kpi label="Orders" value={c.orderCount} />
        <Kpi label="Loyalty points" value={fmtNum(c.loyaltyPoints)} />
      </div>
      <p className="muted">{[c.phone, c.email, c.address].filter(Boolean).join(' · ')}</p>
      {!orders ? <Loading /> : <Table rows={orders} onRowClick={(o) => setOrder(o.id)} empty="No orders yet" columns={[
        { key: 'orderNumber', header: 'Order' },
        { key: 'orderDate', header: 'Date', render: (o) => fmtDateTime(o.orderDate) },
        { key: 'status', header: 'Status', render: (o) => <Badge value={o.status} /> },
        { key: 'total', header: 'Total', align: 'right', render: (o) => fmtMoney(o.total) },
      ]} />}
      {order && <OrderDetail id={order} onClose={() => setOrder(null)} onChanged={() => {}} />}
    </Modal>
  )
}
