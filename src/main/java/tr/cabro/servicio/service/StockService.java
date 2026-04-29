package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.StockMovementRepository;
import tr.cabro.servicio.model.StockMovement;
import tr.cabro.servicio.model.enums.StockType;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.concurrent.CompletableFuture;

public class StockService {
    private final StockMovementRepository stockRepo;

    public StockService(StockMovementRepository repository) {
        this.stockRepo = repository;
    }

    public CompletableFuture<Void> addStock(StockMovement movement) {
        validateMovement(movement);

        movement.setType(StockType.IN);
        movement.setQuantity(Math.abs(movement.getQuantity()));
        movement.setWarehouseId(1L);

        return CompletableFuture.runAsync(() -> stockRepo.insert(movement));
    }

    public CompletableFuture<Void> removeStock(StockMovement movement) {
        validateMovement(movement);

        movement.setType(StockType.OUT);
        movement.setQuantity(-Math.abs(movement.getQuantity()));
        movement.setWarehouseId(1L);

        return getStock(movement.getPartId()).thenCompose(currentStock -> {
            if (currentStock + movement.getQuantity() < 0) {
                throw new ValidationException("Yetersiz stok! Mevcut stok: " + currentStock +
                        ", Çıkılmak istenen: " + Math.abs(movement.getQuantity()));
            }
            return CompletableFuture.runAsync(() -> stockRepo.insert(movement));
        });
    }

    public CompletableFuture<Integer> getStock(Long partId) {
        return CompletableFuture.supplyAsync(() -> stockRepo.getStock(partId));
    }

    private void validateMovement(StockMovement movement) {
        // Zorunlu alan kontrolleri
        if (movement.getPartId() == null) {
            throw new ValidationException("Parça seçimi zorunludur.");
        }

        // Miktar kontrolü
        if (movement.getQuantity() == null || movement.getQuantity() <= 0) {
            throw new ValidationException("Stok miktarı 0'dan büyük olmalıdır.");
        }

        // Referans tipi kontrolü (Hangi işlem sebebiyle stok değişiyor? Satış mı, İade mi?)
        if (movement.getReferenceType() == null) {
            throw new ValidationException("Stok hareket kaynağı (Referans Tipi) belirtilmelidir.");
        }
    }
}
