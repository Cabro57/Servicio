package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.DeviceRepository;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Validator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cihaz yönetimi için servis katmanı.
 * Cihazlar artık servis kaydından ayrı bir varlık olarak yönetilir (V7 kurumsal yapısı).
 */
public class DeviceService {

    private final DeviceRepository deviceRepository;

    public DeviceService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    public CompletableFuture<Device> save(Device device, boolean update) {
        if (Validator.isEmpty(device.getDeviceType())) throw new ValidationException("Cihaz türü zorunludur.");
        if (Validator.isEmpty(device.getBrand())) throw new ValidationException("Cihaz markası zorunludur.");
        if (Validator.isEmpty(device.getModel())) throw new ValidationException("Cihaz modeli zorunludur.");

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

    public CompletableFuture<List<Device>> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) return getAll();
        return CompletableFuture.supplyAsync(
                () -> deviceRepository.search("%" + searchTerm.trim() + "%"));
    }
}
