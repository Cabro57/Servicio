package tr.cabro.servicio.database;

import lombok.Getter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.BackupMode;
import tr.cabro.servicio.settings.Settings;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BackupScheduler {

    private static ScheduledExecutorService scheduler;
    @Getter
    private static LocalDateTime nextBackupTime;

    public static void start() {
        stop(); // Varsa eskiyi durdur

        Settings.BackupSettings settings = Servicio.getSettings().getBackup();
        BackupMode mode = settings.getMode();
        int interval = settings.getInterval();

        // Mod "KAPALI" veya "MANUEL" ise zamanlayıcıyı başlatma
        if (mode == BackupMode.NONE) {
            return;
        }

        // Daemon thread kullanıyoruz (Uygulama kapanırken thread asılı kalmasın)
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Backup-Timer");
            t.setDaemon(true);
            return t;
        });

        scheduleNext(mode, interval);
        Servicio.getLogger().info("Yedekleme zamanlayıcısı başlatıldı. Mod: {}", mode);
    }

    public static void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        nextBackupTime = null;
    }

    public static void restart() {
        start();
    }

    private static void scheduleNext(BackupMode mode, int interval) {
        if (scheduler == null || scheduler.isShutdown()) return;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime next = calculateNextTime(now, mode, interval);
        nextBackupTime = next;

        long delay = Duration.between(now, next).toMillis();
        if (delay < 0) delay = 0; // Zaman senkronizasyon hatası olursa hemen çalıştır

        scheduler.schedule(() -> {
            try {
                Servicio.getLogger().info("Otomatik yedekleme çalışıyor...");
                DatabaseManager.backup();
            } catch (Exception e) {
                Servicio.getLogger().error("Otomatik yedekleme hatası: {}", e.getMessage());
            } finally {
                // Görev bitince bir sonrakini planla (Recursive döngü)
                scheduleNext(mode, interval);
            }
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static LocalDateTime calculateNextTime(LocalDateTime now, BackupMode mode, int interval) {
        LocalDateTime base = now.withSecond(0).withNano(0);

        switch (mode) {
            case EVERY_N_MINUTES:
                // Modulo mantığı ile tam dakikayı bulur (Örn: 10 dk ise 12:00, 12:10, 12:20...)
                return base.plusMinutes(interval - (base.getMinute() % interval));
            case EVERY_N_HOURS:
                return base.withMinute(0).plusHours(interval - (base.getHour() % interval));
            case EVERY_N_DAYS:
                return base.withHour(0).withMinute(0).plusDays(interval);
            case EVERY_N_WEEKS:
                return base.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).plusWeeks(interval);
            case EVERY_N_MONTHS:
                return base.withDayOfMonth(1).withHour(0).withMinute(0).plusMonths(interval);
            default:
                return now.plusMinutes(interval); // Fallback
        }
    }
}