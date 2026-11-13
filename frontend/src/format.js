const CURRENCY = import.meta.env.VITE_CURRENCY || 'INR'
const LOCALE = import.meta.env.VITE_LOCALE || 'en-IN'

const money = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY, maximumFractionDigits: 2 })
const moneyCompact = new Intl.NumberFormat(LOCALE, { style: 'currency', currency: CURRENCY, notation: 'compact', maximumFractionDigits: 1 })
const num = new Intl.NumberFormat(LOCALE)

export const fmtMoney = (v) => money.format(Number(v || 0))
export const fmtMoneyShort = (v) => moneyCompact.format(Number(v || 0))
export const fmtNum = (v) => num.format(Number(v || 0))
export const fmtPct = (v) => `${Number(v || 0).toFixed(1)}%`
export const fmtDate = (d) => (d ? new Date(d).toLocaleDateString(LOCALE, { day: '2-digit', month: 'short', year: 'numeric' }) : '—')
export const fmtDateTime = (d) => (d ? new Date(d).toLocaleString(LOCALE, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—')
export const fmtDay = (d) => new Date(d).toLocaleDateString(LOCALE, { day: 'numeric', month: 'short' })
export const label = (s) => (s ? String(s).replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) : '')

export const isoDate = (d) => {
  const x = new Date(d)
  return `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
}
export const daysAgo = (n) => isoDate(Date.now() - n * 86400000)
export const today = () => isoDate(new Date())
