package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sales_orders", indexes = @Index(columnList = "orderDate"))
@Getter @Setter @NoArgsConstructor
public class SalesOrder extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    private Customer customer;

    /** Store / warehouse the goods are taken from. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.SalesChannel channel = Enums.SalesChannel.IN_STORE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.SalesOrderStatus status = Enums.SalesOrderStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime orderDate;

    @OneToMany(mappedBy = "salesOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<SalesOrderItem> items = new ArrayList<>();

    @Column(precision = 14, scale = 2) private BigDecimal subtotal = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal discountAmount = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal taxAmount = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal shippingCharge = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal total = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal costOfGoods = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal amountPaid = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal amountRefunded = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Enums.PaymentStatus paymentStatus = Enums.PaymentStatus.UNPAID;

    private String shippingAddress;
    private String cashier;
    @Column(length = 1000)
    private String notes;

    public void addItem(SalesOrderItem item) {
        item.setSalesOrder(this);
        items.add(item);
    }
}
