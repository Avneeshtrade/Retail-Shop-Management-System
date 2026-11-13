import { useState } from 'react'
import { api } from '../api/client'
import { useApi } from '../hooks/useApi'
import { R, useAuth } from '../hooks/useAuth.jsx'
import { useToast } from '../components/Toast.jsx'
import { AsyncButton, Badge, Card, ErrorBox, Field, Loading, Modal, PageHeader, Select, Table, Tabs } from '../components/ui.jsx'
import { fmtMoney, fmtPct } from '../format'

export default function Products() {
  const [tab, setTab] = useState('products')
  const { hasRole } = useAuth()
  return (
    <>
      <PageHeader title="Products" subtitle="Catalogue, pricing, tax and reorder rules" />
      <Tabs value={tab} onChange={setTab} tabs={[{ value: 'products', label: 'Products' }, { value: 'categories', label: 'Categories' }]} />
      {tab === 'products' ? <ProductList canEdit={hasRole(...R.MANAGE)} /> : <Categories canEdit={hasRole(...R.MANAGE)} />}
    </>
  )
}

function ProductList({ canEdit }) {
  const [q, setQ] = useState('')
  const [categoryId, setCategoryId] = useState(null)
  const { data, error, loading, reload } = useApi('/products', { q, categoryId })
  const { data: categories } = useApi('/categories')
  const [edit, setEdit] = useState(null)

  return (
    <>
      <div className="toolbar">
        <input type="search" placeholder="Search name, SKU or barcode" value={q} onChange={(e) => setQ(e.target.value)} />
        <Select value={categoryId} onChange={setCategoryId} placeholder="All categories" options={(categories || []).map((c) => ({ value: c.id, label: c.name }))} />
        <span className="spacer" />
        {canEdit && <button className="btn btn-primary" onClick={() => setEdit({})}>+ New product</button>}
      </div>
      <ErrorBox error={error} onRetry={reload} />
      <Card pad={false}>
        {loading && !data ? <Loading /> : (
          <Table rows={data} onRowClick={canEdit ? setEdit : undefined} columns={[
            { key: 'name', header: 'Product', render: (p) => <><strong>{p.name}</strong><div className="sub">{p.sku}{p.barcode ? ` · ${p.barcode}` : ''}</div></> },
            { key: 'category', header: 'Category', render: (p) => p.category?.name || '—' },
            { key: 'supplier', header: 'Supplier', render: (p) => p.preferredSupplier?.name || '—' },
            { key: 'costPrice', header: 'Cost', align: 'right', render: (p) => fmtMoney(p.costPrice) },
            { key: 'sellingPrice', header: 'Price', align: 'right', render: (p) => fmtMoney(p.sellingPrice) },
            { key: 'margin', header: 'Margin', align: 'right', render: (p) => p.sellingPrice > 0 ? fmtPct((p.sellingPrice - p.costPrice) / p.sellingPrice * 100) : '—' },
            { key: 'taxRate', header: 'Tax', align: 'right', render: (p) => `${Number(p.taxRate)}%` },
            { key: 'reorderLevel', header: 'Reorder at', align: 'right' },
            { key: 'active', header: 'Status', render: (p) => <Badge value={p.active ? 'ACTIVE' : 'INACTIVE'} /> },
          ]} />
        )}
      </Card>
      {edit && <ProductForm product={edit} categories={categories || []} onClose={() => setEdit(null)} onSaved={() => { setEdit(null); reload() }} />}
    </>
  )
}

