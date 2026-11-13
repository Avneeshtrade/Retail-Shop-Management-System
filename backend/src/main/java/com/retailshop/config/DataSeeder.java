package com.retailshop.config;

import com.retailshop.domain.*;
import com.retailshop.domain.Enums.*;
import com.retailshop.dto.Requests;
import com.retailshop.repository.*;
import com.retailshop.service.*;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * First-run bootstrap. Always creates the admin account; when app.seed-demo-data=true also
 * loads a realistic 90-day history so every screen and report has data.
 */
@Component
public class DataSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;
    private final EntityManager em;
    private final CatalogService catalog;
    private final SalesService sales;
    private final PurchaseOrderService purchasing;
    private final ShipmentService shipping;
    private final ExpenseService expenses;
    private final InventoryService inventory;
    private final boolean seedDemo;
    private final Random rnd = new Random(42);

    public DataSeeder(UserRepository users, PasswordEncoder encoder, TransactionTemplate tx, EntityManager em,
                      CatalogService catalog, SalesService sales, PurchaseOrderService purchasing,
                      ShipmentService shipping, ExpenseService expenses, InventoryService inventory,
                      @Value("${app.seed-demo-data}") boolean seedDemo) {
        this.users = users;
        this.encoder = encoder;
        this.tx = tx;
        this.em = em;
        this.catalog = catalog;
        this.sales = sales;
        this.purchasing = purchasing;
        this.shipping = shipping;
        this.expenses = expenses;
        this.inventory = inventory;
        this.seedDemo = seedDemo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) return;
        tx.executeWithoutResult(s -> {
            user("admin", "Store Administrator", Role.ADMIN);
            if (seedDemo) {
                user("manager", "Maya Manager", Role.MANAGER);
                user("cashier", "Chris Cashier", Role.CASHIER);
                user("cashier2", "Sam Cashier", Role.CASHIER);
                user("warehouse", "Wes Warehouse", Role.WAREHOUSE);
                user("accountant", "Alex Accountant", Role.ACCOUNTANT);
            }
        });
        log.info("Created default users (password: <username>123). CHANGE THESE IN PRODUCTION.");
        if (!seedDemo) return;
        long t0 = System.currentTimeMillis();
        try {
            seedDemo();
        } finally {
            SecurityContextHolder.clearContext();
        }
        log.info("Demo data seeded in {} ms", System.currentTimeMillis() - t0);
    }

    private void user(String username, String name, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setPassword(encoder.encode(username + "123"));
        u.setFullName(name);
        u.setEmail(username + "@retailshop.local");
        u.setRole(role);
        users.save(u);
    }

    private void actAs(String username, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void seedDemo() {
        actAs("admin", "ADMIN");
        Map<String, Object> ctx = new HashMap<>();
        tx.executeWithoutResult(s -> seedMasterData(ctx));

        @SuppressWarnings("unchecked") List<Long> productIds = (List<Long>) ctx.get("products");
        @SuppressWarnings("unchecked") List<Long> customerIds = (List<Long>) ctx.get("customers");
        Long storeId = (Long) ctx.get("store"), whId = (Long) ctx.get("warehouse");
        @SuppressWarnings("unchecked") List<Long> supplierIds = (List<Long>) ctx.get("suppliers");

        LocalDate today = LocalDate.now();
        String[] cashiers = {"cashier", "cashier2", "manager"};
        PaymentMethod[] methods = {PaymentMethod.CASH, PaymentMethod.CASH, PaymentMethod.CARD, PaymentMethod.CARD,
                PaymentMethod.CARD, PaymentMethod.UPI, PaymentMethod.UPI, PaymentMethod.WALLET};
        String[] carriers = {"FastShip Express", "BlueDart Logistics", "City Courier"};

        // ---- 90 days of trading
        for (int d = 89; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            final int dd = d;
            tx.executeWithoutResult(s -> {
                boolean weekend = day.getDayOfWeek().getValue() >= 6;
                int orders = 6 + rnd.nextInt(6) + (weekend ? 5 : 0) + (89 - dd) / 15; // gentle growth trend
                for (int i = 0; i < orders; i++) {
                    String cashier = cashiers[rnd.nextInt(cashiers.length)];
                    actAs(cashier, cashier.startsWith("cashier") ? "CASHIER" : "MANAGER");
                    int hour = 9 + (int) Math.min(12, Math.abs(rnd.nextGaussian() * 3.5 + 5));
                    LocalDateTime at = day.atTime(hour, rnd.nextInt(60), rnd.nextInt(60));
                    if (at.isAfter(LocalDateTime.now())) at = LocalDateTime.now().minusMinutes(rnd.nextInt(30) + 1);
                    boolean online = rnd.nextInt(100) < 15;
                    List<Requests.LineItem> items = new ArrayList<>();
                    Set<Long> used = new HashSet<>();
                    int lines = 1 + rnd.nextInt(4);
                    for (int l = 0; l < lines; l++) {
                        // Skewed popularity: low-index products sell more.
                        int idx;
                        do { idx = (int) Math.abs(rnd.nextGaussian() * productIds.size() / 2.5); } while (idx >= productIds.size());
                        Long pid = productIds.get(idx);
                        if (!used.add(pid)) continue;
                        items.add(new Requests.LineItem(pid, 1 + rnd.nextInt(3), null, null));
                    }
                    Long customer = online || rnd.nextInt(100) < 55 ? customerIds.get(rnd.nextInt(customerIds.size())) : null;
                    Long wh = online ? whId : storeId;
                    // Pre-check stock: an exception inside the service would roll back the whole day.
                    items.removeIf(li -> inventory.available(li.productId(), wh) < li.quantity());
                    if (items.isEmpty()) continue;
                    {
                        SalesOrder probe = sales.create(new Requests.SalesOrderReq(customer, wh,
                                online ? SalesChannel.ONLINE : SalesChannel.IN_STORE,
                                rnd.nextInt(100) < 10 ? BigDecimal.valueOf(5 * (1 + rnd.nextInt(4))) : BigDecimal.ZERO,
                                online ? BigDecimal.valueOf(49) : BigDecimal.ZERO, null, null, items, List.of()), at);
                        boolean leaveUnpaid = online && dd < 2 && rnd.nextBoolean();
                        if (!leaveUnpaid) {
                            PaymentMethod m = online ? (rnd.nextBoolean() ? PaymentMethod.CARD : PaymentMethod.UPI) : methods[rnd.nextInt(methods.length)];
                            sales.addPaymentAt(probe.getId(), m, probe.getTotal(), m == PaymentMethod.CASH ? null : "TXN" + (100000 + rnd.nextInt(899999)), at);
                        }
                        if (online) seedOutboundShipment(probe, carriers[rnd.nextInt(carriers.length)], at, dd);
                        if (rnd.nextInt(100) < 2 && probe.getAmountPaid().signum() > 0 && !online) {
                            actAs("manager", "MANAGER");
                            sales.refund(probe.getId(), new Requests.RefundReq(null, PaymentMethod.CASH, true, "Customer returned item"));
                        }
                    }
                }
            });

            // Replenish weekly from suppliers; transfer warehouse -> store every 10 days.
            if (d % 7 == 3) restock(supplierIds, whId, storeId, day);
            if (d % 5 == 2) transfer(productIds, whId, storeId, day);
            if (day.getDayOfMonth() == 1 || d == 89) monthlyExpenses(day);
        }
        actAs("admin", "ADMIN");
        // Leave a couple of open POs and a draft for the procurement screens.
        tx.executeWithoutResult(s -> {
            PurchaseOrder po = purchasing.create(new Requests.PurchaseOrderReq(supplierIds.get(0), whId, today.plusDays(4),
                    BigDecimal.valueOf(150), "Seasonal top-up", List.of(
                    new Requests.LineItem(productIds.get(0), 40, null, null),
                    new Requests.LineItem(productIds.get(3), 25, null, null))));
            purchasing.submit(po.getId());
            purchasing.create(new Requests.PurchaseOrderReq(supplierIds.get(1), whId, null, BigDecimal.ZERO,
                    "Draft - awaiting approval", List.of(new Requests.LineItem(productIds.get(10), 30, null, null))));
        });
    }

    private void seedOutboundShipment(SalesOrder so, String carrier, LocalDateTime at, int daysAgo) {
        actAs("warehouse", "WAREHOUSE");
        Shipment s = shipping.create(new Requests.ShipmentReq(ShipmentType.OUTBOUND, carrier,
                "TRK" + (1000000 + rnd.nextInt(8999999)), so.getId(), null, null, null, null, null,
                BigDecimal.valueOf(30 + rnd.nextInt(40)), at.toLocalDate().plusDays(3), null, null));
        if (daysAgo >= 1) {
            shipping.updateStatus(s.getId(), new Requests.ShipmentStatusReq(ShipmentStatus.IN_TRANSIT, null, null));
            s.setShippedAt(at.plusHours(6));
            if (daysAgo >= 4 || rnd.nextInt(3) == 0) {
                shipping.updateStatus(s.getId(), new Requests.ShipmentStatusReq(ShipmentStatus.DELIVERED, null, null));
                int transitDays = 1 + rnd.nextInt(carrier.startsWith("City") ? 5 : 3);
                LocalDateTime delivered = at.plusDays(transitDays).withHour(10 + rnd.nextInt(8));
                s.setDeliveredAt(delivered.isAfter(LocalDateTime.now()) ? LocalDateTime.now() : delivered);
            }
        }
        backdate("shipments", s.getId(), at);
    }

    private void backdate(String table, Long id, LocalDateTime at) {
        em.flush();
        em.createNativeQuery("update " + table + " set created_at = ?1 where id = ?2")
                .setParameter(1, at).setParameter(2, id).executeUpdate();
    }

    private void restock(List<Long> supplierIds, Long whId, Long storeId, LocalDate day) {
        actAs("manager", "MANAGER");
        tx.executeWithoutResult(s -> {
            List<PurchaseOrder> pos = purchasing.autoReorder(whId);
            for (PurchaseOrder po : pos) {
                purchasing.submit(po.getId());
                po.setOrderDate(day.minusDays(3));
                actAs("warehouse", "WAREHOUSE");
                Shipment in = shipping.create(new Requests.ShipmentReq(ShipmentType.INBOUND, "FreightLine Cargo",
                        "FL" + (100000 + rnd.nextInt(899999)), null, po.getId(), null, null, null, null,
                        BigDecimal.valueOf(120), day, null, null));
                shipping.updateStatus(in.getId(), new Requests.ShipmentStatusReq(ShipmentStatus.IN_TRANSIT, null, null));
                shipping.updateStatus(in.getId(), new Requests.ShipmentStatusReq(ShipmentStatus.DELIVERED, null, null));
                in.setShippedAt(day.minusDays(2).atTime(9, 0));
                in.setDeliveredAt(day.atTime(rnd.nextBoolean() ? 11 : 15, 0));
                backdate("shipments", in.getId(), day.minusDays(3).atTime(9, 0));
                purchasing.receive(po.getId(), null);
                po.setReceivedDate(day);
                actAs("accountant", "ACCOUNTANT");
                if (day.isBefore(LocalDate.now().minusDays(10))) {
                    purchasing.pay(po.getId(), new Requests.PaymentReq(PaymentMethod.BANK_TRANSFER, po.getTotal(), "NEFT" + rnd.nextInt(999999), null));
                } else {
                    purchasing.pay(po.getId(), new Requests.PaymentReq(PaymentMethod.BANK_TRANSFER,
                            po.getTotal().divide(BigDecimal.TWO, 2, java.math.RoundingMode.HALF_UP), "NEFT" + rnd.nextInt(999999), "Advance 50%"));
                }
                actAs("manager", "MANAGER");
            }
        });
    }

    /** Moves stock from the warehouse to the store for anything running low on the shop floor. */
    private void transfer(List<Long> productIds, Long whId, Long storeId, LocalDate day) {
        actAs("warehouse", "WAREHOUSE");
        tx.executeWithoutResult(s -> {
            List<Requests.ShipmentLine> lines = new ArrayList<>();
            for (Long pid : productIds) {
                int atStore = inventory.available(pid, storeId);
                int atWh = inventory.available(pid, whId);
                if (atStore < 50 && atWh > 20) lines.add(new Requests.ShipmentLine(pid, Math.min(80, atWh - 10)));
            }
            if (lines.isEmpty()) return;
            Shipment t = shipping.create(new Requests.ShipmentReq(ShipmentType.TRANSFER, "Own Fleet", null, null, null,
                    whId, storeId, null, null, BigDecimal.valueOf(40), day.plusDays(1), "Store replenishment", lines));
            shipping.updateStatus(t.getId(), new Requests.ShipmentStatusReq(ShipmentStatus.IN_TRANSIT, null, null));
            shipping.updateStatus(t.getId(), new Requests.ShipmentStatusReq(ShipmentStatus.DELIVERED, null, null));
            t.setShippedAt(day.atTime(8, 0));
            t.setDeliveredAt(day.atTime(13, 0));
            backdate("shipments", t.getId(), day.atTime(7, 30));
        });
    }

    private void monthlyExpenses(LocalDate day) {
        actAs("accountant", "ACCOUNTANT");
        tx.executeWithoutResult(s -> {
            expenses.create(new Requests.ExpenseReq(ExpenseCategory.RENT, "Store rent", BigDecimal.valueOf(30000), day, PaymentMethod.BANK_TRANSFER, "City Properties Ltd"));
            expenses.create(new Requests.ExpenseReq(ExpenseCategory.SALARIES, "Staff salaries", BigDecimal.valueOf(72000), day.plusDays(1), PaymentMethod.BANK_TRANSFER, "Payroll"));
            expenses.create(new Requests.ExpenseReq(ExpenseCategory.UTILITIES, "Electricity & water", BigDecimal.valueOf(6000 + rnd.nextInt(3000)), day.plusDays(4), PaymentMethod.UPI, "City Utilities"));
            expenses.create(new Requests.ExpenseReq(ExpenseCategory.MARKETING, "Social media ads", BigDecimal.valueOf(4000 + rnd.nextInt(5000)), day.plusDays(7), PaymentMethod.CARD, "AdNetwork"));
            expenses.create(new Requests.ExpenseReq(ExpenseCategory.MAINTENANCE, "AC servicing", BigDecimal.valueOf(2500), day.plusDays(12), PaymentMethod.CASH, "CoolTech Services"));
        });
    }

    private void seedMasterData(Map<String, Object> ctx) {
        Warehouse store = warehouse("STORE-01", "Main Street Store", "12 Main Street, Downtown", WarehouseType.STORE);
        Warehouse wh = warehouse("WH-01", "Central Warehouse", "Plot 7, Industrial Area", WarehouseType.WAREHOUSE);
        warehouse("STORE-02", "Mall Outlet", "Level 2, City Mall", WarehouseType.STORE);
        ctx.put("store", store.getId());
        ctx.put("warehouse", wh.getId());

        Map<String, Category> cats = new LinkedHashMap<>();
        for (String c : List.of("Groceries", "Beverages", "Personal Care", "Household", "Electronics", "Stationery", "Snacks")) {
            Category cat = new Category(c, null);
            em.persist(cat);
            cats.put(c, cat);
        }

        List<Supplier> sups = List.of(
                supplier("FreshFarm Distributors", "Ravi Kumar", "orders@freshfarm.example", "+91-9000000001", 15, 3),
                supplier("Global Beverages Co.", "Anita Shah", "sales@globalbev.example", "+91-9000000002", 30, 5),
                supplier("CleanHome Supplies", "Peter Dsouza", "hello@cleanhome.example", "+91-9000000003", 30, 7),
                supplier("TechWorld Wholesale", "Lina Park", "b2b@techworld.example", "+91-9000000004", 45, 10));
        ctx.put("suppliers", sups.stream().map(Supplier::getId).toList());

        Object[][] products = {
                // name, category, supplier idx, cost, price, tax, reorder level
                {"Basmati Rice 5kg", "Groceries", 0, 380, 499, 5, 20},
                {"Whole Wheat Atta 10kg", "Groceries", 0, 340, 435, 5, 20},
                {"Toor Dal 1kg", "Groceries", 0, 120, 160, 5, 25},
                {"Sunflower Oil 1L", "Groceries", 0, 115, 149, 5, 30},
                {"Cola 2L", "Beverages", 1, 65, 95, 18, 30},
                {"Orange Juice 1L", "Beverages", 1, 85, 125, 12, 20},
                {"Green Tea 100 bags", "Beverages", 1, 160, 249, 5, 15},
                {"Instant Coffee 200g", "Beverages", 1, 310, 445, 18, 15},
                {"Mineral Water 1L (12 pack)", "Beverages", 1, 120, 180, 18, 20},
                {"Herbal Shampoo 400ml", "Personal Care", 2, 180, 275, 18, 15},
                {"Toothpaste 200g", "Personal Care", 2, 70, 110, 18, 25},
                {"Bath Soap (4 pack)", "Personal Care", 2, 95, 150, 18, 20},
                {"Hand Sanitizer 500ml", "Personal Care", 2, 110, 189, 18, 10},
                {"Dishwash Liquid 1L", "Household", 2, 130, 199, 18, 15},
                {"Laundry Detergent 2kg", "Household", 2, 260, 389, 18, 15},
                {"Floor Cleaner 1L", "Household", 2, 120, 179, 18, 12},
                {"Garbage Bags (30)", "Household", 2, 60, 99, 18, 15},
                {"USB-C Charger 20W", "Electronics", 3, 450, 799, 18, 8},
                {"Wireless Earbuds", "Electronics", 3, 900, 1599, 18, 5},
                {"AA Batteries (4 pack)", "Electronics", 3, 80, 140, 18, 20},
                {"LED Bulb 9W", "Electronics", 3, 65, 120, 12, 20},
                {"Notebook A4 (5 pack)", "Stationery", 0, 120, 199, 12, 10},
                {"Ball Pens (10 pack)", "Stationery", 0, 50, 90, 12, 15},
                {"Potato Chips 150g", "Snacks", 1, 30, 50, 12, 40},
                {"Chocolate Bar 100g", "Snacks", 1, 55, 90, 18, 30},
                {"Salted Peanuts 400g", "Snacks", 1, 90, 140, 12, 15},
                {"Digestive Biscuits 1kg", "Snacks", 0, 140, 210, 18, 15},
                {"Vintage Desk Lamp", "Electronics", 3, 1200, 1999, 18, 2},
        };
        List<Long> productIds = new ArrayList<>();
        int i = 0;
        for (Object[] p : products) {
            i++;
            String sku = ((String) p[1]).substring(0, 3).toUpperCase() + "-" + String.format("%04d", i);
            int opening = i == products.length ? 3 : 90 + rnd.nextInt(80);
            Product prod = catalog.create(new Requests.ProductReq(sku, "890" + String.format("%010d", 1000 + i), (String) p[0], null,
                    cats.get((String) p[1]).getId(), sups.get((Integer) p[2]).getId(), "pcs",
                    BigDecimal.valueOf((Integer) p[3]), BigDecimal.valueOf((Integer) p[4]), BigDecimal.valueOf((Integer) p[5]),
                    (Integer) p[6], Math.max(24, (Integer) p[6] * 3), true, opening, store.getId()));
            productIds.add(prod.getId());
            if (i != products.length) {
                inventory.move(prod, wh, 120 + rnd.nextInt(120), MovementType.ADJUSTMENT, "OPENING", "Opening stock");
            }
        }
        ctx.put("products", productIds);

        String[][] customers = {
                {"Priya Sharma", "+91-9810000001"}, {"Rahul Verma", "+91-9810000002"}, {"Aisha Khan", "+91-9810000003"},
                {"John Mathew", "+91-9810000004"}, {"Neha Gupta", "+91-9810000005"}, {"Arjun Reddy", "+91-9810000006"},
                {"Sara Thomas", "+91-9810000007"}, {"Vikram Singh", "+91-9810000008"}, {"Meera Iyer", "+91-9810000009"},
                {"Kabir Das", "+91-9810000010"}, {"Fatima Ali", "+91-9810000011"}, {"Rohan Mehta", "+91-9810000012"},
                {"Ananya Bose", "+91-9810000013"}, {"Dev Patel", "+91-9810000014"}, {"Lakshmi Nair", "+91-9810000015"},
        };
        List<Long> customerIds = new ArrayList<>();
        for (String[] c : customers) {
            Customer cu = new Customer();
            cu.setName(c[0]);
            cu.setPhone(c[1]);
            cu.setEmail(c[0].toLowerCase().replace(' ', '.') + "@example.com");
            cu.setAddress((10 + rnd.nextInt(90)) + " Residency Road, Sector " + (1 + rnd.nextInt(40)));
            em.persist(cu);
            customerIds.add(cu.getId());
        }
        ctx.put("customers", customerIds);
    }

    private Warehouse warehouse(String code, String name, String address, WarehouseType type) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(name);
        w.setAddress(address);
        w.setType(type);
        em.persist(w);
        return w;
    }

    private Supplier supplier(String name, String contact, String email, String phone, int terms, int lead) {
        Supplier s = new Supplier();
        s.setName(name);
        s.setContactName(contact);
        s.setEmail(email);
        s.setPhone(phone);
        s.setAddress("Wholesale Market, Block " + (char) ('A' + rnd.nextInt(6)));
        s.setPaymentTermsDays(terms);
        s.setLeadTimeDays(lead);
        em.persist(s);
        return s;
    }
}
