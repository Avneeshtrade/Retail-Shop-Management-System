import { useState } from 'react'
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { useChartColors } from '../hooks/useChartColors'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import DateRange from '../components/DateRange.jsx'
import ChartTooltip from '../components/ChartTooltip.jsx'
import BarList from '../components/BarList.jsx'
import Legend from '../components/Legend.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Kpi, Loading, PageHeader, Table, Tabs } from '../components/ui.jsx'
import { daysAgo, fmtDay, fmtMoney, fmtMoneyShort, fmtNum, fmtPct, label, today } from '../format'

export default function Reports() {
  const { hasRole } = useAuth()
  const fin = hasRole(...R.FINANCE)
  const tabs = [
    ...(fin ? [{ value: 'sales', label: 'Sales' }, { value: 'pnl', label: 'Profit & Loss' }] : []),
    { value: 'inventory', label: 'Inventory' },
    ...(hasRole(...R.MANAGE, 'WAREHOUSE') ? [{ value: 'logistics', label: 'Logistics' }] : []),
    ...(fin ? [{ value: 'cash', label: 'Cash flow' }] : []),
  ]
  const [tab, setTab] = useState(tabs[0].value)
  const [range, setRange] = useState({ from: daysAgo(29), to: today() })

  return (
    <>
      <PageHeader title="Analytics" subtitle="Sales performance, profitability, stock health, logistics and cash flow" />
      <Tabs tabs={tabs} value={tab} onChange={setTab} />
      {tab !== 'inventory' && <div className="toolbar"><DateRange {...range} onChange={setRange} /></div>}
      {tab === 'sales' && <SalesReport range={range} />}
      {tab === 'pnl' && <PnlReport range={range} />}
      {tab === 'inventory' && <InventoryReport />}
      {tab === 'logistics' && <LogisticsReport range={range} />}
      {tab === 'cash' && <CashReport range={range} />}
    </>
  )
}

const axisProps = (c) => ({ tick: { fill: c.muted, fontSize: 12 }, tickLine: false })

function useReport(path, params) {
  const r = useApi(path, params)
  const node = r.error ? <ErrorBox error={r.error} onRetry={r.reload} /> : (!r.data ? <Loading /> : null)
  return { ...r, node }
}

function SalesReport({ range }) {
  const c = useChartColors()
  const { data: d, node } = useReport('/analytics/sales', range)
  if (node) return node
  return (
    <>
      <div className="kpis">
        <Kpi label="Net sales" value={fmtMoney(d.netSales)} sub={`Gross ${fmtMoney(d.grossSales)} − discounts ${fmtMoney(d.discounts)}`} />
        <Kpi label="Orders" value={fmtNum(d.orders)} sub={`${fmtNum(d.itemsSold)} items sold`} />
        <Kpi label="Avg. order value" value={fmtMoney(d.avgOrderValue)} />
        <Kpi label="Gross profit" value={fmtMoney(d.grossProfit)} sub={`${fmtPct(d.marginPct)} margin`} tone={d.grossProfit >= 0 ? 'good' : 'bad'} />
        <Kpi label="Tax collected" value={fmtMoney(d.tax)} />
        <Kpi label="Refunds" value={fmtMoney(d.refunds)} tone={d.refunds > 0 ? 'warn' : undefined} />
      </div>
      <Card title="Daily net sales">
        <div style={{ height: 280 }}>
          <ResponsiveContainer>
            <LineChart data={d.daily} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
              <CartesianGrid stroke={c.grid} vertical={false} />
              <XAxis dataKey="date" tickFormatter={fmtDay} {...axisProps(c)} axisLine={{ stroke: c.axis }} minTickGap={24} />
              <YAxis tickFormatter={fmtMoneyShort} {...axisProps(c)} axisLine={false} width={64} />
              <Tooltip content={<ChartTooltip labelFormatter={fmtDay} valueFormatter={(v, p) => p.dataKey === 'orders' ? `${v} orders` : fmtMoney(v)} />} cursor={{ stroke: c.axis }} />
              <Line type="monotone" dataKey="revenue" name="Net sales" stroke={c.series[0]} strokeWidth={2} dot={false} activeDot={{ r: 5, stroke: c.surface, strokeWidth: 2 }} />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </Card>
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <Card title="Sales by hour of day">
          <SingleBars data={d.byHour.filter((h) => h.count > 0 || (h.name >= '08' && h.name <= '21'))} c={c} />
        </Card>
        <Card title="Sales by day of week">
          <SingleBars data={d.byWeekday} c={c} />
        </Card>
      </div>
      <div className="grid grid-3">
        <Card title="By category"><BarList items={d.byCategory} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${fmtNum(i.count)} units`} /></Card>
        <Card title="By channel"><BarList items={d.byChannel.map((x) => ({ ...x, name: label(x.name) }))} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${i.count} orders`} /></Card>
        <Card title="By staff member"><BarList items={d.byCashier} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${i.count} orders`} /></Card>
      </div>
      <div className="grid grid-2-1">
        <Card title="Product performance" pad={false}>
          <Table rowKey="productId" rows={d.topProducts} columns={[
            { key: 'name', header: 'Product', render: (p) => <>{p.name}<div className="sub">{p.sku}</div></> },
            { key: 'quantity', header: 'Units', align: 'right', render: (p) => fmtNum(p.quantity) },
            { key: 'revenue', header: 'Revenue', align: 'right', render: (p) => fmtMoney(p.revenue) },
            { key: 'profit', header: 'Profit', align: 'right', render: (p) => fmtMoney(p.profit) },
            { key: 'marginPct', header: 'Margin', align: 'right', render: (p) => fmtPct(p.marginPct) },
          ]} />
        </Card>
        <Card title="Top customers"><BarList items={d.topCustomers} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${i.count} orders`} /></Card>
      </div>
    </>
  )
}

