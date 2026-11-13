package com.retailshop.service;

import com.retailshop.common.Money;
import com.retailshop.domain.*;
import com.retailshop.domain.Enums.*;
import com.retailshop.dto.Responses.*;
import com.retailshop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Read-only reporting across sales, inventory, finance, payments and logistics. */
@Service
@Transactional(readOnly = true)
public class AnalyticsService {
    private final SalesOrderRepository salesOrders;
    private final PurchaseOrderRepository purchaseOrders;
    private final StockLevelRepository stockLevels;
    private final ProductRepository products;
    private final ExpenseRepository expenses;
    private final PaymentRepository payments;
    private final ShipmentRepository shipments;

    public AnalyticsService(SalesOrderRepository salesOrders, PurchaseOrderRepository purchaseOrders,
                            StockLevelRepository stockLevels,
                            ProductRepository products, ExpenseRepository expenses, PaymentRepository payments,
                            ShipmentRepository shipments) {
        this.salesOrders = salesOrders;
        this.purchaseOrders = purchaseOrders;
        this.stockLevels = stockLevels;
        this.products = products;
        this.expenses = expenses;
        this.payments = payments;
        this.shipments = shipments;
    }

    // ---------------------------------------------------------------- helpers

    private static LocalDateTime start(LocalDate d) { return d.atStartOfDay(); }
    private static LocalDateTime end(LocalDate d) { return d.atTime(LocalTime.MAX); }

    /** Net revenue excl. tax and shipping, after order discount and refunds (pro-rata). */
    private static BigDecimal netRevenue(SalesOrder o) {
        BigDecimal net = o.getSubtotal().subtract(o.getDiscountAmount());
        if (o.getAmountRefunded().signum() > 0 && o.getTotal().signum() > 0) {
            BigDecimal ratio = BigDecimal.ONE.subtract(o.getAmountRefunded().divide(o.getTotal(), 6, RoundingMode.HALF_UP));
            net = net.multiply(ratio.max(BigDecimal.ZERO));
        }
        return net;
    }

    private static BigDecimal cogs(SalesOrder o) {
        // Fully refunded orders whose stock came back carry no cost of goods.
        return o.getStatus() == SalesOrderStatus.REFUNDED ? BigDecimal.ZERO : o.getCostOfGoods();
    }

    private static BigDecimal pct(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return BigDecimal.ZERO;
        return part.multiply(Money.HUNDRED).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static <T> List<NamedValue> group(Collection<T> items, Function<T, String> key, Function<T, BigDecimal> value) {
        Map<String, BigDecimal> sums = new LinkedHashMap<>();
        Map<String, Long> counts = new HashMap<>();
        for (T t : items) {
            String k = key.apply(t);
            sums.merge(k, value.apply(t), BigDecimal::add);
            counts.merge(k, 1L, Long::sum);
        }
        return sums.entrySet().stream()
                .map(e -> new NamedValue(e.getKey(), Money.of(e.getValue()), counts.get(e.getKey())))
                .sorted(Comparator.comparing(NamedValue::value).reversed())
                .toList();
    }

    private List<DailyPoint> daily(List<SalesOrder> orders, LocalDate from, LocalDate to) {
        Map<LocalDate, List<SalesOrder>> byDay = orders.stream().collect(Collectors.groupingBy(o -> o.getOrderDate().toLocalDate()));
        List<DailyPoint> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            List<SalesOrder> day = byDay.getOrDefault(d, List.of());
            BigDecimal rev = day.stream().map(AnalyticsService::netRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal cost = day.stream().map(AnalyticsService::cogs).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new DailyPoint(d, Money.of(rev), day.size(), Money.of(rev.subtract(cost))));
        }
        return out;
    }

    private List<ProductPerf> productPerf(LocalDate from, LocalDate to, int limit) {
        return salesOrders.productPerformance(start(from), end(to)).stream().limit(limit).map(r -> {
            BigDecimal revenue = Money.of((BigDecimal) r[4]);
            BigDecimal cost = Money.of((BigDecimal) r[5]);
            BigDecimal profit = revenue.subtract(cost);
            return new ProductPerf((Long) r[0], (String) r[1], (String) r[2], ((Number) r[3]).longValue(),
                    revenue, cost, profit, pct(profit, revenue));
        }).toList();
    }

