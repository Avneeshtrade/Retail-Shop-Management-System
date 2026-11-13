package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "purchase_orders")
@Getter @Setter @NoArgsConstructor
public class PurchaseOrder extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String poNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Supplier supplier;

    /** Destination warehouse for received goods. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.PurchaseOrderStatus status = Enums.PurchaseOrderStatus.DRAFT;

    private LocalDate orderDate;
    private LocalDate expectedDate;
    private LocalDate receivedDate;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @Column(precision = 14, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal shippingCost = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal amountPaid = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Enums.PaymentStatus paymentStatus = Enums.PaymentStatus.UNPAID;

    @Column(length = 1000)
    private String notes;
    private String createdBy;

    public void addItem(PurchaseOrderItem item) {
        item.setPurchaseOrder(this);
        items.add(item);
    }
}
