package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.ServiceNoteRepository;
import tr.cabro.servicio.model.WorkOrderNote;

import java.util.List;
import java.util.concurrent.CompletableFuture;
// TODO bu servis sınıfı WorkOrder Servisine taşınacak
@Deprecated
public class ServiceNoteManager {

    private final ServiceNoteRepository repository;

    public ServiceNoteManager(ServiceNoteRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<WorkOrderNote> addNote(WorkOrderNote note) {
        return CompletableFuture.supplyAsync(() -> {
            Long id = repository.insert(note);
            note.setId(id);
            return note;
        });
    }

    public CompletableFuture<Void> deleteNote(Long id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<List<WorkOrderNote>> getNotesForService(Long serviceId) {
        return CompletableFuture.supplyAsync(() -> repository.findByServiceId(serviceId));
    }
}