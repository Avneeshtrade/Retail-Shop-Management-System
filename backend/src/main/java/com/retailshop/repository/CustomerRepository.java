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

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPhone(String phone);

    @Query("select c from Customer c where :q is null or lower(c.name) like lower(concat('%', :q, '%')) " +
           "or c.phone like concat('%', :q, '%') or lower(c.email) like lower(concat('%', :q, '%')) order by c.name")
    List<Customer> search(@Param("q") String q);

    List<Customer> findTop10ByOrderByTotalSpentDesc();
}
