package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.PartCategoryRepository;
import tr.cabro.servicio.database.repository.ProductRepository;
import tr.cabro.servicio.database.repository.ProductStockMovementRepository;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.ProductStockMovement;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.dto.PartStatsDto;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockType;
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
    private final ProductStockMovementRepository stockMovementRepository;
    private final PartCategoryRepository partCategoryRepository;

    public ProductService(ProductRepository productRepository, ProductStockMovementRepository stockMovementRepository,
                           PartCategoryRepository partCategoryRepository) {
        this.productRepository = productRepository;
        this.stockMovementRepository = stockMovementRepository;
        this.partCategoryRepository = partCategoryRepository;
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

        Integer stock = product.getStockQuantity();

        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                if (productRepository.existsByBarcode(product.getBarcode())) {
                    throw new ValidationException("Bu barkod (" + product.getBarcode() + ") zaten sistemde kayıtlı!");
                }
                product.setStockQuantity(0);
                Long id = productRepository.insert(product);
                product.setId(id);
            } else {
                productRepository.update(product);
            }
            return product;
        }).thenCompose(saved -> {
            ProductStockMovement movement = new ProductStockMovement();
            movement.setProductId(saved.getId());
            movement.setQuantity(stock != null ? stock : 0);
            movement.setType(StockType.IN);
            movement.setReferenceType(ReferenceType.PURCHASE);
            return CompletableFuture.runAsync(() -> stockMovementRepository.insert(movement))
                    .thenApply(v -> saved);
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> productRepository.delete(id));
    }

    public CompletableFuture<List<Product>> getAll() {
        return CompletableFuture.supplyAsync(() -> hydrateProducts(productRepository.findAll()));
    }

    public CompletableFuture<Optional<Product>> get(String barcode) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Product> opt = productRepository.findByBarcode(barcode);
            opt.ifPresent(p -> hydrateProducts(Collections.singletonList(p)));
            return opt;
        });
    }

    public CompletableFuture<Optional<Product>> getById(Long id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Product> opt = productRepository.findById(id);
            opt.ifPresent(p -> hydrateProducts(Collections.singletonList(p)));
            return opt;
        });
    }

    public CompletableFuture<Boolean> isBarcodeAvailable(String barcode) {
        return CompletableFuture.supplyAsync(() -> !productRepository.existsByBarcode(barcode));
    }

    public CompletableFuture<PageResult<Product>> getAllPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return CompletableFuture.supplyAsync(() -> {
            List<Product> items = hydrateProducts(productRepository.findAllPaged(pageSize, offset));
            long total = productRepository.countAll();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<Product>> searchPaged(String searchTerm, int page, int pageSize) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAllPaged(page, pageSize);
        int offset = (page - 1) * pageSize;
        String likeTerm = "%" + searchTerm.trim() + "%";
        return CompletableFuture.supplyAsync(() -> {
            List<Product> items = hydrateProducts(productRepository.searchPaged(likeTerm, pageSize, offset));
            long total = productRepository.countSearch(likeTerm);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PartStatsDto> getStats() {
        return CompletableFuture.supplyAsync(productRepository::getStats);
    }

    private List<Product> hydrateProducts(List<Product> products) {
        if (products == null || products.isEmpty()) return products;

        List<Long> categoryIds = products.stream()
                .map(Product::getCategoryId).filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
        Map<Long, PartCategory> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : partCategoryRepository.findByIds(categoryIds).stream().collect(Collectors.toMap(PartCategory::getId, c -> c));

        for (Product product : products) {
            product.setCategory(categoryMap.get(product.getCategoryId()));
        }
        return products;
    }
}
