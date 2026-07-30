package tr.cabro.servicio.updater;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.application.component.AppSplashScreen;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import tr.cabro.servicio.settings.AppSettings;

/**
 * Güncelleme kontrol servisi.
 * <p>
 * ── AŞAMA 1: Splash kontrolü (checkOnSplash) ─────────────────────────────
 *   Arka planda manifest indirilir. Sonuç flag'e yazılır.
 *   Splash BLOKE EDİLMEZ — uygulama başlamaya devam eder.
 *   Güncelleme varsa {@link #hasPendingUpdate()} true döner.
 *   UI tarafı bunu istediği zaman sorgulamalı ve kendi diyaloğunu göstermelidir.
 * <p>
 * ── AŞAMA 2: Periyodik kontrol (startPeriodicCheck) ──────────────────────
 *   Her 6 saatte bir kontrol yapılır. UpdateDialog gösterilir.
 */
public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    // ─── Sabitler ────────────────────────────────────────────────────────────

    private static final long   SPLASH_CHECK_TIMEOUT_SEC = 10;
    private static final long   PERIODIC_INTERVAL_HOURS  = 6;

    // ─── Durum ───────────────────────────────────────────────────────────────

    /**
     * -- GETTER --
     * Doğrudan UpdateManager erişimi.
     */
    @Getter
    private final UpdateManager             manager;
    private       JFrame                    ownerFrame;

    /** Splash kontrolünden gelen sonuç — null: henüz bitmedi */
    private final AtomicReference<UpdateManifest> pendingUpdate =
            new AtomicReference<UpdateManifest>(null);

    /** true → splash kontrolü tamamlandı (güncelleme var veya yok) */
    private final AtomicBoolean splashCheckDone = new AtomicBoolean(false);

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "servicio-update-scheduler");
                t.setDaemon(true);
                return t;
            });

    // ─── Oluşturucu ──────────────────────────────────────────────────────────

    public UpdateChecker() {
        this.manager = buildManager();
    }

    // ─── "Bu sürümü atla" kaydı (config.json) ────────────────────────────────

    private static String skippedVersion() {
        String value = AppSettings.get().getUpdate().getSkippedVersion();
        return value != null ? value : "";
    }

    static void setSkippedVersion(String version) {
        AppSettings.get().getUpdate().setSkippedVersion(version);
        AppSettings.save();
    }

    private static void clearSkippedVersion() {
        setSkippedVersion(null);
    }

    // ─── AŞAMA 1: Splash kontrolü ────────────────────────────────────────────

    /**
     * Splash ekranı açıkken arka planda güncelleme kontrolü başlatır.
     * <p>
     * Bu metot NON-BLOCKING'dir — hemen döner, uygulama başlamaya devam eder.
     * Kontrol arka planda tamamlanır; sonuç {@link #hasPendingUpdate()} ile sorgulanabilir.
     * <p>
     * Splash mesajını günceller ama uygulamayı durdurmaz.
     *
     * @param splash Mesaj güncellemesi için splash ekranı referansı.
     */
    public void checkOnSplash(final AppSplashScreen splash) {
        splash.updateMessage("Güncelleme kontrol ediliyor...");

        manager.checkForUpdates(
                manifest -> {
                    String  skipped    = skippedVersion();
                    boolean wasSkipped = skipped.equals(manifest.getVersion());
                    boolean isHotfix   = UpdateManager.sameVersion(manifest.getVersion(), skipped);

                    if (wasSkipped && isHotfix) {
                        clearSkippedVersion();
                        log.info("Hotfix tespit edildi, atla kaydı sıfırlandı: v{}",
                                manifest.getVersion());
                    } else if (wasSkipped) {
                        log.info("Sürüm {} daha önce atlandı.", manifest.getVersion());
                        splashCheckDone.set(true);
                        return;
                    }

                    log.info("Splash kontrolü: güncelleme mevcut v{}", manifest.getVersion());
                    pendingUpdate.set(manifest);
                    splashCheckDone.set(true);
                    splash.updateMessage("Güncelleme mevcut: v" + manifest.getVersion());
                },
                () -> {
                    log.info("Splash kontrolü: güncel.");
                    splashCheckDone.set(true);
                    splash.updateMessage("Başlatılıyor...");
                },
                e -> {
                    log.warn("Splash güncelleme kontrolü başarısız: {}", e.getMessage());
                    splashCheckDone.set(true);
                    splash.updateMessage("Başlatılıyor...");
                }
        );
    }


    /**
     * Güncelleme mevcut mu?
     * Splash kontrolü tamamlandıktan sonra güvenilir sonuç döner.
     *
     * @return true → bekleyen güncelleme var, {@link #getPendingUpdate()} ile manifest alınabilir.
     */
    public boolean hasPendingUpdate() {
        return pendingUpdate.get() != null;
    }

    /**
     * Splash kontrolünden gelen manifest.
     * null dönebilir — önce {@link #hasPendingUpdate()} kontrol edin.
     */
    public UpdateManifest getPendingUpdate() {
        return pendingUpdate.get();
    }

    /**
     * Splash kontrolü tamamlandı mı?
     * false → henüz devam ediyor (timeout bekleniyor olabilir).
     */
    public boolean isSplashCheckDone() {
        return splashCheckDone.get();
    }

    /**
     * Splash kontrolünün bitmesini bekler (maksimum SPLASH_CHECK_TIMEOUT_SEC saniye).
     * Uygulama açılırken güncelleme sonucunu kesin bilmek istiyorsanız çağırın.
     * NON-BLOCKING alternatifi: {@link #hasPendingUpdate()} ile polling yapın.
     */
    public void awaitSplashCheck() {
        long deadline = System.currentTimeMillis() + SPLASH_CHECK_TIMEOUT_SEC * 1000L;
        while (!splashCheckDone.get() && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(50); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Bekleyen güncelleme bildirimini temizler (kullanıcı güncellemeyi reddetti/erteledi). */
    public void clearPendingUpdate() {
        pendingUpdate.set(null);
    }

    // ─── AŞAMA 2: Periyodik kontrol ──────────────────────────────────────────

    /**
     * Uygulama açıldıktan sonra periyodik kontrolü başlatır.
     * Servicio.run() içinde launchMainUI() çağrısından sonra çağrılmalıdır.
     */
    public void startPeriodicCheck(JFrame frame) {
        this.ownerFrame = frame;
        long intervalSec = PERIODIC_INTERVAL_HOURS * 3600L;
        scheduler.scheduleAtFixedRate(this::performPeriodicCheck, intervalSec, intervalSec, TimeUnit.SECONDS);
        log.info("Periyodik güncelleme kontrolü başlatıldı (her {}h).", PERIODIC_INTERVAL_HOURS);
    }

    /**
     * Manuel kontrol — "Yardım → Güncellemeleri Kontrol Et" menüsünden.
     * "Bu sürümü atla" filtresini sıfırlar.
     */
    public void checkNow() {
        clearSkippedVersion();
        scheduler.submit(this::performPeriodicCheck);
    }

    /** Zamanlayıcıyı durdurur. Servicio.shutdown() içinde çağrılır. */
    public void stop() {
        scheduler.shutdownNow();
        log.info("Güncelleme zamanlayıcısı durduruldu.");
    }

    // ─── İç: Periyodik kontrol ───────────────────────────────────────────────

    private void performPeriodicCheck() {
        manager.checkForUpdates(
            manifest -> {
                String  skipped    = skippedVersion();
                boolean wasSkipped = skipped.equals(manifest.getVersion());
                boolean isHotfix   = UpdateManager.sameVersion(manifest.getVersion(), skipped);

                if (wasSkipped && isHotfix) {
                    clearSkippedVersion();
                } else if (wasSkipped) {
                    log.debug("Periyodik: sürüm {} atlandı.", manifest.getVersion());
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    if (ownerFrame == null) return;
                    boolean hotfix = UpdateManager.sameVersion(
                            manifest.getVersion(), currentVersion());
                    UpdateDialog dialog = new UpdateDialog(
                            ownerFrame, manifest, manager, hotfix);
                    dialog.setOnSkipVersion(UpdateChecker::setSkippedVersion);
                    dialog.setVisible(true);
                });
            },
            () -> log.debug("Periyodik kontrol: güncel."),
            e  -> log.warn("Periyodik güncelleme kontrolü başarısız: {}", e.getMessage())
        );
    }

    // ─── Yardımcılar ─────────────────────────────────────────────────────────

    private static UpdateManager buildManager() {
        String manifestUrl = readProp("manifest.url",
                "https://raw.githubusercontent.com/KULLANICI/REPO/master/update-manifest.json");
        String version     = readProp("version", "0.0.0");
        File   appRoot     = resolveAppRoot();

        if (appRoot == null) {
            log.info("UpdateManager | surum={} | mod=GELISTIRME", version);
            appRoot = new File(".");
            return new UpdateManager(manifestUrl, version, appRoot, true);
        }

        log.info("UpdateManager | surum={} | appRoot={}", version, appRoot.getAbsolutePath());
        return new UpdateManager(manifestUrl, version, appRoot, false);
    }

    static String currentVersion() {
        return readProp("version", "0.0.0");
    }

    private static String readProp(String key, String def) {
        InputStream is = null;
        try {
            is = UpdateChecker.class.getResourceAsStream("/version.properties");
            if (is == null) return def;
            Properties p = new Properties();
            p.load(is);
            String v = p.getProperty(key, def);
            return (v == null || v.trim().isEmpty()) ? def : v.trim();
        } catch (Exception e) {
            return def;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) {}
        }
    }

    private static File resolveAppRoot() {
        try {
            File f = new File(UpdateChecker.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            if (f.isFile() && f.getName().endsWith(".jar")) return f.getParentFile();
            return null; // IDE ortamı
        } catch (Exception e) {
            return null;
        }
    }

    static boolean isDevEnvironment() {
        return resolveAppRoot() == null;
    }
}