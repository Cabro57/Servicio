package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.ServicePaymentRepository;
import tr.cabro.servicio.model.ServicePayment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServicePaymentManager {

    private final ServicePaymentRepository repository;

    public ServicePaymentManager(ServicePaymentRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Integer> addPayment(ServicePayment payment) {
        return CompletableFuture.supplyAsync(() -> repository.insertPayment(payment));
    }

    public CompletableFuture<Void> deletePayment(int id) {
        return CompletableFuture.runAsync(() -> repository.deletePayment(id));
    }

    public CompletableFuture<List<ServicePayment>> getPaymentsByServiceId(int serviceId) {
        return CompletableFuture.supplyAsync(() -> repository.findPaymentsByServiceId(serviceId));
    }
}