function SingleBars({ data, c, format = fmtMoneyShort }) {
  return (
    <div style={{ height: 220 }}>
      <ResponsiveContainer>
        <BarChart data={data} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
          <CartesianGrid stroke={c.grid} vertical={false} />
          <XAxis dataKey="name" {...axisProps(c)} axisLine={{ stroke: c.axis }} interval="preserveStartEnd" />
          <YAxis tickFormatter={format} {...axisProps(c)} axisLine={false} width={60} />
          <Tooltip content={<ChartTooltip valueFormatter={(v) => fmtMoney(v)} />} cursor={{ fill: c.grid, opacity: 0.5 }} />
          <Bar dataKey="value" name="Net sales" fill={c.series[0]} radius={[4, 4, 0, 0]} maxBarSize={28} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  )
}

function PnlReport({ range }) {
  const c = useChartColors()
  const { data: d, node } = useReport('/analytics/profit-loss', range)
  if (node) return node
  const series = [
    { key: 'revenue', label: 'Revenue', color: c.series[0] },
    { key: 'cogs', label: 'Cost of goods', color: c.series[1] },
    { key: 'expenses', label: 'Operating costs', color: c.series[2] },
  ]
  const Row = ({ l, v, strong, indent, neg }) => (
    <tr><td style={{ paddingLeft: indent ? 28 : 10, fontWeight: strong ? 600 : 400 }}>{l}</td>
      <td className="num" style={{ fontWeight: strong ? 600 : 400 }}>{neg ? `(${fmtMoney(v)})` : fmtMoney(v)}</td></tr>
  )
  return (
    <>
      <div className="kpis">
        <Kpi label="Revenue" value={fmtMoney(d.revenue)} />
        <Kpi label="Gross profit" value={fmtMoney(d.grossProfit)} sub={`${fmtPct(d.grossMarginPct)} gross margin`} />
        <Kpi label="Operating costs" value={fmtMoney(Number(d.operatingExpenses) + Number(d.shippingCosts))} sub={`incl. ${fmtMoney(d.shippingCosts)} shipping`} />
        <Kpi label="Net profit" value={fmtMoney(d.netProfit)} sub={`${fmtPct(d.netMarginPct)} net margin`} tone={d.netProfit >= 0 ? 'good' : 'bad'} />
        <Kpi label="Stock purchased" value={fmtMoney(d.purchases)} sub="Purchase orders placed" />
      </div>
      <div className="grid grid-2-1">
        <Card title="Monthly revenue vs costs">
          <Legend items={series.map((s) => ({ label: s.label, color: s.color }))} />
          <div style={{ height: 280 }}>
            <ResponsiveContainer>
              <BarChart data={d.monthly} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barGap={2}>
                <CartesianGrid stroke={c.grid} vertical={false} />
                <XAxis dataKey="month" {...axisProps(c)} axisLine={{ stroke: c.axis }} />
                <YAxis tickFormatter={fmtMoneyShort} {...axisProps(c)} axisLine={false} width={64} />
                <Tooltip content={<ChartTooltip valueFormatter={fmtMoney} />} cursor={{ fill: c.grid, opacity: 0.5 }} />
                {series.map((s) => <Bar key={s.key} dataKey={s.key} name={s.label} fill={s.color} radius={[4, 4, 0, 0]} maxBarSize={36} />)}
              </BarChart>
            </ResponsiveContainer>
          </div>
          <Table rowKey="month" rows={d.monthly} columns={[
            { key: 'month', header: 'Month' },
            { key: 'revenue', header: 'Revenue', align: 'right', render: (m) => fmtMoney(m.revenue) },
            { key: 'cogs', header: 'COGS', align: 'right', render: (m) => fmtMoney(m.cogs) },
            { key: 'expenses', header: 'Op. costs', align: 'right', render: (m) => fmtMoney(m.expenses) },
            { key: 'netProfit', header: 'Net profit', align: 'right', render: (m) => <strong className={m.netProfit >= 0 ? 'tone-good' : 'tone-bad'}>{fmtMoney(m.netProfit)}</strong> },
          ]} />
        </Card>
        <div className="stack">
          <Card title="Income statement" pad={false}>
            <table className="table"><tbody>
              <Row l="Revenue (net of discounts & refunds)" v={d.revenue} strong />
              <Row l="Cost of goods sold" v={d.costOfGoods} indent neg />
              <Row l="Gross profit" v={d.grossProfit} strong />
              {d.expensesByCategory.map((e) => <Row key={e.name} l={label(e.name)} v={e.value} indent neg />)}
              <Row l="Shipping & freight" v={d.shippingCosts} indent neg />
              <Row l="Net profit" v={d.netProfit} strong />
            </tbody></table>
          </Card>
        </div>
      </div>
    </>
  )
}

