import { useEffect, useState } from 'react'

// Reference palette (dataviz skill): categorical order is fixed, light/dark steps selected per mode.
const LIGHT = {
  series: ['#2a78d6', '#eb6834', '#1baf7a', '#eda100', '#e87ba4', '#008300', '#6250d6', '#e34948'],
  grid: '#e1e0d9', axis: '#c3c2b7', muted: '#898781', text: '#52514e', surface: '#fcfcfb',
  seq: ['#cde2fb', '#86b6ef', '#3987e5', '#256abf', '#184f95'],
  good: '#0ca30c', critical: '#d03b3b',
}
const DARK = {
  series: ['#3987e5', '#d95926', '#199e70', '#c98500', '#d55181', '#008300', '#9085e9', '#e66767'],
  grid: '#2c2c2a', axis: '#383835', muted: '#898781', text: '#c3c2b7', surface: '#1a1a19',
  seq: ['#184f95', '#1c5cab', '#2a78d6', '#5598e7', '#86b6ef'],
  good: '#0ca30c', critical: '#d03b3b',
}

const query = () => window.matchMedia?.('(prefers-color-scheme: dark)')

export function useChartColors() {
  const [dark, setDark] = useState(() => !!query()?.matches)
  useEffect(() => {
    const q = query()
    if (!q) return
    const fn = (e) => setDark(e.matches)
    q.addEventListener('change', fn)
    return () => q.removeEventListener('change', fn)
  }, [])
  return dark ? DARK : LIGHT
}
