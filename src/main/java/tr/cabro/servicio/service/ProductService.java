package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.PartCategoryRepository;
import tr.cabro.servicio.database.repository.ProductRepository;
import tr.cabro.servicio.database.repository.SupplierRepository;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.dto.PartStatsDto;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockItemKind;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Validator;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/** {@link tr.cabro.servicio.service.PartService}'in POS kataloğu için bağımsız muadili — bkz. Product. */
public class ProductService {

    private final ProductRepository productRepository;
    private final StockService stockService;
    private final PartCategoryRepository partCategoryRepository;
    private final SupplierRepository supplierRepository;

    public ProductService(ProductRepository productRepository, StockService stockService,
                           PartCategoryRepository partCategoryRepository,
                           SupplierRepository supplierRepository) {
        this.productRepository = productRepository;
        this.stockService = stockService;
        this.partCategoryRepository = partCategoryRepository;
        this.supplierRepository = supplierRepository;
    }

    public CompletableFuture<Product> save(Product product, boolean update) {
        if (Validator.isEmpty(product.getBarcode())) throw new ValidationException("Barkod boş olamaz.");
        if (Validator.isEmpty(product.getName())) throw new ValidationException("Ürün adı boş olamaz.");
        if (product.getPurchasePrice() != null && product.getPurchasePrice().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Alış fiyatı negatif olamaz.");
        if (product.getSalePrice() != null && product.getSalePrice().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Satış fiyatı negatif olamaz.");
        if (product.getStockQuantity() != null && product.getStockQuantity() < 0)
            throw new ValidationException("Stok miktarı negatif olamaz.");

        // Stok yalnızca kart açılışında girilir (açılış hareketi); düzenleme stoğa dokunmaz.
        // Sonraki değişiklikler StockService işlemleriyle yapılır — bkz. PartService.save.
        int openingStock = product.getStockQuantity() != null ? product.getStockQuantity() : 0;

        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            ProductRepository repo = handle.attach(ProductRepository.class);
            if (!update) {
                if (repo.existsByBarcode(product.getBarcode())) {
                    throw new ValidationException("Bu barkod (" + product.getBarcode() + ") zaten sistemde kayıtlı!");
                }
                Long id = repo.insert(product);
                product.setId(id);
                if (openingStock > 0) {
                    Long warehouseId = stockService.resolveWarehouse(handle, product.getOpeningWarehouseId(), true);
                    stockService.record(handle, StockItemKind.PRODUCT, id, warehouseId, openingStock,
                            ReferenceType.OPENING, null, product.getPurchasePrice(), null);
                }
                product.setStockQuantity(openingStock);
            } else {
                repo.update(product);
                product.setStockQuantity(repo.findById(product.getId())
                        .map(p -> p.getStockQuantity() != null ? p.getStockQuantity() : 0).orElse(0));
            }
            return product;
        }));
    }

    public CompletableFuture<Void> delete(Long id) {
        return DbExecutor.run(() -> productRepository.delete(id));
    }

    public CompletableFuture<List<Product>> getAll() {
        return DbExecutor.supply(() -> hydrateProducts(productRepository.findAll()));
    }

    public CompletableFuture<Optional<Product>> get(String barcode) {
        return DbExecutor.supply(() -> {
            Optional<Product> opt = productRepository.findByBarcode(barcode);
            opt.ifPresent(p -> hydrateProducts(Collections.singletonList(p)));
            return opt;
        });
    }

    public CompletableFuture<Optional<Product>> getById(Long id) {
        return DbExecutor.supply(() -> {
            Optional<Product> opt = productRepository.findById(id);
            opt.ifPresent(p -> hydrateProducts(Collections.singletonList(p)));
            return opt;
        });
    }

    public CompletableFuture<Boolean> isBarcodeAvailable(String barcode) {
        return DbExecutor.supply(() -> !productRepository.existsByBarcode(barcode));
    }

    public CompletableFuture<PageResult<Product>> getAllPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return DbExecutor.supply(() -> {
            List<Product> items = hydrateProducts(productRepository.findAllPaged(pageSize, offset));
            long total = productRepository.countAll();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<Product>> searchPaged(String searchTerm, int page, int pageSize) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAllPaged(page, pageSize);
        int offset = (page - 1) * pageSize;
        String likeTerm = "%" + searchTerm.trim() + "%";
        return DbExecutor.supply(() -> {
            List<Product> items = hydrateProducts(productRepository.searchPaged(likeTerm, pageSize, offset));
            long total = productRepository.countSearch(likeTerm);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    /** Liste sayfası: arama + görünüm sekmesi/başlık filtreleri + sayfalama. */
    public CompletableFuture<PageResult<Product>> searchFilteredPaged(String searchTerm,
            java.util.Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters, int page, int pageSize) {
        return DbExecutor.supply(() -> {
            PageResult<Product> r = productRepository.searchFilteredPaged(searchTerm, filters, page, pageSize);
            return new PageResult<>(hydrateProducts(r.getItems()), r.getPage(), r.getPageSize(), r.getTotalItems());
        });
    }

    public CompletableFuture<PageResult<Product>> searchFilteredPaged(String searchTerm,
            java.util.Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters, int page, int pageSize, String sortKey) {
        return DbExecutor.supply(() -> {
            PageResult<Product> r = productRepository.searchFilteredPaged(searchTerm, filters, page, pageSize, sortKey);
            return new PageResult<>(hydrateProducts(r.getItems()), r.getPage(), r.getPageSize(), r.getTotalItems());
        });
    }

    public CompletableFuture<PartStatsDto> getStats() {
        return DbExecutor.supply(productRepository::getStats);
    }

    public CompletableFuture<List<Product>> getBySupplierId(Long supplierId) {
        return DbExecutor.supply(() -> hydrateProducts(productRepository.findBySupplierId(supplierId)));
    }

    private List<Product> hydrateProducts(List<Product> products) {
        if (products == null || products.isEmpty()) return products;

        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId).filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        Map<Long, PartCategory> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : partCategoryRepository.findByIds(categoryIds).stream().collect(Collectors.toMap(PartCategory::getId, c -> c));

        List<Long> supplierIds = products.stream()
                .map(Product::getSupplierId).filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        Map<Long, Supplier> supplierMap = supplierIds.isEmpty()
                ? Collections.emptyMap()
                : supplierRepository.findByIds(supplierIds).stream().collect(Collectors.toMap(Supplier::getId, s -> s));

        for (Product product : products) {
            product.setCategory(categoryMap.get(product.getCategoryId()));
            product.setSupplier(supplierMap.get(product.getSupplierId()));
        }
        return products;
    }
}
