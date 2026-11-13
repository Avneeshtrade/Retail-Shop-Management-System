package com.retailshop.dto;

import com.retailshop.domain.Enums.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** All inbound request payloads. */
public final class Requests {
    private Requests() {}

    public record Login(@NotBlank String username, @NotBlank String password) {}

    public record ChangePassword(@NotBlank String currentPassword, @NotBlank @Size(min = 6) String newPassword) {}

    public record UserReq(@NotBlank @Size(max = 60) String username,
                          String password,
                          @NotBlank String fullName,
                          @Email String email,
                          String phone,
                          @NotNull Role role,
                          Boolean active) {}

    public record CategoryReq(@NotBlank String name, String description) {}

    public record ProductReq(@NotBlank String sku,
                             String barcode,
                             @NotBlank String name,
                             String description,
                             Long categoryId,
                             Long preferredSupplierId,
                             String unit,
                             @NotNull @PositiveOrZero BigDecimal costPrice,
                             @NotNull @PositiveOrZero BigDecimal sellingPrice,
                             @PositiveOrZero BigDecimal taxRate,
                             @PositiveOrZero Integer reorderLevel,
                             @Positive Integer reorderQuantity,
                             Boolean active,
                             /** Optional opening stock, applied to openingWarehouseId on create only. */
                             @PositiveOrZero Integer openingStock,
                             Long openingWarehouseId) {}

    public record WarehouseReq(@NotBlank String code, @NotBlank String name, String address,
                               @NotNull WarehouseType type, Boolean active) {}

    public record StockAdjustment(@NotNull Long productId, @NotNull Long warehouseId,
                                  /** Signed change. Ignored when countedQuantity is set. */
                                  Integer quantityChange,
                                  /** Physical count result; the system adjusts to match. */
                                  @PositiveOrZero Integer countedQuantity,
                                  @NotBlank String reason) {}

    public record SupplierReq(@NotBlank String name, String contactName, @Email String email, String phone,
                              String address, String taxId, @PositiveOrZero Integer paymentTermsDays,
                              @PositiveOrZero Integer leadTimeDays, Boolean active) {}

    public record LineItem(@NotNull Long productId, @Positive int quantity,
                           /** Unit cost (PO) or unit price override (sales). Null = product default. */
                           @PositiveOrZero BigDecimal unitPrice,
                           @PositiveOrZero BigDecimal discount) {}

    public record PurchaseOrderReq(@NotNull Long supplierId, @NotNull Long warehouseId, LocalDate expectedDate,
                                   @PositiveOrZero BigDecimal shippingCost, String notes,
                                   @NotEmpty List<@Valid LineItem> items) {}

    public record ReceiveLine(@NotNull Long itemId, @PositiveOrZero int quantity) {}

    /** Empty / null lines = receive everything outstanding. */
    public record ReceiveReq(List<@Valid ReceiveLine> lines) {}

    public record PaymentLine(@NotNull PaymentMethod method, @NotNull @Positive BigDecimal amount,
                              String transactionReference) {}

    public record SalesOrderReq(Long customerId, @NotNull Long warehouseId, SalesChannel channel,
                                @PositiveOrZero BigDecimal discountAmount, @PositiveOrZero BigDecimal shippingCharge,
                                String shippingAddress, String notes,
                                @NotEmpty List<@Valid LineItem> items,
                                List<@Valid PaymentLine> payments) {}

    public record PaymentReq(@NotNull PaymentMethod method, @NotNull @Positive BigDecimal amount,
                             String transactionReference, String notes) {}

    public record RefundReq(/** Null = refund everything paid. */ @Positive BigDecimal amount,
                            @NotNull PaymentMethod method, boolean restock, @NotBlank String reason) {}

    public record CustomerReq(@NotBlank String name, @Email String email, String phone, String address) {}

    public record ShipmentReq(@NotNull ShipmentType type, String carrier, String trackingNumber,
                              Long salesOrderId, Long purchaseOrderId,
                              Long fromWarehouseId, Long toWarehouseId,
                              String originAddress, String destinationAddress,
                              @PositiveOrZero BigDecimal shippingCost, LocalDate estimatedDelivery, String notes,
                              List<@Valid ShipmentLine> items) {}

    public record ShipmentLine(@NotNull Long productId, @Positive int quantity) {}

    public record ShipmentStatusReq(@NotNull ShipmentStatus status, String trackingNumber, String note) {}

    public record ExpenseReq(@NotNull ExpenseCategory category, @NotBlank String description,
                             @NotNull @Positive BigDecimal amount, @NotNull LocalDate expenseDate,
                             PaymentMethod paymentMethod, String vendor) {}
}
