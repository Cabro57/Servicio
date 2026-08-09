package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.LaborRepository;
import tr.cabro.servicio.model.Labor;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Validator;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class LaborService {

    private final LaborRepository repository;

    public LaborService(LaborRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Labor> save(Labor labor, boolean updated) {
        validateLabor(labor);

        return CompletableFuture.supplyAsync(() -> {
            if (updated) {
                repository.update(labor);
            } else {
                Long id = repository.insert(labor);
                labor.setId(id);
            }

            return labor;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<Labor>> get(Long id) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }

    public CompletableFuture<List<Labor>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    // Belirli bir cihaz türüne özel + türden bağımsız (Genel) işçilikleri getirir.
    public CompletableFuture<List<Labor>> getByTypeId(Long typeId) {
        return CompletableFuture.supplyAsync(() -> repository.findByTypeId(typeId));
    }

    public CompletableFuture<List<Labor>> search(String searchStr) {
        return CompletableFuture.supplyAsync(() -> repository.search("%" + searchStr + "%"));
    }

    private void validateLabor(Labor labor) {
        if (Validator.isEmpty(labor.getName())) {
            throw new ValidationException("İşçilik adı boş bırakılamaz.");
        }
        if (labor.getDefaultPrice() != null && labor.getDefaultPrice().compareTo(BigDecimal.ZERO) < 0) {
            throw new ValidationException("İşçilik fiyatı negatif olamaz.");
        }
    }
}