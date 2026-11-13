import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, Field, Loading, Modal, PageHeader, Table } from '../components/ui.jsx'

export default function Suppliers() {
  const { data, reload } = useApi('/suppliers')
  const [edit, setEdit] = useState(null)
  return (
    <>
      <PageHeader title="Suppliers" subtitle="Vendors, payment terms and lead times"
                  actions={<button className="btn btn-primary" onClick={() => setEdit({})}>+ New supplier</button>} />
      <Card pad={false}>
        {!data ? <Loading /> : <Table rows={data} onRowClick={setEdit} columns={[
          { key: 'name', header: 'Supplier', render: (s) => <><strong>{s.name}</strong><div className="sub">{s.contactName}</div></> },
          { key: 'phone', header: 'Phone' },
          { key: 'email', header: 'Email' },
          { key: 'paymentTermsDays', header: 'Terms', align: 'right', render: (s) => `Net ${s.paymentTermsDays}` },
          { key: 'leadTimeDays', header: 'Lead time', align: 'right', render: (s) => `${s.leadTimeDays} days` },
          { key: 'active', header: 'Status', render: (s) => <Badge value={s.active ? 'ACTIVE' : 'INACTIVE'} /> },
        ]} />}
      </Card>
      {edit && <SupplierForm s={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); reload() }} />}
    </>
  )
}

function SupplierForm({ s, onClose, onSaved }) {
  const [f, setF] = useState({
    name: s.name || '', contactName: s.contactName || '', email: s.email || '', phone: s.phone || '', address: s.address || '',
    taxId: s.taxId || '', paymentTermsDays: s.paymentTermsDays ?? 30, leadTimeDays: s.leadTimeDays ?? 7, active: s.active ?? true,
  })
  const toast = useToast()
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const save = async () => {
    const body = { ...f, paymentTermsDays: Number(f.paymentTermsDays), leadTimeDays: Number(f.leadTimeDays), email: f.email || null }
    try { await (s.id ? api.put(`/suppliers/${s.id}`, body) : api.post('/suppliers', body)); toast('Supplier saved'); onSaved() } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal title={s.id ? `Edit ${s.name}` : 'New supplier'} onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" onClick={save} disabled={!f.name}>Save</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Company name *" span={2}><input value={f.name} onChange={set('name')} /></Field>
        <Field label="Contact person"><input value={f.contactName} onChange={set('contactName')} /></Field>
        <Field label="Phone"><input value={f.phone} onChange={set('phone')} /></Field>
        <Field label="Email"><input type="email" value={f.email} onChange={set('email')} /></Field>
        <Field label="Tax ID / GSTIN"><input value={f.taxId} onChange={set('taxId')} /></Field>
        <Field label="Payment terms (days)"><input type="number" value={f.paymentTermsDays} onChange={set('paymentTermsDays')} /></Field>
        <Field label="Lead time (days)"><input type="number" value={f.leadTimeDays} onChange={set('leadTimeDays')} /></Field>
        <Field label="Address" span={2}><textarea value={f.address} onChange={set('address')} /></Field>
        <label className="check"><input type="checkbox" checked={f.active} onChange={set('active')} /> Active</label>
      </div>
    </Modal>
  )
}
