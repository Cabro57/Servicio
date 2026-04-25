package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.ServicePaymentRepository;
import tr.cabro.servicio.model.WorkOrderPayment;

import java.util.List;
import java.util.concurrent.CompletableFuture;
// TODO bu servis sınıfı WorkOrder Servisine taşınacak
@Deprecated
public class ServicePaymentManager {

    private final ServicePaymentRepository repository;

    public ServicePaymentManager(ServicePaymentRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Long> addPayment(WorkOrderPayment payment) {
        return CompletableFuture.supplyAsync(() -> repository.insertPayment(payment));
    }

    public CompletableFuture<Void> deletePayment(Long id) {
        return CompletableFuture.runAsync(() -> repository.deletePayment(id));
    }

    public CompletableFuture<List<WorkOrderPayment>> getPaymentsByServiceId(Long serviceId) {
        return CompletableFuture.supplyAsync(() -> repository.findPaymentsByServiceId(serviceId));
    }
}