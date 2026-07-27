package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.DocumentTemplateRepository;
import tr.cabro.servicio.model.DocumentTemplate;
import tr.cabro.servicio.model.enums.TemplateType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DocumentTemplateService {

    private final DocumentTemplateRepository repository;

    public DocumentTemplateService(DocumentTemplateRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<DocumentTemplate> save(DocumentTemplate template, boolean update) {
        return CompletableFuture.supplyAsync(() -> {
            template.setUpdatedAt(LocalDateTime.now());
            if (update) {
                repository.update(template);
            } else {
                Long id = repository.insert(template);
                template.setId(id);
            }
            return template;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<List<DocumentTemplate>> getByType(TemplateType type) {
        return CompletableFuture.supplyAsync(() -> repository.findByType(type));
    }

    public CompletableFuture<List<DocumentTemplate>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }
}
