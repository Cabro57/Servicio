package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.ServiceNoteRepository;
import tr.cabro.servicio.model.ServiceNote;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServiceNoteManager {

    private final ServiceNoteRepository repository;

    public ServiceNoteManager(ServiceNoteRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<ServiceNote> addNote(ServiceNote note) {
        return CompletableFuture.supplyAsync(() -> {
            int id = repository.insert(note);
            note.setId(id);
            return note;
        });
    }

    public CompletableFuture<Void> deleteNote(int id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<List<ServiceNote>> getNotesForService(int serviceId) {
        return CompletableFuture.supplyAsync(() -> repository.findByServiceId(serviceId));
    }
}