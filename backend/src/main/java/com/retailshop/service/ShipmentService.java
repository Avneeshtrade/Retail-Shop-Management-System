package com.retailshop.service;

import com.retailshop.common.BusinessException;
import com.retailshop.common.DocumentNumbers;
import com.retailshop.common.Money;
import com.retailshop.common.NotFoundException;
import com.retailshop.domain.*;
import com.retailshop.domain.Enums.*;
import com.retailshop.dto.Requests;
import com.retailshop.repository.*;
import com.retailshop.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Logistics: outbound deliveries for sales orders, inbound freight for purchase orders and
 * inter-warehouse stock transfers (which move stock when dispatched/delivered).
 */
@Service
@Transactional
public class ShipmentService {
    private static final Map<ShipmentStatus, Set<ShipmentStatus>> TRANSITIONS = Map.of(
            ShipmentStatus.PENDING, EnumSet.of(ShipmentStatus.PACKED, ShipmentStatus.IN_TRANSIT, ShipmentStatus.CANCELLED),
            ShipmentStatus.PACKED, EnumSet.of(ShipmentStatus.IN_TRANSIT, ShipmentStatus.CANCELLED),
            ShipmentStatus.IN_TRANSIT, EnumSet.of(ShipmentStatus.DELIVERED, ShipmentStatus.RETURNED),
            ShipmentStatus.DELIVERED, EnumSet.noneOf(ShipmentStatus.class),
            ShipmentStatus.CANCELLED, EnumSet.noneOf(ShipmentStatus.class),
            ShipmentStatus.RETURNED, EnumSet.noneOf(ShipmentStatus.class));

    private final ShipmentRepository shipments;
    private final SalesOrderRepository salesOrders;
    private final PurchaseOrderRepository purchaseOrders;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;
    private final InventoryService inventory;

