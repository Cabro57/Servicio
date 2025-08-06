package tr.cabro.servicio.importer;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.importer.mapper.*;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.model.*;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.SupplierService;
import tr.cabro.servicio.util.barcode.BarcodeConfig;
import tr.cabro.servicio.util.barcode.BarcodeGenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ImportManager {

    private final CustomerService customerService;
    private final PartService partService;
    private final RepairService serviceService;
    private final SupplierService supplierService;

    private final Map<Integer, Integer> customerIdMap = new HashMap<>();
    private final Map<Integer, Integer> serviceIdMap = new HashMap<>();
    private final Map<Integer, String> partIdMap = new HashMap<>();
    private final Map<String, Integer> supplierMap = new HashMap<>();

    private final StringBuilder log = new StringBuilder();
    private final Consumer<String> logCallback;

    public ImportManager(Consumer<String> logCallback) {
        this.customerService = ServiceManager.getCustomerService();
        this.partService = ServiceManager.getPartService();
        this.serviceService = ServiceManager.getRepairService();
        this.supplierService = ServiceManager.getSupplierService();
        this.logCallback = logCallback;
    }

    private void logLine(String line) {
        log.append(line).append("\n");
        if (logCallback != null) logCallback.accept(line);
    }

    public String getLog() {
        return log.toString();
    }

    public void importAll(String folderPath) {
        try {
            logLine("=== Veri İçe Aktarma Başladı ===");

            // 0. Device Type-Brand
            logLine("[INFO] Device Name içe aktarılıyor...");
            CsvImporter<String> defaultImporter = new CsvImporter<>();
            CsvImportResult<String> deviceTypeResult = defaultImporter.importFromCsv(folderPath + "/device_name.csv", new DefaultCsvMapper());
            for (String s : deviceTypeResult.getData()) {
                Servicio.getSettings().addDeviceType(s);
                logLine("[DeviceType] Eklendi: " + s);
            }
            logLine("[INFO] Device Name toplam: " + deviceTypeResult.getData().size());

            logLine("[INFO] Device Brand içe aktarılıyor...");
            CsvImportResult<String> deviceBrandResult = defaultImporter.importFromCsv(folderPath + "/device_brand.csv", new DefaultCsvMapper());
            for (String s : deviceBrandResult.getData()) {
                Servicio.getSettings().addBrand("Telefon", s);
                logLine("[DeviceBrand] Eklendi: " + s);
            }
            logLine("[INFO] Device Brand toplam: " + deviceBrandResult.getData().size());

            // 1. Customer
            logLine("[INFO] Müşteri verileri içe aktarılıyor...");
            CsvImporter<Customer> customerImporter = new CsvImporter<>();
            CsvImportResult<Customer> customerResult = customerImporter.importFromCsv(folderPath + "/customers.csv", new CustomerCsvMapper());
            int customerAdded = 0;
            for (Customer c : customerResult.getData()) {
                int oldId = c.getID();
                boolean success = customerService.save(c, false);
                if (success) {
                    customerIdMap.put(oldId, c.getID());
                    logLine("[Müşteri] Eklendi: " + c.getName() + " (ID: " + oldId + " -> " + c.getID() + ")");
                    customerAdded++;
                } else {
                    logLine("[Müşteri] Kayıt başarısız: " + c.getName());
                }
            }
            logLine("Müşteri: " + customerAdded + " / " + customerResult.getData().size() + " kayıt eklendi.");
            customerResult.getErrors().forEach(err -> logLine("[Müşteri][HATA] " + err));

            // 2. Supplier
            logLine("[INFO] Tedarikçi verileri içe aktarılıyor...");
            CsvImporter<Supplier> supplierImporter = new CsvImporter<>();
            CsvImportResult<Supplier> supplierResult = supplierImporter.importFromCsv(folderPath + "/suppliers.csv", new SupplierCsvMapper());
            int supplierAdded = 0;
            for (Supplier s : supplierResult.getData()) {
                boolean success = supplierService.save(s, false);
                if (success) {
                    supplierMap.put(s.getBusiness_name(), s.getId());
                    logLine("[Tedarikçi] Eklendi: " + s.getBusiness_name() + " (ID: " + s.getId() + ")");
                    supplierAdded++;
                } else {
                    logLine("[Tedarikçi] Kayıt başarısız: " + s.getBusiness_name());
                }
            }
            logLine("Tedarikçi: " + supplierAdded + " / " + supplierResult.getData().size() + " kayıt eklendi.");
            supplierResult.getErrors().forEach(err -> logLine("[Tedarikçi][HATA] " + err));

            // 3. Part
            logLine("[INFO] Parça verileri içe aktarılıyor...");
            BarcodeConfig barcodeConfig = new BarcodeConfig();
            barcodeConfig.setPrefix("SILA");
            barcodeConfig.setSeparator("-");

            BarcodeGenerator barcodeGenerator = new BarcodeGenerator(barcodeConfig);

            CsvImporter<Part> partImporter = new CsvImporter<>();
            CsvImportResult<Part> partResult = partImporter.importFromCsv(folderPath + "/device_parts.csv", new PartCsvMapper(barcodeGenerator));
            int partAdded = 0, partStockUpdated = 0, partBarcodeChanged = 0;
            for (Part p : partResult.getData()) {
                Integer newSupplierId = supplierMap.get(p.getSupplier_name());
                if (newSupplierId == null) {
                    logLine("[Parça][UYARI] Tedarikçi bulunamadı: " + p.getSupplier_id());
                    p.setSupplier_id(0);
                } else {
                    p.setSupplier_id(newSupplierId);
                }

                Part existing = partService.getPartByBarcode(p.getBarcode());
                if (existing != null) {
                    if (existing.getSupplier_id() == p.getSupplier_id()
                            && existing.getDevice_type().equalsIgnoreCase(p.getDevice_type())
                            && existing.getModels().equalsIgnoreCase(p.getModels())) {

                        existing.setStock(existing.getStock() + p.getStock());
                        partService.savePart(existing, true);
                        logLine("[Parça] Mevcut barkod bulundu, stok arttırıldı: " + p.getBarcode() + " -> Yeni stok: " + existing.getStock());
                        partIdMap.put(p.getOldId(), existing.getBarcode());
                        partStockUpdated++;
                        continue;

                    } else {
                        String newBarcode = barcodeGenerator.generate();
                        logLine("[Parça] Çakışan barkod için yeni barkod atandı: " + p.getBarcode() + " -> " + newBarcode);
                        p.setBarcode(newBarcode);
                        partBarcodeChanged++;
                    }
                }

                partService.savePart(p, false);
                partIdMap.put(p.getOldId(), p.getBarcode());
                partAdded++;
            }
            logLine("Parça: " + partAdded + " eklendi, " + partStockUpdated + " stok arttırıldı, " + partBarcodeChanged + " barkod değiştirildi.");
            partResult.getErrors().forEach(err -> logLine("[Parça][HATA] " + err));

            // 4. Service
            logLine("[INFO] Servis verileri içe aktarılıyor...");
            CsvImporter<Service> serviceImporter = new CsvImporter<>();
            CsvImportResult<Service> serviceResult = serviceImporter.importFromCsv(folderPath + "/service_records.csv", new ServiceCsvMapper());
            int serviceAdded = 0;
            for (Service s : serviceResult.getData()) {
                Integer newCustomerId = customerIdMap.get(s.getCustomer_id());
                if (newCustomerId == null) {
                    logLine("[Servis][HATA] Müşteri bulunamadı: " + s.getCustomer_id());
                    continue;
                }
                s.setCustomer_id(newCustomerId);
                int oldServiceId = s.getId();
                boolean success = serviceService.saveService(s, false);
                if (success) {
                    serviceIdMap.put(oldServiceId, s.getId());
                    logLine("[Servis] Eklendi: ID " + oldServiceId + " -> " + s.getId());
                    serviceAdded++;
                }
            }
            logLine("Servis: " + serviceAdded + " / " + serviceResult.getData().size() + " kayıt eklendi.");
            serviceResult.getErrors().forEach(err -> logLine("[Servis][HATA] " + err));

            // 5. AddedParts
            logLine("[INFO] Eklenen parçalar içe aktarılıyor...");
            CsvImporter<AddedPart> addedPartImporter = new CsvImporter<>();
            CsvImportResult<AddedPart> addedPartsResult = addedPartImporter.importFromCsv(folderPath + "/added_parts.csv", new AddedPartCsvMapper());
            int addedPartsCount = 0;
            for (AddedPart ap : addedPartsResult.getData()) {
                Integer newServiceId = serviceIdMap.get(ap.getServiceId());
                String newBarcode = partIdMap.get(Integer.parseInt(ap.getBarcode()));
                if (newServiceId == null || newBarcode == null) {
                    logLine("[Eklenen Parça][HATA] Servis veya Parça bulunamadı: " + ap.getServiceId() + " / " + ap.getBarcode());
                    continue;
                }
                ap.setServiceId(newServiceId);
                ap.setBarcode(newBarcode);
                partService.addPartToService(ap);
                logLine("[Eklenen Parça] Servise eklendi: ServisID " + newServiceId + ", Barkod: " + newBarcode);
                addedPartsCount++;
            }
            logLine("Eklenen Parçalar: " + addedPartsCount + " / " + addedPartsResult.getData().size() + " kayıt eklendi.");
            addedPartsResult.getErrors().forEach(err -> logLine("[Eklenen Parça][HATA] " + err));

            logLine("=== İçe Aktarma Tamamlandı ===");

        } catch (Exception ex) {
            logLine("HATA: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
