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

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {
    List<SalesOrder> findByOrderDateBetweenOrderByOrderDateDesc(LocalDateTime from, LocalDateTime to);
    List<SalesOrder> findByCustomerIdOrderByOrderDateDesc(Long customerId);

    /** Orders that count as revenue (not cancelled, not pending). */
    @Query("select o from SalesOrder o where o.orderDate between :from and :to and o.status in " +
           "(com.retailshop.domain.Enums.SalesOrderStatus.COMPLETED, com.retailshop.domain.Enums.SalesOrderStatus.SHIPPED, " +
           "com.retailshop.domain.Enums.SalesOrderStatus.DELIVERED, com.retailshop.domain.Enums.SalesOrderStatus.REFUNDED)")
    List<SalesOrder> findRevenueOrders(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select i.product.id, i.product.name, i.product.sku, sum(i.quantity), sum(i.lineTotal - i.taxAmount), sum(i.unitCost * i.quantity) " +
           "from SalesOrderItem i where i.salesOrder.orderDate between :from and :to and i.salesOrder.status in " +
           "(com.retailshop.domain.Enums.SalesOrderStatus.COMPLETED, com.retailshop.domain.Enums.SalesOrderStatus.SHIPPED, com.retailshop.domain.Enums.SalesOrderStatus.DELIVERED) " +
           "group by i.product.id, i.product.name, i.product.sku order by sum(i.lineTotal - i.taxAmount) desc")
    List<Object[]> productPerformance(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(i.product.category.name, 'Uncategorized'), sum(i.quantity), sum(i.lineTotal - i.taxAmount) " +
           "from SalesOrderItem i left join i.product.category where i.salesOrder.orderDate between :from and :to and i.salesOrder.status in " +
           "(com.retailshop.domain.Enums.SalesOrderStatus.COMPLETED, com.retailshop.domain.Enums.SalesOrderStatus.SHIPPED, com.retailshop.domain.Enums.SalesOrderStatus.DELIVERED) " +
           "group by i.product.category.name")
    List<Object[]> categoryPerformance(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(o.total - o.amountPaid), 0) from SalesOrder o where o.status not in " +
           "(com.retailshop.domain.Enums.SalesOrderStatus.CANCELLED, com.retailshop.domain.Enums.SalesOrderStatus.REFUNDED)")
    BigDecimal accountsReceivable();

    long countByStatus(Enums.SalesOrderStatus status);
}
