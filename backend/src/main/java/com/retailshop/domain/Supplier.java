package com.retailshop.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "suppliers")
@Getter @Setter @NoArgsConstructor
public class Supplier extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
    private String taxId;
    private int paymentTermsDays = 30;
    private int leadTimeDays = 7;
    private boolean active = true;
}
