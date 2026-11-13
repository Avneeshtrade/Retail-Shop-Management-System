package com.retailshop.service;

import com.retailshop.common.BusinessException;
import com.retailshop.common.DocumentNumbers;
import com.retailshop.common.Money;
import com.retailshop.common.NotFoundException;
import com.retailshop.domain.*;
import com.retailshop.domain.Enums.*;
import com.retailshop.dto.Requests;
import com.retailshop.repository.*;
import com.retailshop.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class PurchaseOrderService {
    private final PurchaseOrderRepository orders;
    private final SupplierRepository suppliers;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final StockLevelRepository stockLevels;
    private final InventoryService inventory;
    private final PaymentService payments;

    public PurchaseOrderService(PurchaseOrderRepository orders, SupplierRepository suppliers,
                                WarehouseRepository warehouses, ProductRepository products,
                                StockLevelRepository stockLevels, InventoryService inventory,
                                PaymentService payments) {
        this.orders = orders;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.products = products;
        this.stockLevels = stockLevels;
        this.inventory = inventory;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<PurchaseOrder> list(PurchaseOrderStatus status, Long supplierId) {
        if (supplierId != null) return orders.findBySupplierIdOrderByCreatedAtDesc(supplierId);
        if (status != null) return orders.findByStatusOrderByCreatedAtDesc(status);
        return orders.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public PurchaseOrder get(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Purchase order", id));
    }

    public PurchaseOrder create(Requests.PurchaseOrderReq req) {
        PurchaseOrder po = new PurchaseOrder();
        po.setPoNumber(DocumentNumbers.next("PO"));
        po.setCreatedBy(CurrentUser.username());
        fill(po, req);
        return orders.save(po);
    }

    public PurchaseOrder update(Long id, Requests.PurchaseOrderReq req) {
        PurchaseOrder po = get(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) throw new BusinessException("Only draft purchase orders can be edited");
        po.getItems().clear();
        fill(po, req);
        return po;
    }

    private void fill(PurchaseOrder po, Requests.PurchaseOrderReq req) {
        po.setSupplier(suppliers.findById(req.supplierId()).orElseThrow(() -> new NotFoundException("Supplier", req.supplierId())));
        po.setWarehouse(warehouses.findById(req.warehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.warehouseId())));
        po.setExpectedDate(req.expectedDate() != null ? req.expectedDate()
                : LocalDate.now().plusDays(po.getSupplier().getLeadTimeDays()));
        po.setShippingCost(Money.of(req.shippingCost()));
        po.setNotes(req.notes());
        for (Requests.LineItem li : req.items()) {
            Product p = products.findById(li.productId()).orElseThrow(() -> new NotFoundException("Product", li.productId()));
            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setProduct(p);
            item.setQuantity(li.quantity());
            item.setUnitCost(Money.of(li.unitPrice() != null ? li.unitPrice() : p.getCostPrice()));
            item.setTaxRate(p.getTaxRate());
            po.addItem(item);
        }
        recalc(po);
    }

    private void recalc(PurchaseOrder po) {
        BigDecimal subtotal = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (PurchaseOrderItem i : po.getItems()) {
            BigDecimal net = i.getUnitCost().multiply(BigDecimal.valueOf(i.getQuantity()));
            BigDecimal t = Money.percentOf(net, i.getTaxRate());
            i.setLineTotal(Money.of(net.add(t)));
            subtotal = subtotal.add(net);
            tax = tax.add(t);
        }
        po.setSubtotal(Money.of(subtotal));
        po.setTaxAmount(Money.of(tax));
        po.setTotal(Money.of(subtotal.add(tax).add(Money.nz(po.getShippingCost()))));
    }

    /** DRAFT -> ORDERED (sent to supplier). */
    public PurchaseOrder submit(Long id) {
        PurchaseOrder po = get(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT) throw new BusinessException("Only draft orders can be submitted");
        po.setStatus(PurchaseOrderStatus.ORDERED);
        po.setOrderDate(LocalDate.now());
        return po;
    }

    /** Receives goods into the PO's warehouse; supports partial receipts. */
    public PurchaseOrder receive(Long id, Requests.ReceiveReq req) {
        PurchaseOrder po = get(id);
        if (po.getStatus() != PurchaseOrderStatus.ORDERED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            throw new BusinessException("Goods can only be received on ordered purchase orders (current: " + po.getStatus() + ")");
        }
        Map<Long, Integer> qtyByItem = new HashMap<>();
        if (req == null || req.lines() == null || req.lines().isEmpty()) {
            po.getItems().forEach(i -> qtyByItem.put(i.getId(), i.getQuantity() - i.getReceivedQuantity()));
        } else {
            req.lines().forEach(l -> qtyByItem.merge(l.itemId(), l.quantity(), Integer::sum));
        }
        boolean any = false;
        for (PurchaseOrderItem item : po.getItems()) {
            int qty = qtyByItem.getOrDefault(item.getId(), 0);
            if (qty <= 0) continue;
            int outstanding = item.getQuantity() - item.getReceivedQuantity();
            if (qty > outstanding) {
                throw new BusinessException("Cannot receive " + qty + " of " + item.getProduct().getName()
                        + "; only " + outstanding + " outstanding");
            }
            item.setReceivedQuantity(item.getReceivedQuantity() + qty);
            inventory.move(item.getProduct(), po.getWarehouse(), qty, MovementType.PURCHASE_RECEIPT,
                    po.getPoNumber(), "Received from " + po.getSupplier().getName());
            updateCostPrice(item.getProduct(), qty, item.getUnitCost());
            any = true;
        }
        if (!any) throw new BusinessException("Nothing to receive");
        boolean complete = po.getItems().stream().allMatch(i -> i.getReceivedQuantity() >= i.getQuantity());
        po.setStatus(complete ? PurchaseOrderStatus.RECEIVED : PurchaseOrderStatus.PARTIALLY_RECEIVED);
        if (complete) po.setReceivedDate(LocalDate.now());
        return po;
    }

    /** Moving weighted-average cost. */
    private void updateCostPrice(Product p, int receivedQty, BigDecimal unitCost) {
        long onHand = stockLevels.totalQuantity(p.getId()); // already includes the received qty
        long before = Math.max(0, onHand - receivedQty);
        if (onHand <= 0) return;
        BigDecimal value = p.getCostPrice().multiply(BigDecimal.valueOf(before))
                .add(unitCost.multiply(BigDecimal.valueOf(receivedQty)));
        p.setCostPrice(Money.of(value.divide(BigDecimal.valueOf(onHand), 4, java.math.RoundingMode.HALF_UP)));
    }

    public PurchaseOrder cancel(Long id) {
        PurchaseOrder po = get(id);
        if (po.getStatus() != PurchaseOrderStatus.DRAFT && po.getStatus() != PurchaseOrderStatus.ORDERED) {
            throw new BusinessException("Only draft or ordered (not yet received) purchase orders can be cancelled");
        }
        if (po.getAmountPaid().signum() > 0) throw new BusinessException("Purchase order has payments; settle with supplier first");
        po.setStatus(PurchaseOrderStatus.CANCELLED);
        return po;
    }

    public Payment pay(Long id, Requests.PaymentReq req) {
        PurchaseOrder po = get(id);
        if (po.getStatus() == PurchaseOrderStatus.DRAFT || po.getStatus() == PurchaseOrderStatus.CANCELLED) {
            throw new BusinessException("Cannot pay a " + po.getStatus() + " purchase order");
        }
        BigDecimal due = po.getTotal().subtract(po.getAmountPaid());
        if (req.amount().compareTo(due) > 0) throw new BusinessException("Amount exceeds balance due (" + due + ")");
        Payment p = payments.record(PaymentType.SUPPLIER_PAYMENT, req.method(), req.amount(), req.transactionReference(),
                po.getSupplier().getName(), null, po, null, req.notes(), null);
        po.setAmountPaid(Money.of(po.getAmountPaid().add(req.amount())));
        po.setPaymentStatus(po.getAmountPaid().compareTo(po.getTotal()) >= 0 ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
        return p;
    }

    /**
     * Creates draft POs (one per preferred supplier) for every active product at or below its reorder level.
     */
    public List<PurchaseOrder> autoReorder(Long warehouseId) {
        Warehouse w = warehouses.findById(warehouseId).orElseThrow(() -> new NotFoundException("Warehouse", warehouseId));
        Map<Supplier, List<Requests.LineItem>> bySupplier = new LinkedHashMap<>();
        for (Product p : products.findAll()) {
            if (!p.isActive() || p.getPreferredSupplier() == null) continue;
            long total = stockLevels.totalQuantity(p.getId());
            if (total > p.getReorderLevel()) continue;
            int qty = Math.max(p.getReorderQuantity(), (int) (p.getReorderLevel() * 2 - total));
            bySupplier.computeIfAbsent(p.getPreferredSupplier(), k -> new ArrayList<>())
                    .add(new Requests.LineItem(p.getId(), qty, p.getCostPrice(), null));
        }
        List<PurchaseOrder> created = new ArrayList<>();
        bySupplier.forEach((s, items) -> created.add(create(new Requests.PurchaseOrderReq(
                s.getId(), w.getId(), null, BigDecimal.ZERO, "Auto-generated reorder", items))));
        return created;
    }
}
