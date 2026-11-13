package com.retailshop;

import com.retailshop.domain.*;
import com.retailshop.domain.Enums.*;
import com.retailshop.dto.Requests.*;
import com.retailshop.common.BusinessException;
import com.retailshop.repository.*;
import com.retailshop.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "tester", roles = "ADMIN")
class BusinessFlowsTest {
    @Autowired CatalogService catalog;
    @Autowired InventoryService inventory;
    @Autowired SalesService sales;
    @Autowired PurchaseOrderService purchasing;
    @Autowired ShipmentService shipping;
    @Autowired WarehouseRepository warehouses;
    @Autowired SupplierRepository suppliers;
    @Autowired CustomerRepository customers;

    Warehouse store, wh;
    Supplier supplier;
    Product rice;

    @BeforeEach
    void setUp() {
        store = warehouse("S1", WarehouseType.STORE);
        wh = warehouse("W1", WarehouseType.WAREHOUSE);
        supplier = new Supplier();
        supplier.setName("Acme");
        suppliers.save(supplier);
        rice = catalog.create(new ProductReq("RICE-1", null, "Rice", null, null, supplier.getId(), null,
                bd("100"), bd("150"), bd("5"), 10, 50, true, 20, store.getId()));
    }

    @Test
    void posSaleDeductsStockComputesTaxAndCapsCashChange() {
        SalesOrder o = sales.create(new SalesOrderReq(null, store.getId(), null, null, null, null, null,
                List.of(new LineItem(rice.getId(), 3, null, null)),
                List.of(new PaymentLine(PaymentMethod.CASH, bd("500"), null))));

        assertThat(o.getSubtotal()).isEqualByComparingTo("450");
        assertThat(o.getTaxAmount()).isEqualByComparingTo("22.50");
        assertThat(o.getTotal()).isEqualByComparingTo("472.50");
        assertThat(o.getAmountPaid()).isEqualByComparingTo("472.50"); // cash above total is change, not revenue
        assertThat(o.getStatus()).isEqualTo(SalesOrderStatus.COMPLETED);
        assertThat(o.getCostOfGoods()).isEqualByComparingTo("300");
        assertThat(inventory.available(rice.getId(), store.getId())).isEqualTo(17);
    }

    @Test
    void cannotOversell() {
        assertThatThrownBy(() -> sales.create(new SalesOrderReq(null, store.getId(), null, null, null, null, null,
                List.of(new LineItem(rice.getId(), 21, null, null)), List.of())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Insufficient stock");
    }

    @Test
    void nonCashOverpaymentRejected() {
        assertThatThrownBy(() -> sales.create(new SalesOrderReq(null, store.getId(), null, null, null, null, null,
                List.of(new LineItem(rice.getId(), 1, null, null)),
                List.of(new PaymentLine(PaymentMethod.CARD, bd("1000"), null)))))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void refundRestocksAndUpdatesCustomer() {
        Customer c = new Customer();
        c.setName("Jane");
        customers.save(c);
        SalesOrder o = sales.create(new SalesOrderReq(c.getId(), store.getId(), null, null, null, null, null,
                List.of(new LineItem(rice.getId(), 2, null, null)),
                List.of(new PaymentLine(PaymentMethod.CARD, bd("315"), null))));
        assertThat(c.getTotalSpent()).isEqualByComparingTo("315");
        assertThat(c.getLoyaltyPoints()).isEqualTo(3);

        sales.refund(o.getId(), new RefundReq(null, PaymentMethod.CARD, true, "damaged"));

        assertThat(o.getStatus()).isEqualTo(SalesOrderStatus.REFUNDED);
        assertThat(inventory.available(rice.getId(), store.getId())).isEqualTo(20);
        assertThat(c.getTotalSpent()).isEqualByComparingTo("0");
        // A second refund attempt is rejected and never double-restocks
        assertThatThrownBy(() -> sales.refund(o.getId(), new RefundReq(null, PaymentMethod.CARD, true, "again")))
                .isInstanceOf(BusinessException.class);
        assertThat(inventory.available(rice.getId(), store.getId())).isEqualTo(20);
    }

    @Test
    void purchaseOrderPartialReceiptAndWeightedAverageCost() {
        PurchaseOrder po = purchasing.create(new PurchaseOrderReq(supplier.getId(), store.getId(), null, BigDecimal.ZERO, null,
                List.of(new LineItem(rice.getId(), 20, bd("130"), null))));
        assertThatThrownBy(() -> purchasing.receive(po.getId(), null)).isInstanceOf(BusinessException.class); // still draft
        purchasing.submit(po.getId());
        Long itemId = po.getItems().get(0).getId();

        purchasing.receive(po.getId(), new ReceiveReq(List.of(new ReceiveLine(itemId, 20))));

        assertThat(po.getStatus()).isEqualTo(PurchaseOrderStatus.RECEIVED);
        assertThat(inventory.available(rice.getId(), store.getId())).isEqualTo(40);
        // (20 * 100 + 20 * 130) / 40 = 115
        assertThat(rice.getCostPrice()).isEqualByComparingTo("115");

        purchasing.pay(po.getId(), new PaymentReq(PaymentMethod.BANK_TRANSFER, po.getTotal(), "T1", null));
        assertThat(po.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    void transferShipmentMovesStockOnlyWhenDispatchedAndDelivered() {
        Shipment s = shipping.create(new ShipmentReq(ShipmentType.TRANSFER, "Fleet", null, null, null,
                store.getId(), wh.getId(), null, null, null, null, null, List.of(new ShipmentLine(rice.getId(), 5))));
        assertThat(inventory.available(rice.getId(), store.getId())).isEqualTo(20);

        shipping.updateStatus(s.getId(), new ShipmentStatusReq(ShipmentStatus.IN_TRANSIT, null, null));
        assertThat(inventory.available(rice.getId(), store.getId())).isEqualTo(15);
        assertThat(inventory.available(rice.getId(), wh.getId())).isZero();

        shipping.updateStatus(s.getId(), new ShipmentStatusReq(ShipmentStatus.DELIVERED, null, null));
        assertThat(inventory.available(rice.getId(), wh.getId())).isEqualTo(5);

        assertThatThrownBy(() -> shipping.updateStatus(s.getId(), new ShipmentStatusReq(ShipmentStatus.IN_TRANSIT, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Invalid status change");
    }

    @Test
    void autoReorderDraftsPoForLowStock() {
        inventory.adjust(new StockAdjustment(rice.getId(), store.getId(), null, 4, "count"));
        List<PurchaseOrder> pos = purchasing.autoReorder(wh.getId());
        assertThat(pos).hasSize(1);
        assertThat(pos.get(0).getStatus()).isEqualTo(PurchaseOrderStatus.DRAFT);
        assertThat(pos.get(0).getItems().get(0).getQuantity()).isEqualTo(50);
    }

    private Warehouse warehouse(String code, WarehouseType type) {
        Warehouse w = new Warehouse();
        w.setCode(code);
        w.setName(code);
        w.setType(type);
        return warehouses.save(w);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