function ProductForm({ product: p, categories, onClose, onSaved }) {
  const { data: suppliers } = useApi('/suppliers')
  const { data: warehouses } = useApi('/warehouses')
  const toast = useToast()
  const [f, setF] = useState({
    sku: p.sku || '', barcode: p.barcode || '', name: p.name || '', description: p.description || '',
    categoryId: p.category?.id ?? null, preferredSupplierId: p.preferredSupplier?.id ?? null, unit: p.unit || 'pcs',
    costPrice: p.costPrice ?? '', sellingPrice: p.sellingPrice ?? '', taxRate: p.taxRate ?? 0,
    reorderLevel: p.reorderLevel ?? 10, reorderQuantity: p.reorderQuantity ?? 50, active: p.active ?? true,
    openingStock: '', openingWarehouseId: null,
  })
  const set = (k) => (e) => setF({ ...f, [k]: e?.target ? (e.target.type === 'checkbox' ? e.target.checked : e.target.value) : e })
  const save = async () => {
    const body = { ...f, costPrice: Number(f.costPrice), sellingPrice: Number(f.sellingPrice), taxRate: Number(f.taxRate),
      reorderLevel: Number(f.reorderLevel), reorderQuantity: Number(f.reorderQuantity),
      openingStock: f.openingStock === '' ? null : Number(f.openingStock), openingWarehouseId: f.openingWarehouseId ? Number(f.openingWarehouseId) : null }
    try {
      await (p.id ? api.put(`/products/${p.id}`, body) : api.post('/products', body))
      toast('Product saved')
      onSaved()
    } catch (e) { toast(e.message, 'error') }
  }
  return (
    <Modal wide title={p.id ? `Edit ${p.name}` : 'New product'} onClose={onClose}
           footer={<><button className="btn" onClick={onClose}>Cancel</button><AsyncButton className="btn btn-primary" onClick={save}>Save</AsyncButton></>}>
      <div className="form-grid">
        <Field label="Name *" span={2}><input value={f.name} onChange={set('name')} /></Field>
        <Field label="SKU *"><input value={f.sku} onChange={set('sku')} /></Field>
        <Field label="Barcode"><input value={f.barcode} onChange={set('barcode')} /></Field>
        <Field label="Category"><Select value={f.categoryId} onChange={set('categoryId')} placeholder="—" options={categories.map((c) => ({ value: c.id, label: c.name }))} /></Field>
        <Field label="Preferred supplier" hint="Used for automatic reordering"><Select value={f.preferredSupplierId} onChange={set('preferredSupplierId')} placeholder="—" options={(suppliers || []).map((s) => ({ value: s.id, label: s.name }))} /></Field>
        <Field label="Cost price *" hint={p.id ? 'Updated automatically (weighted average) when goods are received' : undefined}><input type="number" step="0.01" value={f.costPrice} onChange={set('costPrice')} /></Field>
        <Field label="Selling price *"><input type="number" step="0.01" value={f.sellingPrice} onChange={set('sellingPrice')} /></Field>
        <Field label="Tax rate (%)"><input type="number" step="0.01" value={f.taxRate} onChange={set('taxRate')} /></Field>
        <Field label="Unit"><input value={f.unit} onChange={set('unit')} /></Field>
        <Field label="Reorder level" hint="Alert when total stock falls to this"><input type="number" value={f.reorderLevel} onChange={set('reorderLevel')} /></Field>
        <Field label="Reorder quantity"><input type="number" value={f.reorderQuantity} onChange={set('reorderQuantity')} /></Field>
        {!p.id && <>
          <Field label="Opening stock"><input type="number" value={f.openingStock} onChange={set('openingStock')} /></Field>
          <Field label="Opening stock location"><Select value={f.openingWarehouseId} onChange={set('openingWarehouseId')} placeholder="—" options={(warehouses || []).map((w) => ({ value: w.id, label: w.name }))} /></Field>
        </>}
        <Field label="Description" span={2}><textarea value={f.description} onChange={set('description')} /></Field>
        <label className="check"><input type="checkbox" checked={f.active} onChange={set('active')} /> Active (available for sale)</label>
      </div>
    </Modal>
  )
}

function Categories({ canEdit }) {
  const { data, reload } = useApi('/categories')
  const toast = useToast()
  const [name, setName] = useState('')
  return (
    <Card>
      {canEdit && (
        <div className="row" style={{ marginBottom: 12 }}>
          <input placeholder="New category name" value={name} onChange={(e) => setName(e.target.value)} />
          <AsyncButton className="btn btn-primary" disabled={!name} onClick={async () => {
            try { await api.post('/categories', { name }); setName(''); reload() } catch (e) { toast(e.message, 'error') }
          }}>Add</AsyncButton>
        </div>
      )}
      <Table rows={data || []} columns={[
        { key: 'name', header: 'Category' },
        { key: 'x', header: '', align: 'right', render: (c) => canEdit && (
          <AsyncButton className="btn btn-sm btn-danger" confirm={`Delete category "${c.name}"?`} onClick={async () => {
            try { await api.del(`/categories/${c.id}`); reload() } catch (e) { toast(e.status === 409 ? 'Category is in use by products' : e.message, 'error') }
          }}>Delete</AsyncButton>) },
      ]} />
    </Card>
  )
}