function InventoryReport() {
  const c = useChartColors()
  const toast = useToast()
  const { hasRole } = useAuth()
  const { data: d, node, reload } = useReport('/analytics/inventory')
  const { data: warehouses } = useApi('/warehouses')
  if (node) return node
  const reorder = async () => {
    const wh = warehouses?.find((w) => w.type === 'WAREHOUSE') || warehouses?.[0]
    try {
      const pos = await api.post('/purchase-orders/auto-reorder', null, { warehouseId: wh.id })
      toast(pos.length ? `Created ${pos.length} draft purchase order(s) — review them under Purchase Orders` : 'Nothing to reorder (products need a preferred supplier)')
      reload()
    } catch (e) { toast(e.message, 'error') }
  }
  return (
    <>
      <div className="kpis">
        <Kpi label="Stock value (cost)" value={fmtMoney(d.valueAtCost)} sub={`${fmtNum(d.totalUnits)} units · ${d.skuCount} SKUs`} />
        <Kpi label="Stock value (retail)" value={fmtMoney(d.valueAtRetail)} sub={`Potential profit ${fmtMoneyShort(d.potentialProfit)}`} />
        <Kpi label="Turnover (90d)" value={`${d.turnoverRatio90d}×`} sub={`≈ ${d.daysOfInventory} days of inventory on hand`} />
        <Kpi label="Needs reorder" value={d.reorderSuggestions.length} sub={`${d.outOfStock} out of stock`} tone={d.reorderSuggestions.length ? 'warn' : 'good'} />
      </div>
      <div className="grid grid-2">
        <Card title="Stock value by location"><BarList items={d.byWarehouse} color={c.series[0]} format={fmtMoneyShort} /></Card>
        <Card title="Stock value by category"><BarList items={d.byCategory} color={c.series[0]} format={fmtMoneyShort} /></Card>
      </div>
      <div className="grid grid-2">
        <Card title="Reorder suggestions" pad={false} actions={hasRole(...R.MANAGE, 'WAREHOUSE') && d.reorderSuggestions.length > 0 &&
          <AsyncButton className="btn btn-sm btn-primary" onClick={reorder}>Draft purchase orders</AsyncButton>}>
          <Table rowKey="productId" rows={d.reorderSuggestions} empty="Everything is above its reorder level" columns={[
            { key: 'name', header: 'Product', render: (r) => <>{r.name}<div className="sub">{r.sku} · {r.supplier || 'no supplier'}</div></> },
            { key: 'quantity', header: 'On hand', align: 'right', render: (r) => <Badge value={String(r.quantity)} tone={r.quantity <= 0 ? 'bad' : 'warn'} /> },
            { key: 'reorderLevel', header: 'Min', align: 'right' },
            { key: 'suggestedOrderQty', header: 'Order', align: 'right' },
          ]} />
        </Card>
        <Card title="Dead stock (no sales in 60 days)" pad={false}>
          <Table rowKey="name" rows={d.deadStock} empty="No dead stock — everything is moving" columns={[
            { key: 'name', header: 'Product' },
            { key: 'count', header: 'Units', align: 'right' },
            { key: 'value', header: 'Tied-up value', align: 'right', render: (r) => fmtMoney(r.value) },
          ]} />
        </Card>
      </div>
    </>
  )
}

