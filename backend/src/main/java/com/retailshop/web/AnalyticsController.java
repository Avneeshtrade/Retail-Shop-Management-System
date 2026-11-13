package com.retailshop.web;

import com.retailshop.common.BusinessException;
import com.retailshop.dto.Responses.*;
import com.retailshop.service.AnalyticsService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    private static LocalDate[] range(LocalDate from, LocalDate to) {
        LocalDate t = to == null ? LocalDate.now() : to;
        LocalDate f = from == null ? t.minusDays(29) : from;
        if (f.isAfter(t)) throw new BusinessException("'from' must be before 'to'");
        if (ChronoUnit.DAYS.between(f, t) > 731) throw new BusinessException("Date range is limited to 2 years");
        return new LocalDate[]{f, t};
    }

    /** Open to every role, but profit and receivable/payable figures are only sent to finance roles. */
    @GetMapping("/dashboard")
    public Dashboard dashboard(org.springframework.security.core.Authentication auth) {
        Dashboard d = analytics.dashboard();
        boolean finance = auth.getAuthorities().stream().map(a -> a.getAuthority())
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_MANAGER") || a.equals("ROLE_ACCOUNTANT"));
        if (finance) return d;
        Kpis k = d.kpis();
        Kpis redacted = new Kpis(k.todaySales(), k.todayOrders(), k.monthSales(), k.monthOrders(), null, null, null,
                k.avgOrderValue(), k.inventoryValueCost(), k.inventoryValueRetail(), k.inventoryUnits(), k.lowStockCount(),
                k.outOfStockCount(), null, null, k.pendingShipments(), k.openPurchaseOrders(), k.pendingOrders(), k.salesGrowthPct());
        return new Dashboard(redacted,
                d.salesTrend().stream().map(p -> new DailyPoint(p.date(), p.revenue(), p.orders(), null)).toList(),
                d.topProducts().stream().map(p -> new ProductPerf(p.productId(), p.name(), p.sku(), p.quantity(), p.revenue(), null, null, null)).toList(),
                d.paymentMethods(), d.salesByCategory(), d.lowStock(), d.recentOrders());
    }

    @GetMapping("/sales")
    @PreAuthorize(Roles.FINANCE)
    public SalesReport sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = range(from, to);
        return analytics.sales(r[0], r[1]);
    }

    @GetMapping("/profit-loss")
    @PreAuthorize(Roles.FINANCE)
    public ProfitLoss profitLoss(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = range(from, to);
        return analytics.profitLoss(r[0], r[1]);
    }

    @GetMapping("/inventory")
    @PreAuthorize(Roles.FINANCE + " or hasRole('WAREHOUSE')")
    public InventoryReport inventory() { return analytics.inventory(); }

    @GetMapping("/logistics")
    @PreAuthorize(Roles.MANAGE + " or hasRole('WAREHOUSE')")
    public LogisticsReport logistics(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                     @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = range(from, to);
        return analytics.logistics(r[0], r[1]);
    }

    @GetMapping("/payments")
    @PreAuthorize(Roles.FINANCE)
    public PaymentsReport payments(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var r = range(from, to);
        return analytics.paymentsBreakdown(r[0], r[1]);
    }
}
