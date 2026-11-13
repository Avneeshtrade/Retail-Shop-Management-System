# RetailHub — Retail Shop Management System

Full-stack shop management: **point of sale, inventory, procurement, logistics, payments, expenses and analytics**.

| Layer    | Stack |
|----------|-------|
| Backend  | Java 25 (LTS) · Spring Boot 4.1.2 · Spring Security (JWT, role-based) · Spring Data JPA / Hibernate 7 · H2 (dev) / PostgreSQL (prod) |
| Frontend | React 19.3 · Vite 8 · React Router 7 · Recharts 3 |

## Quick start (development)

Prerequisites: JDK 25, Maven 3.9+, Node 22+.

```bash
# 1. Backend  → http://localhost:8080   (H2 file DB in backend/data, demo data seeded on first run)
cd backend
mvn spring-boot:run

# 2. Frontend → http://localhost:5173   (proxies /api to :8080)
cd frontend
npm install
npm run dev
```

> Only have JDK 21? Everything builds on it too: `mvn spring-boot:run -Djava.version=21`.

### Demo accounts
Password is the username + `123` (e.g. `admin` / `admin123`). **Change or disable these before going live.**

| User | Role | Can do |
|------|------|--------|
| `admin` | ADMIN | Everything, incl. user management |
| `manager` | MANAGER | Sales, refunds, catalogue, stock, purchasing, all reports |
| `cashier` | CASHIER | POS, customers, taking payments (lands on POS after login) |
| `warehouse` | WAREHOUSE | Stock adjustments, receiving goods, shipments/transfers, inventory & logistics reports |
| `accountant` | ACCOUNTANT | Payments ledger, expenses, supplier payments, financial reports |

Permissions are enforced on the server (`@PreAuthorize`, see `web/Roles.java`); the UI just hides what a role can't use.

## Production (Docker + PostgreSQL)

```bash
APP_JWT_SECRET=$(openssl rand -base64 48) docker compose up --build   # → http://localhost:3000
```

Set `APP_SEED_DEMO_DATA=false` for a clean install (only the `admin` account is created).

| Env var | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=postgres` | Use PostgreSQL (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) |
| `APP_JWT_SECRET` | JWT signing key, ≥ 32 chars — **required in production** |
| `APP_CORS_ORIGINS` | Comma-separated allowed origins |
| `APP_SEED_DEMO_DATA` | `true` loads 90 days of sample history on an empty DB |
| `VITE_CURRENCY` / `VITE_LOCALE` | Frontend display currency (default `INR` / `en-IN`) |

## Modules

**Sales & POS** — barcode/SKU scan, product grid with live stock per location, cart, order discount, split payments
(cash/card/UPI/wallet/bank/cheque), automatic change, printable receipts, "on account" sales for known customers.
Orders support later payments, partial/full refunds (with optional restock) and cancellation.

**Customers** — profiles, purchase history, lifetime spend, loyalty points (1 pt per ₹100).

**Catalogue & inventory** — products with SKU/barcode, cost/price/tax, reorder level & quantity, preferred supplier;
stock per location (stores + warehouses); physical-count or +/- adjustments with reasons; every change is written to an
audited stock-movement ledger. Overselling is impossible (stock can't go negative).

**Procurement** — suppliers with terms & lead times; purchase orders (draft → ordered → partially received → received);
partial receipts post stock and update cost price by **weighted average**; supplier payments track payables;
**auto-reorder** drafts one PO per supplier for every low-stock product.

**Logistics** — outbound customer deliveries (order → shipped → delivered), inbound supplier freight, and
**inter-location transfers** that move stock out on dispatch and in on delivery (returns put it back). Carrier,
tracking number, ETA, shipping cost and a validated status workflow.

**Finance** — one payments ledger for receipts, refunds, supplier payments and expenses (CSV export); expense tracking by
category; receivables and payables.

**Analytics**
- *Dashboard*: today / month-to-date sales, growth vs last month, AOV, gross & net profit, stock alerts, receivables/payables, 30-day trend, top products, category & payment mix, recent orders.
- *Sales*: net sales, margin, daily trend, by hour, weekday, category, channel, staff member; product profitability; top customers.
- *Profit & Loss*: income statement, monthly revenue vs COGS vs operating costs.
- *Inventory*: valuation (cost/retail), turnover, days of inventory, value by location/category, reorder suggestions, dead stock.
- *Logistics*: shipments by status, carrier on-time %, average transit time, delays, shipping spend.
- *Cash flow*: daily money in vs out, by method and type.

## Project layout

```
backend/src/main/java/com/retailshop/
  domain/       JPA entities + enums
  repository/   Spring Data repositories (incl. reporting queries)
  service/      Business rules: Inventory, Catalog, Sales, PurchaseOrder, Shipment, Payment, Expense, Analytics
  web/          REST controllers (+ Roles.java permission matrix)
  security/     JWT filter & service
  config/       Security config, demo data seeder
frontend/src/
  pages/        One screen per module
  components/   Shared UI (tables, modals, badges, charts helpers)
  hooks/        Auth, data fetching, chart palette
```

## Tests

```bash
cd backend && mvn test
```
Covers POS checkout maths, oversell protection, refunds/restocking, partial PO receipt and weighted-average cost,
transfer shipments, auto-reorder, JWT login and role-based access.

## Main API endpoints

`POST /api/auth/login` · `/api/products` · `/api/categories` · `/api/inventory` (+ `/movements`, `/adjustments`) ·
`/api/warehouses` · `/api/suppliers` · `/api/purchase-orders` (+ `/{id}/submit|receive|cancel|payments`, `/auto-reorder`) ·
`/api/customers` · `/api/sales-orders` (+ `/{id}/payments|refund|cancel`) · `/api/shipments` (+ `/{id}/status`) ·
`/api/payments` · `/api/expenses` · `/api/users` · `/api/analytics/{dashboard|sales|profit-loss|inventory|logistics|payments}`

All endpoints except login require `Authorization: Bearer <token>`. Errors use RFC 7807 problem details.

## Next steps worth considering
- Database migrations (Flyway) instead of `ddl-auto: update` before the first production deploy
- Pagination on large lists (orders, payments, movements)
- Payment gateway integration (card/UPI) — today payments are recorded, not processed
- Returns of individual line items (refunds are currently amount-based with all-or-nothing restock)
