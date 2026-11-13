package com.retailshop.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "sales_order_items")
@Getter @Setter @NoArgsConstructor
public class SalesOrderItem extends BaseEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Product product;

    private int quantity;

    @Column(precision = 14, scale = 2) private BigDecimal unitPrice = BigDecimal.ZERO;
    /** Cost snapshot at time of sale, used for profit analytics. */
    @Column(precision = 14, scale = 2) private BigDecimal unitCost = BigDecimal.ZERO;
    @Column(precision = 5, scale = 2) private BigDecimal taxRate = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal discount = BigDecimal.ZERO;
    @Column(precision = 14, scale = 2) private BigDecimal taxAmount = BigDecimal.ZERO;
    /** (unitPrice * qty) - discount + tax */
    @Column(precision = 14, scale = 2) private BigDecimal lineTotal = BigDecimal.ZERO;
}
