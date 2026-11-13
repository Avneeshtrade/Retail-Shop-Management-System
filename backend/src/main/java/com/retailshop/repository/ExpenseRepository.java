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

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    List<Expense> findByExpenseDateBetweenOrderByExpenseDateDesc(LocalDate from, LocalDate to);

    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.expenseDate between :from and :to")
    BigDecimal total(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select e.category, sum(e.amount) from Expense e where e.expenseDate between :from and :to group by e.category")
    List<Object[]> byCategory(@Param("from") LocalDate from, @Param("to") LocalDate to);
}
