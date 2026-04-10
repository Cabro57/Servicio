package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.PartRepository;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Validator;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class PartService {

    private final PartRepository repository;

    public PartService(PartRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Part> save(Part part, boolean update) {
        // --- Validasyon (Ana Thread - Anında fırlatılır) ---
        if (Validator.isEmpty(part.getBarcode())) throw new ValidationException("Barkod boş olamaz.");
        if (Validator.isEmpty(part.getName())) throw new ValidationException("Ürün adı boş olamaz.");
        if (Validator.isEmpty(part.getBrand())) throw new ValidationException("Marka boş olamaz.");
        if (part.getPurchasePrice() < 0) throw new ValidationException("Alış fiyatı negatif olamaz.");
        if (part.getSalePrice() < 0) throw new ValidationException("Satış fiyatı negatif olamaz.");
        if (part.getStock() < 0) throw new ValidationException("Stok miktarı negatif olamaz.");

        // --- Veritabanı İşlemi (Arka Plan Thread) ---
        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                // Sadece yeni kayıtta barkod çakışma kontrolü (Arka planda veritabanına sorulur)
                if (repository.existsByBarcode(part.getBarcode())) {
                    throw new ValidationException("Bu barkod (" + part.getBarcode() + ") zaten sistemde kayıtlı!");
                }
                repository.insert(part);
            } else {
                repository.update(part);
            }
            return part;
        });
    }

    public CompletableFuture<Void> delete(String barcode) {
        return CompletableFuture.runAsync(() -> repository.delete(barcode));
    }

    public CompletableFuture<Void> deleteMultiple(List<String> barcodes) {
        return CompletableFuture.runAsync(() -> repository.deleteByBarcodes(barcodes));
    }

    public CompletableFuture<List<Part>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    public CompletableFuture<Optional<Part>> get(String barcode) {
        return CompletableFuture.supplyAsync(() -> repository.findByBarcode(barcode));
    }

    public CompletableFuture<List<Part>> getPartsBelowMinStockAsync() {
        return CompletableFuture.supplyAsync(repository::findBelowMinStock);
    }

    public CompletableFuture<Boolean> isBarcodeAvailableAsync(String barcode) {
        return CompletableFuture.supplyAsync(() -> !repository.existsByBarcode(barcode));
    }

    public CompletableFuture<Void> increaseStockAsync(String barcode, int amount) {
        if (amount <= 0) throw new ValidationException("Artırılacak miktar 0'dan büyük olmalıdır.");
        return CompletableFuture.runAsync(() -> repository.increaseStockAtomically(barcode, amount));
    }

    public CompletableFuture<Void> decreaseStockAsync(String barcode, int amount) {
        if (amount <= 0) throw new ValidationException("Azaltılacak miktar 0'dan büyük olmalıdır.");

        return CompletableFuture.runAsync(() -> {
            int updatedRows = repository.decreaseStockAtomically(barcode, amount);

            if (updatedRows == 0) {
                throw new ValidationException(String.format("İşlem Başarısız! Barkod: %s için yeterli stok bulunamadı veya ürün yok.", barcode));
            }
        });
    }

    public CompletableFuture<List<Part>> searchAsync(String searchTerm) {
        // İleride repository entegrasyonu yapılacak
        return CompletableFuture.supplyAsync(Collections::emptyList);
    }
}