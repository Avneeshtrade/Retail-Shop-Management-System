package com.retailshop.service;

import com.retailshop.common.BusinessException;
import com.retailshop.common.Money;
import com.retailshop.common.NotFoundException;
import com.retailshop.domain.*;
import com.retailshop.dto.Requests;
import com.retailshop.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CatalogService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final SupplierRepository suppliers;
    private final WarehouseRepository warehouses;
    private final InventoryService inventory;

    public CatalogService(ProductRepository products, CategoryRepository categories, SupplierRepository suppliers,
                          WarehouseRepository warehouses, InventoryService inventory) {
        this.products = products;
        this.categories = categories;
        this.suppliers = suppliers;
        this.warehouses = warehouses;
        this.inventory = inventory;
    }

    @Transactional(readOnly = true)
    public List<Product> search(String q, Long categoryId) {
        return products.search(q == null || q.isBlank() ? null : q.trim(), categoryId);
    }

    @Transactional(readOnly = true)
    public Product get(Long id) {
        return products.findById(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    @Transactional(readOnly = true)
    public Product byCode(String code) {
        return products.findByBarcode(code).or(() -> products.findBySku(code))
                .orElseThrow(() -> new NotFoundException("Product with barcode/SKU", code));
    }

    public Product create(Requests.ProductReq req) {
        if (products.existsBySku(req.sku())) throw new BusinessException("SKU already exists: " + req.sku());
        Product p = new Product();
        apply(p, req);
        p = products.save(p);
        if (req.openingStock() != null && req.openingStock() > 0) {
            if (req.openingWarehouseId() == null) throw new BusinessException("openingWarehouseId is required for opening stock");
            Warehouse w = warehouses.findById(req.openingWarehouseId())
                    .orElseThrow(() -> new NotFoundException("Warehouse", req.openingWarehouseId()));
            inventory.move(p, w, req.openingStock(), Enums.MovementType.ADJUSTMENT, "OPENING", "Opening stock");
        }
        return p;
    }

    public Product update(Long id, Requests.ProductReq req) {
        Product p = get(id);
        if (!p.getSku().equals(req.sku()) && products.existsBySku(req.sku())) {
            throw new BusinessException("SKU already exists: " + req.sku());
        }
        apply(p, req);
        return p;
    }

    private void apply(Product p, Requests.ProductReq r) {
        p.setSku(r.sku().trim());
        p.setBarcode(r.barcode() == null || r.barcode().isBlank() ? null : r.barcode().trim());
        p.setName(r.name().trim());
        p.setDescription(r.description());
        p.setCategory(r.categoryId() == null ? null :
                categories.findById(r.categoryId()).orElseThrow(() -> new NotFoundException("Category", r.categoryId())));
        p.setPreferredSupplier(r.preferredSupplierId() == null ? null :
                suppliers.findById(r.preferredSupplierId()).orElseThrow(() -> new NotFoundException("Supplier", r.preferredSupplierId())));
        p.setUnit(r.unit() == null || r.unit().isBlank() ? "pcs" : r.unit());
        p.setCostPrice(Money.of(r.costPrice()));
        p.setSellingPrice(Money.of(r.sellingPrice()));
        p.setTaxRate(r.taxRate() == null ? BigDecimal.ZERO : r.taxRate());
        if (r.reorderLevel() != null) p.setReorderLevel(r.reorderLevel());
        if (r.reorderQuantity() != null) p.setReorderQuantity(r.reorderQuantity());
        if (r.active() != null) p.setActive(r.active());
    }
}
