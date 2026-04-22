package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.LaborRepository;
import tr.cabro.servicio.model.Labor;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class LaborManager {

    private final LaborRepository repository;

    public LaborManager(LaborRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Integer> addLabor(Labor labor) {
        return CompletableFuture.supplyAsync(() -> repository.insert(labor));
    }

    public CompletableFuture<Void> updateLabor(Labor labor) {
        return CompletableFuture.runAsync(() -> repository.update(labor));
    }

    public CompletableFuture<Void> deleteLabor(int id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<List<Labor>> getAllLabors() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    public CompletableFuture<List<Labor>> searchLabors(String searchStr) {
        return CompletableFuture.supplyAsync(() -> repository.search("%" + searchStr + "%"));
    }

    public CompletableFuture<Optional<Labor>> getLaborById(int id) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }
}