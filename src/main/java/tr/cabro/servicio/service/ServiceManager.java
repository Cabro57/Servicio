package tr.cabro.servicio.service;

import lombok.Getter;
import org.jdbi.v3.core.Jdbi;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.*;

public final class ServiceManager {

    @Getter private static PartService partService;
    @Getter private static WorkOrderService workOrderService;
    @Getter private static CustomerService customerService;
    @Getter private static SupplierService supplierService;
    @Getter private static DeviceService deviceService;
    @Getter private static UserService userService;

    // --- YENİ EKLENEN YÖNETİCİLER ---
    @Getter private static ServiceItemManager serviceItemManager;
    @Getter private static ServicePaymentManager servicePaymentManager;
    @Getter private static ServiceNoteManager serviceNoteManager;
    @Getter private static DeviceDictionaryManager deviceDictionaryManager;
    @Getter private static LaborService laborService;
    @Getter private static ReportManager reportManager;

    public static void initialize() {
        Jdbi jdbi = DatabaseManager.getJdbi();

        // --- Temel Repository'ler ---
        CustomerRepository customerRepo     = jdbi.onDemand(CustomerRepository.class);
        PartRepository partRepo             = jdbi.onDemand(PartRepository.class);
        ServiceRepository serviceRepo       = jdbi.onDemand(ServiceRepository.class);
        SupplierRepository supplierRepo     = jdbi.onDemand(SupplierRepository.class);
        UserRepository userRepo             = jdbi.onDemand(UserRepository.class);
        ServiceItemRepository itemRepo      = jdbi.onDemand(ServiceItemRepository.class);
        ServicePaymentRepository paymentRepo= jdbi.onDemand(ServicePaymentRepository.class);
        DeviceRepository deviceRepo         = jdbi.onDemand(DeviceRepository.class);

        // --- Yeni Kurumsal Repository'ler ---
        DeviceDictionaryRepository dictRepo = jdbi.onDemand(DeviceDictionaryRepository.class);
        LaborRepository laborRepo           = jdbi.onDemand(LaborRepository.class);
        ReportRepository reportRepo         = jdbi.onDemand(ReportRepository.class);
        ServiceNoteRepository noteRepo      = jdbi.onDemand(ServiceNoteRepository.class);

        // --- Servislerin Başlatılması ---
        partService       = new PartService(partRepo, supplierRepo);
        deviceService     = new DeviceService(deviceRepo);
        workOrderService = new WorkOrderService(serviceRepo, customerRepo, deviceRepo, itemRepo, paymentRepo);
        customerService   = new CustomerService(customerRepo, workOrderService);
        supplierService   = new SupplierService(supplierRepo);
        userService       = new UserService(userRepo);
        serviceItemManager= new ServiceItemManager(itemRepo, partRepo);

        // --- Yeni Servislerin Başlatılması ---
        deviceDictionaryManager = new DeviceDictionaryManager(dictRepo);
        laborService = new LaborService(laborRepo);
        reportManager           = new ReportManager(reportRepo);
        serviceNoteManager      = new ServiceNoteManager(noteRepo);
        servicePaymentManager = new ServicePaymentManager(paymentRepo);
    }
}