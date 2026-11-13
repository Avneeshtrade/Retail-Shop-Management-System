package com.retailshop.web;

import com.retailshop.domain.Enums.SalesOrderStatus;
import com.retailshop.domain.Payment;
import com.retailshop.domain.SalesOrder;
import com.retailshop.dto.Requests;
import com.retailshop.service.PaymentService;
import com.retailshop.service.SalesService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales-orders")
public class SalesController {
    private final SalesService sales;
    private final PaymentService payments;

    public SalesController(SalesService sales, PaymentService payments) {
        this.sales = sales;
        this.payments = payments;
    }

    @GetMapping
    public List<SalesOrder> list(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                 @RequestParam(required = false) SalesOrderStatus status,
                                 @RequestParam(required = false) Long customerId) {
        return sales.list(from, to, status, customerId);
    }

    @GetMapping("/{id}")
    public SalesOrder get(@PathVariable Long id) { return sales.get(id); }

    /** POS checkout / new order. */
    @PostMapping
    @PreAuthorize(Roles.SELL)
    public SalesOrder create(@Valid @RequestBody Requests.SalesOrderReq req) { return sales.create(req); }

    @GetMapping("/{id}/payments")
    public List<Payment> payments(@PathVariable Long id) { return payments.forSalesOrder(id); }

    @PostMapping("/{id}/payments")
    @PreAuthorize(Roles.SELL + " or hasRole('ACCOUNTANT')")
    public Payment pay(@PathVariable Long id, @Valid @RequestBody Requests.PaymentReq req) { return sales.addPayment(id, req); }

    @PostMapping("/{id}/refund")
    @PreAuthorize(Roles.MANAGE)
    public Payment refund(@PathVariable Long id, @Valid @RequestBody Requests.RefundReq req) { return sales.refund(id, req); }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(Roles.MANAGE)
    public SalesOrder cancel(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        return sales.cancel(id, body == null ? "No reason given" : body.getOrDefault("reason", "No reason given"));
    }
}
