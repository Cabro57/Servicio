package tr.cabro.servicio.service;

import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.repository.DeviceRepository;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.service.exception.ValidationException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cihaz yönetimi için servis katmanı.
 * Cihazlar servis kaydından bağımsız bir varlık olarak yönetilir.
 */
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public CompletableFuture<Device> save(Device device, boolean update) {
        validateDevice(device);

        return CompletableFuture.supplyAsync(() -> {
            if (!update) {
                if (device.getCreatedAt() == null) {
                    device.setCreatedAt(LocalDateTime.now());
                }
                Long id = deviceRepository.insert(device);
                device.setId(id);
            } else {
                deviceRepository.update(device);
            }
            return device;
        });
    }

    public CompletableFuture<Void> delete(Long id) {
        return CompletableFuture.runAsync(() -> deviceRepository.delete(id));
    }

    public CompletableFuture<Optional<Device>> get(Long id) {
        return CompletableFuture.supplyAsync(() -> deviceRepository.findById(id));
    }

    public CompletableFuture<Optional<Device>> getBySerialNo(String serialNo) {
        return CompletableFuture.supplyAsync(() -> deviceRepository.findBySerialNo(serialNo));
    }

    public CompletableFuture<List<Device>> getAll() {
        return CompletableFuture.supplyAsync(deviceRepository::findAll);
    }

    public CompletableFuture<List<Device>> getAll(List<Long> ids) {
        return CompletableFuture.supplyAsync(() -> deviceRepository.findByIds(ids));
    }

    public CompletableFuture<List<Device>> getAllByCustomerId(Long customerId) {
        return CompletableFuture.supplyAsync(() -> deviceRepository.findByCustomerId(customerId));
    }

    public CompletableFuture<List<Device>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return CompletableFuture.supplyAsync(
                () -> deviceRepository.search("%" + searchTerm.trim() + "%"));
    }

    /** Tablo başlığı filtresi (kayıt tarihi) + serbest metin arama — sunucu tarafında, tüm kayıtlar üzerinde. */
    public CompletableFuture<PageResult<Device>> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                                     int page, int pageSize) {
        return CompletableFuture.supplyAsync(() -> deviceRepository.searchFilteredPaged(searchTerm, filters, page, pageSize));
    }

    // -------------------------------------------------------------------------
    // Doğrulama
    // -------------------------------------------------------------------------

    /**
     * Cihaz kaydı için zorunlu alanları doğrular.
     * <p>
     * Model alanı zorunlu değildir — bazı cihazlarda model bilgisi olmayabilir
     * (örn. jenerik adaptörler, aksesuarlar). Tür ve marka seçimi zorunludur
     * çünkü bunlar sözlük tabanlıdır ve raporlama/filtreleme için gereklidir.
     */
    private void validateDevice(Device device) {
        if (device == null) {
            throw new ValidationException("Cihaz bilgisi boş olamaz.");
        }
        if (device.getDeviceType() == null || device.getDeviceType().getId() == null) {
            throw new ValidationException("Cihaz türü seçilmek zorundadır.");
        }
        if (device.getBrand() == null || device.getBrand().getId() == null) {
            throw new ValidationException("Cihaz markası seçilmek zorundadır.");
        }
    }
}