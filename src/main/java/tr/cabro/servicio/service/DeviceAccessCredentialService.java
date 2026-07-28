package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.repository.DeviceAccessCredentialRepository;
import tr.cabro.servicio.database.repository.DeviceRepository;
import tr.cabro.servicio.database.repository.WorkOrderRepository;
import tr.cabro.servicio.model.DeviceAccessCredential;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.enums.DeviceAccessType;
import tr.cabro.servicio.util.CryptoUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Cihaz ekran kilidi erişim bilgisinin (PIN/şifre/desen) şifrelenerek saklanması,
 * okunması ve teslimattan belirli süre sonra kalıcı olarak silinmesinden (purge) sorumlu servis.
 * <p>
 * Şifreleme/çözme burada yapılır — repository katmanı ham (şifreli) veriyle, UI/çağıran
 * kod ise her zaman düz metinle çalışır.
 */
public class DeviceAccessCredentialService {

    private final DeviceAccessCredentialRepository repository;

    public DeviceAccessCredentialService(DeviceAccessCredentialRepository repository) {
        this.repository = repository;
    }

    /**
     * Verilen servis kaydı için erişim bilgisini kaydeder (varsa günceller, yoksa oluşturur).
     * {@code accessType} {@link DeviceAccessType#NONE} veya {@code secret} boşsa hiçbir şey yazılmaz.
     */
    public CompletableFuture<Void> save(Long workOrderId, DeviceAccessType accessType, String secret) {
        if (workOrderId == null || accessType == null || accessType == DeviceAccessType.NONE
                || secret == null || secret.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            String encrypted = CryptoUtil.encrypt(secret.trim());
            Optional<DeviceAccessCredential> existing = repository.findActiveByWorkOrderId(workOrderId);

            DeviceAccessCredential credential = existing.orElseGet(DeviceAccessCredential::new);
            credential.setWorkOrderId(workOrderId);
            credential.setAccessType(accessType);
            credential.setSecret(encrypted);
            credential.setSetAt(LocalDateTime.now());

            if (existing.isPresent()) {
                repository.update(credential);
            } else {
                credential.setCreatedAt(LocalDateTime.now());
                repository.insert(credential);
            }
        });
    }

    /**
     * Servis kaydına ait aktif (henüz silinmemiş) erişim bilgisini döner; şifre çözülmüş halde.
     * Süre dolup silindiyse ({@code secret} null olur) yine döner ama {@code getSecret()} null olur —
     * çağıran taraf bunu "artık erişilemiyor" olarak yorumlamalı.
     */
    public CompletableFuture<Optional<DeviceAccessCredential>> getActive(Long workOrderId) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<DeviceAccessCredential> credential = repository.findActiveByWorkOrderId(workOrderId);
            credential.ifPresent(c -> {
                if (c.getSecret() != null) {
                    c.setSecret(CryptoUtil.decrypt(c.getSecret()));
                }
            });
            return credential;
        });
    }

    /**
     * Teslim edilmiş servis kayıtlarından, ayarlarda belirtilen süre (saat) geçmiş olanların
     * erişim kodunu kalıcı olarak siler (secret NULL'lanır — geri getirilemez).
     * Uygulama açılışında bir kez ve periyodik olarak çağrılır (bkz. DeviceAccessPurgeScheduler).
     */
    public void purgeExpired(int purgeHours) {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(purgeHours);
        List<DeviceAccessCredential> expired = repository.findExpiredSince(cutoff);
        if (expired.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        for (DeviceAccessCredential credential : expired) {
            repository.purge(credential.getId(), now);
        }
        Servicio.getLogger().info("Süresi dolan {} cihaz erişim kodu kalıcı olarak silindi.", expired.size());
    }

    /**
     * Tek seferlik veri taşıma: eski {@code devices.password} alanındaki düz metin şifreleri,
     * ilgili cihazın en güncel servis kaydına bu tabloya (şifrelenerek) taşır ve eski alanı temizler.
     * İdempotent'tir — {@code devices.password} taşındıktan sonra NULL'landığı için tekrar çalıştırılsa
     * aynı cihaz için ikinci kez taşıma yapmaz. Uygulama açılışında bir kez çağrılır.
     */
    public void migrateLegacyDevicePasswords(DeviceRepository deviceRepository, WorkOrderRepository workOrderRepository) {
        List<DeviceRepository.LegacyPassword> legacyPasswords = deviceRepository.findLegacyPasswords();
        if (legacyPasswords.isEmpty()) return;

        int migrated = 0;
        for (DeviceRepository.LegacyPassword legacy : legacyPasswords) {
            List<WorkOrder> workOrders = workOrderRepository.findByDeviceId(legacy.getId());
            if (workOrders.isEmpty()) {
                // Bu cihaza bağlı hiç servis kaydı yoksa taşınacak yer yok — eski alanı yine de temizle
                deviceRepository.clearPassword(legacy.getId());
                continue;
            }

            WorkOrder latest = workOrders.get(0); // findByDeviceId created_at DESC sıralı
            save(latest.getId(), DeviceAccessType.PASSWORD, legacy.getPassword()).join();
            deviceRepository.clearPassword(legacy.getId());
            migrated++;
        }
        Servicio.getLogger().info("{} cihazın eski şifresi yeni erişim tablosuna taşındı.", migrated);
    }
}
