package com.retailshop.dto;

import com.retailshop.domain.Product;
import com.retailshop.domain.User;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Outbound payloads that are not plain entities. */
public final class Responses {
    private Responses() {}

    public record LoginResp(String token, UserView user) {}

    public record UserView(Long id, String username, String fullName, String email, String phone,
                           String role, boolean active) {
        public static UserView of(User u) {
            return new UserView(u.getId(), u.getUsername(), u.getFullName(), u.getEmail(), u.getPhone(),
                    u.getRole().name(), u.isActive());
        }
    }

    public record ProductStock(Product product, long totalQuantity, boolean lowStock,
                               List<WarehouseQty> byWarehouse) {}

    public record WarehouseQty(Long warehouseId, String warehouseCode, String warehouseName, int quantity) {}

    // ---------- Analytics ----------

    public record Kpis(BigDecimal todaySales, long todayOrders,
                       BigDecimal monthSales, long monthOrders, BigDecimal monthGrossProfit,
                       BigDecimal monthExpenses, BigDecimal monthNetProfit, BigDecimal avgOrderValue,
                       BigDecimal inventoryValueCost, BigDecimal inventoryValueRetail, long inventoryUnits,
                       long lowStockCount, long outOfStockCount,
                       BigDecimal accountsReceivable, BigDecimal accountsPayable,
                       long pendingShipments, long openPurchaseOrders, long pendingOrders,
                       BigDecimal salesGrowthPct) {}

    public record DailyPoint(LocalDate date, BigDecimal revenue, long orders, BigDecimal profit) {}

    public record ProductPerf(Long productId, String name, String sku, long quantity,
                              BigDecimal revenue, BigDecimal cost, BigDecimal profit, BigDecimal marginPct) {}

    public record NamedValue(String name, BigDecimal value, long count) {}

    public record LowStockItem(Long productId, String sku, String name, long quantity, int reorderLevel,
                               int suggestedOrderQty, String supplier) {}

    public record Dashboard(Kpis kpis, List<DailyPoint> salesTrend, List<ProductPerf> topProducts,
                            List<NamedValue> paymentMethods, List<NamedValue> salesByCategory,
                            List<LowStockItem> lowStock, List<Map<String, Object>> recentOrders) {}

    public record SalesReport(LocalDate from, LocalDate to, BigDecimal grossSales, BigDecimal discounts,
                              BigDecimal netSales, BigDecimal tax, BigDecimal refunds, BigDecimal costOfGoods,
                              BigDecimal grossProfit, BigDecimal marginPct, long orders, long itemsSold,
                              BigDecimal avgOrderValue,
                              List<DailyPoint> daily, List<NamedValue> byChannel, List<NamedValue> byHour,
                              List<NamedValue> byWeekday, List<NamedValue> byCategory,
                              List<ProductPerf> topProducts, List<NamedValue> topCustomers,
                              List<NamedValue> byCashier) {}

    public record MonthlyPnl(String month, BigDecimal revenue, BigDecimal cogs, BigDecimal expenses,
                             BigDecimal netProfit) {}

    public record ProfitLoss(LocalDate from, LocalDate to, BigDecimal revenue, BigDecimal costOfGoods,
                             BigDecimal grossProfit, BigDecimal grossMarginPct, BigDecimal shippingCosts,
                             BigDecimal operatingExpenses, BigDecimal netProfit, BigDecimal netMarginPct,
                             BigDecimal purchases, List<NamedValue> expensesByCategory,
                             List<MonthlyPnl> monthly) {}

    public record InventoryReport(BigDecimal valueAtCost, BigDecimal valueAtRetail, BigDecimal potentialProfit,
                                  long totalUnits, long skuCount, long outOfStock,
                                  BigDecimal turnoverRatio90d, BigDecimal daysOfInventory,
                                  List<NamedValue> byWarehouse, List<NamedValue> byCategory,
                                  List<LowStockItem> reorderSuggestions,
                                  List<NamedValue> deadStock) {}

    public record CarrierPerf(String carrier, long shipments, long delivered, long onTime,
                              BigDecimal onTimePct, BigDecimal totalCost) {}

    public record LogisticsReport(Map<String, Long> byStatus, List<CarrierPerf> carriers,
                                  BigDecimal avgDeliveryDays, BigDecimal totalShippingCost,
                                  long inTransit, long delayed) {}

    public record CashFlowPoint(LocalDate date, BigDecimal inflow, BigDecimal outflow, BigDecimal net) {}

    public record PaymentsReport(BigDecimal inflow, BigDecimal outflow, BigDecimal net,
                                 List<NamedValue> byMethod, List<NamedValue> byType,
                                 List<CashFlowPoint> daily) {}
}
