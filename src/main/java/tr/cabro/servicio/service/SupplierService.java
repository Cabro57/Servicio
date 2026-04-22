package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.SupplierRepository;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Supplier> save(Supplier supplier, boolean update) {
        // --- Senkron Validasyon (Hata varsa UI'da anında fırlatılır) ---
        if (Validator.isEmpty(supplier.getName())) throw new ValidationException("Ad alanı boş olamaz.");
        if (Validator.isEmpty(supplier.getBusinessName())) throw new ValidationException("Firma adı boş olamaz.");

        try {
            if (!Validator.isEmpty(supplier.getPhone())) {
                supplier.setPhone(PhoneHelper.normalize("TR", supplier.getPhone()));
            }
        } catch (Exception e) {
            throw new ValidationException("Telefon numarası hatası: " + e.getMessage());
        }

        if (!Validator.isEmpty(supplier.getEmail()) && !Validator.isValidEmail(supplier.getEmail())) {
            throw new ValidationException("Geçersiz e-posta formatı.");
        }

        // --- Veritabanı İşlemi (Arka Plan Thread) ---
        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                int id = repository.insert(supplier);
                supplier.setId(id);
            } else {
                repository.update(supplier);
            }
            return supplier;
        });
    }

    public CompletableFuture<Void> delete(int id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<Supplier>> get(int id) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }

    public CompletableFuture<List<Supplier>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }
}