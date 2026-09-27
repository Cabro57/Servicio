package tr.cabro.servicio.service;

import tr.cabro.servicio.model.enums.SupplierRole;

import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.repository.SupplierRepository;
import tr.cabro.servicio.model.Supplier;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.util.List;
import java.util.Map;
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
        if (supplier.getRole() == null) supplier.setRole(SupplierRole.SUPPLIER);

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
        return DbExecutor.supply(() -> {
            if (!update) {
                Long id = repository.insert(supplier);
                supplier.setId(id);
            } else {
                repository.update(supplier);
            }
            return supplier;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return DbExecutor.run(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<Supplier>> get(Long id) {
        return DbExecutor.supply(() -> repository.findById(id));
    }

    public CompletableFuture<List<Supplier>> getAll() {
        return DbExecutor.supply(repository::findAll);
    }

    /** Parça formundaki "Tedarikçi" listesi: tedarikçi ve iki rollü firmalar. */
    public CompletableFuture<List<Supplier>> getPartSuppliers() {
        return getAll().thenApply(list -> list.stream().filter(s -> role(s).suppliesParts()).toList());
    }

    /** Ürün formundaki "Toptancı" listesi: toptancı ve iki rollü firmalar. */
    public CompletableFuture<List<Supplier>> getWholesalers() {
        return getAll().thenApply(list -> list.stream().filter(s -> role(s).suppliesProducts()).toList());
    }

    private static SupplierRole role(Supplier s) {
        return s.getRole() != null ? s.getRole() : SupplierRole.SUPPLIER;
    }

    /** Tablo başlığı filtresi (kayıt tarihi) + serbest metin arama — sunucu tarafında, tüm kayıtlar üzerinde. */
    public CompletableFuture<PageResult<Supplier>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                       int page, int pageSize) {
        return DbExecutor.supply(() -> repository.searchFilteredPaged(searchTerm, filters, page, pageSize));
    }

    public CompletableFuture<PageResult<Supplier>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                       int page, int pageSize, String sortKey) {
        return DbExecutor.supply(() -> repository.searchFilteredPaged(searchTerm, filters, page, pageSize, sortKey));
    }
}