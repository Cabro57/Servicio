package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.DeviceDictionaryRepository;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.exception.AlreadyExistsException;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DeviceDictionaryManager {

    private final DeviceDictionaryRepository repository;

    public DeviceDictionaryManager(DeviceDictionaryRepository repository) {
        this.repository = repository;
    }

    // --- TÜRLER (Types) ---
    public CompletableFuture<List<DeviceType>> getAllTypes() {
        return CompletableFuture.supplyAsync(repository::findAllTypes);
    }

    public CompletableFuture<Integer> addType(String name) {
        return CompletableFuture.supplyAsync(() -> {
            repository.findTypeByName(name.trim()).ifPresent(type -> {
                throw new AlreadyExistsException("Bu cihaz türü zaten kayıtlı: " + name);
            });

            return repository.insertType(name.trim());
        });
    }

    public CompletableFuture<Void> deleteType(Long id) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.deleteType(id);
            } catch (Exception ex) {
                if (isForeignKeyViolation(ex)) {
                    throw new ValidationException("Bu tür bir veya daha fazla cihazda kullanıldığı için silinemez.");
                }
                throw ex;
            }
        });
    }

    private boolean isForeignKeyViolation(Throwable ex) {
        while (ex != null) {
            String message = ex.getMessage();
            if (message != null && message.toUpperCase().contains("FOREIGN KEY")) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }

    // --- MARKALAR (Brands) ---
    public CompletableFuture<List<DeviceBrand>> getBrandsByTypeId(Long typeId) {
        return CompletableFuture.supplyAsync(() -> repository.findBrandsByTypeId(typeId));
    }

    // Marka adı zaten kayıtlıysa var olanı kullanır, yoksa yeni oluşturur; ardından türe bağlar.
    public CompletableFuture<Void> addBrandToType(Long typeId, String name) {
        return CompletableFuture.supplyAsync(() -> {
            String trimmedName = name.trim();

            Long brandId = repository.findBrandByName(trimmedName)
                    .map(DeviceBrand::getId)
                    .orElseGet(() -> (long) repository.insertBrand(trimmedName));

            boolean alreadyLinked = repository.findBrandsByTypeId(typeId).stream()
                    .anyMatch(brand -> brand.getId().equals(brandId));

            if (alreadyLinked) {
                throw new AlreadyExistsException("Bu marka zaten bu türe kayıtlı: " + trimmedName);
            }

            repository.linkTypeAndBrand(typeId, brandId);
            return null;
        });
    }

    public CompletableFuture<Void> deleteBrand(Long id) {
        return CompletableFuture.runAsync(() -> {
            try {
                repository.deleteBrand(id);
            } catch (Exception ex) {
                if (isForeignKeyViolation(ex)) {
                    throw new ValidationException("Bu marka bir veya daha fazla cihazda kullanıldığı için silinemez.");
                }
                throw ex;
            }
        });
    }

    // --- İLİŞKİLENDİRME (Bağlantı Tablosu) ---
    public CompletableFuture<Void> linkBrandToType(Long typeId, Long brandId) {
        return CompletableFuture.runAsync(() -> repository.linkTypeAndBrand(typeId, brandId));
    }

    public CompletableFuture<Void> unlinkBrandFromType(Long typeId, Long brandId) {
        return CompletableFuture.runAsync(() -> repository.unlinkTypeAndBrand(typeId, brandId));
    }
}