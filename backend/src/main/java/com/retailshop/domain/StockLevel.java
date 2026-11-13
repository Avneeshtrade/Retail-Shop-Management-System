package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stock_levels",
       uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "warehouse_id"}))
@Getter @Setter @NoArgsConstructor
public class StockLevel extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Product product;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    private Warehouse warehouse;

    private int quantity;

    /** Optional bin/shelf location inside the warehouse. */
    private String binLocation;

    @Version
    private Long version;

    public StockLevel(Product product, Warehouse warehouse) {
        this.product = product;
        this.warehouse = warehouse;
    }
}
