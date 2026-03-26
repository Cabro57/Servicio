package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.SupplierRepository;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.util.List;
import java.util.Optional;

public class SupplierService {

    private final SupplierRepository repository;

    public SupplierService(SupplierRepository repository) {
        this.repository = repository;
    }

    public Supplier save(Supplier supplier, boolean update) {
        // --- Validasyon ---
        if (Validator.isEmpty(supplier.getName())) throw new ValidationException("Ad alanı boş olamaz.");
        if (Validator.isEmpty(supplier.getBusinessName())) throw new ValidationException("Firma adı boş olamaz.");

        // Telefon Normalizasyonu
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

        // --- DB İşlemi ---
        if (!update) {
            int id = repository.insert(supplier);
            supplier.setId(id);
            return supplier;
        } else {
            repository.update(supplier);
            return supplier;
        }
    }

    public void delete(int id) {
        repository.delete(id);
    }

    public Optional<Supplier> get(int id) {
        return repository.findById(id);
    }

    public List<Supplier> getAll() {
        return repository.findAll();
    }
}