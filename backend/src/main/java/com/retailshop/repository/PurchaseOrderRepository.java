package com.retailshop.repository;

import com.retailshop.domain.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {
    List<PurchaseOrder> findAllByOrderByCreatedAtDesc();
    List<PurchaseOrder> findByStatusOrderByCreatedAtDesc(Enums.PurchaseOrderStatus status);
    List<PurchaseOrder> findBySupplierIdOrderByCreatedAtDesc(Long supplierId);

    @Query("select coalesce(sum(p.total), 0) from PurchaseOrder p where p.status <> com.retailshop.domain.Enums.PurchaseOrderStatus.CANCELLED " +
           "and p.status <> com.retailshop.domain.Enums.PurchaseOrderStatus.DRAFT and p.orderDate between :from and :to")
    BigDecimal totalPurchases(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select coalesce(sum(p.total - p.amountPaid), 0) from PurchaseOrder p where p.status in " +
           "(com.retailshop.domain.Enums.PurchaseOrderStatus.ORDERED, com.retailshop.domain.Enums.PurchaseOrderStatus.PARTIALLY_RECEIVED, com.retailshop.domain.Enums.PurchaseOrderStatus.RECEIVED)")
    BigDecimal accountsPayable();

    long countByStatusIn(List<Enums.PurchaseOrderStatus> statuses);
}
