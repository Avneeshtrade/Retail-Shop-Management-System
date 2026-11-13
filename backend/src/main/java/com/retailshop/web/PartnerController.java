package com.retailshop.web;

import com.retailshop.common.NotFoundException;
import com.retailshop.domain.Customer;
import com.retailshop.domain.Supplier;
import com.retailshop.dto.Requests;
import com.retailshop.repository.CustomerRepository;
import com.retailshop.repository.SupplierRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Suppliers and customers. */
@RestController
@RequestMapping("/api")
@Transactional
public class PartnerController {
    private final SupplierRepository suppliers;
    private final CustomerRepository customers;

    public PartnerController(SupplierRepository suppliers, CustomerRepository customers) {
        this.suppliers = suppliers;
        this.customers = customers;
    }

    @GetMapping("/suppliers")
    public List<Supplier> suppliers() { return suppliers.findAll(Sort.by("name")); }

    @GetMapping("/suppliers/{id}")
    public Supplier supplier(@PathVariable Long id) {
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Supplier", id));
    }

    @PostMapping("/suppliers")
    @PreAuthorize(Roles.PROCURE)
    public Supplier createSupplier(@Valid @RequestBody Requests.SupplierReq req) {
        Supplier s = new Supplier();
        apply(s, req);
        return suppliers.save(s);
    }

    @PutMapping("/suppliers/{id}")
    @PreAuthorize(Roles.PROCURE)
    public Supplier updateSupplier(@PathVariable Long id, @Valid @RequestBody Requests.SupplierReq req) {
        Supplier s = supplier(id);
        apply(s, req);
        return s;
    }

    private static void apply(Supplier s, Requests.SupplierReq r) {
        s.setName(r.name());
        s.setContactName(r.contactName());
        s.setEmail(r.email());
        s.setPhone(r.phone());
        s.setAddress(r.address());
        s.setTaxId(r.taxId());
        if (r.paymentTermsDays() != null) s.setPaymentTermsDays(r.paymentTermsDays());
        if (r.leadTimeDays() != null) s.setLeadTimeDays(r.leadTimeDays());
        if (r.active() != null) s.setActive(r.active());
    }

    @GetMapping("/customers")
    public List<Customer> customers(@RequestParam(required = false) String q) {
        return customers.search(q == null || q.isBlank() ? null : q.trim());
    }

    @GetMapping("/customers/{id}")
    public Customer customer(@PathVariable Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Customer", id));
    }

    @PostMapping("/customers")
    @PreAuthorize(Roles.SELL)
    public Customer createCustomer(@Valid @RequestBody Requests.CustomerReq req) {
        Customer c = new Customer();
        apply(c, req);
        return customers.save(c);
    }

    @PutMapping("/customers/{id}")
    @PreAuthorize(Roles.SELL)
    public Customer updateCustomer(@PathVariable Long id, @Valid @RequestBody Requests.CustomerReq req) {
        Customer c = customer(id);
        apply(c, req);
        return c;
    }

    private static void apply(Customer c, Requests.CustomerReq r) {
        c.setName(r.name());
        c.setEmail(r.email());
        c.setPhone(r.phone() == null || r.phone().isBlank() ? null : r.phone().trim());
        c.setAddress(r.address());
    }
}
