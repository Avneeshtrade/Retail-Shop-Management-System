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

public interface StockLevelRepository extends JpaRepository<StockLevel, Long> {
    Optional<StockLevel> findByProductIdAndWarehouseId(Long productId, Long warehouseId);
    List<StockLevel> findByWarehouseIdOrderByProductName(Long warehouseId);
    List<StockLevel> findByProductId(Long productId);

    @Query("select coalesce(sum(s.quantity), 0) from StockLevel s where s.product.id = :productId")
    long totalQuantity(@Param("productId") Long productId);

    @Query("select coalesce(sum(s.quantity * s.product.costPrice), 0) from StockLevel s")
    BigDecimal inventoryValueAtCost();

    @Query("select coalesce(sum(s.quantity * s.product.sellingPrice), 0) from StockLevel s")
    BigDecimal inventoryValueAtRetail();

    @Query("select coalesce(sum(s.quantity), 0) from StockLevel s")
    long totalUnits();
}
