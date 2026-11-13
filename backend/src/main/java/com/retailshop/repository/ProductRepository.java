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

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findBySku(String sku);
    Optional<Product> findByBarcode(String barcode);
    boolean existsBySku(String sku);

    @Query("select p from Product p where (:q is null or lower(p.name) like lower(concat('%', :q, '%')) " +
           "or lower(p.sku) like lower(concat('%', :q, '%')) or p.barcode = :q) " +
           "and (:categoryId is null or p.category.id = :categoryId) order by p.name")
    List<Product> search(@Param("q") String q, @Param("categoryId") Long categoryId);
}
