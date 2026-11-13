import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, Field, Loading, Modal, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { label } from '../format'

export default function Warehouses() {
  const { data, reload } = useApi('/warehouses')
  const { hasRole } = useAuth()
  const [edit, setEdit] = useState(null)
  const canEdit = hasRole(...R.MANAGE)
  return (
    <>
      <PageHeader title="Locations" subtitle="Stores and warehouses that hold stock"
                  actions={canEdit && <button className="btn btn-primary" onClick={() => setEdit({})}>+ New location</button>} />
      <Card pad={false}>
        {!data ? <Loading /> : <Table rows={data} onRowClick={canEdit ? setEdit : undefined} columns={[
          { key: 'code', header: 'Code', render: (w) => <strong>{w.code}</strong> },
          { key: 'name', header: 'Name' },
          { key: 'type', header: 'Type', render: (w) => label(w.type) },
          { key: 'address', header: 'Address' },
          { key: 'active', header: 'Status', render: (w) => <Badge value={w.active ? 'ACTIVE' : 'INACTIVE'} /> },
        ]} />}
      </Card>
      {edit && <WarehouseForm w={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); reload() }} />}
    </>
  )
}

function WarehouseForm({ w, onClose, onSaved }) {
  const [f, setF] = useState({ code: w.code || '', name: w.name || '', address: w.address || '', type: w.type || 'STORE', active: w.active ?? true })
  const toast = useToast()
  const save = async () => {
    try { await (w.id ? api.put(`/warehouses/${w.id}`, f) : api.post('/warehouses', f)); toast('Saved'); onSaved() } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal title={w.id ? 'Edit location' : 'New location'} onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" onClick={save}>Save</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Code *"><input value={f.code} onChange={(e) => setF({ ...f, code: e.target.value })} /></Field>
        <Field label="Type"><Select value={f.type} onChange={(v) => setF({ ...f, type: v })} options={enumOptions(['STORE', 'WAREHOUSE'])} /></Field>
        <Field label="Name *" span={2}><input value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
        <Field label="Address" span={2}><textarea value={f.address} onChange={(e) => setF({ ...f, address: e.target.value })} /></Field>
        <label className="check"><input type="checkbox" checked={f.active} onChange={(e) => setF({ ...f, active: e.target.checked })} /> Active</label>
      </div>
    </Modal>
  )
}
