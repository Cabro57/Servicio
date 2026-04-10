package tr.cabro.servicio.service;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<Customer> save(Customer customer, boolean update) {

        validateCustomer(customer);

        return CompletableFuture.supplyAsync(() -> {
            // --- 2. Veritabanı İşlemi ---
            if (!update) {
                int id = repository.insert(customer);
                customer.setId(id);
                return customer;
            } else {
                repository.update(customer);
                return customer;
            }
        });
    }

    public CompletableFuture<Void> delete(int id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Void> deleteMultiple(List<Integer> ids) {
        return CompletableFuture.runAsync(() -> repository.deleteByIds(ids));
    }

    public CompletableFuture<Optional<Customer>> get(int id) {
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }

    public CompletableFuture<List<Customer>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll);
    }

    public CompletableFuture<List<Customer>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        return CompletableFuture.supplyAsync(() -> repository.search("%" + searchTerm.trim() + "%"));
    }

    private void validateCustomer(Customer customer) {
        // --- 1. Validasyon ve Normalizasyon ---

        // Zorunlu Alanlar
        if (Validator.isEmpty(customer.getName())) {
            throw new ValidationException("Müşteri adı boş bırakılamaz.");
        }
        if (Validator.isEmpty(customer.getSurname())) {
            throw new ValidationException("Müşteri soyadı boş bırakılamaz.");
        }

        // Telefon Validasyonu ve Normalizasyonu (Global Format)
        try {
            // Eğer numara UI'dan (PhoneField) geliyorsa zaten E.164 formatındadır (+90532...).
            // Ancak dışarıdan veya ham veri gelme ihtimaline karşı tekrar normalize ediyoruz.
            // Varsayılan bölge olarak "TR" veriyoruz, ancak numara "+" ile başlıyorsa
            // libphonenumber zaten ülke kodunu otomatik algılar.

            String phone1 = customer.getPhoneNumber1();
            if (Validator.isEmpty(phone1)) {
                throw new ValidationException("Telefon numarası zorunludur.");
            }
            customer.setPhoneNumber1(PhoneHelper.normalize("TR", phone1));

            String phone2 = customer.getPhoneNumber2();
            if (!Validator.isEmpty(phone2)) {
                customer.setPhoneNumber2(PhoneHelper.normalize("TR", phone2));
            }

        } catch (Exception e) {
            // PhoneHelper'dan gelen ValidationException veya parse hatalarını yakala
            throw new ValidationException("Telefon numarası hatası: " + e.getMessage());
        }

        // Email Kontrolü
        if (!Validator.isEmpty(customer.getEmail()) && !Validator.isValidEmail(customer.getEmail())) {
            throw new ValidationException("Geçersiz e-posta formatı.");
        }

        // TC Kimlik Kontrolü
        String idNo = customer.getIdNo();
        if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
            throw new ValidationException("TC Kimlik numarası 11 haneli ve rakamlardan oluşmalıdır.");
        }
    }
}