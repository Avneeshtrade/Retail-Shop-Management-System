package com.retailshop.web;

/** SpEL snippets for @PreAuthorize, kept in one place so the permission model is easy to audit. */
final class Roles {
    private Roles() {}
    static final String ADMIN = "hasRole('ADMIN')";
    static final String MANAGE = "hasAnyRole('ADMIN','MANAGER')";
    static final String SELL = "hasAnyRole('ADMIN','MANAGER','CASHIER')";
    static final String STOCK = "hasAnyRole('ADMIN','MANAGER','WAREHOUSE')";
    static final String FINANCE = "hasAnyRole('ADMIN','MANAGER','ACCOUNTANT')";
    static final String PROCURE = "hasAnyRole('ADMIN','MANAGER','WAREHOUSE','ACCOUNTANT')";
}
