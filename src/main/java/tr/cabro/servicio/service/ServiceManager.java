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
    @Getter private static DeviceDictionaryManager deviceDictionaryManager;
    @Getter private static PartCategoryManager partCategoryManager;
    @Getter private static DocumentTemplateService documentTemplateService;
    @Getter private static LaborService laborService;
    @Getter private static ReportManager reportManager;
    @Getter private static StockService stockService;
    @Getter private static DeviceAccessCredentialService deviceAccessCredentialService;
    @Getter private static AppSettingService appSettingService;

    public static void initialize() {
        Jdbi jdbi = DatabaseManager.getJdbi();

        // --- Temel Repository'ler ---
        CustomerRepository customerRepo = jdbi.onDemand(CustomerRepository.class);
        PartRepository partRepo = jdbi.onDemand(PartRepository.class);
        WorkOrderRepository serviceRepo = jdbi.onDemand(WorkOrderRepository.class);
        SupplierRepository supplierRepo = jdbi.onDemand(SupplierRepository.class);
        UserRepository userRepo = jdbi.onDemand(UserRepository.class);
        ServiceItemRepository itemRepo = jdbi.onDemand(ServiceItemRepository.class);
        ServicePaymentRepository paymentRepo = jdbi.onDemand(ServicePaymentRepository.class);
        DeviceRepository deviceRepo = jdbi.onDemand(DeviceRepository.class);

        // --- Yeni Kurumsal Repository'ler ---
        DeviceDictionaryRepository dictRepo = jdbi.onDemand(DeviceDictionaryRepository.class);
        PartCategoryRepository partCategoryRepo = jdbi.onDemand(PartCategoryRepository.class);
        DocumentTemplateRepository documentTemplateRepo = jdbi.onDemand(DocumentTemplateRepository.class);
        LaborRepository laborRepo = jdbi.onDemand(LaborRepository.class);
        ReportRepository reportRepo = jdbi.onDemand(ReportRepository.class);
        ServiceNoteRepository noteRepo = jdbi.onDemand(ServiceNoteRepository.class);
        StockMovementRepository stockMovementRepo = jdbi.onDemand(StockMovementRepository.class);
        DeviceAccessCredentialRepository deviceAccessCredentialRepo = jdbi.onDemand(DeviceAccessCredentialRepository.class);
        AppSettingRepository appSettingRepo = jdbi.onDemand(AppSettingRepository.class);

        // --- Servislerin Başlatılması ---
        customerService = new CustomerService(customerRepo);
        deviceService = new DeviceService(deviceRepo);
        supplierService = new SupplierService(supplierRepo);
        stockService = new StockService(stockMovementRepo);
        userService = new UserService(userRepo);

        // --- Yeni Servislerin Başlatılması ---
        deviceDictionaryManager = new DeviceDictionaryManager(dictRepo);
        partCategoryManager = new PartCategoryManager(partCategoryRepo);
        documentTemplateService = new DocumentTemplateService(documentTemplateRepo);
        laborService = new LaborService(laborRepo);
        reportManager = new ReportManager(reportRepo);
        partService = new PartService(partRepo, supplierRepo, stockService, partCategoryRepo);
        deviceAccessCredentialService = new DeviceAccessCredentialService(deviceAccessCredentialRepo);

        workOrderService = new WorkOrderService(serviceRepo, itemRepo, paymentRepo, noteRepo, partService, stockService, deviceService);

        appSettingService = new AppSettingService(appSettingRepo);

        // Eski Device.password verisini yeni tabloya bir kerelik taşı (idempotent — bkz. servis içi yorum)
        deviceAccessCredentialService.migrateLegacyDevicePasswords(deviceRepo, serviceRepo);

        // config.json'da kalmış işletme ayarlarını app_settings tablosuna taşı (idempotent)
        appSettingService.consumePendingMigration();
    }
}