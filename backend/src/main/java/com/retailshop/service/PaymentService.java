package com.retailshop.service;

import com.retailshop.common.DocumentNumbers;
import com.retailshop.common.Money;
import com.retailshop.domain.*;
import com.retailshop.domain.Enums.*;
import com.retailshop.repository.PaymentRepository;
import com.retailshop.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/** Ledger of all money movements (customer receipts, refunds, supplier payments, expenses). */
@Service
@Transactional
public class PaymentService {
    private final PaymentRepository payments;

    public PaymentService(PaymentRepository payments) {
        this.payments = payments;
    }

    public Payment record(PaymentType type, PaymentMethod method, BigDecimal amount, String txRef, String party,
                          SalesOrder so, PurchaseOrder po, Expense expense, String notes, LocalDateTime paidAt) {
        Payment p = new Payment();
        LocalDateTime when = paidAt == null ? LocalDateTime.now() : paidAt;
        String prefix = switch (type) {
            case CUSTOMER_PAYMENT -> "RCPT";
            case CUSTOMER_REFUND -> "RFND";
            case SUPPLIER_PAYMENT -> "SPAY";
            case EXPENSE_PAYMENT -> "EXP";
        };
        p.setPaymentNumber(DocumentNumbers.next(prefix, when.toLocalDate()));
        p.setType(type);
        p.setMethod(method);
        p.setStatus(TransactionStatus.COMPLETED);
        p.setAmount(Money.of(amount));
        p.setTransactionReference(txRef);
        p.setPartyName(party);
        p.setSalesOrder(so);
        p.setPurchaseOrder(po);
        p.setExpense(expense);
        p.setNotes(notes);
        p.setPaidAt(when);
        p.setReceivedBy(CurrentUser.username());
        return payments.save(p);
    }

    @Transactional(readOnly = true)
    public List<Payment> list(LocalDate from, LocalDate to, PaymentType type, PaymentMethod method) {
        LocalDateTime f = (from == null ? LocalDate.now().minusDays(30) : from).atStartOfDay();
        LocalDateTime t = (to == null ? LocalDate.now() : to).atTime(LocalTime.MAX);
        return payments.findByPaidAtBetweenOrderByPaidAtDesc(f, t).stream()
                .filter(p -> type == null || p.getType() == type)
                .filter(p -> method == null || p.getMethod() == method)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Payment> forSalesOrder(Long id) {
        return payments.findBySalesOrderIdOrderByPaidAtDesc(id);
    }

    @Transactional(readOnly = true)
    public List<Payment> forPurchaseOrder(Long id) {
        return payments.findByPurchaseOrderIdOrderByPaidAtDesc(id);
    }
}
