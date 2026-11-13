import { lazy, Suspense, useState } from 'react'
import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { R, useAuth } from './hooks/useAuth.jsx'
import { Loading } from './components/ui.jsx'
import Login from './pages/Login.jsx'
const Dashboard = lazy(() => import('./pages/Dashboard.jsx'))
const Pos = lazy(() => import('./pages/Pos.jsx'))
const SalesOrders = lazy(() => import('./pages/SalesOrders.jsx'))
const Products = lazy(() => import('./pages/Products.jsx'))
const Inventory = lazy(() => import('./pages/Inventory.jsx'))
const Warehouses = lazy(() => import('./pages/Warehouses.jsx'))
const Suppliers = lazy(() => import('./pages/Suppliers.jsx'))
const PurchaseOrders = lazy(() => import('./pages/PurchaseOrders.jsx'))
const Shipments = lazy(() => import('./pages/Shipments.jsx'))
const Customers = lazy(() => import('./pages/Customers.jsx'))
const Payments = lazy(() => import('./pages/Payments.jsx'))
const Expenses = lazy(() => import('./pages/Expenses.jsx'))
const Reports = lazy(() => import('./pages/Reports.jsx'))
const Users = lazy(() => import('./pages/Users.jsx'))
import { label } from './format'

const NAV = [
  { group: 'Overview' },
  { to: '/', icon: '◧', text: 'Dashboard', roles: R.ALL, el: <Dashboard />, end: true },
  { to: '/reports', icon: '📈', text: 'Analytics', roles: [...R.FINANCE, 'WAREHOUSE'], el: <Reports /> },
  { group: 'Sales' },
  { to: '/pos', icon: '🛒', text: 'Point of Sale', roles: R.SELL, el: <Pos /> },
  { to: '/orders', icon: '🧾', text: 'Sales Orders', roles: R.ALL, el: <SalesOrders /> },
  { to: '/customers', icon: '👥', text: 'Customers', roles: R.ALL, el: <Customers /> },
  { group: 'Stock' },
  { to: '/products', icon: '🏷', text: 'Products', roles: R.ALL, el: <Products /> },
  { to: '/inventory', icon: '📦', text: 'Inventory', roles: R.ALL, el: <Inventory /> },
  { to: '/warehouses', icon: '🏬', text: 'Locations', roles: R.ALL, el: <Warehouses /> },
  { group: 'Supply chain' },
  { to: '/suppliers', icon: '🏭', text: 'Suppliers', roles: R.PROCURE, el: <Suppliers /> },
  { to: '/purchase-orders', icon: '📋', text: 'Purchase Orders', roles: R.PROCURE, el: <PurchaseOrders /> },
  { to: '/shipments', icon: '🚚', text: 'Logistics', roles: R.ALL, el: <Shipments /> },
  { group: 'Finance' },
  { to: '/payments', icon: '💳', text: 'Payments', roles: R.FINANCE, el: <Payments /> },
  { to: '/expenses', icon: '💸', text: 'Expenses', roles: R.FINANCE, el: <Expenses /> },
  { group: 'Admin' },
  { to: '/users', icon: '🔐', text: 'Users', roles: R.ADMIN, el: <Users /> },
]

export default function App() {
  const { user, loading, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const location = useLocation()

  if (loading) return <Loading text="Starting…" />
  if (!user) return location.pathname === '/login' ? <Login /> : <Navigate to="/login" replace />

  const allowed = NAV.filter((n) => n.group || n.roles.includes(user.role))
  const visible = allowed.filter((n, i) => !n.group || (allowed[i + 1] && !allowed[i + 1].group))

  return (
    <div className="app">
      <aside className={`sidebar ${open ? 'open' : ''}`}>
        <div className="brand"><span className="brand-mark">◆</span> RetailHub</div>
        <nav className="nav" onClick={() => setOpen(false)}>
          {visible.map((n, i) => n.group
            ? <div key={'g' + i} className="nav-group">{n.group}</div>
            : <NavLink key={n.to} to={n.to} end={n.end}><span className="nav-icon" aria-hidden>{n.icon}</span>{n.text}</NavLink>)}
        </nav>
        <div className="sidebar-footer">
          <div className="who">{user.fullName}</div>
          <div>{label(user.role)}</div>
          <button className="btn btn-sm" onClick={logout}>Sign out</button>
        </div>
      </aside>
      <main className="main">
        <button className="btn menu-toggle" onClick={() => setOpen(!open)} aria-label="Menu">☰ Menu</button>
        <Suspense fallback={<Loading />}>
        <Routes>
          {allowed.filter((n) => !n.group).map((n) => <Route key={n.to} path={n.to} element={n.el} />)}
          <Route path="/login" element={<Navigate to={user.role === 'CASHIER' ? '/pos' : '/'} replace />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
      </main>
    </div>
  )
}
