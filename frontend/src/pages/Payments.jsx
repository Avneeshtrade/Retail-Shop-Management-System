import { useState } from 'react'
import { useApi } from '../hooks/useApi'
import DateRange from '../components/DateRange.jsx'
import { Badge, Card, ErrorBox, Kpi, Loading, PageHeader, Select, Table, enumOptions } from '../components/ui.jsx'
import { daysAgo, fmtDateTime, fmtMoney, label, today } from '../format'

const TYPES = ['CUSTOMER_PAYMENT', 'CUSTOMER_REFUND', 'SUPPLIER_PAYMENT', 'EXPENSE_PAYMENT']
const METHODS = ['CASH', 'CARD', 'UPI', 'WALLET', 'BANK_TRANSFER', 'CHEQUE']
const isIn = (p) => p.type === 'CUSTOMER_PAYMENT'

function exportCsv(rows) {
  const head = ['Payment #', 'Date', 'Type', 'Method', 'Party', 'Order/PO', 'Txn ref', 'Amount', 'Direction']
  const lines = rows.map((p) => [p.paymentNumber, p.paidAt, p.type, p.method, p.partyName, p.salesOrder?.orderNumber || p.purchaseOrder?.poNumber || '',
    p.transactionReference || '', p.amount, isIn(p) ? 'IN' : 'OUT'])
  const csv = [head, ...lines].map((r) => r.map((v) => `"${String(v ?? '').replaceAll('"', '""')}"`).join(',')).join('\n')
  const a = document.createElement('a')
  a.href = URL.createObjectURL(new Blob([csv], { type: 'text/csv' }))
  a.download = `payments-${today()}.csv`
  a.click()
}

export default function Payments() {
  const [range, setRange] = useState({ from: daysAgo(29), to: today() })
  const [type, setType] = useState(null)
  const [method, setMethod] = useState(null)
  const { data, error, loading, reload } = useApi('/payments', { ...range, type, method })
  const rows = data || []
  const inflow = rows.filter(isIn).reduce((a, p) => a + Number(p.amount), 0)
  const outflow = rows.filter((p) => !isIn(p)).reduce((a, p) => a + Number(p.amount), 0)

  return (
    <>
      <PageHeader title="Payments" subtitle="Every receipt, refund, supplier payment and expense in one ledger"
                  actions={<button className="btn" disabled={!rows.length} onClick={() => exportCsv(rows)}>⬇ Export CSV</button>} />
      <div className="toolbar">
        <DateRange {...range} onChange={setRange} />
        <Select value={type} onChange={setType} placeholder="All types" options={enumOptions(TYPES)} />
        <Select value={method} onChange={setMethod} placeholder="All methods" options={enumOptions(METHODS)} />
      </div>
      <div className="kpis">
        <Kpi label="Money in" value={fmtMoney(inflow)} sub={`${rows.filter(isIn).length} receipts`} />
        <Kpi label="Money out" value={fmtMoney(outflow)} sub={`${rows.filter((p) => !isIn(p)).length} payments`} />
        <Kpi label="Net cash flow" value={fmtMoney(inflow - outflow)} tone={inflow - outflow >= 0 ? 'good' : 'bad'} sub={inflow - outflow >= 0 ? '▲ positive' : '▼ negative'} />
      </div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : <Table rows={rows} empty="No payments in this period" columns={[
          { key: 'paymentNumber', header: 'Payment', render: (p) => <><strong>{p.paymentNumber}</strong><div className="sub">{p.receivedBy}</div></> },
          { key: 'paidAt', header: 'Date', render: (p) => fmtDateTime(p.paidAt) },
          { key: 'type', header: 'Type', render: (p) => <Badge value={p.type} tone={isIn(p) ? 'good' : p.type === 'CUSTOMER_REFUND' ? 'bad' : 'info'} /> },
          { key: 'method', header: 'Method', render: (p) => label(p.method) },
          { key: 'partyName', header: 'Party', render: (p) => <>{p.partyName || '—'}<div className="sub">{p.salesOrder?.orderNumber || p.purchaseOrder?.poNumber || p.notes}</div></> },
          { key: 'transactionReference', header: 'Txn ref', render: (p) => <span className="sub">{p.transactionReference || '—'}</span> },
          { key: 'amount', header: 'Amount', align: 'right', render: (p) => <strong className={isIn(p) ? 'tone-good' : ''}>{isIn(p) ? '+' : '−'}{fmtMoney(p.amount)}</strong> },
        ]} />}
      </Card>
    </>
  )
}
