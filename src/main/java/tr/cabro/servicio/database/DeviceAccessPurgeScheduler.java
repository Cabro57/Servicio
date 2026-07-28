package tr.cabro.servicio.database;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.service.ServiceManager;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Teslim edilmiş servis kayıtlarının cihaz erişim kodunu (PIN/şifre/desen), ayarlarda
 * belirtilen süre dolunca kalıcı olarak silmek için periyodik olarak
 * {@link tr.cabro.servicio.service.DeviceAccessCredentialService#purgeExpired(int)} çağırır.
 * <p>
 * {@link BackupScheduler} ile aynı desen: tek daemon thread, açılışta bir kez ve sonrasında
 * sabit aralıklarla çalışır (saatlik kontrol yeterli — süre saat cinsinden tutuluyor).
 */
public class DeviceAccessPurgeScheduler {

    private static final long CHECK_INTERVAL_MINUTES = 60;

    private static ScheduledExecutorService scheduler;

    public static void start() {
        stop();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DeviceAccessPurge-Timer");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(DeviceAccessPurgeScheduler::runPurge, 0, CHECK_INTERVAL_MINUTES, TimeUnit.MINUTES);
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private static void runPurge() {
        try {
            int purgeHours = Servicio.getSettings().getDeviceAccessPurgeHours();
            ServiceManager.getDeviceAccessCredentialService().purgeExpired(purgeHours);
        } catch (Exception e) {
            Servicio.getLogger().error("Cihaz erişim kodu temizleme hatası: {}", e.getMessage());
        }
    }
}
