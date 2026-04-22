package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.PartRepository;
import tr.cabro.servicio.database.repository.SupplierRepository;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.model.Supplier;
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

    public PartService(PartRepository partRepository, SupplierRepository supplierRepository) {
        this.partRepository = partRepository;
        this.supplierRepository = supplierRepository;
    }

    public CompletableFuture<Part> save(Part part, boolean update) {
        if (Validator.isEmpty(part.getBarcode())) throw new ValidationException("Barkod boş olamaz.");
        if (Validator.isEmpty(part.getName())) throw new ValidationException("Ürün adı boş olamaz.");

        // FIX: BigDecimal karşılaştırması için compareTo kullanılmalı (< operatörü compile hatası)
        if (part.getPurchasePrice() != null && part.getPurchasePrice().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Alış fiyatı negatif olamaz.");
        if (part.getSalePrice() != null && part.getSalePrice().compareTo(BigDecimal.ZERO) < 0)
            throw new ValidationException("Satış fiyatı negatif olamaz.");

        // FIX: getStock() → getStockQuantity()
        if (part.getStockQuantity() < 0) throw new ValidationException("Stok miktarı negatif olamaz.");

        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                // FIX: existsByBarcode metodu PartRepository'ye eklendi
                if (partRepository.existsByBarcode(part.getBarcode())) {
                    throw new ValidationException("Bu barkod (" + part.getBarcode() + ") zaten sistemde kayıtlı!");
                }
                int id = partRepository.insert(part);
                part.setId(id);
            } else {
                partRepository.update(part);
            }
            return part;
        });
    }

    /** ID tabanlı silme */
    public CompletableFuture<Void> delete(int id) {
        return CompletableFuture.runAsync(() -> partRepository.delete(id));
    }

    /** Barcode tabanlı silme */
    public CompletableFuture<Void> deleteByBarcode(String barcode) {
        // FIX: deleteByBarcode metodu PartRepository'ye eklendi
        return CompletableFuture.runAsync(() -> partRepository.deleteByBarcode(barcode));
    }

    public CompletableFuture<Void> deleteMultiple(List<String> barcodes) {
        // FIX: deleteByBarcodes metodu PartRepository'ye eklendi
        return CompletableFuture.runAsync(() -> partRepository.deleteByBarcodes(barcodes));
    }

    public CompletableFuture<List<Part>> getAll() {
        return CompletableFuture.supplyAsync(() -> hydrateParts(partRepository.findAll()));
    }

    public CompletableFuture<Optional<Part>> get(String barcode) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Part> optPart = partRepository.findByBarcode(barcode);
            optPart.ifPresent(p -> hydrateParts(Collections.singletonList(p)));
            return optPart;
        });
    }

    public CompletableFuture<Optional<Part>> getById(int id) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<Part> optPart = partRepository.findById(id);
            optPart.ifPresent(p -> hydrateParts(Collections.singletonList(p)));
            return optPart;
        });
    }

    public CompletableFuture<List<Part>> getPartsBelowMinStock() {
        // FIX: findBelowMinStock() → artık PartRepository'de default alias olarak tanımlı
        return CompletableFuture.supplyAsync(() -> hydrateParts(partRepository.findBelowMinStock()));
    }

    public CompletableFuture<Boolean> isBarcodeAvailable(String barcode) {
        return CompletableFuture.supplyAsync(() -> !partRepository.existsByBarcode(barcode));
    }

    public CompletableFuture<Void> increaseStock(String barcode, int amount) {
        if (amount <= 0) throw new ValidationException("Artırılacak miktar 0'dan büyük olmalıdır.");
        // FIX: increaseStockAtomically(barcode, amount) metodu eklendi
        return CompletableFuture.runAsync(() -> partRepository.increaseStockAtomically(barcode, amount));
    }

    public CompletableFuture<Void> decreaseStock(String barcode, int amount) {
        if (amount <= 0) throw new ValidationException("Azaltılacak miktar 0'dan büyük olmalıdır.");

        // FIX: decreaseStockAtomically(barcode, amount) metodu eklendi
        return CompletableFuture.runAsync(() -> {
            int updatedRows = partRepository.decreaseStockAtomically(barcode, amount);
            if (updatedRows == 0) {
                throw new ValidationException(
                        String.format("İşlem Başarısız! Barkod: %s için yeterli stok bulunamadı veya ürün yok.", barcode));
            }
        });
    }

    public CompletableFuture<List<Part>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return CompletableFuture.supplyAsync(
                () -> hydrateParts(partRepository.search("%" + searchTerm.trim() + "%")));
    }

    // --- Helper ---
    private List<Part> hydrateParts(List<Part> parts) {
        if (parts == null || parts.isEmpty()) return parts;

        List<Integer> supplierIds = parts.stream()
                .map(Part::getSupplierId)
                .filter(id -> id != null && id > 0)
                .distinct()
                .collect(Collectors.toList());

        if (supplierIds.isEmpty()) {
            parts.forEach(p -> p.setSupplier(new Supplier()));
            return parts;
        }

        Map<Integer, Supplier> supplierMap = supplierRepository.findByIds(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getId, s -> s));

        for (Part part : parts) {
            part.setSupplier(supplierMap.get(part.getSupplierId()));
        }
        return parts;
    }
}