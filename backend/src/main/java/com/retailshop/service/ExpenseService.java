package com.retailshop.service;

import com.retailshop.common.Money;
import com.retailshop.common.NotFoundException;
import com.retailshop.domain.Enums.PaymentType;
import com.retailshop.domain.Expense;
import com.retailshop.dto.Requests;
import com.retailshop.repository.ExpenseRepository;
import com.retailshop.repository.PaymentRepository;
import com.retailshop.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional
public class ExpenseService {
    private final ExpenseRepository expenses;
    private final PaymentRepository paymentRepo;
    private final PaymentService payments;

    public ExpenseService(ExpenseRepository expenses, PaymentRepository paymentRepo, PaymentService payments) {
        this.expenses = expenses;
        this.paymentRepo = paymentRepo;
        this.payments = payments;
    }

    @Transactional(readOnly = true)
    public List<Expense> list(LocalDate from, LocalDate to) {
        return expenses.findByExpenseDateBetweenOrderByExpenseDateDesc(
                from == null ? LocalDate.now().minusDays(90) : from, to == null ? LocalDate.now() : to);
    }

    /** Records an expense and, when a payment method is given, the matching outgoing payment. */
    public Expense create(Requests.ExpenseReq req) {
        Expense e = new Expense();
        apply(e, req);
        e.setRecordedBy(CurrentUser.username());
        e = expenses.save(e);
        if (req.paymentMethod() != null) {
            payments.record(PaymentType.EXPENSE_PAYMENT, req.paymentMethod(), e.getAmount(), null, e.getVendor(),
                    null, null, e, e.getCategory() + ": " + e.getDescription(), e.getExpenseDate().atTime(12, 0));
        }
        return e;
    }

    public Expense update(Long id, Requests.ExpenseReq req) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense", id));
        apply(e, req);
        paymentRepo.findByExpenseId(id)
                .forEach(p -> { p.setAmount(e.getAmount()); if (req.paymentMethod() != null) p.setMethod(req.paymentMethod()); });
        return e;
    }

    public void delete(Long id) {
        Expense e = expenses.findById(id).orElseThrow(() -> new NotFoundException("Expense", id));
        paymentRepo.findByExpenseId(id)
                .forEach(paymentRepo::delete);
        expenses.delete(e);
    }

    private static void apply(Expense e, Requests.ExpenseReq r) {
        e.setCategory(r.category());
        e.setDescription(r.description());
        e.setAmount(Money.of(r.amount()));
        e.setExpenseDate(r.expenseDate());
        e.setPaymentMethod(r.paymentMethod());
        e.setVendor(r.vendor());
    }
}
