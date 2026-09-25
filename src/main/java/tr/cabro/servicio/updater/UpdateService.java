package tr.cabro.servicio.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.application.component.AppSplashScreen;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.settings.AppSettings;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Güncelleme akışının tek sahibi: denetim, arka planda indirme, yeniden başlatma ve kapanışta kurma.
 * <p>
 * Arayüz (alt çubuktaki gösterge, güncelleme penceresi, Ayarlar &gt; Güncelleme) yalnızca bu sınıfın
 * durumunu okur ve {@link #addListener} ile değişiklikleri EDT'de dinler. Hiçbir otomatik adım pencere
 * açmaz: yeni sürüm bulunduğunda yalnızca durum {@link State#AVAILABLE} olur, indirme her zaman
 * kullanıcının isteğiyle başlar.
 * <pre>
 *   IDLE ─denetle→ CHECKING ─→ UP_TO_DATE | AVAILABLE | CHECK_FAILED
 *   AVAILABLE ─indir→ DOWNLOADING ─→ READY | DOWNLOAD_FAILED   (iptal → AVAILABLE)
 *   READY ─yeniden başlat→ RESTARTING   (ya da uygulama kapanırken sessizce kurulur)
 * </pre>
 * Ağ, hash ve launcher script işleri {@link UpdateManager}'dadır.
 */
public class UpdateService {

    private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

    private static final long PERIODIC_INTERVAL_HOURS = 6;

    public enum State {
        IDLE, CHECKING, UP_TO_DATE, CHECK_FAILED, AVAILABLE, DOWNLOADING, DOWNLOAD_FAILED, READY, RESTARTING;

        /** İndirme başlamış ya da bitmiş: denetim artık durumu değiştirmez. */
        boolean isPastCheck() {
            return this == DOWNLOADING || this == READY || this == RESTARTING;
        }
    }

    private final UpdateManager manager;
    private final String currentVersion;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile State state = State.IDLE;
    private volatile UpdateManifest manifest;
    private volatile String error;
    private volatile LocalDateTime lastChecked;
    private volatile double progress;
    private volatile String currentFile;

    private volatile UpdateManifest.GitHubReleaseInfo releaseInfo;
    private volatile String releaseInfoVersion;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "servicio-update-scheduler");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> periodic;

    public UpdateService() {
        this.currentVersion = readProp("version", "0.0.0");
        this.manager = buildManager(currentVersion);
    }

    // ------------------------------------------------------------------ durum

    public State getState() { return state; }
    public UpdateManifest getManifest() { return manifest; }
    public String getError() { return error; }
    public LocalDateTime getLastChecked() { return lastChecked; }
    public double getProgress() { return progress; }
    public String getCurrentFile() { return currentFile; }
    public String getCurrentVersion() { return currentVersion; }

    /** Bulunan sürüm, kullanılan sürümle aynı numarada ama dosyaları farklı: kritik yama. */
    public boolean isHotfix() {
        UpdateManifest m = manifest;
        return m != null && UpdateManager.sameVersion(m.getVersion(), currentVersion);
    }

    public String getSkippedVersion() {
        String value = AppSettings.get().getUpdate().getSkippedVersion();
        return value == null || value.isBlank() ? null : value;
    }

    public boolean isAutoCheck() {
        return AppSettings.get().getUpdate().isAutoCheck();
    }

    /** Durum her değiştiğinde EDT'de çağrılır. Kısa ömürlü bileşenler {@link #removeListener} ile bırakmalı. */
    public void addListener(Runnable listener) { listeners.add(listener); }
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    private void fire() {
        SwingUtilities.invokeLater(() -> listeners.forEach(Runnable::run));
    }

    private void set(State newState) {
        state = newState;
        fire();
    }

    // ------------------------------------------------------------------ denetim

    /** Açılışta, splash açıkken sessiz denetim. Otomatik denetim kapalıysa hiçbir şey yapmaz. */
    public void checkOnSplash(AppSplashScreen splash) {
        if (!isAutoCheck()) return;
        splash.updateMessage(Messages.get("updater.checking"));
        check(false);
    }

    /** Arayüz açıldıktan sonra periyodik sessiz denetimi kurar (ayar kapalıysa kurmaz). */
    public void startAutomaticChecks() {
        reschedule();
    }

    public void setAutoCheck(boolean enabled) {
        AppSettings.get().getUpdate().setAutoCheck(enabled);
        AppSettings.save();
        reschedule();
        fire();
    }

    private synchronized void reschedule() {
        if (periodic != null) {
            periodic.cancel(false);
            periodic = null;
        }
        if (!isAutoCheck() || scheduler.isShutdown()) return;
        long interval = PERIODIC_INTERVAL_HOURS * 3600L;
        periodic = scheduler.scheduleAtFixedRate(() -> check(false), interval, interval, TimeUnit.SECONDS);
        log.info("Periyodik güncelleme denetimi kuruldu (her {} saatte bir).", PERIODIC_INTERVAL_HOURS);
    }

    /**
     * Güncelleme denetler. {@code manual} ise sonuç (hata dahil) ekranda gösterilir ve atlanan sürüm
     * de bildirilir; otomatik denetimde hata sessizce günlüğe yazılır, önceki durum korunur.
     */
    public synchronized void check(boolean manual) {
        if (state.isPastCheck() || state == State.CHECKING) return;
        State before = state;
        if (manual) set(State.CHECKING);

        manager.checkForUpdates(
                found -> onCheckResult(found, manual),
                () -> {
                    lastChecked = LocalDateTime.now();
                    if (state.isPastCheck()) return;
                    manifest = null;
                    set(State.UP_TO_DATE);
                },
                e -> {
                    lastChecked = LocalDateTime.now();
                    log.warn("Güncelleme denetimi başarısız: {}", e.getMessage());
                    if (state.isPastCheck()) return;
                    // Zaten bulunmuş bir sürüm, başarısız bir yeniden denetim yüzünden kaybolmasın.
                    if (manual && before != State.AVAILABLE) {
                        error = e.getMessage();
                        set(State.CHECK_FAILED);
                    } else {
                        set(before);
                    }
                });
    }

    private void onCheckResult(UpdateManifest found, boolean manual) {
        lastChecked = LocalDateTime.now();
        if (state.isPastCheck()) return;

        String skipped = getSkippedVersion();
        boolean hotfix = UpdateManager.sameVersion(found.getVersion(), currentVersion);
        if (skipped != null && skipped.equals(found.getVersion()) && !hotfix && !manual) {
            log.info("Sürüm {} daha önce atlanmıştı, bildirilmedi.", found.getVersion());
            manifest = null;
            set(State.UP_TO_DATE);
            return;
        }
        log.info("Güncelleme mevcut: v{}{}", found.getVersion(), hotfix ? " (yama)" : "");
        manifest = found;
        set(State.AVAILABLE);
    }

    // ------------------------------------------------------------------ indirme

    /** Arka planda indirir; ilerleme {@link #getProgress()} ile okunur. */
    public synchronized void download() {
        UpdateManifest m = manifest;
        if (m == null || !(state == State.AVAILABLE || state == State.DOWNLOAD_FAILED)) return;

        int total = Math.max(1, m.getFiles() != null ? m.getFiles().size() : 1);
        int[] done = {0};
        int[] lastPercent = {-1};
        progress = 0;
        currentFile = null;
        error = null;
        set(State.DOWNLOADING);

        manager.downloadUpdate(m,
                (name, fraction) -> {
                    currentFile = name;
                    progress = Math.min(1.0, (done[0] + fraction) / total);
                    int percent = (int) (progress * 100);
                    // Her 16 KB'ta bir EDT'ye iş yığmamak için yalnızca yüzde değişince haber ver.
                    if (percent != lastPercent[0]) {
                        lastPercent[0] = percent;
                        fire();
                    }
                },
                name -> done[0]++,
                name -> done[0]++,
                () -> {
                    progress = 1.0;
                    log.info("Güncelleme v{} indirildi, yeniden başlatmada kurulacak.", m.getVersion());
                    set(State.READY);
                },
                e -> {
                    if (state != State.DOWNLOADING) return;
                    error = e.getMessage();
                    set(State.DOWNLOAD_FAILED);
                });
    }

    public synchronized void cancelDownload() {
        if (state != State.DOWNLOADING) return;
        manager.cancel();
        log.info("Güncelleme indirmesi kullanıcı tarafından iptal edildi.");
        set(State.AVAILABLE);
    }

    // ------------------------------------------------------------------ kurma

    /**
     * İndirilen güncellemeyi kurar ve uygulamayı yeniden açar. Hata olursa durum
     * {@link State#DOWNLOAD_FAILED} olur ve {@code onError} EDT'de çağrılır.
     */
    public synchronized void restartNow(Consumer<Exception> onError) {
        if (state != State.READY) return;
        set(State.RESTARTING);
        new Thread(() -> {
            try {
                manager.applyUpdate();
                File script = manager.writeLauncherScript("servicio.jar", "", true);
                manager.launchAndExit(script);
                SwingUtilities.invokeLater(() -> tr.cabro.servicio.Servicio.getInstance().shutdown());
            } catch (Exception e) {
                log.error("Güncelleme kurulamadı", e);
                error = e.getMessage();
                set(State.DOWNLOAD_FAILED);
                SwingUtilities.invokeLater(() -> onError.accept(e));
            }
        }, "servicio-apply-update").start();
    }

    /**
     * Uygulama kapanırken çağrılır: indirilmiş bir güncelleme bekliyorsa yeniden açmadan kurar.
     * "Sonra" diyen kullanıcının güncellemesi bir sonraki açılışta hazır olur.
     */
    public void installOnExitIfReady() {
        if (state != State.READY) return;
        try {
            manager.applyUpdate();
            File script = manager.writeLauncherScript("servicio.jar", "", false);
            manager.launchAndExit(script);
            log.info("Bekleyen güncelleme kapanışta kuruluyor.");
        } catch (Exception e) {
            log.warn("Bekleyen güncelleme kapanışta kurulamadı: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ atla

    /** Bulunan sürümü bir daha otomatik bildirme. Yamalar atlanamaz. */
    public synchronized void skipVersion() {
        UpdateManifest m = manifest;
        if (m == null || isHotfix() || state != State.AVAILABLE) return;
        AppSettings.get().getUpdate().setSkippedVersion(m.getVersion());
        AppSettings.save();
        log.info("Sürüm {} atlandı.", m.getVersion());
        manifest = null;
        set(State.IDLE);
    }

    public void clearSkippedVersion() {
        AppSettings.get().getUpdate().setSkippedVersion(null);
        AppSettings.save();
        fire();
    }

    // ------------------------------------------------------------------ sürüm notları

    /** Bulunan sürümün GitHub sürüm notlarını getirir; aynı sürüm için bir kez indirilir. */
    public void fetchReleaseInfo(Consumer<UpdateManifest.GitHubReleaseInfo> onSuccess, Consumer<Exception> onError) {
        UpdateManifest m = manifest;
        if (m == null) {
            onError.accept(new IllegalStateException("Güncelleme yok"));
            return;
        }
        if (releaseInfo != null && m.getVersion().equals(releaseInfoVersion)) {
            onSuccess.accept(releaseInfo);
            return;
        }
        manager.fetchReleaseInfo(m,
                info -> {
                    releaseInfo = info;
                    releaseInfoVersion = m.getVersion();
                    SwingUtilities.invokeLater(() -> onSuccess.accept(info));
                },
                e -> SwingUtilities.invokeLater(() -> onError.accept(e)));
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    // ------------------------------------------------------------------ kurulum

    private static UpdateManager buildManager(String version) {
        String manifestUrl = readProp("manifest.url",
                "https://raw.githubusercontent.com/KULLANICI/REPO/master/update-manifest.json");
        File appRoot = resolveAppRoot();
        if (appRoot == null) {
            log.info("UpdateManager | surum={} | mod=GELISTIRME", version);
            return new UpdateManager(manifestUrl, version, new File("."), true);
        }
        log.info("UpdateManager | surum={} | appRoot={}", version, appRoot.getAbsolutePath());
        return new UpdateManager(manifestUrl, version, appRoot, false);
    }

    private static String readProp(String key, String def) {
        try (InputStream is = UpdateService.class.getResourceAsStream("/version.properties")) {
            if (is == null) return def;
            Properties p = new Properties();
            p.load(is);
            String v = p.getProperty(key, def);
            return (v == null || v.trim().isEmpty()) ? def : v.trim();
        } catch (IOException e) {
            return def;
        }
    }

    /** Çalışan JAR'ın klasörü; IDE'den çalışırken null. */
    private static File resolveAppRoot() {
        try {
            File f = new File(UpdateService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() && f.getName().endsWith(".jar") ? f.getParentFile() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
