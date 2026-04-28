package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.CustomerRepository;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.PhoneHelper;
import tr.cabro.servicio.util.Validator;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
                Long id = repository.insert(customer);
                customer.setId(id);
                return customer;
            } else {
                repository.update(customer);
                return customer;
            }
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> repository.delete(id));
    }

    public CompletableFuture<Optional<Customer>> get(Long id) {
        // Tekil müşteri çekerken de cihaz sayısını almak isteyebiliriz
        return CompletableFuture.supplyAsync(() -> repository.findById(id));
    }

    public CompletableFuture<List<Customer>> getAll() {
        return CompletableFuture.supplyAsync(repository::findAll); // Çekilen listeyi cihaz sayısıyla doldur
    }

//    public CompletableFuture<List<CustomersTableDto>> getAllTable() {
//        return CompletableFuture.supplyAsync(() -> {
//            List<Customer> customers = repository.findAll();
//            WorkOrderService workOrderService = ServiceManager.getWorkOrderService();
//            List<WorkOrder> allWorkOrders = workOrderService.getAll().join();
//
//            Map<Long, List<WorkOrder>> workOrdersByCustomer = allWorkOrders.stream()
//                    .filter(workOrder -> workOrder.getCustomerId() != null)
//                    .collect(Collectors.groupingBy(WorkOrder::getCustomerId));
//
//            CustomerMapper customerMapper = Mappers.getMapper(CustomerMapper.class);
//
//            return customers.stream()
//                    .map(customer -> {
//                        CustomersTableDto dto = customerMapper.toDto(customer);
//
//                        List<WorkOrder> customerWorkOrders = workOrdersByCustomer
//                                .getOrDefault(customer.getId(), Collections.emptyList());
//
//                        long deviceCount = customerWorkOrders.stream()
//                                .map(WorkOrder::getDeviceId)
//                                .filter(Objects::nonNull)
//                                .distinct()
//                                .count();
//
//                        dto.setDeviceCount((int) deviceCount);
//
//                        BigDecimal totalSpent = customerWorkOrders.stream()
//                                .filter(wo -> wo.getPayments() != null)
//                                .map(WorkOrder::getTotalPaid)
//                                .reduce(BigDecimal.ZERO, BigDecimal::add);
//
//                        dto.setSpent(totalSpent);
//
//                        return dto;
//                    })
//                    .collect(Collectors.toList());
//        });
//    }

    public CompletableFuture<List<Customer>> getAll(List<Long> customerIds) {
        if (customerIds== null || customerIds.isEmpty()) {
            throw new ValidationException("BOŞ");
        }
        return CompletableFuture.supplyAsync(() -> repository.findByIds(customerIds)); // Çekilen listeyi cihaz sayısıyla doldur
    }

    public CompletableFuture<List<Customer>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }
        return CompletableFuture.supplyAsync(() -> repository.search("%" + searchTerm.trim() + "%")); // Arama sonucunu cihaz sayısıyla doldur
    }

//    private CompletableFuture<List<Customer>> populateDeviceCounts(List<Customer> customers) {
//        if (customers.isEmpty()) {
//            return CompletableFuture.completedFuture(customers);
//        }
//
//        List<Long> customerIds = customers.stream()
//                .map(Customer::getId)
//                .collect(Collectors.toList());
//
//        WorkOrderService workOrderService = ServiceManager.getWorkOrderService();
//
//        // TODO: bunu workorder servisinden kaldırdık ve gerekli bir yerden çekmeliyiz.
//        return workOrderService.getAll(customerIds)
//                .thenApply(countsMap -> {
//                    for (Customer c : customers) {
//                        // Eğer müşterinin hiç servisi yoksa Map'ten null döner, bu yüzden getOrDefault(id, 0)
//                        c.setDeviceCount(countsMap.getOrDefault(c.getId(), 0));
//                    }
//                    return customers;
//                });
//    }

    private void validateCustomer(Customer customer) {
        // --- 1. Validasyon ve Normalizasyon ---

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