package com.retailshop.web;

import com.retailshop.domain.Enums.ShipmentStatus;
import com.retailshop.domain.Enums.ShipmentType;
import com.retailshop.domain.Shipment;
import com.retailshop.dto.Requests;
import com.retailshop.service.ShipmentService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shipments")
public class LogisticsController {
    private final ShipmentService shipments;

    public LogisticsController(ShipmentService shipments) {
        this.shipments = shipments;
    }

    @GetMapping
    public List<Shipment> list(@RequestParam(required = false) ShipmentStatus status,
                               @RequestParam(required = false) ShipmentType type) {
        return shipments.list(status, type);
    }

    @GetMapping("/{id}")
    public Shipment get(@PathVariable Long id) { return shipments.get(id); }

    @PostMapping
    @PreAuthorize(Roles.STOCK)
    public Shipment create(@Valid @RequestBody Requests.ShipmentReq req) { return shipments.create(req); }

    @PostMapping("/{id}/status")
    @PreAuthorize(Roles.STOCK)
    public Shipment status(@PathVariable Long id, @Valid @RequestBody Requests.ShipmentStatusReq req) {
        return shipments.updateStatus(id, req);
    }
}
