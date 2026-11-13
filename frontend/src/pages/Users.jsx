import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, Field, Loading, Modal, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { label } from '../format'

const ROLES = ['ADMIN', 'MANAGER', 'CASHIER', 'WAREHOUSE', 'ACCOUNTANT']
const ROLE_HELP = {
  ADMIN: 'Everything, including user management',
  MANAGER: 'Runs the shop: sales, stock, purchasing, refunds, reports',
  CASHIER: 'Point of sale, customers, taking payments',
  WAREHOUSE: 'Stock adjustments, receiving goods, shipments & transfers',
  ACCOUNTANT: 'Payments, expenses, supplier invoices, financial reports',
}

export default function Users() {
  const { data, reload } = useApi('/users')
  const [edit, setEdit] = useState(null)
  return (
    <>
      <PageHeader title="Users & roles" subtitle="Staff accounts and what each role can do"
                  actions={<button className="btn btn-primary" onClick={() => setEdit({})}>+ New user</button>} />
      <Card pad={false}>
        {!data ? <Loading /> : <Table rows={data} onRowClick={setEdit} columns={[
          { key: 'fullName', header: 'Name', render: (u) => <><strong>{u.fullName}</strong><div className="sub">{u.username}</div></> },
          { key: 'email', header: 'Email' },
          { key: 'role', header: 'Role', render: (u) => <>{label(u.role)}<div className="sub">{ROLE_HELP[u.role]}</div></> },
          { key: 'active', header: 'Status', render: (u) => <Badge value={u.active ? 'ACTIVE' : 'INACTIVE'} /> },
        ]} />}
      </Card>
      {edit && <UserForm u={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); reload() }} />}
    </>
  )
}

function UserForm({ u, onClose, onSaved }) {
  const toast = useToast()
  const [f, setF] = useState({ username: u.username || '', fullName: u.fullName || '', email: u.email || '', phone: u.phone || '', role: u.role || 'CASHIER', active: u.active ?? true, password: '' })
  const save = async () => {
    const body = { ...f, email: f.email || null, password: f.password || null }
    try { await (u.id ? api.put(`/users/${u.id}`, body) : api.post('/users', body)); toast('User saved'); onSaved() } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal title={u.id ? `Edit ${u.fullName}` : 'New user'} onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" onClick={save}>Save</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Username *"><input value={f.username} disabled={!!u.id} onChange={(e) => setF({ ...f, username: e.target.value })} /></Field>
        <Field label={u.id ? 'New password' : 'Password *'} hint={u.id ? 'Leave blank to keep current' : 'At least 6 characters'}>
          <input type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} autoComplete="new-password" /></Field>
        <Field label="Full name *" span={2}><input value={f.fullName} onChange={(e) => setF({ ...f, fullName: e.target.value })} /></Field>
        <Field label="Email"><input type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} /></Field>
        <Field label="Phone"><input value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value })} /></Field>
        <Field label="Role" span={2} hint={ROLE_HELP[f.role]}><Select value={f.role} onChange={(v) => setF({ ...f, role: v })} options={enumOptions(ROLES)} /></Field>
        <label className="check"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Active</label>
      </div>
    </Modal>
  )
}