    public ShipmentService(ShipmentRepository shipments, SalesOrderRepository salesOrders,
                           PurchaseOrderRepository purchaseOrders, WarehouseRepository warehouses,
                           ProductRepository products, InventoryService inventory) {
        this.shipments = shipments;
        this.salesOrders = salesOrders;
        this.purchaseOrders = purchaseOrders;
        this.warehouses = warehouses;
        this.products = products;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public List<Shipment> list(ShipmentStatus status, ShipmentType type) {
        return shipments.findAllByOrderByCreatedAtDesc().stream()
                .filter(s -> status == null || s.getStatus() == status)
                .filter(s -> type == null || s.getType() == type).toList();
    }

    @Transactional(readOnly = true)
    public Shipment get(Long id) {
        return shipments.findById(id).orElseThrow(() -> new NotFoundException("Shipment", id));
    }

    public Shipment create(Requests.ShipmentReq req) {
        Shipment s = new Shipment();
        s.setShipmentNumber(DocumentNumbers.next("SHP"));
        s.setType(req.type());
        s.setCarrier(req.carrier());
        s.setTrackingNumber(req.trackingNumber());
        s.setShippingCost(Money.of(req.shippingCost()));
        s.setEstimatedDelivery(req.estimatedDelivery());
        s.setNotes(req.notes());
        s.setOriginAddress(req.originAddress());
        s.setDestinationAddress(req.destinationAddress());
        s.setCreatedBy(CurrentUser.username());

        switch (req.type()) {
            case OUTBOUND -> {
                if (req.salesOrderId() == null) throw new BusinessException("salesOrderId is required for outbound shipments");
                SalesOrder so = salesOrders.findById(req.salesOrderId()).orElseThrow(() -> new NotFoundException("Sales order", req.salesOrderId()));
                if (so.getStatus() == SalesOrderStatus.CANCELLED || so.getStatus() == SalesOrderStatus.REFUNDED) {
                    throw new BusinessException("Cannot ship a " + so.getStatus() + " order");
                }
                boolean active = shipments.findBySalesOrderId(so.getId()).stream()
                        .anyMatch(x -> x.getStatus() != ShipmentStatus.CANCELLED && x.getStatus() != ShipmentStatus.RETURNED);
                if (active) throw new BusinessException("Order " + so.getOrderNumber() + " already has an active shipment");
                s.setSalesOrder(so);
                s.setFromWarehouse(so.getWarehouse());
                if (s.getDestinationAddress() == null) s.setDestinationAddress(so.getShippingAddress());
                if (s.getOriginAddress() == null) s.setOriginAddress(so.getWarehouse().getAddress());
                so.getItems().forEach(i -> addItem(s, i.getProduct(), i.getQuantity()));
            }
            case INBOUND -> {
                if (req.purchaseOrderId() == null) throw new BusinessException("purchaseOrderId is required for inbound shipments");
                PurchaseOrder po = purchaseOrders.findById(req.purchaseOrderId()).orElseThrow(() -> new NotFoundException("Purchase order", req.purchaseOrderId()));
                s.setPurchaseOrder(po);
                s.setToWarehouse(po.getWarehouse());
                if (s.getOriginAddress() == null) s.setOriginAddress(po.getSupplier().getAddress());
                if (s.getDestinationAddress() == null) s.setDestinationAddress(po.getWarehouse().getAddress());
                po.getItems().forEach(i -> addItem(s, i.getProduct(), i.getQuantity() - i.getReceivedQuantity()));
            }
            case TRANSFER -> {
                if (req.fromWarehouseId() == null || req.toWarehouseId() == null) {
                    throw new BusinessException("fromWarehouseId and toWarehouseId are required for transfers");
                }
                if (req.fromWarehouseId().equals(req.toWarehouseId())) throw new BusinessException("Source and destination must differ");
                if (req.items() == null || req.items().isEmpty()) throw new BusinessException("Transfer needs at least one item");
                s.setFromWarehouse(warehouses.findById(req.fromWarehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.fromWarehouseId())));
                s.setToWarehouse(warehouses.findById(req.toWarehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.toWarehouseId())));
                s.setOriginAddress(s.getFromWarehouse().getAddress());
                s.setDestinationAddress(s.getToWarehouse().getAddress());
                for (Requests.ShipmentLine l : req.items()) {
                    Product p = products.findById(l.productId()).orElseThrow(() -> new NotFoundException("Product", l.productId()));
                    int avail = inventory.available(p.getId(), s.getFromWarehouse().getId());
                    if (avail < l.quantity()) throw new BusinessException("Only " + avail + " of " + p.getName() + " available at " + s.getFromWarehouse().getName());
                    addItem(s, p, l.quantity());
                }
            }
        }
        return shipments.save(s);
    }

    private static void addItem(Shipment s, Product p, int qty) {
        if (qty <= 0) return;
        ShipmentItem i = new ShipmentItem();
        i.setProduct(p);
        i.setQuantity(qty);
        s.addItem(i);
    }

    public Shipment updateStatus(Long id, Requests.ShipmentStatusReq req) {
        Shipment s = get(id);
        ShipmentStatus from = s.getStatus(), to = req.status();
        if (!TRANSITIONS.get(from).contains(to)) {
            throw new BusinessException("Invalid status change " + from + " -> " + to);
        }
        if (req.trackingNumber() != null && !req.trackingNumber().isBlank()) s.setTrackingNumber(req.trackingNumber());
        if (req.note() != null && !req.note().isBlank()) {
            s.setNotes((s.getNotes() == null ? "" : s.getNotes() + "\n") + "[" + to + "] " + req.note());
        }
        s.setStatus(to);

        switch (to) {
            case IN_TRANSIT -> {
                s.setShippedAt(LocalDateTime.now());
                if (s.getType() == ShipmentType.TRANSFER) {
                    s.getItems().forEach(i -> inventory.move(i.getProduct(), s.getFromWarehouse(), -i.getQuantity(),
                            MovementType.TRANSFER_OUT, s.getShipmentNumber(), "To " + s.getToWarehouse().getName()));
                }
                if (s.getSalesOrder() != null) s.getSalesOrder().setStatus(SalesOrderStatus.SHIPPED);
            }
            case DELIVERED -> {
                s.setDeliveredAt(LocalDateTime.now());
                if (s.getType() == ShipmentType.TRANSFER) {
                    s.getItems().forEach(i -> inventory.move(i.getProduct(), s.getToWarehouse(), i.getQuantity(),
                            MovementType.TRANSFER_IN, s.getShipmentNumber(), "From " + s.getFromWarehouse().getName()));
                }
                if (s.getSalesOrder() != null) s.getSalesOrder().setStatus(SalesOrderStatus.DELIVERED);
            }
            case RETURNED -> {
                // A transfer that bounced: stock goes back to the source warehouse.
                if (s.getType() == ShipmentType.TRANSFER) {
                    s.getItems().forEach(i -> inventory.move(i.getProduct(), s.getFromWarehouse(), i.getQuantity(),
                            MovementType.TRANSFER_IN, s.getShipmentNumber(), "Returned transfer"));
                }
                if (s.getSalesOrder() != null) {
                    SalesOrder so = s.getSalesOrder();
                    so.setStatus(so.getPaymentStatus() == PaymentStatus.PAID ? SalesOrderStatus.COMPLETED : SalesOrderStatus.PENDING);
                }
            }
            default -> { }
        }
        return s;
    }
}
