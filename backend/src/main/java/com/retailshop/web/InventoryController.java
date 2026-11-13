package com.retailshop.web;

import com.retailshop.common.NotFoundException;
import com.retailshop.domain.StockMovement;
import com.retailshop.domain.Warehouse;
import com.retailshop.dto.Requests;
import com.retailshop.dto.Responses.ProductStock;
import com.retailshop.repository.WarehouseRepository;
import com.retailshop.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class InventoryController {
    private final InventoryService inventory;
    private final WarehouseRepository warehouses;

    public InventoryController(InventoryService inventory, WarehouseRepository warehouses) {
        this.inventory = inventory;
        this.warehouses = warehouses;
    }

    @GetMapping("/inventory")
    public List<ProductStock> stock(@RequestParam(required = false) Long warehouseId,
                                    @RequestParam(defaultValue = "false") boolean lowOnly) {
        return inventory.overview(warehouseId, lowOnly);
    }

    @GetMapping("/inventory/available")
    public int available(@RequestParam Long productId, @RequestParam Long warehouseId) {
        return inventory.available(productId, warehouseId);
    }

    @GetMapping("/inventory/movements")
    @PreAuthorize(Roles.STOCK + " or " + Roles.FINANCE)
    public List<StockMovement> movements(@RequestParam(required = false) Long productId,
                                         @RequestParam(required = false) Long warehouseId) {
        return inventory.movements(productId, warehouseId);
    }

    @PostMapping("/inventory/adjustments")
    @PreAuthorize(Roles.STOCK)
    public StockMovement adjust(@Valid @RequestBody Requests.StockAdjustment req) {
        return inventory.adjust(req);
    }

    @GetMapping("/warehouses")
    public List<Warehouse> warehouses() { return warehouses.findAll(Sort.by("code")); }

    @PostMapping("/warehouses")
    @PreAuthorize(Roles.MANAGE)
    public Warehouse create(@Valid @RequestBody Requests.WarehouseReq req) {
        Warehouse w = new Warehouse();
        apply(w, req);
        return warehouses.save(w);
    }

    @PutMapping("/warehouses/{id}")
    @PreAuthorize(Roles.MANAGE)
    @Transactional
    public Warehouse update(@PathVariable Long id, @Valid @RequestBody Requests.WarehouseReq req) {
        Warehouse w = warehouses.findById(id).orElseThrow(() -> new NotFoundException("Warehouse", id));
        apply(w, req);
        return w;
    }

    private static void apply(Warehouse w, Requests.WarehouseReq r) {
        w.setCode(r.code().trim().toUpperCase());
        w.setName(r.name());
        w.setAddress(r.address());
        w.setType(r.type());
        if (r.active() != null) w.setActive(r.active());
    }
}
