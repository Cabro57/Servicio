package tr.cabro.servicio.service;

import lombok.Getter;
import org.jdbi.v3.core.Jdbi;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.*; // Önceki cevapta oluşturduğumuz Interface'ler

public final class ServiceManager {

    @Getter private static PartService partService;
    @Getter private static RepairService repairService;
    @Getter private static CustomerService customerService;
    @Getter private static SupplierService supplierService;
    @Getter private static UserService userService;

    public static void initialize() {
        // 1. JDBI Nesnesini Al
        Jdbi jdbi = DatabaseManager.getJdbi();

        // 2. Repository'leri Oluştur (JDBI Interface Proxy'leri)
        CustomerRepository customerRepo = jdbi.onDemand(CustomerRepository.class);
        PartRepository partRepo = jdbi.onDemand(PartRepository.class);
        ServiceRepository repairRepo = jdbi.onDemand(ServiceRepository.class);
        SupplierRepository supplierRepo = jdbi.onDemand(SupplierRepository.class);
        UserRepository userRepo = jdbi.onDemand(UserRepository.class);

        // 3. Servislere Enjekte Et (Constructor Injection)
        // Artık Servisler veritabanı bağlantısını kendileri oluşturmuyor,
        // dışarıdan "hazır repository" alıyorlar.
        customerService = new CustomerService(customerRepo);
        partService = new PartService(partRepo);
        repairService = new RepairService(repairRepo);
        supplierService = new SupplierService(supplierRepo);
        userService = new UserService(userRepo);
    }
}