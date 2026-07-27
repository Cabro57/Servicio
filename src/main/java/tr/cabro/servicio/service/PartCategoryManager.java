package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.PartCategoryRepository;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.service.exception.AlreadyExistsException;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PartCategoryManager {

    private final PartCategoryRepository repository;

    public PartCategoryManager(PartCategoryRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<List<PartCategory>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    public CompletableFuture<Integer> add(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Kategori adı boş olamaz.");
        }
        return CompletableFuture.supplyAsync(() -> {
            repository.findByName(name.trim()).ifPresent(category -> {
                throw new AlreadyExistsException("Bu kategori zaten kayıtlı: " + name);
            });
            return repository.insert(name.trim());
        });
    }

    public CompletableFuture<Void> rename(Long id, String newName) {
        if (newName == null || newName.trim().isEmpty()) {
            throw new ValidationException("Kategori adı boş olamaz.");
        }
        return CompletableFuture.runAsync(() -> {
            repository.findByName(newName.trim())
                    .filter(category -> !category.getId().equals(id))
                    .ifPresent(category -> {
                        throw new AlreadyExistsException("Bu kategori zaten kayıtlı: " + newName);
                    });
            repository.rename(id, newName.trim());
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }
}
