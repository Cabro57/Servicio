package tr.cabro.servicio.database;

import lombok.Getter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.enums.BackupMode;
import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Aralıklı otomatik yedekleme ("her N dakika/saat/gün/hafta/ay").
 * <p>
 * Açılış/kapanış modları burada değil, {@link Servicio} içinde çalışır; bu modlarda zamanlayıcı
 * başlamaz. Sonraki yedek son yedeğin tarihine göre hesaplanır: uygulama her gün kapatılıp açılsa
 * bile "günde bir" yedek alınır, gecikmiş yedek açılıştan kısa süre sonra alınır.
 */
public class BackupScheduler {

    /** Gecikmiş yedek, açılışı yavaşlatmasın diye bu kadar sonra alınır. */
    private static final Duration OVERDUE_DELAY = Duration.ofMinutes(1);

    private static ScheduledExecutorService scheduler;
    @Getter
    private static LocalDateTime nextBackupTime;

    public static synchronized void start() {
        stop(); // Varsa eskiyi durdur

        AppConfig.Backup backupSettings = AppSettings.get().getBackup();
        BackupMode mode = backupSettings.getMode();
        int interval = Math.max(1, backupSettings.getInterval());
        if (!isPeriodic(mode)) return;

        // Daemon thread kullanıyoruz (Uygulama kapanırken thread asılı kalmasın)
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Backup-Timer");
            t.setDaemon(true);
            return t;
        });

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime last = BackupManager.latestBackupTime();
        LocalDateTime next = last == null ? now : advance(last, mode, interval);
        LocalDateTime earliest = now.plus(OVERDUE_DELAY);
        schedule(next.isBefore(earliest) ? earliest : next, mode, interval);
        Servicio.getLogger().info("Yedekleme zamanlayıcısı başlatıldı. Mod: {}, sonraki: {}", mode, nextBackupTime);
    }

    public static synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        nextBackupTime = null;
    }

    public static void restart() {
        start();
    }

    private static synchronized void schedule(LocalDateTime next, BackupMode mode, int interval) {
        if (scheduler == null || scheduler.isShutdown()) return;
        nextBackupTime = next;
        long delay = Math.max(0, Duration.between(LocalDateTime.now(), next).toMillis());

        ScheduledExecutorService current = scheduler;
        current.schedule(() -> {
            Servicio.getLogger().info("Otomatik yedekleme çalışıyor...");
            BackupManager.createQuietly(BackupManager.Kind.AUTO);
            // Başarısız olsa da bir sonraki denemeyi planla; durdurulduysa (ör. geri yükleme) planlama.
            if (current == scheduler) schedule(advance(LocalDateTime.now(), mode, interval), mode, interval);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static boolean isPeriodic(BackupMode mode) {
        return mode == BackupMode.EVERY_N_MINUTES || mode == BackupMode.EVERY_N_HOURS
                || mode == BackupMode.EVERY_N_DAYS || mode == BackupMode.EVERY_N_WEEKS
                || mode == BackupMode.EVERY_N_MONTHS;
    }

    private static LocalDateTime advance(LocalDateTime from, BackupMode mode, int interval) {
        return switch (mode) {
            case EVERY_N_MINUTES -> from.plusMinutes(interval);
            case EVERY_N_HOURS -> from.plusHours(interval);
            case EVERY_N_DAYS -> from.plusDays(interval);
            case EVERY_N_WEEKS -> from.plusWeeks(interval);
            case EVERY_N_MONTHS -> from.plusMonths(interval);
            default -> throw new IllegalArgumentException("Aralıklı olmayan yedek modu: " + mode);
        };
    }
}
