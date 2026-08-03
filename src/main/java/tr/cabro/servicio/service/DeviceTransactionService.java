package tr.cabro.servicio.service;

import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.repository.DeviceTransactionRepository;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.DeviceTransactionType;
import tr.cabro.servicio.service.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class DeviceTransactionService {

    private final DeviceTransactionRepository repository;

    public DeviceTransactionService(DeviceTransactionRepository repository) {
        this.repository = repository;
    }

    /** Bir cihazın satın alınması — mevcut Device/Customer üzerinden, yeni bir PURCHASE kaydı açar. */
    public CompletableFuture<DeviceTransaction> recordPurchase(Long deviceId, Long sellerCustomerId,
                                                                java.math.BigDecimal price, LocalDateTime purchaseDate,
                                                                String expertiseNotes, String note) {
        if (deviceId == null) throw new ValidationException("Cihaz seçilmeden alım kaydı oluşturulamaz.");
        if (sellerCustomerId == null) throw new ValidationException("Satıcı müşteri seçilmeden alım kaydı oluşturulamaz.");

        return CompletableFuture.supplyAsync(() -> {
            Optional<DeviceTransaction> latest = repository.findLatestByDeviceId(deviceId);
            if (latest.isPresent() && latest.get().getType() == DeviceTransactionType.PURCHASE) {
                throw new ValidationException("Bu cihaz zaten stokta — tekrar alım kaydı açılamaz.");
            }

            DeviceTransaction transaction = new DeviceTransaction();
            transaction.setDeviceId(deviceId);
            transaction.setType(DeviceTransactionType.PURCHASE);
            transaction.setCustomerId(sellerCustomerId);
            transaction.setTransactionDate(purchaseDate != null ? purchaseDate : LocalDateTime.now());
            transaction.setPrice(price);
            transaction.setExpertiseNotes(expertiseNotes);
            transaction.setNote(note);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());

            Long id = repository.insert(transaction);
            transaction.setId(id);
            return transaction;
        });
    }

    /** Stoktaki bir cihazın satılması — yalnızca en son işlemi PURCHASE olan cihazlar için. */
    public CompletableFuture<DeviceTransaction> recordSale(Long deviceId, Long buyerCustomerId,
                                                            java.math.BigDecimal price, LocalDateTime saleDate,
                                                            Integer warrantyMonths, String note) {
        if (deviceId == null) throw new ValidationException("Cihaz seçilmeden satış kaydı oluşturulamaz.");
        if (buyerCustomerId == null) throw new ValidationException("Alıcı müşteri seçilmeden satış kaydı oluşturulamaz.");

        return CompletableFuture.supplyAsync(() -> {
            Optional<DeviceTransaction> latest = repository.findLatestByDeviceId(deviceId);
            if (latest.isEmpty() || latest.get().getType() != DeviceTransactionType.PURCHASE) {
                throw new ValidationException("Bu cihaz stokta değil — önce alım kaydı oluşturulmalı.");
            }

            DeviceTransaction transaction = new DeviceTransaction();
            transaction.setDeviceId(deviceId);
            transaction.setType(DeviceTransactionType.SALE);
            transaction.setCustomerId(buyerCustomerId);
            transaction.setTransactionDate(saleDate != null ? saleDate : LocalDateTime.now());
            transaction.setPrice(price);
            transaction.setWarrantyMonths(warrantyMonths);
            transaction.setNote(note);
            transaction.setCreatedAt(LocalDateTime.now());
            transaction.setUpdatedAt(LocalDateTime.now());

            Long id = repository.insert(transaction);
            transaction.setId(id);
            return transaction;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<DeviceTransaction>> get(Long id) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }

    /** Cihaz Geçmişi paneli için — bir cihazın tüm alım/satım olayları, en yeni önce. */
    public CompletableFuture<List<DeviceTransaction>> getHistoryByDeviceId(Long deviceId) {
        return CompletableFuture.supplyAsync(() -> repository.findByDeviceId(deviceId));
    }

    public CompletableFuture<List<DeviceTransaction>> getCurrentStock() {
        return CompletableFuture.supplyAsync(repository::findCurrentStockDevices);
    }

    public CompletableFuture<PageResult<DeviceTransaction>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                                 boolean onlyInStock, int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> repository.searchFilteredPaged(searchTerm, filters, onlyInStock, page, pageSize));
    }
}
