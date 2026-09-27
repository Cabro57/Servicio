package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.WarehouseRepository;
import tr.cabro.servicio.model.Warehouse;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Depo tanımları (Ayarlar > Depolar). Kurallar:
 * <ul>
 *   <li>Her zaman tam bir varsayılan depo vardır; varsayılan depo pasife alınamaz/silinemez.</li>
 *   <li>Stoğu olan depo pasife alınamaz — önce transfer edilmelidir.</li>
 *   <li>Hareketi olan depo silinemez (geçmiş korunur), yalnızca pasife alınır.</li>
 * </ul>
 */
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public WarehouseService(WarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    public CompletableFuture<List<Warehouse>> getAll() {
        return DbExecutor.supply(warehouseRepository::findAll);
    }

    /** Yeni işlemlerde seçilebilecek depolar (varsayılan önce). */
    public CompletableFuture<List<Warehouse>> getActive() {
        return DbExecutor.supply(warehouseRepository::findActive);
    }

    /** Ayarlar listesi: kalem sayısı, adet, stok değeri ve hareket sayısıyla. */
    public CompletableFuture<List<Warehouse>> getAllWithStats() {
        return DbExecutor.supply(warehouseRepository::findAllWithStats);
    }

    public CompletableFuture<Warehouse> save(Warehouse warehouse) {
        String name = warehouse.getName() != null ? warehouse.getName().trim() : "";
        if (name.isEmpty()) throw new ValidationException("Depo adı boş olamaz.");
        if (name.length() > 100) throw new ValidationException("Depo adı en fazla 100 karakter olabilir.");
        warehouse.setName(name);
        if (warehouse.getDescription() != null && warehouse.getDescription().trim().isEmpty()) {
            warehouse.setDescription(null);
        }

        return DbExecutor.supply(() -> DatabaseManager.inTransaction(handle -> {
            WarehouseRepository repo = handle.attach(WarehouseRepository.class);
            if (repo.existsByName(name, warehouse.getId())) {
                throw new ValidationException("'" + name + "' adında bir depo zaten var.");
            }
            if (warehouse.getId() == null) {
                warehouse.setSortOrder(repo.nextSortOrder());
                warehouse.setActive(true);
                warehouse.setId(repo.insert(warehouse));
            } else {
                repo.update(warehouse);
            }
            return warehouse;
        }));
    }

    public CompletableFuture<Void> setDefault(Long id) {
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            WarehouseRepository repo = handle.attach(WarehouseRepository.class);
            repo.findById(id).orElseThrow(() -> new ValidationException("Depo bulunamadı."));
            repo.clearDefault();
            repo.markDefault(id);
        }));
    }

    public CompletableFuture<Void> setActive(Long id, boolean active) {
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            WarehouseRepository repo = handle.attach(WarehouseRepository.class);
            Warehouse warehouse = repo.findById(id).orElseThrow(() -> new ValidationException("Depo bulunamadı."));
            if (!active) {
                if (warehouse.isDefaultWarehouse()) {
                    throw new ValidationException("Varsayılan depo pasife alınamaz. Önce başka bir depoyu varsayılan yapın.");
                }
                if (repo.hasStock(id)) {
                    throw new ValidationException("'" + warehouse.getName()
                            + "' deposunda stok var. Pasife almadan önce stoğu başka depoya transfer edin.");
                }
            }
            repo.setActive(id, active);
        }));
    }

    public CompletableFuture<Void> delete(Long id) {
        return DbExecutor.run(() -> DatabaseManager.useTransaction(handle -> {
            WarehouseRepository repo = handle.attach(WarehouseRepository.class);
            Warehouse warehouse = repo.findById(id).orElseThrow(() -> new ValidationException("Depo bulunamadı."));
            if (warehouse.isDefaultWarehouse()) {
                throw new ValidationException("Varsayılan depo silinemez.");
            }
            if (repo.hasMovements(id)) {
                throw new ValidationException("'" + warehouse.getName()
                        + "' deposunun stok geçmişi var; silinemez, yalnızca pasife alınabilir.");
            }
            repo.delete(id);
        }));
    }
}
