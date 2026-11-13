package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_movements", indexes = @Index(columnList = "createdAt"))
@Getter @Setter @NoArgsConstructor
public class StockMovement extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Warehouse warehouse;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Enums.MovementType type;

    /** Signed quantity: positive adds stock, negative removes stock. */
    private int quantity;

    private int balanceAfter;

    /** Document number that caused the movement (PO, SO, shipment...). */
    private String reference;

    private String note;
    private String performedBy;
}
