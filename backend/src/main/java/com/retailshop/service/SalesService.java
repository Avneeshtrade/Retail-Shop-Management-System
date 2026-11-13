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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class SalesService {
    /** Loyalty: 1 point per this much currency paid. */
    private static final BigDecimal POINT_VALUE = BigDecimal.valueOf(100);

    private final SalesOrderRepository orders;
    private final CustomerRepository customers;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final InventoryService inventory;
    private final PaymentService payments;

    public SalesService(SalesOrderRepository orders, CustomerRepository customers, WarehouseRepository warehouses,
                        ProductRepository products, InventoryService inventory, PaymentService payments) {
        this.orders = orders;
        this.customers = customers;
        this.warehouses = warehouses;
        this.products = products;
        this.inventory = inventory;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<SalesOrder> list(LocalDate from, LocalDate to, SalesOrderStatus status, Long customerId) {
        if (customerId != null) return orders.findByCustomerIdOrderByOrderDateDesc(customerId);
        LocalDateTime f = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime t = (to == null ? LocalDate.now() : to).atTime(LocalTime.MAX);
        return orders.findByOrderDateBetweenOrderByOrderDateDesc(f, t).stream()
                .filter(o -> status == null || o.getStatus() == status).toList();
    }

    @Transactional(readOnly = true)
    public SalesOrder get(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Sales order", id));
    }

    /** Creates an order, deducts stock and records any upfront payments (POS checkout). */
    public SalesOrder create(Requests.SalesOrderReq req) {
        return create(req, LocalDateTime.now());
    }

    public SalesOrder create(Requests.SalesOrderReq req, LocalDateTime orderDate) {
        SalesOrder o = new SalesOrder();
        o.setOrderNumber(DocumentNumbers.next("SO", orderDate.toLocalDate()));
        o.setOrderDate(orderDate);
        o.setWarehouse(warehouses.findById(req.warehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.warehouseId())));
        if (req.customerId() != null) {
            o.setCustomer(customers.findById(req.customerId()).orElseThrow(() -> new NotFoundException("Customer", req.customerId())));
        }
        o.setChannel(req.channel() == null ? SalesChannel.IN_STORE : req.channel());
        o.setDiscountAmount(Money.of(req.discountAmount()));
        o.setShippingCharge(Money.of(req.shippingCharge()));
        o.setShippingAddress(req.shippingAddress() != null ? req.shippingAddress()
                : o.getCustomer() != null ? o.getCustomer().getAddress() : null);
        o.setNotes(req.notes());
        o.setCashier(CurrentUser.username());

        BigDecimal subtotal = BigDecimal.ZERO, tax = BigDecimal.ZERO, cogs = BigDecimal.ZERO;
        for (Requests.LineItem li : req.items()) {
            Product p = products.findById(li.productId()).orElseThrow(() -> new NotFoundException("Product", li.productId()));
            if (!p.isActive()) throw new BusinessException("Product is inactive: " + p.getName());
            SalesOrderItem item = new SalesOrderItem();
            item.setProduct(p);
            item.setQuantity(li.quantity());
            item.setUnitPrice(Money.of(li.unitPrice() != null ? li.unitPrice() : p.getSellingPrice()));
            item.setUnitCost(p.getCostPrice());
            item.setTaxRate(p.getTaxRate());
            item.setDiscount(Money.of(li.discount()));
            BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(li.quantity()));
            if (item.getDiscount().compareTo(gross) > 0) throw new BusinessException("Line discount exceeds line amount for " + p.getName());
            BigDecimal net = gross.subtract(item.getDiscount());
            item.setTaxAmount(Money.percentOf(net, item.getTaxRate()));
            item.setLineTotal(Money.of(net.add(item.getTaxAmount())));
            o.addItem(item);
            subtotal = subtotal.add(net);
            tax = tax.add(item.getTaxAmount());
            cogs = cogs.add(p.getCostPrice().multiply(BigDecimal.valueOf(li.quantity())));
        }
        if (o.getDiscountAmount().compareTo(subtotal) > 0) throw new BusinessException("Discount exceeds order subtotal");
        o.setSubtotal(Money.of(subtotal));
        o.setTaxAmount(Money.of(tax));
        o.setCostOfGoods(Money.of(cogs));
        o.setTotal(Money.of(subtotal.add(tax).add(o.getShippingCharge()).subtract(o.getDiscountAmount())));
        o.setStatus(SalesOrderStatus.PENDING);
        o = orders.save(o);

        for (SalesOrderItem item : o.getItems()) {
            inventory.move(item.getProduct(), o.getWarehouse(), -item.getQuantity(), MovementType.SALE, o.getOrderNumber(), null);
        }
        if (o.getCustomer() != null) {
            o.getCustomer().setOrderCount(o.getCustomer().getOrderCount() + 1);
        }
        if (req.payments() != null) {
            for (Requests.PaymentLine pl : req.payments()) {
                applyPayment(o, pl.method(), pl.amount(), pl.transactionReference(), null, orderDate);
            }
        }
        return o;
    }

    /** Records a payment with an explicit timestamp (imports / historical data). */
    public Payment addPaymentAt(Long id, PaymentMethod method, BigDecimal amount, String txRef, LocalDateTime at) {
        return applyPayment(get(id), method, amount, txRef, null, at);
    }

    public Payment addPayment(Long id, Requests.PaymentReq req) {
        return applyPayment(get(id), req.method(), req.amount(), req.transactionReference(), req.notes(), null);
    }

    private Payment applyPayment(SalesOrder o, PaymentMethod method, BigDecimal amount, String txRef, String notes,
                                 LocalDateTime at) {
        if (o.getStatus() == SalesOrderStatus.CANCELLED || o.getStatus() == SalesOrderStatus.REFUNDED) {
            throw new BusinessException("Cannot take payment on a " + o.getStatus() + " order");
        }
        BigDecimal due = o.getTotal().subtract(o.getAmountPaid());
        if (due.signum() <= 0) throw new BusinessException("Order is already fully paid");
        BigDecimal applied = amount;
        if (amount.compareTo(due) > 0) {
            if (method != PaymentMethod.CASH) throw new BusinessException("Amount exceeds balance due (" + due + ")");
            BigDecimal change = amount.subtract(due);
            applied = due; // cash tendered above due is returned as change
            notes = (notes == null ? "" : notes + " ") + "Cash tendered " + Money.of(amount) + ", change " + Money.of(change);
        }
        Payment p = payments.record(PaymentType.CUSTOMER_PAYMENT, method, applied, txRef,
                o.getCustomer() != null ? o.getCustomer().getName() : "Walk-in customer", o, null, null, notes, at);
        o.setAmountPaid(Money.of(o.getAmountPaid().add(applied)));
        boolean full = o.getAmountPaid().compareTo(o.getTotal()) >= 0;
        o.setPaymentStatus(full ? PaymentStatus.PAID : PaymentStatus.PARTIAL);
        if (full && o.getStatus() == SalesOrderStatus.PENDING) o.setStatus(SalesOrderStatus.COMPLETED);
        if (o.getCustomer() != null) {
            Customer c = o.getCustomer();
            c.setTotalSpent(Money.of(c.getTotalSpent().add(applied)));
            c.setLoyaltyPoints(c.getLoyaltyPoints() + applied.divide(POINT_VALUE, 0, RoundingMode.DOWN).intValue());
        }
        return p;
    }

    /** Cancel an unpaid order and put stock back. */
    public SalesOrder cancel(Long id, String reason) {
        SalesOrder o = get(id);
        if (o.getStatus() != SalesOrderStatus.PENDING && o.getStatus() != SalesOrderStatus.COMPLETED) {
            throw new BusinessException("Only pending or completed (not shipped) orders can be cancelled");
        }
        if (o.getAmountPaid().subtract(o.getAmountRefunded()).signum() > 0) {
            throw new BusinessException("Order has payments; issue a refund instead");
        }
        restock(o, "Cancelled: " + reason);
        o.setStatus(SalesOrderStatus.CANCELLED);
        o.setNotes(appendNote(o.getNotes(), "Cancelled: " + reason));
        if (o.getCustomer() != null) o.getCustomer().setOrderCount(Math.max(0, o.getCustomer().getOrderCount() - 1));
        return o;
    }

    public Payment refund(Long id, Requests.RefundReq req) {
        SalesOrder o = get(id);
        if (o.getStatus() == SalesOrderStatus.CANCELLED || o.getStatus() == SalesOrderStatus.REFUNDED) {
            throw new BusinessException("Order is already " + o.getStatus());
        }
        BigDecimal refundable = o.getAmountPaid().subtract(o.getAmountRefunded());
        BigDecimal amount = req.amount() == null ? refundable : req.amount();
        if (amount.signum() <= 0 || amount.compareTo(refundable) > 0) {
            throw new BusinessException("Refund must be between 0 and " + refundable);
        }
        Payment p = payments.record(PaymentType.CUSTOMER_REFUND, req.method(), amount, null,
                o.getCustomer() != null ? o.getCustomer().getName() : "Walk-in customer", o, null, null, req.reason(), null);
        o.setAmountRefunded(Money.of(o.getAmountRefunded().add(amount)));
        boolean full = o.getAmountRefunded().compareTo(o.getAmountPaid()) >= 0;
        if (full) {
            o.setStatus(SalesOrderStatus.REFUNDED);
            o.setPaymentStatus(PaymentStatus.REFUNDED);
        }
        if (req.restock()) restock(o, "Refund: " + req.reason());
        o.setNotes(appendNote(o.getNotes(), "Refunded " + amount + ": " + req.reason()));
        if (o.getCustomer() != null) {
            Customer c = o.getCustomer();
            c.setTotalSpent(Money.of(c.getTotalSpent().subtract(amount).max(BigDecimal.ZERO)));
            c.setLoyaltyPoints(Math.max(0, c.getLoyaltyPoints() - amount.divide(POINT_VALUE, 0, RoundingMode.DOWN).intValue()));
        }
        return p;
    }

    private void restock(SalesOrder o, String note) {
        if (o.getNotes() != null && o.getNotes().contains("[restocked]")) return; // never restock twice
        for (SalesOrderItem i : o.getItems()) {
            inventory.move(i.getProduct(), o.getWarehouse(), i.getQuantity(), MovementType.SALE_RETURN, o.getOrderNumber(), note);
        }
        o.setNotes(appendNote(o.getNotes(), "[restocked]"));
    }

    private static String appendNote(String existing, String add) {
        return existing == null || existing.isBlank() ? add : existing + "\n" + add;
    }
}
