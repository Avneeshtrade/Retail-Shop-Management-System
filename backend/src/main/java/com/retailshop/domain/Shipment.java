package com.retailshop.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "shipments")
@Getter @Setter @NoArgsConstructor
public class Shipment extends BaseEntity {
    @Column(nullable = false, unique = true, length = 40)
    private String shipmentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.ShipmentType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Enums.ShipmentStatus status = Enums.ShipmentStatus.PENDING;

    private String carrier;
    private String trackingNumber;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"items"})
    private SalesOrder salesOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    @JsonIgnoreProperties({"items"})
    private PurchaseOrder purchaseOrder;

    @ManyToOne(fetch = FetchType.EAGER)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.EAGER)
    private Warehouse toWarehouse;

    private String originAddress;
    private String destinationAddress;

    @Column(precision = 14, scale = 2)
    private BigDecimal shippingCost = BigDecimal.ZERO;

    private LocalDate estimatedDelivery;
    private LocalDateTime shippedAt;
    private LocalDateTime deliveredAt;

    /** Items carried; required for TRANSFER shipments (moves stock between warehouses). */
    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ShipmentItem> items = new ArrayList<>();

    @Column(length = 1000)
    private String notes;
    private String createdBy;

    public void addItem(ShipmentItem item) {
        item.setShipment(this);
        items.add(item);
    }
}
