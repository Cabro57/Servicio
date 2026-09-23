package tr.cabro.servicio.service;

import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;

    }

    public CompletableFuture<Customer> save(Customer customer, boolean update) {

        validateCustomer(customer);

        return CompletableFuture.supplyAsync(() -> {
            // --- 2. Benzersiz alan çakışmaları (TC / vergi no) ---
            Customer deletedMatch = resolveUniqueConflicts(customer, update);
            if (deletedMatch != null) {
                // Aynı TC/vergi no ile silinmiş müşteri var: yeni kayıt açmak yerine geri getirilir,
                // böylece eski cihaz/servis geçmişi de kişiye bağlı kalır.
                customer.setId(deletedMatch.getId());
                customerRepository.restore(customer);
                return customer;
            }

            // --- 3. Veritabanı İşlemi ---
            if (!update) {
                Long id = customerRepository.insert(customer);
                customer.setId(id);
                return customer;
            } else {
                customerRepository.update(customer);
                return customer;
            }
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> customerRepository.delete(id));
    }

    public CompletableFuture<Optional<Customer>> get(Long id) {
        // Tekil müşteri çekerken de cihaz sayısını almak isteyebiliriz
        return CompletableFuture.supplyAsync(() -> customerRepository.findById(id));
    }

    public CompletableFuture<List<Customer>> getAll() {
        return CompletableFuture.supplyAsync(customerRepository::findAll);
    }

    public CompletableFuture<List<Customer>> getAllTable() {
        return CompletableFuture.supplyAsync(customerRepository::findAllTable);
    }

    public CompletableFuture<List<Customer>> getAll(List<Long> customerIds) {
        if (customerIds== null || customerIds.isEmpty()) {
            throw new ValidationException("BOŞ");
        }
        return CompletableFuture.supplyAsync(() -> customerRepository.findByIds(customerIds)); // Çekilen listeyi cihaz sayısıyla doldur
    }

    /**
     * Normalize edilmiş (E.164) numaranın başka bir müşteride kayıtlı olup olmadığını arar.
     *
     * @param excludeId düzenlenen müşterinin id'si; yeni kayıtta {@code null}
     */
    public CompletableFuture<Optional<Customer>> findOtherByPhone(String normalizedPhone, Long excludeId) {
        if (Validator.isEmpty(normalizedPhone)) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        long exclude = excludeId != null ? excludeId : -1L;
        return CompletableFuture.supplyAsync(() -> customerRepository.findOtherByPhone(normalizedPhone, exclude));
    }

    public CompletableFuture<List<Customer>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        return CompletableFuture.supplyAsync(() -> customerRepository.search("%" + searchTerm.trim() + "%")); // Arama sonucunu cihaz sayısıyla doldur
    }

    /** Tablo başlığı filtresi (tip/kayıt tarihi vb.) + serbest metin arama — sunucu tarafında, tüm kayıtlar üzerinde. */
    public CompletableFuture<PageResult<Customer>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                       int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> customerRepository.searchFilteredPaged(searchTerm, filters, page, pageSize));
    }

    /**
     * TC kimlik / vergi no çakışmalarını çözer. Aktif bir müşteride kullanılıyorsa kullanıcıya
     * anlaşılır bir hata verir. Silinmiş bir müşteride kullanılıyorsa: yeni kayıtta ve tek eşleşmede o
     * müşteri döndürülür (geri getirilecek), diğer durumlarda silinmiş kaydın alanları boşaltılır.
     *
     * @return geri getirilecek silinmiş müşteri; yoksa {@code null}
     */
    private Customer resolveUniqueConflicts(Customer customer, boolean update) {
        List<Customer> matches = customerRepository.findByUniqueKeys(customer.getIdentityNo(), customer.getTaxNumber());
        List<Customer> deleted = new ArrayList<>();
        for (Customer match : matches) {
            if (update && Objects.equals(match.getId(), customer.getId())) continue;
            if (!match.isDeleted()) {
                String field = Objects.equals(match.getIdentityNo(), customer.getIdentityNo())
                        ? "TC kimlik numarası" : "Vergi numarası";
                throw new ValidationException(field + " başka bir müşteride kayıtlı: " + displayName(match));
            }
            deleted.add(match);
        }
        if (!update && deleted.size() == 1) {
            return deleted.get(0);
        }
        for (Customer match : deleted) {
            customerRepository.releaseUniqueKeys(match.getId(), customer.getIdentityNo(), customer.getTaxNumber());
        }
        return null;
    }

    private static String displayName(Customer c) {
        String name = (Objects.toString(c.getFirstName(), "") + " " + Objects.toString(c.getLastName(), "")).trim();
        return Validator.isEmpty(c.getBusinessName()) ? name : c.getBusinessName() + " (" + name + ")";
    }

    private static String blankToNull(String value) {
        return Validator.isEmpty(value) ? null : value.trim();
    }

    private void validateCustomer(Customer customer) {
        // --- 1. Validasyon ve Normalizasyon ---

        // UNIQUE kolonlar: boş metin NULL'a çevrilir, aksi halde iki boş değer birbiriyle çakışır.
        customer.setIdentityNo(blankToNull(customer.getIdentityNo()));
        customer.setTaxNumber(blankToNull(customer.getTaxNumber()));

        // Zorunlu Alanlar
        if (Validator.isEmpty(customer.getFirstName())) {
            throw new ValidationException("Müşteri adı boş bırakılamaz.");
        }
        if (Validator.isEmpty(customer.getLastName())) {
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
        String idNo = customer.getIdentityNo();
        if (!Validator.isEmpty(idNo) && (!Validator.isNumeric(idNo) || !Validator.hasLength(idNo, 11))) {
            throw new ValidationException("TC Kimlik numarası 11 haneli ve rakamlardan oluşmalıdır.");
        }
    }
}