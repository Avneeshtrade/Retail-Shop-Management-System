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

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByPaidAtBetweenOrderByPaidAtDesc(LocalDateTime from, LocalDateTime to);
    List<Payment> findBySalesOrderIdOrderByPaidAtDesc(Long salesOrderId);
    List<Payment> findByPurchaseOrderIdOrderByPaidAtDesc(Long purchaseOrderId);
    List<Payment> findByExpenseId(Long expenseId);

    @Query("select p.method, p.type, count(p), sum(p.amount) from Payment p where p.status = com.retailshop.domain.Enums.TransactionStatus.COMPLETED " +
           "and p.paidAt between :from and :to group by p.method, p.type")
    List<Object[]> summary(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
