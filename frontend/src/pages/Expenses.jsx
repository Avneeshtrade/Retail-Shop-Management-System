import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useToast } from '../components/Toast.jsx'
import DateRange from '../components/DateRange.jsx'
import BarList from '../components/BarList.jsx'
import { useChartColors } from '../hooks/useChartColors'
import { AsyncButton, Card, ErrorBox, Field, Loading, Modal, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { daysAgo, fmtDate, fmtMoney, label, today } from '../format'

const CATS = ['RENT', 'UTILITIES', 'SALARIES', 'MARKETING', 'MAINTENANCE', 'LOGISTICS', 'SUPPLIES', 'TAXES', 'OTHER']
const METHODS = ['BANK_TRANSFER', 'CASH', 'CARD', 'UPI', 'CHEQUE', 'WALLET']

export default function Expenses() {
  const [range, setRange] = useState({ from: daysAgo(89), to: today() })
  const { data, error, loading, reload } = useApi('/expenses', range)
  const [edit, setEdit] = useState(null)
  const c = useChartColors()
  const rows = data || []
  const total = rows.reduce((a, e) => a + Number(e.amount), 0)
  const byCat = Object.entries(rows.reduce((m, e) => ({ ...m, [e.category]: (m[e.category] || 0) + Number(e.amount) }), {}))
    .map(([name, value]) => ({ name: label(name), value })).sort((a, b) => b.value - a.value)

  return (
    <>
      <PageHeader title="Expenses" subtitle={`Operating costs · ${fmtMoney(total)} in period`}
                  actions={<button className="btn btn-primary" onClick={() => setEdit({})}>+ Record expense</button>} />
      <div className="toolbar"><DateRange {...range} onChange={setRange} /></div>
      <ErrorBox error={error} onRetry={reload} />
      <div className="grid grid-2-1">
        <Card pad={false}>
          {loading && !data ? <Loading /> : <Table rows={rows} onRowClick={setEdit} empty="No expenses recorded" columns={[
            { key: 'expenseDate', header: 'Date', render: (e) => fmtDate(e.expenseDate) },
            { key: 'category', header: 'Category', render: (e) => label(e.category) },
            { key: 'description', header: 'Description', render: (e) => <>{e.description}<div className="sub">{e.vendor}</div></> },
            { key: 'paymentMethod', header: 'Paid via', render: (e) => label(e.paymentMethod) || '—' },
            { key: 'amount', header: 'Amount', align: 'right', render: (e) => fmtMoney(e.amount) },
          ]} />}
        </Card>
        <Card title="By category">{byCat.length ? <BarList items={byCat} color={c.series[0]} format={fmtMoney} /> : <span className="muted">—</span>}</Card>
      </div>
      {edit && <ExpenseForm e={edit} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); reload() }} />}
    </>
  )
}

function ExpenseForm({ e, onClose, onSaved }) {
  const toast = useToast()
  const [f, setF] = useState({ category: e.category || 'OTHER', description: e.description || '', amount: e.amount ?? '', expenseDate: e.expenseDate || today(),
    paymentMethod: e.paymentMethod || 'BANK_TRANSFER', vendor: e.vendor || '' })
  const save = async () => {
    try { await (e.id ? api.put(`/expenses/${e.id}`, { ...f, amount: Number(f.amount) }) : api.post('/expenses', { ...f, amount: Number(f.amount) })); toast('Expense saved'); onSaved() }
    catch (err) { toast(err.message, 'error') }
  }
  return (
    <Modal title={e.id ? 'Edit expense' : 'Record expense'} onClose={onClose} footer={<>
      {e.id && <AsyncButton className="btn btn-danger" confirm="Delete this expense and its payment record?" onClick={async () => { await api.del(`/expenses/${e.id}`); toast('Deleted'); onSaved() }}>Delete</AsyncButton>}
      <span className="spacer" /><button className="btn" onClick={onClose}>Cancel</button>
      <AsyncButton className="btn btn-primary" onClick={save} disabled={!f.description || !f.amount}>Save</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Category"><Select value={f.category} onChange={(v) => setF({ ...f, category: v })} options={enumOptions(CATS)} /></Field>
        <Field label="Date"><input type="date" value={f.expenseDate} onChange={(ev) => setF({ ...f, expenseDate: ev.target.value })} /></Field>
        <Field label="Description *" span={2}><input value={f.description} onChange={(ev) => setF({ ...f, description: ev.target.value })} /></Field>
        <Field label="Amount *"><input type="number" step="0.01" value={f.amount} onChange={(ev) => setF({ ...f, amount: ev.target.value })} /></Field>
        <Field label="Paid via" hint="Creates a matching payment in the ledger"><Select value={f.paymentMethod} onChange={(v) => setF({ ...f, paymentMethod: v })} options={enumOptions(METHODS)} /></Field>
        <Field label="Vendor / payee" span={2}><input value={f.vendor} onChange={(ev) => setF({ ...f, vendor: ev.target.value })} /></Field>
      </div>
    </Modal>
  )
}
