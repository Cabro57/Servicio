package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.util.List;
import java.util.Optional;

public class CustomerService {

    private final CustomerRepository repository;

    public CustomerService(CustomerRepository repository) {
        this.repository = repository;
    }

    public Customer save(Customer customer, boolean update) {
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

        // --- 2. Veritabanı İşlemi ---
        if (!update) {
            int id = repository.insert(customer);
            customer.setId(id);
            return customer;
        } else {
            repository.update(customer);
            return customer;
        }
    }

    public void delete(int id) {
        repository.delete(id);
    }

    public Optional<Customer> get(int id) {
        return repository.findById(id);
    }

    public List<Customer> getAll() {
        return repository.findAll();
    }

    public List<Customer> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        return repository.search("%" + searchTerm.trim() + "%");
    }
}