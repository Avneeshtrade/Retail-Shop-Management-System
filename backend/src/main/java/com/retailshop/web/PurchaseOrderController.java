package com.retailshop.web;

import com.retailshop.domain.Enums.PurchaseOrderStatus;
import com.retailshop.domain.Payment;
import com.retailshop.domain.PurchaseOrder;
import com.retailshop.dto.Requests;
import com.retailshop.service.PaymentService;
import com.retailshop.service.PurchaseOrderService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/purchase-orders")
@PreAuthorize(Roles.PROCURE)
public class PurchaseOrderController {
    private final PurchaseOrderService service;
    private final PaymentService payments;

    public PurchaseOrderController(PurchaseOrderService service, PaymentService payments) {
        this.service = service;
        this.payments = payments;
    }

    @GetMapping
    public List<PurchaseOrder> list(@RequestParam(required = false) PurchaseOrderStatus status,
                                    @RequestParam(required = false) Long supplierId) {
        return service.list(status, supplierId);
    }

    @GetMapping("/{id}")
    public PurchaseOrder get(@PathVariable Long id) { return service.get(id); }

    @PostMapping
    public PurchaseOrder create(@Valid @RequestBody Requests.PurchaseOrderReq req) { return service.create(req); }

    @PutMapping("/{id}")
    public PurchaseOrder update(@PathVariable Long id, @Valid @RequestBody Requests.PurchaseOrderReq req) {
        return service.update(id, req);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize(Roles.MANAGE + " or hasRole('ACCOUNTANT')")
    public PurchaseOrder submit(@PathVariable Long id) { return service.submit(id); }

    @PostMapping("/{id}/receive")
    @PreAuthorize(Roles.STOCK)
    public PurchaseOrder receive(@PathVariable Long id, @Valid @RequestBody(required = false) Requests.ReceiveReq req) {
        return service.receive(id, req);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(Roles.MANAGE)
    public PurchaseOrder cancel(@PathVariable Long id) { return service.cancel(id); }

    @GetMapping("/{id}/payments")
    public List<Payment> payments(@PathVariable Long id) { return payments.forPurchaseOrder(id); }

    @PostMapping("/{id}/payments")
    @PreAuthorize(Roles.FINANCE)
    public Payment pay(@PathVariable Long id, @Valid @RequestBody Requests.PaymentReq req) { return service.pay(id, req); }

    /** Drafts POs for every low-stock product, grouped by preferred supplier. */
    @PostMapping("/auto-reorder")
    @PreAuthorize(Roles.MANAGE + " or hasRole('WAREHOUSE')")
    public List<PurchaseOrder> autoReorder(@RequestParam Long warehouseId) { return service.autoReorder(warehouseId); }
}
