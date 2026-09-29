package tr.cabro.servicio.service;

import lombok.Getter;
import org.jdbi.v3.core.Jdbi;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.repository.*;

public final class ServiceManager {

    @Getter private static PartService partService;
    @Getter private static ProductService productService;
    @Getter private static WorkOrderService workOrderService;
    @Getter private static CustomerService customerService;
    @Getter private static SupplierService supplierService;
    @Getter private static DeviceService deviceService;
    @Getter private static UserService userService;
    @Getter private static BusinessService businessService;

    // --- YENİ EKLENEN YÖNETİCİLER ---
    @Getter private static DeviceDictionaryManager deviceDictionaryManager;
    @Getter private static PartCategoryManager partCategoryManager;
    @Getter private static DocumentTemplateService documentTemplateService;
    @Getter private static LaborService laborService;
    @Getter private static ReportManager reportManager;
    @Getter private static StockService stockService;
    @Getter private static WarehouseService warehouseService;
    @Getter private static DeviceAccessCredentialService deviceAccessCredentialService;
    @Getter private static AppSettingService appSettingService;
    @Getter private static DeviceTransactionService deviceTransactionService;
    @Getter private static ExchangeRateManager exchangeRateManager;
    @Getter private static PaymentService paymentService;
    @Getter private static SaleService saleService;

    public static void initialize() {
        Jdbi jdbi = DatabaseManager.getJdbi();

        // --- Temel Repository'ler ---
        CustomerRepository customerRepo = jdbi.onDemand(CustomerRepository.class);
        PartRepository partRepo = jdbi.onDemand(PartRepository.class);
        WorkOrderRepository serviceRepo = jdbi.onDemand(WorkOrderRepository.class);
        SupplierRepository supplierRepo = jdbi.onDemand(SupplierRepository.class);
        UserRepository userRepo = jdbi.onDemand(UserRepository.class);
        ServiceItemRepository itemRepo = jdbi.onDemand(ServiceItemRepository.class);
        DeviceRepository deviceRepo = jdbi.onDemand(DeviceRepository.class);

        // --- Yeni Kurumsal Repository'ler ---
        DeviceDictionaryRepository dictRepo = jdbi.onDemand(DeviceDictionaryRepository.class);
        PartCategoryRepository partCategoryRepo = jdbi.onDemand(PartCategoryRepository.class);
        DocumentTemplateRepository documentTemplateRepo = jdbi.onDemand(DocumentTemplateRepository.class);
        LaborRepository laborRepo = jdbi.onDemand(LaborRepository.class);
        ReportRepository reportRepo = jdbi.onDemand(ReportRepository.class);
        ServiceNoteRepository noteRepo = jdbi.onDemand(ServiceNoteRepository.class);
        StockLedgerRepository stockLedgerRepo = jdbi.onDemand(StockLedgerRepository.class);
        WarehouseRepository warehouseRepo = jdbi.onDemand(WarehouseRepository.class);
        ProductRepository productRepo = jdbi.onDemand(ProductRepository.class);
        DeviceAccessCredentialRepository deviceAccessCredentialRepo = jdbi.onDemand(DeviceAccessCredentialRepository.class);
        AppSettingRepository appSettingRepo = jdbi.onDemand(AppSettingRepository.class);
        DeviceTransactionRepository deviceTransactionRepo = jdbi.onDemand(DeviceTransactionRepository.class);
        ExchangeRateRepository exchangeRateRepo = jdbi.onDemand(ExchangeRateRepository.class);
        PaymentRepository paymentRepo = jdbi.onDemand(PaymentRepository.class);
        PaymentAllocationRepository paymentAllocationRepo = jdbi.onDemand(PaymentAllocationRepository.class);
        AccountRepository accountRepo = jdbi.onDemand(AccountRepository.class);

        // --- Servislerin Başlatılması ---
        customerService = new CustomerService(customerRepo);
        deviceService = new DeviceService(deviceRepo);
        supplierService = new SupplierService(supplierRepo);
        stockService = new StockService(stockLedgerRepo);
        warehouseService = new WarehouseService(warehouseRepo);
        userService = new UserService(userRepo);
        businessService = new BusinessService(jdbi.onDemand(BusinessRepository.class));

        // --- Yeni Servislerin Başlatılması ---
        deviceDictionaryManager = new DeviceDictionaryManager(dictRepo);
        partCategoryManager = new PartCategoryManager(partCategoryRepo);
        documentTemplateService = new DocumentTemplateService(documentTemplateRepo);
        laborService = new LaborService(laborRepo);
        reportManager = new ReportManager(reportRepo, new AnalyticsRepository(jdbi));
        partService = new PartService(partRepo, supplierRepo, stockService, partCategoryRepo);
        productService = new ProductService(productRepo, stockService, partCategoryRepo, supplierRepo);
        deviceAccessCredentialService = new DeviceAccessCredentialService(deviceAccessCredentialRepo);
        paymentService = new PaymentService(paymentRepo, paymentAllocationRepo, accountRepo);

        SaleRepository saleRepo = jdbi.onDemand(SaleRepository.class);
        SaleItemRepository saleItemRepo = jdbi.onDemand(SaleItemRepository.class);
        saleService = new SaleService(saleRepo, saleItemRepo, customerRepo, paymentService, stockService);

        workOrderService = new WorkOrderService(serviceRepo, itemRepo, paymentService, noteRepo, stockService, deviceService,
                customerRepo, deviceRepo, paymentRepo);

        appSettingService = new AppSettingService(appSettingRepo);
        deviceTransactionService = new DeviceTransactionService(deviceTransactionRepo);
        exchangeRateManager = new ExchangeRateManager(exchangeRateRepo);

        // Eski Device.password verisini yeni tabloya bir kerelik taşı (idempotent — bkz. servis içi yorum)
        deviceAccessCredentialService.migrateLegacyDevicePasswords(deviceRepo, serviceRepo);

        // config.json'da kalmış işletme ayarlarını app_settings tablosuna taşı (idempotent)
        appSettingService.consumePendingMigration();
    }
}