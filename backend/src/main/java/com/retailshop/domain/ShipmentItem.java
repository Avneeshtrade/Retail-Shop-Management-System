package com.retailshop.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "shipment_items")
@Getter @Setter @NoArgsConstructor
public class ShipmentItem extends BaseEntity {
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Shipment shipment;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Product product;

    private int quantity;
}
