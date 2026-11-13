import { Link } from 'react-router-dom'
import { Area, AreaChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { useApi } from '../hooks/useApi'
import { useChartColors } from '../hooks/useChartColors'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { Badge, Card, ErrorBox, Kpi, Loading, PageHeader, Table } from '../components/ui.jsx'
import ChartTooltip from '../components/ChartTooltip.jsx'
import BarList from '../components/BarList.jsx'
import Legend from '../components/Legend.jsx'
import { fmtDateTime, fmtDay, fmtMoney, fmtMoneyShort, fmtNum, fmtPct, label } from '../format'

export default function Dashboard() {
  const { data, error, loading, reload } = useApi('/analytics/dashboard')
  const c = useChartColors()
  const { user, hasRole } = useAuth()
  const finance = hasRole(...R.FINANCE)

  if (loading && !data) return <Loading />
  if (error) return <ErrorBox error={error} onRetry={reload} />
  const k = data.kpis
  const growth = Number(k.salesGrowthPct)

  return (
    <>
      <PageHeader title={`Welcome back, ${user.fullName.split(' ')[0]}`} subtitle="Here's how the shop is doing today."
                  actions={<button className="btn" onClick={reload}>↻ Refresh</button>} />

      <div className="kpis">
        <Kpi label="Today's sales" value={fmtMoney(k.todaySales)} sub={`${fmtNum(k.todayOrders)} orders`} />
        <Kpi label="Month to date" value={fmtMoney(k.monthSales)}
             sub={`${growth >= 0 ? '▲' : '▼'} ${fmtPct(Math.abs(growth))} vs last month`} tone={growth >= 0 ? 'good' : 'bad'} />
        <Kpi label="Avg. order value" value={fmtMoney(k.avgOrderValue)} sub={`${fmtNum(k.monthOrders)} orders this month`} />
        {finance && <Kpi label="Gross profit (MTD)" value={fmtMoney(k.monthGrossProfit)}
             sub={`Net after expenses ${fmtMoney(k.monthNetProfit)}`} tone={k.monthNetProfit >= 0 ? 'good' : 'bad'} />}
        <Kpi label="Inventory value" value={fmtMoneyShort(k.inventoryValueCost)} sub={`${fmtNum(k.inventoryUnits)} units at cost`} />
        <Kpi label="Stock alerts" value={fmtNum(k.lowStockCount)} sub={`${k.outOfStockCount} out of stock`} tone={k.lowStockCount ? 'warn' : 'good'} />
        {finance && <Kpi label="Receivables / Payables" value={fmtMoneyShort(k.accountsReceivable)} sub={`Owe suppliers ${fmtMoney(k.accountsPayable)}`} />}
        <Kpi label="Operations" value={`${k.pendingShipments} shipments`} sub={`${k.openPurchaseOrders} open POs · ${k.pendingOrders} pending orders`} />
      </div>

      <div className="grid grid-2-1">
        <Card title="Sales — last 30 days">
          <Legend items={[{ label: 'Net revenue', color: c.series[0], line: true }, ...(finance ? [{ label: 'Gross profit', color: c.series[2], line: true }] : [])]} />
          <div style={{ height: 280 }}>
            <ResponsiveContainer>
              <AreaChart data={data.salesTrend} margin={{ top: 8, right: 8, left: 0, bottom: 0 }}>
                <defs>
                  <linearGradient id="rev" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stopColor={c.series[0]} stopOpacity={0.22} />
                    <stop offset="100%" stopColor={c.series[0]} stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid stroke={c.grid} vertical={false} />
                <XAxis dataKey="date" tickFormatter={fmtDay} tick={{ fill: c.muted, fontSize: 12 }} axisLine={{ stroke: c.axis }} tickLine={false} minTickGap={24} />
                <YAxis tickFormatter={fmtMoneyShort} tick={{ fill: c.muted, fontSize: 12 }} axisLine={false} tickLine={false} width={64} />
                <Tooltip content={<ChartTooltip labelFormatter={fmtDay} valueFormatter={fmtMoney} />} cursor={{ stroke: c.axis }} />
                <Area type="monotone" dataKey="revenue" name="Net revenue" stroke={c.series[0]} strokeWidth={2} fill="url(#rev)" activeDot={{ r: 5, stroke: c.surface, strokeWidth: 2 }} />
                {finance && <Area type="monotone" dataKey="profit" name="Gross profit" stroke={c.series[2]} strokeWidth={2} fill="none" activeDot={{ r: 5, stroke: c.surface, strokeWidth: 2 }} />}
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </Card>
        <Card title="Top products (30d)">
          <BarList items={data.topProducts.map((p) => ({ name: p.name, value: p.revenue, qty: p.quantity }))}
                   color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${fmtNum(i.qty)} sold`} />
        </Card>
      </div>

      <div className="grid grid-3">
        <Card title="Sales by category (30d)">
          <BarList items={data.salesByCategory} color={c.series[0]} format={fmtMoneyShort} />
        </Card>
        <Card title="Payments received by method (30d)">
          <BarList items={data.paymentMethods.map((m) => ({ ...m, name: label(m.name) }))} color={c.series[0]} format={fmtMoneyShort} sub={(i) => `${i.count} txns`} />
        </Card>
        <Card title="Low stock" actions={<Link to="/inventory" className="btn btn-sm">View all</Link>} pad={false}>
          <Table rowKey="productId" empty="All products are above reorder level 🎉"
                 columns={[
                   { key: 'name', header: 'Product', render: (r) => <>{r.name}<div className="sub">{r.sku}</div></> },
                   { key: 'quantity', header: 'On hand', align: 'right', render: (r) => <Badge value={r.quantity <= 0 ? 'Out' : String(r.quantity)} tone={r.quantity <= 0 ? 'bad' : 'warn'} /> },
                   { key: 'suggestedOrderQty', header: 'Reorder', align: 'right' },
                 ]} rows={data.lowStock} />
        </Card>
      </div>

      <Card title="Recent orders" actions={<Link to="/orders" className="btn btn-sm">All orders</Link>} pad={false}>
        <Table columns={[
          { key: 'orderNumber', header: 'Order' },
          { key: 'customer', header: 'Customer' },
          { key: 'orderDate', header: 'Date', render: (r) => fmtDateTime(r.orderDate) },
          { key: 'status', header: 'Status', render: (r) => <Badge value={r.status} /> },
          { key: 'paymentStatus', header: 'Payment', render: (r) => <Badge value={r.paymentStatus} /> },
          { key: 'total', header: 'Total', align: 'right', render: (r) => fmtMoney(r.total) },
        ]} rows={data.recentOrders} />
      </Card>
    </>
  )
}
