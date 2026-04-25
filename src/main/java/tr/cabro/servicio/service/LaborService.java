package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.LaborRepository;
import tr.cabro.servicio.model.Labor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class LaborService {

    private final LaborRepository repository;

    public LaborService(LaborRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Labor> save(Labor labor, boolean updated) {
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

    public CompletableFuture<List<Labor>> search(String searchStr) {
        return CompletableFuture.supplyAsync(() -> repository.search("%" + searchStr + "%"));
    }
}