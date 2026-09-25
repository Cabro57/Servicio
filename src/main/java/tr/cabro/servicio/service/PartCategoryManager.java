package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.PartCategoryRepository;
import tr.cabro.servicio.model.dictionary.PartCategory;
import tr.cabro.servicio.model.enums.CategoryScope;
import tr.cabro.servicio.service.exception.AlreadyExistsException;
import tr.cabro.servicio.service.exception.ValidationException;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Parça ve ürün kategorileri. Silinen kategorinin kayıtları başka bir kategoriye taşınabilir
 * (birleştirme) ya da kategorisiz bırakılır.
 */
public class PartCategoryManager {

    private final PartCategoryRepository repository;

    public PartCategoryManager(PartCategoryRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<List<PartCategory>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    /**
     * {@code catalog} (PART ya da PRODUCT) ekranında seçilebilen kategoriler. {@code keepId} kapsam
     * dışında kalsa da listede tutulur: kaydın mevcut kategorisi düzenlemede kaybolmasın.
     */
    public CompletableFuture<List<PartCategory>> getFor(CategoryScope catalog, Long keepId) {
        return getAll().thenApply(list -> list.stream()
                .filter(c -> c.appliesTo(catalog) || c.getId().equals(keepId))
                .collect(Collectors.toList()));
    }

    public CompletableFuture<Integer> add(String name, CategoryScope scope) {
        return CompletableFuture.supplyAsync(() -> {
            String trimmed = requireName(name);
            repository.findByName(trimmed).ifPresent(category -> {
                throw new AlreadyExistsException("\"" + category.getName() + "\" kategorisi zaten kayıtlı.");
            });
            return repository.insert(trimmed, (scope != null ? scope : CategoryScope.BOTH).name());
        });
    }

    public CompletableFuture<Void> update(Long id, String name, CategoryScope scope) {
        return CompletableFuture.runAsync(() -> {
            String trimmed = requireName(name);
            repository.findByName(trimmed)
                    .filter(category -> !category.getId().equals(id))
                    .ifPresent(category -> {
                        throw new AlreadyExistsException("\"" + category.getName() + "\" kategorisi zaten kayıtlı.");
                    });
            repository.update(id, trimmed, (scope != null ? scope : CategoryScope.BOTH).name());
        });
    }

    /** Kategoriyi siler; {@code targetId} verilirse parçalar ve ürünler önce oraya taşınır, verilmezse kategorisiz kalır. */
    public CompletableFuture<Void> delete(Long id, Long targetId) {
        return CompletableFuture.runAsync(() -> DatabaseManager.useTransaction(handle -> {
            if (Objects.equals(id, targetId)) throw new ValidationException("Kategori kendisine taşınamaz.");
            PartCategoryRepository repo = handle.attach(PartCategoryRepository.class);
            if (targetId != null) {
                repo.moveParts(id, targetId);
                repo.moveProducts(id, targetId);
            }
            repo.delete(id);
        }));
    }

    private static String requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new ValidationException("Kategori adı boş olamaz.");
        }
        return name.trim();
    }
}
