package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.PartCategoryRepository;
import tr.cabro.servicio.database.repository.PartRepository;
import tr.cabro.servicio.database.repository.SupplierRepository;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
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

public class PartService {

    private final PartRepository partRepository;
    private final SupplierRepository supplierRepository;
    private final StockService stockService;
    private final PartCategoryRepository partCategoryRepository;

    public PartService(PartRepository partRepository, SupplierRepository supplierRepository, StockService stockService,
                        PartCategoryRepository partCategoryRepository) {
        this.partRepository = partRepository;
        this.supplierRepository = supplierRepository;
        this.stockService = stockService;
        this.partCategoryRepository = partCategoryRepository;
    }

    public CompletableFuture<Part> save(Part part, boolean update) {
        if (Validator.isEmpty(part.getBarcode())) throw new ValidationException("Barkod boş olamaz.");
        if (Validator.isEmpty(part.getName())) throw new ValidationException("Ürün adı boş olamaz.");

        // FIX: BigDecimal karşılaştırması için compareTo kullanılmalı (< operatörü compile hatası)
        if (part.getPurchasePrice() != null && part.getPurchasePrice().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Alış fiyatı negatif olamaz.");
        if (part.getSalePrice() != null && part.getSalePrice().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Satış fiyatı negatif olamaz.");

        if (part.getStockQuantity() != null && part.getStockQuantity() < 0)
            throw new ValidationException("Stok miktarı negatif olamaz.");

        // Stok kartla birlikte yalnızca AÇILIŞTA girilir ve açılış hareketi olarak deftere yazılır.
        // Düzenlemede stok hiç değişmez (update SQL'inde stock_quantity yok); stok değişiklikleri
        // StockService'in giriş/çıkış/sayım/transfer işlemleriyle, sebebiyle birlikte yapılır.
        int openingStock = part.getStockQuantity() != null ? part.getStockQuantity() : 0;

        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            PartRepository repo = handle.attach(PartRepository.class);
            if (!update) {
                if (repo.existsByBarcode(part.getBarcode())) {
                    throw new ValidationException("Bu barkod (" + part.getBarcode() + ") zaten sistemde kayıtlı!");
                }
                Long id = repo.insert(part);
                part.setId(id);
                if (openingStock > 0) {
                    Long warehouseId = stockService.resolveWarehouse(handle, part.getOpeningWarehouseId(), true);
                    stockService.record(handle, StockItemKind.PART, id, warehouseId, openingStock,
                            ReferenceType.OPENING, null, part.getPurchasePrice(), null);
                }
                part.setStockQuantity(openingStock);
            } else {
                repo.update(part);
                part.setStockQuantity(repo.findById(part.getId()).map(Part::getStockQuantity).orElse(0));
            }
            return part;
        }));
    }

    /** ID tabanlı silme */
    public CompletableFuture<Void> delete(Long id) {
        return DbExecutor.run(() -> partRepository.delete(id));
    }

    /** Barcode tabanlı silme */
    public CompletableFuture<Void> deleteByBarcode(String barcode) {
        // FIX: deleteByBarcode metodu PartRepository'ye eklendi
        return DbExecutor.run(() -> partRepository.deleteByBarcode(barcode));
    }

    public CompletableFuture<Void> deleteMultiple(List<String> barcodes) {
        // FIX: deleteByBarcodes metodu PartRepository'ye eklendi
        return DbExecutor.run(() -> partRepository.deleteByBarcodes(barcodes));
    }

    public CompletableFuture<List<Part>> getAll() {
        return DbExecutor.supply(() -> hydrateParts(partRepository.findAll()));
    }

    public CompletableFuture<Optional<Part>> get(String barcode) {
        return DbExecutor.supply(() -> {
            Optional<Part> optPart = partRepository.findByBarcode(barcode);
            optPart.ifPresent(p -> hydrateParts(Collections.singletonList(p)));
            return optPart;
        });
    }

    public CompletableFuture<Optional<Part>> getById(Long id) {
        return DbExecutor.supply(() -> {
            Optional<Part> optPart = partRepository.findById(id);
            optPart.ifPresent(p -> hydrateParts(Collections.singletonList(p)));
            return optPart;
        });
    }

    public CompletableFuture<List<Part>> getBySupplierId(Long supplierId) {
        return DbExecutor.supply(() -> hydrateParts(partRepository.findBySupplierId(supplierId)));
    }

    public CompletableFuture<List<Part>> getPartsBelowMinStock() {
        // FIX: findBelowMinStock() → artık PartRepository'de default alias olarak tanımlı
        return DbExecutor.supply(() -> hydrateParts(partRepository.findBelowMinStock()));
    }

    public CompletableFuture<Boolean> isBarcodeAvailable(String barcode) {
        return DbExecutor.supply(() -> !partRepository.existsByBarcode(barcode));
    }

    public CompletableFuture<List<Part>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return DbExecutor.supply(
                () -> hydrateParts(partRepository.search("%" + searchTerm.trim() + "%")));
    }

    // =========================================================================
    // SAYFALAMA (LIMIT/OFFSET) — DB-tabanlı liste ekranı için
    // =========================================================================

    public CompletableFuture<PageResult<Part>> getAllPaged(int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        return DbExecutor.supply(() -> {
            List<Part> items = hydrateParts(partRepository.findAllPaged(pageSize, offset));
            long total = partRepository.countAll();
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    public CompletableFuture<PageResult<Part>> searchPaged(String searchTerm, int page, int pageSize) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAllPaged(page, pageSize);
        int offset = (page - 1) * pageSize;
        String likeTerm = "%" + searchTerm.trim() + "%";
        return DbExecutor.supply(() -> {
            List<Part> items = hydrateParts(partRepository.searchPaged(likeTerm, pageSize, offset));
            long total = partRepository.countSearch(likeTerm);
            return new PageResult<>(items, page, pageSize, total);
        });
    }

    /** Liste sayfası: arama + görünüm sekmesi/başlık filtreleri + sayfalama. */
    public CompletableFuture<PageResult<Part>> searchFilteredPaged(String searchTerm,
            java.util.Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters, int page, int pageSize) {
        return DbExecutor.supply(() -> {
            PageResult<Part> r = partRepository.searchFilteredPaged(searchTerm, filters, page, pageSize);
            return new PageResult<>(hydrateParts(r.getItems()), r.getPage(), r.getPageSize(), r.getTotalItems());
        });
    }

    public CompletableFuture<PageResult<Part>> searchFilteredPaged(String searchTerm,
            java.util.Map<String, tr.cabro.servicio.database.filter.ColumnFilterValue> filters, int page, int pageSize, String sortKey) {
        return DbExecutor.supply(() -> {
            PageResult<Part> r = partRepository.searchFilteredPaged(searchTerm, filters, page, pageSize, sortKey);
            return new PageResult<>(hydrateParts(r.getItems()), r.getPage(), r.getPageSize(), r.getTotalItems());
        });
    }

    public CompletableFuture<PartStatsDto> getStats() {
        return DbExecutor.supply(partRepository::getStats);
    }

    // --- Helper ---
    private List<Part> hydrateParts(List<Part> parts) {
        if (parts == null || parts.isEmpty()) return parts;

        List<Long> supplierIds = parts.stream()
                .map(Part::getSupplierId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Supplier> supplierMap = supplierIds.isEmpty()
                ? Collections.emptyMap()
                : supplierRepository.findByIds(supplierIds).stream()
                  .collect(Collectors.toMap(Supplier::getId, s -> s));

        List<Long> categoryIds = parts.stream()
                .map(Part::getCategoryId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, PartCategory> categoryMap = categoryIds.isEmpty()
                ? Collections.emptyMap()
                : partCategoryRepository.findByIds(categoryIds).stream()
                  .collect(Collectors.toMap(PartCategory::getId, c -> c));

        for (Part part : parts) {
            part.setSupplier(supplierMap.getOrDefault(part.getSupplierId(), new Supplier()));
            part.setCategory(categoryMap.get(part.getCategoryId()));
        }
        return parts;
    }
}