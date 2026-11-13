package com.retailshop.domain;

/** Namespace holder for all domain enums. */
public final class Enums {
    private Enums() {}

    public enum Role { ADMIN, MANAGER, CASHIER, WAREHOUSE, ACCOUNTANT }

    public enum WarehouseType { STORE, WAREHOUSE }

    public enum MovementType { PURCHASE_RECEIPT, SALE, SALE_RETURN, ADJUSTMENT, TRANSFER_OUT, TRANSFER_IN }

    public enum PurchaseOrderStatus { DRAFT, ORDERED, PARTIALLY_RECEIVED, RECEIVED, CANCELLED }

    public enum SalesOrderStatus { PENDING, COMPLETED, SHIPPED, DELIVERED, CANCELLED, REFUNDED }

    public enum SalesChannel { IN_STORE, ONLINE, PHONE }

    public enum PaymentStatus { UNPAID, PARTIAL, PAID, REFUNDED }

    public enum PaymentMethod { CASH, CARD, UPI, BANK_TRANSFER, WALLET, CHEQUE }

    public enum PaymentType { CUSTOMER_PAYMENT, CUSTOMER_REFUND, SUPPLIER_PAYMENT, EXPENSE_PAYMENT }

    public enum TransactionStatus { PENDING, COMPLETED, FAILED }

    public enum ShipmentType { INBOUND, OUTBOUND, TRANSFER }

    public enum ShipmentStatus { PENDING, PACKED, IN_TRANSIT, DELIVERED, CANCELLED, RETURNED }

    public enum ExpenseCategory { RENT, UTILITIES, SALARIES, MARKETING, MAINTENANCE, LOGISTICS, SUPPLIES, TAXES, OTHER }
}
