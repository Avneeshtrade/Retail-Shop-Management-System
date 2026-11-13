package com.retailshop.service;

import com.retailshop.common.BusinessException;
import com.retailshop.common.NotFoundException;
import com.retailshop.domain.*;
import com.retailshop.domain.Enums.MovementType;
import com.retailshop.dto.Requests;
import com.retailshop.dto.Responses.*;
import com.retailshop.repository.*;
import com.retailshop.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * The single place where stock quantities change. Every change writes an audit StockMovement.
 */
@Service
@Transactional
public class InventoryService {
    private final StockLevelRepository stockLevels;
    private final StockMovementRepository movements;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;

    public InventoryService(StockLevelRepository stockLevels, StockMovementRepository movements,
                            ProductRepository products, WarehouseRepository warehouses) {
        this.stockLevels = stockLevels;
        this.movements = movements;
        this.products = products;
        this.warehouses = warehouses;
    }

    /**
     * Applies a signed quantity change. Throws if the result would be negative.
     */
    public StockMovement move(Product product, Warehouse warehouse, int delta, MovementType type,
                              String reference, String note) {
        if (delta == 0) return null;
        StockLevel level = stockLevels.findByProductIdAndWarehouseId(product.getId(), warehouse.getId())
                .orElseGet(() -> stockLevels.save(new StockLevel(product, warehouse)));
        int newQty = level.getQuantity() + delta;
        if (newQty < 0) {
            throw new BusinessException("Insufficient stock for " + product.getName() + " (" + product.getSku()
                    + ") at " + warehouse.getName() + ": available " + level.getQuantity()
                    + ", requested " + (-delta));
        }
        level.setQuantity(newQty);
        StockMovement m = new StockMovement();
        m.setProduct(product);
        m.setWarehouse(warehouse);
        m.setType(type);
        m.setQuantity(delta);
        m.setBalanceAfter(newQty);
        m.setReference(reference);
        m.setNote(note);
        m.setPerformedBy(CurrentUser.username());
        return movements.save(m);
    }

    public StockMovement adjust(Requests.StockAdjustment req) {
        Product p = products.findById(req.productId()).orElseThrow(() -> new NotFoundException("Product", req.productId()));
        Warehouse w = warehouses.findById(req.warehouseId()).orElseThrow(() -> new NotFoundException("Warehouse", req.warehouseId()));
        int current = stockLevels.findByProductIdAndWarehouseId(p.getId(), w.getId()).map(StockLevel::getQuantity).orElse(0);
        int delta;
        if (req.countedQuantity() != null) {
            delta = req.countedQuantity() - current;
        } else if (req.quantityChange() != null) {
            delta = req.quantityChange();
        } else {
            throw new BusinessException("Provide quantityChange or countedQuantity");
        }
        if (delta == 0) throw new BusinessException("No change: stock already at " + current);
        return move(p, w, delta, MovementType.ADJUSTMENT, "ADJ", req.reason());
    }

    @Transactional(readOnly = true)
    public int available(Long productId, Long warehouseId) {
        return stockLevels.findByProductIdAndWarehouseId(productId, warehouseId).map(StockLevel::getQuantity).orElse(0);
    }

    @Transactional(readOnly = true)
    public List<ProductStock> overview(Long warehouseId, boolean lowOnly) {
        Map<Long, List<StockLevel>> byProduct = new HashMap<>();
        for (StockLevel s : stockLevels.findAll()) {
            if (warehouseId != null && !s.getWarehouse().getId().equals(warehouseId)) continue;
            byProduct.computeIfAbsent(s.getProduct().getId(), k -> new ArrayList<>()).add(s);
        }
        List<ProductStock> result = new ArrayList<>();
        for (Product p : products.findAll(org.springframework.data.domain.Sort.by("name"))) {
            if (!p.isActive()) continue;
            List<StockLevel> levels = byProduct.getOrDefault(p.getId(), List.of());
            long total = levels.stream().mapToLong(StockLevel::getQuantity).sum();
            boolean low = total <= p.getReorderLevel();
            if (lowOnly && !low) continue;
            result.add(new ProductStock(p, total, low, levels.stream()
                    .map(l -> new WarehouseQty(l.getWarehouse().getId(), l.getWarehouse().getCode(),
                            l.getWarehouse().getName(), l.getQuantity()))
                    .sorted(Comparator.comparing(WarehouseQty::warehouseCode))
                    .toList()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<StockMovement> movements(Long productId, Long warehouseId) {
        if (productId != null) return movements.findByProductIdOrderByCreatedAtDesc(productId);
        if (warehouseId != null) return movements.findByWarehouseIdOrderByCreatedAtDesc(warehouseId);
        return movements.findTop200ByOrderByCreatedAtDesc();
    }
}
