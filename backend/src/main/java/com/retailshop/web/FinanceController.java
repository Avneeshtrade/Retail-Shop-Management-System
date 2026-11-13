package com.retailshop.web;

import com.retailshop.domain.Enums.PaymentMethod;
import com.retailshop.domain.Enums.PaymentType;
import com.retailshop.domain.Expense;
import com.retailshop.domain.Payment;
import com.retailshop.dto.Requests;
import com.retailshop.service.ExpenseService;
import com.retailshop.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api")
@PreAuthorize(Roles.FINANCE)
public class FinanceController {
    private final PaymentService payments;
    private final ExpenseService expenses;

    public FinanceController(PaymentService payments, ExpenseService expenses) {
        this.payments = payments;
        this.expenses = expenses;
    }

    @GetMapping("/payments")
    public List<Payment> payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) PaymentType type,
                                  @RequestParam(required = false) PaymentMethod method) {
        return payments.list(from, to, type, method);
    }

    @GetMapping("/expenses")
    public List<Expense> expenses(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return expenses.list(from, to);
    }

    @PostMapping("/expenses")
    public Expense create(@Valid @RequestBody Requests.ExpenseReq req) { return expenses.create(req); }

    @PutMapping("/expenses/{id}")
    public Expense update(@PathVariable Long id, @Valid @RequestBody Requests.ExpenseReq req) { return expenses.update(id, req); }

    @DeleteMapping("/expenses/{id}")
    public void delete(@PathVariable Long id) { expenses.delete(id); }
}
