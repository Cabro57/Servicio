package tr.cabro.servicio.database;

import lombok.Getter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.BackupMode;
import tr.cabro.servicio.settings.Settings;

import java.time.*;
import java.util.concurrent.*;

public class BackupScheduler {

    private static ScheduledExecutorService scheduler;
    @Getter
    private static LocalDateTime nextBackupTime; // Sonraki planlanan zaman

    public static void start() {
        stop();
        scheduler = Executors.newSingleThreadScheduledExecutor();

        Settings.BackupSettings backupSettings = Servicio.getSettings().getBackup();
        BackupMode mode = backupSettings.getMode();

        if (isIntervalMode(mode)) {
            scheduleNext(mode, backupSettings.getInterval());
            Servicio.getLogger().info("BackupScheduler: {} modunda başlatıldı.", mode);
        }
    }

    public static void stop() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        nextBackupTime = null;
    }

    public static void restart() {
        Servicio.getLogger().info("BackupScheduler: ayar değişti, yeniden başlatılıyor...");
        start();
    }

    private static boolean isIntervalMode(BackupMode mode) {
        return mode == BackupMode.EVERY_N_MINUTES ||
                mode == BackupMode.EVERY_N_HOURS ||
                mode == BackupMode.EVERY_N_DAYS ||
                mode == BackupMode.EVERY_N_WEEKS ||
                mode == BackupMode.EVERY_N_MONTHS;
    }

    private static void scheduleNext(BackupMode mode, int interval) {
        LocalDateTime next = nextAlignedTime(mode, interval);
        nextBackupTime = next; // Sonraki zamanı kaydet

        long delay = Duration.between(LocalDateTime.now(), next).toMillis();

        scheduler.schedule(() -> {
            try {
                Servicio.getLogger().info("Planlanan yedekleme başlatıldı: {}", LocalDateTime.now());
                DatabaseManager.backup();
            } catch (Exception e) {
                Servicio.getLogger().error("Planlanan yedekleme hatası: {}", e.getMessage());
            }
            scheduleNext(mode, interval);
        }, delay, TimeUnit.MILLISECONDS);
    }

    private static LocalDateTime nextAlignedTime(BackupMode mode, int interval) {
        LocalDateTime now = LocalDateTime.now();
        switch (mode) {
            case EVERY_N_MINUTES: {
                int minute = ((now.getMinute() / interval) + 1) * interval;
                LocalDateTime aligned = now.withMinute(0).withSecond(0).plusMinutes(minute);
                if (aligned.isBefore(now)) aligned = aligned.plusMinutes(interval);
                return aligned;
            }
            case EVERY_N_HOURS: {
                int hour = ((now.getHour() / interval) + 1) * interval;
                LocalDateTime aligned = now.withHour(0).withMinute(0).withSecond(0).plusHours(hour);
                if (aligned.isBefore(now)) aligned = aligned.plusHours(interval);
                return aligned;
            }
            case EVERY_N_DAYS:
                return now.withHour(0).withMinute(0).withSecond(0).plusDays(interval);
            case EVERY_N_WEEKS:
                return now.with(DayOfWeek.MONDAY).withHour(0).withMinute(0).withSecond(0).plusWeeks(interval);
            case EVERY_N_MONTHS:
                return now.withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).plusMonths(interval);
            default:
                throw new IllegalArgumentException("Zaman tabanlı olmayan mod: " + mode);
        }
    }
}