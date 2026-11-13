package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "customers")
@Getter @Setter @NoArgsConstructor
public class Customer extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String email;
    @Column(unique = true, length = 30)
    private String phone;
    private String address;
    private int loyaltyPoints;
    @Column(precision = 14, scale = 2)
    private BigDecimal totalSpent = BigDecimal.ZERO;
    private int orderCount;
}