function LogisticsReport({ range }) {
  const c = useChartColors()
  const { data: d, node } = useReport('/analytics/logistics', range)
  if (node) return node
  const statuses = Object.entries(d.byStatus).map(([name, value]) => ({ name: label(name), value }))
  return (
    <>
      <div className="kpis">
        <Kpi label="Shipments" value={fmtNum(Object.values(d.byStatus).reduce((a, b) => a + b, 0))} />
        <Kpi label="In transit" value={d.inTransit} />
        <Kpi label="Delayed (past ETA)" value={d.delayed} tone={d.delayed ? 'bad' : 'good'} />
        <Kpi label="Avg. transit time" value={`${d.avgDeliveryDays} days`} sub="Dispatch → delivery" />
        <Kpi label="Shipping spend" value={fmtMoney(d.totalShippingCost)} />
      </div>
      <div className="grid grid-2-1">
        <Card title="Carrier performance" pad={false}>
          <Table rowKey="carrier" rows={d.carriers} empty="No shipments with a carrier in this period" columns={[
            { key: 'carrier', header: 'Carrier', render: (r) => <strong>{r.carrier}</strong> },
            { key: 'shipments', header: 'Shipments', align: 'right' },
            { key: 'delivered', header: 'Delivered', align: 'right' },
            { key: 'onTimePct', header: 'On time', align: 'right', render: (r) => r.delivered ? <Badge value={fmtPct(r.onTimePct)} tone={r.onTimePct >= 90 ? 'good' : r.onTimePct >= 75 ? 'warn' : 'bad'} /> : '—' },
            { key: 'totalCost', header: 'Cost', align: 'right', render: (r) => fmtMoney(r.totalCost) },
          ]} />
        </Card>
        <Card title="Shipments by status"><BarList items={statuses} color={c.series[0]} format={fmtNum} /></Card>
      </div>
    </>
  )
}

function CashReport({ range }) {
  const c = useChartColors()
  const { data: d, node } = useReport('/analytics/payments', range)
  if (node) return node
  return (
    <>
      <div className="kpis">
        <Kpi label="Money in" value={fmtMoney(d.inflow)} sub="Customer payments" />
        <Kpi label="Money out" value={fmtMoney(d.outflow)} sub="Suppliers, expenses, refunds" />
        <Kpi label="Net cash flow" value={fmtMoney(d.net)} tone={d.net >= 0 ? 'good' : 'bad'} sub={d.net >= 0 ? '▲ cash positive' : '▼ cash negative'} />
      </div>
      <Card title="Daily cash in vs out">
        <Legend items={[{ label: 'Money in', color: c.series[0] }, { label: 'Money out', color: c.series[1] }]} />
        <div style={{ height: 280 }}>
          <ResponsiveContainer>
            <BarChart data={d.daily} margin={{ top: 8, right: 8, left: 0, bottom: 0 }} barGap={2}>
              <CartesianGrid stroke={c.grid} vertical={false} />
              <XAxis dataKey="date" tickFormatter={fmtDay} {...axisProps(c)} axisLine={{ stroke: c.axis }} minTickGap={24} />
              <YAxis tickFormatter={fmtMoneyShort} {...axisProps(c)} axisLine={false} width={64} />
              <Tooltip content={<ChartTooltip labelFormatter={fmtDay} valueFormatter={fmtMoney} />} cursor={{ fill: c.grid, opacity: 0.5 }} />
              <Bar dataKey="inflow" name="Money in" fill={c.series[0]} radius={[4, 4, 0, 0]} maxBarSize={18} />
              <Bar dataKey="outflow" name="Money out" fill={c.series[1]} radius={[4, 4, 0, 0]} maxBarSize={18} />
            </BarChart>
          </ResponsiveContainer>
        </div>
      </Card>
      <div className="grid grid-2" style={{ marginTop: 16 }}>
        <Card title="Receipts by payment method"><BarList items={d.byMethod.map((m) => ({ ...m, name: label(m.name) }))} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${i.count} txns`} /></Card>
        <Card title="All flows by type"><BarList items={d.byType.map((m) => ({ ...m, name: label(m.name) }))} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${i.count} txns`} /></Card>
      </div>
    </>
  )
}
