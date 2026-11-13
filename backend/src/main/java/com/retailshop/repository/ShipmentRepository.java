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

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    List<Shipment> findAllByOrderByCreatedAtDesc();
    List<Shipment> findByStatusInOrderByCreatedAtDesc(List<Enums.ShipmentStatus> statuses);
    List<Shipment> findBySalesOrderId(Long salesOrderId);
    long countByStatusIn(List<Enums.ShipmentStatus> statuses);

    @Query("select s.status, count(s) from Shipment s group by s.status")
    List<Object[]> countByStatus();

}