    private List<NamedValue> categoryPerf(LocalDate from, LocalDate to) {
        return salesOrders.categoryPerformance(start(from), end(to)).stream()
                .map(r -> new NamedValue(r[0] == null ? "Uncategorized" : (String) r[0], Money.of((BigDecimal) r[2]), ((Number) r[1]).longValue()))
                .sorted(Comparator.comparing(NamedValue::value).reversed()).toList();
    }

    private List<LowStockItem> lowStock() {
        List<LowStockItem> out = new ArrayList<>();
        for (Product p : products.findAll()) {
            if (!p.isActive()) continue;
            long qty = stockLevels.totalQuantity(p.getId());
            if (qty <= p.getReorderLevel()) {
                int suggested = (int) Math.max(p.getReorderQuantity(), p.getReorderLevel() * 2L - qty);
                out.add(new LowStockItem(p.getId(), p.getSku(), p.getName(), qty, p.getReorderLevel(), suggested,
                        p.getPreferredSupplier() == null ? null : p.getPreferredSupplier().getName()));
            }
        }
        out.sort(Comparator.comparingLong(LowStockItem::quantity));
        return out;
    }

    // ---------------------------------------------------------------- dashboard

    public Dashboard dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        LocalDate trendFrom = today.minusDays(29);

        List<SalesOrder> todayOrders = salesOrders.findRevenueOrders(start(today), end(today));
        List<SalesOrder> monthOrders = salesOrders.findRevenueOrders(start(monthStart), end(today));
        // Same number of days in the previous month for a like-for-like growth figure.
        LocalDate prevStart = monthStart.minusMonths(1);
        LocalDate prevEnd = prevStart.plusDays(ChronoUnit.DAYS.between(monthStart, today));
        List<SalesOrder> prevOrders = salesOrders.findRevenueOrders(start(prevStart), end(prevEnd));

        BigDecimal todaySales = sumRevenue(todayOrders);
        BigDecimal monthSales = sumRevenue(monthOrders);
        BigDecimal prevSales = sumRevenue(prevOrders);
        BigDecimal monthGross = monthSales.subtract(monthOrders.stream().map(AnalyticsService::cogs).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal monthExpenses = expenses.total(monthStart, today);
        BigDecimal aov = monthOrders.isEmpty() ? BigDecimal.ZERO
                : monthSales.divide(BigDecimal.valueOf(monthOrders.size()), 2, RoundingMode.HALF_UP);

        List<LowStockItem> low = lowStock();
        long out = low.stream().filter(l -> l.quantity() <= 0).count();

        Kpis kpis = new Kpis(Money.of(todaySales), todayOrders.size(), Money.of(monthSales), monthOrders.size(),
                Money.of(monthGross), Money.of(monthExpenses), Money.of(monthGross.subtract(monthExpenses)), aov,
                Money.of(stockLevels.inventoryValueAtCost()), Money.of(stockLevels.inventoryValueAtRetail()),
                stockLevels.totalUnits(), low.size(), out,
                Money.of(salesOrders.accountsReceivable()), Money.of(purchaseOrders.accountsPayable()),
                shipments.countByStatusIn(List.of(ShipmentStatus.PENDING, ShipmentStatus.PACKED, ShipmentStatus.IN_TRANSIT)),
                purchaseOrders.countByStatusIn(List.of(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED)),
                salesOrders.countByStatus(SalesOrderStatus.PENDING),
                pct(monthSales.subtract(prevSales), prevSales));

        List<SalesOrder> trendOrders = salesOrders.findRevenueOrders(start(trendFrom), end(today));
        List<NamedValue> methods = paymentsBreakdown(trendFrom, today).byMethod();

        List<Map<String, Object>> recent = salesOrders.findByOrderDateBetweenOrderByOrderDateDesc(start(today.minusDays(7)), end(today))
                .stream().limit(8).map(o -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", o.getId());
                    m.put("orderNumber", o.getOrderNumber());
                    m.put("customer", o.getCustomer() == null ? "Walk-in" : o.getCustomer().getName());
                    m.put("total", o.getTotal());
                    m.put("status", o.getStatus());
                    m.put("paymentStatus", o.getPaymentStatus());
                    m.put("orderDate", o.getOrderDate());
                    return m;
                }).toList();

