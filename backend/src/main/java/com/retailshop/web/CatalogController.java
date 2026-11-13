package com.retailshop.web;

import com.retailshop.common.NotFoundException;
import com.retailshop.domain.Category;
import com.retailshop.domain.Product;
import com.retailshop.dto.Requests;
import com.retailshop.repository.CategoryRepository;
import com.retailshop.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class CatalogController {
    private final CatalogService catalog;
    private final CategoryRepository categories;

    public CatalogController(CatalogService catalog, CategoryRepository categories) {
        this.catalog = catalog;
        this.categories = categories;
    }

    @GetMapping("/products")
    public List<Product> products(@RequestParam(required = false) String q, @RequestParam(required = false) Long categoryId) {
        return catalog.search(q, categoryId);
    }

    @GetMapping("/products/{id}")
    public Product product(@PathVariable Long id) { return catalog.get(id); }

    /** Barcode scanner / SKU lookup for the POS. */
    @GetMapping("/products/lookup/{code}")
    public Product lookup(@PathVariable String code) { return catalog.byCode(code); }

    @PostMapping("/products")
    @PreAuthorize(Roles.MANAGE)
    public Product create(@Valid @RequestBody Requests.ProductReq req) { return catalog.create(req); }

    @PutMapping("/products/{id}")
    @PreAuthorize(Roles.MANAGE)
    public Product update(@PathVariable Long id, @Valid @RequestBody Requests.ProductReq req) { return catalog.update(id, req); }

    @GetMapping("/categories")
    public List<Category> categories() { return categories.findAll(Sort.by("name")); }

    @PostMapping("/categories")
    @PreAuthorize(Roles.MANAGE)
    public Category createCategory(@Valid @RequestBody Requests.CategoryReq req) {
        return categories.save(new Category(req.name().trim(), req.description()));
    }

    @PutMapping("/categories/{id}")
    @PreAuthorize(Roles.MANAGE)
    @Transactional
    public Category updateCategory(@PathVariable Long id, @Valid @RequestBody Requests.CategoryReq req) {
        Category c = categories.findById(id).orElseThrow(() -> new NotFoundException("Category", id));
        c.setName(req.name().trim());
        c.setDescription(req.description());
        return c;
    }

    @DeleteMapping("/categories/{id}")
    @PreAuthorize(Roles.MANAGE)
    public void deleteCategory(@PathVariable Long id) { categories.deleteById(id); }
}
