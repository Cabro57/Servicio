package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.DeviceDictionaryRepository;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.exception.AlreadyExistsException;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Cihaz türü ve marka sözlüğü.
 * <p>
 * Kullanımdaki bir tür ya da marka doğrudan silinmez: kayıtları (cihazlar, türe özel işçilikler,
 * marka bağlantıları) önce başka bir kayda taşınır, sonra silinir. Aynı işlem yinelenen kayıtları
 * ("Samsung" / "Samsung Electronics") birleştirmek için de kullanılır.
 */
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
            String trimmed = requireName(name, "Tür adı boş olamaz.");
            repository.findTypeByName(trimmed).ifPresent(type -> {
                throw new AlreadyExistsException("\"" + type.getName() + "\" türü zaten kayıtlı.");
            });
            return repository.insertType(trimmed);
        });
    }

    public CompletableFuture<Void> renameType(Long id, String name) {
        return CompletableFuture.runAsync(() -> {
            String trimmed = requireName(name, "Tür adı boş olamaz.");
            repository.findTypeByName(trimmed)
                    .filter(type -> !type.getId().equals(id))
                    .ifPresent(type -> {
                        throw new AlreadyExistsException("\"" + type.getName() + "\" türü zaten kayıtlı.");
                    });
            repository.renameType(id, trimmed);
        });
    }

    /**
     * Türü siler. {@code targetId} verilirse önce cihazlar, türe özel işçilikler ve marka bağlantıları
     * o türe taşınır (birleştirme). Verilmezse tür kullanımda olmamalı.
     */
    public CompletableFuture<Void> deleteType(Long id, Long targetId) {
        return CompletableFuture.runAsync(() -> DatabaseManager.useTransaction(handle -> {
            if (Objects.equals(id, targetId)) throw new ValidationException("Tür kendisine taşınamaz.");
            DeviceDictionaryRepository repo = handle.attach(DeviceDictionaryRepository.class);
            if (targetId != null) {
                repo.moveDevicesToType(id, targetId);
                repo.moveLaborsToType(id, targetId);
                repo.copyBrandLinksToType(id, targetId);
            }
            try {
                repo.deleteType(id);
            } catch (RuntimeException ex) {
                if (isForeignKeyViolation(ex)) {
                    throw new ValidationException("Bu tür cihaz kayıtlarında kullanılıyor; silmek için kayıtları başka bir türe taşıyın.");
                }
                throw ex;
            }
        }));
    }

    // --- MARKALAR (Brands) ---

    public CompletableFuture<List<DeviceBrand>> getBrandsByTypeId(Long typeId) {
        return CompletableFuture.supplyAsync(() -> repository.findBrandsByTypeId(typeId));
    }

    public CompletableFuture<List<DeviceBrand>> getAllBrands() {
        return CompletableFuture.supplyAsync(repository::findAllBrands);
    }

    public CompletableFuture<List<Long>> getTypeIdsOfBrand(Long brandId) {
        return CompletableFuture.supplyAsync(() -> repository.findTypeIdsByBrand(brandId));
    }

    /** Marka adı başka türde zaten kayıtlıysa aynı marka kullanılır, yoksa oluşturulur; ardından türe bağlanır. */
    public CompletableFuture<Void> addBrandToType(Long typeId, String name) {
        return CompletableFuture.runAsync(() -> {
            String trimmed = requireName(name, "Marka adı boş olamaz.");
            Long brandId = repository.findBrandByName(trimmed)
                    .map(DeviceBrand::getId)
                    .orElseGet(() -> (long) repository.insertBrand(trimmed));

            if (repository.findTypeIdsByBrand(brandId).contains(typeId)) {
                throw new AlreadyExistsException("\"" + trimmed + "\" bu türde zaten var.");
            }
            repository.linkTypeAndBrand(typeId, brandId);
        });
    }

    public CompletableFuture<Void> renameBrand(Long id, String name) {
        return CompletableFuture.runAsync(() -> {
            String trimmed = requireName(name, "Marka adı boş olamaz.");
            repository.findBrandByName(trimmed)
                    .filter(brand -> !brand.getId().equals(id))
                    .ifPresent(brand -> {
                        throw new AlreadyExistsException("\"" + brand.getName() + "\" markası zaten kayıtlı.");
                    });
            repository.renameBrand(id, trimmed);
        });
    }

    /**
     * Markanın bağlı olduğu türleri {@code typeIds} ile eşitler. Hiçbir türe bağlı kalmayan ve
     * cihazda kullanılmayan marka silinir.
     */
    public CompletableFuture<Void> setBrandTypes(Long brandId, Set<Long> typeIds) {
        return CompletableFuture.runAsync(() -> DatabaseManager.useTransaction(handle -> {
            DeviceDictionaryRepository repo = handle.attach(DeviceDictionaryRepository.class);
            Set<Long> current = new HashSet<>(repo.findTypeIdsByBrand(brandId));
            for (Long typeId : current) {
                if (!typeIds.contains(typeId)) repo.unlinkTypeAndBrand(typeId, brandId);
            }
            for (Long typeId : typeIds) {
                if (!current.contains(typeId)) repo.linkTypeAndBrand(typeId, brandId);
            }
            repo.deleteBrandIfOrphan(brandId);
        }));
    }

    /**
     * Markayı türden ayırır. Cihaz kayıtları değişmez (marka ve türlerini korur); marka yalnızca bu
     * türün seçim listesinden çıkar. Başka türe bağlı değilse ve cihazda kullanılmıyorsa silinir.
     */
    public CompletableFuture<Void> unlinkBrandFromType(Long typeId, Long brandId) {
        return CompletableFuture.runAsync(() -> DatabaseManager.useTransaction(handle -> {
            DeviceDictionaryRepository repo = handle.attach(DeviceDictionaryRepository.class);
            repo.unlinkTypeAndBrand(typeId, brandId);
            repo.deleteBrandIfOrphan(brandId);
        }));
    }

    /**
     * Markayı tamamen siler. {@code targetId} verilirse önce cihazlar ve tür bağlantıları o markaya
     * taşınır (birleştirme). Verilmezse marka hiçbir cihazda kullanılmıyor olmalı.
     */
    public CompletableFuture<Void> deleteBrand(Long id, Long targetId) {
        return CompletableFuture.runAsync(() -> DatabaseManager.useTransaction(handle -> {
            if (Objects.equals(id, targetId)) throw new ValidationException("Marka kendisine taşınamaz.");
            DeviceDictionaryRepository repo = handle.attach(DeviceDictionaryRepository.class);
            if (targetId != null) {
                repo.moveDevicesToBrand(id, targetId);
                repo.copyTypeLinksToBrand(id, targetId);
            }
            try {
                repo.deleteBrand(id);
            } catch (RuntimeException ex) {
                if (isForeignKeyViolation(ex)) {
                    throw new ValidationException("Bu marka cihaz kayıtlarında kullanılıyor; silmek için kayıtları başka bir markaya taşıyın.");
                }
                throw ex;
            }
        }));
    }

    // --- Yardımcılar ---

    private static String requireName(String name, String message) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException(message);
        }
        return name.trim();
    }

    private static boolean isForeignKeyViolation(Throwable ex) {
        while (ex != null) {
            String message = ex.getMessage();
            if (message != null && message.toUpperCase().contains("FOREIGN KEY")) {
                return true;
            }
            ex = ex.getCause();
        }
        return false;
    }
}
