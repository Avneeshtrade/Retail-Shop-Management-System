package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments", indexes = @Index(columnList = "paidAt"))
@Getter @Setter @NoArgsConstructor
public class Payment extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String paymentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.PaymentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.TransactionStatus status = Enums.TransactionStatus.COMPLETED;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"items", "customer", "warehouse"})
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"items", "supplier", "warehouse"})
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    private Expense expense;

    /** Gateway / bank transaction id, cheque number, etc. */
    private String transactionReference;

    /** Free text party name (customer / supplier / vendor) for quick display. */
    private String partyName;

    private LocalDateTime paidAt;
    private String receivedBy;
    private String notes;
}