        return new Dashboard(kpis, daily(trendOrders, trendFrom, today), productPerf(trendFrom, today, 5),
                methods, categoryPerf(trendFrom, today), low.stream().limit(10).toList(), recent);
    }

    private static BigDecimal sumRevenue(List<SalesOrder> orders) {
        return orders.stream().map(AnalyticsService::netRevenue).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ---------------------------------------------------------------- sales

    public SalesReport sales(LocalDate from, LocalDate to) {
        List<SalesOrder> orders = salesOrders.findRevenueOrders(start(from), end(to));
        BigDecimal gross = BigDecimal.ZERO, disc = BigDecimal.ZERO, tax = BigDecimal.ZERO, refunds = BigDecimal.ZERO, cost = BigDecimal.ZERO;
        long items = 0;
        for (SalesOrder o : orders) {
            gross = gross.add(o.getSubtotal());
            disc = disc.add(o.getDiscountAmount());
            tax = tax.add(o.getTaxAmount());
            refunds = refunds.add(o.getAmountRefunded());
            cost = cost.add(cogs(o));
            if (o.getStatus() != SalesOrderStatus.REFUNDED) items += o.getItems().stream().mapToLong(SalesOrderItem::getQuantity).sum();
        }
        BigDecimal net = sumRevenue(orders);
        BigDecimal profit = net.subtract(cost);
        BigDecimal aov = orders.isEmpty() ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(orders.size()), 2, RoundingMode.HALF_UP);

        List<NamedValue> byHour = new ArrayList<>();
        Map<Integer, List<SalesOrder>> hours = orders.stream().collect(Collectors.groupingBy(o -> o.getOrderDate().getHour()));
        for (int h = 0; h < 24; h++) {
            List<SalesOrder> l = hours.getOrDefault(h, List.of());
            byHour.add(new NamedValue(String.format("%02d:00", h), Money.of(sumRevenue(l)), l.size()));
        }
        List<NamedValue> byWeekday = new ArrayList<>();
        Map<DayOfWeek, List<SalesOrder>> days = orders.stream().collect(Collectors.groupingBy(o -> o.getOrderDate().getDayOfWeek()));
        for (DayOfWeek d : DayOfWeek.values()) {
            List<SalesOrder> l = days.getOrDefault(d, List.of());
            byWeekday.add(new NamedValue(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), Money.of(sumRevenue(l)), l.size()));
        }
        List<NamedValue> topCustomers = group(orders.stream().filter(o -> o.getCustomer() != null).toList(),
                o -> o.getCustomer().getName(), AnalyticsService::netRevenue).stream().limit(10).toList();

        return new SalesReport(from, to, Money.of(gross), Money.of(disc), Money.of(net), Money.of(tax), Money.of(refunds),
                Money.of(cost), Money.of(profit), pct(profit, net), orders.size(), items, aov,
                daily(orders, from, to),
                group(orders, o -> o.getChannel().name(), AnalyticsService::netRevenue),
                byHour, byWeekday, categoryPerf(from, to), productPerf(from, to, 20), topCustomers,
                group(orders, o -> o.getCashier() == null ? "unknown" : o.getCashier(), AnalyticsService::netRevenue));
    }

    // ---------------------------------------------------------------- profit & loss

    public ProfitLoss profitLoss(LocalDate from, LocalDate to) {
        List<SalesOrder> orders = salesOrders.findRevenueOrders(start(from), end(to));
        BigDecimal revenue = sumRevenue(orders);
        BigDecimal cost = orders.stream().map(AnalyticsService::cogs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gross = revenue.subtract(cost);
        List<Shipment> ships = shipments.findAll().stream()
                .filter(s -> !s.getCreatedAt().isBefore(start(from)) && !s.getCreatedAt().isAfter(end(to)))
                .filter(s -> s.getStatus() != ShipmentStatus.CANCELLED).toList();
        BigDecimal shipping = ships.stream().map(s -> Money.nz(s.getShippingCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Expense> exp = expenses.findByExpenseDateBetweenOrderByExpenseDateDesc(from, to);
        BigDecimal opex = exp.stream().map(Expense::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal net = gross.subtract(opex).subtract(shipping);

        Map<YearMonth, List<SalesOrder>> ordersByMonth = orders.stream().collect(Collectors.groupingBy(o -> YearMonth.from(o.getOrderDate())));
        Map<YearMonth, BigDecimal> expByMonth = new HashMap<>();
        exp.forEach(e -> expByMonth.merge(YearMonth.from(e.getExpenseDate()), e.getAmount(), BigDecimal::add));
        ships.forEach(s -> expByMonth.merge(YearMonth.from(s.getCreatedAt()), Money.nz(s.getShippingCost()), BigDecimal::add));
        List<MonthlyPnl> monthly = new ArrayList<>();
        for (YearMonth m = YearMonth.from(from); !m.isAfter(YearMonth.from(to)); m = m.plusMonths(1)) {
            List<SalesOrder> mo = ordersByMonth.getOrDefault(m, List.of());
            BigDecimal r = sumRevenue(mo);
            BigDecimal c = mo.stream().map(AnalyticsService::cogs).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal e = expByMonth.getOrDefault(m, BigDecimal.ZERO);
            monthly.add(new MonthlyPnl(m.toString(), Money.of(r), Money.of(c), Money.of(e), Money.of(r.subtract(c).subtract(e))));
        }
        return new ProfitLoss(from, to, Money.of(revenue), Money.of(cost), Money.of(gross), pct(gross, revenue),
                Money.of(shipping), Money.of(opex), Money.of(net), pct(net, revenue),
                Money.of(purchaseOrders.totalPurchases(from, to)),
                group(exp, e -> e.getCategory().name(), Expense::getAmount), monthly);
    }

    // ---------------------------------------------------------------- inventory

    public InventoryReport inventory() {
        List<StockLevel> levels = stockLevels.findAll();
        BigDecimal atCost = Money.of(stockLevels.inventoryValueAtCost());
        BigDecimal atRetail = Money.of(stockLevels.inventoryValueAtRetail());
        long units = stockLevels.totalUnits();
        List<Product> active = products.findAll().stream().filter(Product::isActive).toList();
        Map<Long, Long> qtyByProduct = new HashMap<>();
        levels.forEach(l -> qtyByProduct.merge(l.getProduct().getId(), (long) l.getQuantity(), Long::sum));
        long outOfStock = active.stream().filter(p -> qtyByProduct.getOrDefault(p.getId(), 0L) <= 0).count();

        LocalDate today = LocalDate.now();
        BigDecimal cogs90 = salesOrders.findRevenueOrders(start(today.minusDays(89)), end(today)).stream()
                .map(AnalyticsService::cogs).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal turnover = atCost.signum() == 0 ? BigDecimal.ZERO : cogs90.divide(atCost, 2, RoundingMode.HALF_UP);
        BigDecimal dailyCogs = cogs90.divide(BigDecimal.valueOf(90), 4, RoundingMode.HALF_UP);
        BigDecimal daysOfInventory = dailyCogs.signum() == 0 ? BigDecimal.ZERO : atCost.divide(dailyCogs, 1, RoundingMode.HALF_UP);

        List<StockLevel> positive = levels.stream().filter(l -> l.getQuantity() > 0).toList();
        List<NamedValue> byWarehouse = group(positive, l -> l.getWarehouse().getName(),
                l -> l.getProduct().getCostPrice().multiply(BigDecimal.valueOf(l.getQuantity())));
        List<NamedValue> byCategory = group(positive,
                l -> l.getProduct().getCategory() == null ? "Uncategorized" : l.getProduct().getCategory().getName(),
                l -> l.getProduct().getCostPrice().multiply(BigDecimal.valueOf(l.getQuantity())));

        // Dead stock: on hand but no sale in the last 60 days.
        Set<Long> soldRecently = salesOrders.productPerformance(start(today.minusDays(59)), end(today)).stream()
                .map(r -> (Long) r[0]).collect(Collectors.toSet());
        List<NamedValue> dead = active.stream()
                .filter(p -> qtyByProduct.getOrDefault(p.getId(), 0L) > 0 && !soldRecently.contains(p.getId()))
                .map(p -> new NamedValue(p.getName() + " (" + p.getSku() + ")",
                        Money.of(p.getCostPrice().multiply(BigDecimal.valueOf(qtyByProduct.get(p.getId())))),
                        qtyByProduct.get(p.getId())))
                .sorted(Comparator.comparing(NamedValue::value).reversed()).toList();

        return new InventoryReport(atCost, atRetail, atRetail.subtract(atCost), units, active.size(), outOfStock,
                turnover, daysOfInventory, byWarehouse, byCategory, lowStock(), dead);
    }

    // ---------------------------------------------------------------- logistics

    public LogisticsReport logistics(LocalDate from, LocalDate to) {
        List<Shipment> all = shipments.findAll().stream()
                .filter(s -> !s.getCreatedAt().isBefore(start(from)) && !s.getCreatedAt().isAfter(end(to))).toList();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (ShipmentStatus st : ShipmentStatus.values()) byStatus.put(st.name(), 0L);
        all.forEach(s -> byStatus.merge(s.getStatus().name(), 1L, Long::sum));

        Map<String, List<Shipment>> byCarrier = all.stream().filter(s -> s.getCarrier() != null)
                .collect(Collectors.groupingBy(Shipment::getCarrier));
        List<CarrierPerf> carriers = byCarrier.entrySet().stream().map(e -> {
            List<Shipment> l = e.getValue();
            long delivered = l.stream().filter(s -> s.getStatus() == ShipmentStatus.DELIVERED).count();
            long onTime = l.stream().filter(s -> s.getStatus() == ShipmentStatus.DELIVERED && s.getEstimatedDelivery() != null
                    && s.getDeliveredAt() != null && !s.getDeliveredAt().toLocalDate().isAfter(s.getEstimatedDelivery())).count();
            BigDecimal cost = l.stream().map(s -> Money.nz(s.getShippingCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new CarrierPerf(e.getKey(), l.size(), delivered, onTime,
                    delivered == 0 ? BigDecimal.ZERO : pct(BigDecimal.valueOf(onTime), BigDecimal.valueOf(delivered)), Money.of(cost));
        }).sorted(Comparator.comparingLong(CarrierPerf::shipments).reversed()).toList();

        OptionalDouble avg = all.stream().filter(s -> s.getShippedAt() != null && s.getDeliveredAt() != null)
                .mapToLong(s -> Duration.between(s.getShippedAt(), s.getDeliveredAt()).toHours()).average();
        LocalDate today = LocalDate.now();
        long delayed = all.stream().filter(s -> (s.getStatus() == ShipmentStatus.IN_TRANSIT || s.getStatus() == ShipmentStatus.PENDING
                || s.getStatus() == ShipmentStatus.PACKED) && s.getEstimatedDelivery() != null && s.getEstimatedDelivery().isBefore(today)).count();
        return new LogisticsReport(byStatus, carriers,
                avg.isPresent() ? BigDecimal.valueOf(avg.getAsDouble() / 24.0).setScale(1, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                Money.of(all.stream().filter(s -> s.getStatus() != ShipmentStatus.CANCELLED)
                        .map(s -> Money.nz(s.getShippingCost())).reduce(BigDecimal.ZERO, BigDecimal::add)),
                byStatus.get(ShipmentStatus.IN_TRANSIT.name()), delayed);
    }

    // ---------------------------------------------------------------- payments / cash flow

    public PaymentsReport paymentsBreakdown(LocalDate from, LocalDate to) {
        List<Payment> list = payments.findByPaidAtBetweenOrderByPaidAtDesc(start(from), end(to)).stream()
                .filter(p -> p.getStatus() == TransactionStatus.COMPLETED).toList();
        Function<Payment, Boolean> incoming = p -> p.getType() == PaymentType.CUSTOMER_PAYMENT;
        BigDecimal in = list.stream().filter(incoming::apply).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal outflow = list.stream().filter(p -> !incoming.apply(p)).map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<LocalDate, BigDecimal[]> byDay = new TreeMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) byDay.put(d, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
        for (Payment p : list) {
            BigDecimal[] v = byDay.get(p.getPaidAt().toLocalDate());
            if (v == null) continue;
            if (incoming.apply(p)) v[0] = v[0].add(p.getAmount()); else v[1] = v[1].add(p.getAmount());
        }
        List<CashFlowPoint> daily = byDay.entrySet().stream()
                .map(e -> new CashFlowPoint(e.getKey(), Money.of(e.getValue()[0]), Money.of(e.getValue()[1]),
                        Money.of(e.getValue()[0].subtract(e.getValue()[1])))).toList();

        return new PaymentsReport(Money.of(in), Money.of(outflow), Money.of(in.subtract(outflow)),
                group(list.stream().filter(incoming::apply).toList(), p -> p.getMethod().name(), Payment::getAmount),
                group(list, p -> p.getType().name(), Payment::getAmount), daily);
    }
}
