package tr.cabro.servicio.updater;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.application.component.AppSplashScreen;

import javax.swing.*;
import java.io.*;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * İki aşamalı güncelleme kontrol servisi.
 * <p>
 * ── AŞAMA 1: Splash aşaması (checkOnSplash) ──────────────────────────────
 *   Servicio(splash) constructor'ı çağrıldıktan hemen sonra arka planda
 *   manifest indirilir. Sonuç geldiğinde:
 *     • Güncelleme varsa → SplashUpdatePanel splash'in üzerine gösterilir.
 *       Kullanıcı "Güncelle" derse indirilir ve JVM kapanır (launcher devam eder).
 *       "Şimdi Değil" / "Sürümü Atla" derse panel kaldırılır, uygulama başlar.
 *     • Güncelleme yoksa → hiçbir şey olmaz, splash ilerlemeye devam eder.
 *   checkOnSplash(), uygulama başlatma iş parçacığını BLOKElemez;
 *   splash ilerlemesi devam ederken kontrol arka planda olur.
 *   Güncelleme bulunursa splash dondurulur (progress güncellenmez),
 *   kullanıcı karar verdikten sonra devam edilir.
 * <p>
 * ── AŞAMA 2: Periyodik kontrol (startPeriodicCheck) ─────────────────────
 *   Uygulama tamamen açıldıktan sonra her 6 saatte bir kontrol yapılır.
 *   Güncelleme varsa UpdateDialog (modal diyalog) gösterilir.
 * <p>
 * Manifest URL ve sürüm: /version.properties'ten okunur (Maven filtering).
 */
public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    // ─── Sabitler ─────────────────────────────────────────────────────────────

    private static final long   PERIODIC_INTERVAL_HOURS = 6;
    private static final String PREF_NODE               = "tr/cabro/servicio";
    private static final String PREF_SKIPPED_VERSION    = "update.skipped_version";

    // ─── Bağımlılıklar ────────────────────────────────────────────────────────

    /**
     * -- GETTER --
     * UpdateManager'e doğrudan erişim (gerekirse).
     */
    @Getter
    private final UpdateManager manager;
    private final Preferences   prefs;
    private       JFrame        ownerFrame; // periyodik kontrol için, sonradan set edilir

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "servicio-update-scheduler");
                t.setDaemon(true);
                return t;
            });

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    public UpdateChecker() {
        this.prefs   = Preferences.userRoot().node(PREF_NODE);
        this.manager = buildManager();
    }

    // ─── AŞAMA 1: Splash ekranında kontrol ───────────────────────────────────

    /**
     * Splash ekranında güncelleme kontrolü yapar.
     * <p>
     * Bu metot NON-BLOCKING'dir; arka plan iş parçacığında kontrol yapar.
     * Güncelleme bulunursa splash freeze olur ve SplashUpdatePanel gösterilir.
     * Kullanıcı karar verince (güncelle/atla) bu metot döner — splice'ın devamı çalışır.
     * <p>
     * Kullanım — Servicio constructor'ı içinde, diğer initler ile paralel:
     * <pre>
     *   splash.updateProgress(10, "Güncelleme kontrol ediliyor...");
     *   updateChecker.checkOnSplash(splash);
     *   // checkOnSplash dönüşünden sonra kullanıcı ya güncellemeyi seçti (JVM kapandı)
     *   // ya da devam et dedi; normal akış sürer.
     * </pre>
     *
     * @param splash  Mevcut splash ekranı. SplashUpdatePanel buna bindirilerek gösterilir.
     */
    public void checkOnSplash(final AppSplashScreen splash) {
        // Kullanıcı kararını beklemek için latch
        final CountDownLatch userDecision = new CountDownLatch(1);
        final AtomicBoolean  updateChosen = new AtomicBoolean(false);

        Thread checker = new Thread(() -> manager.checkForUpdates(

                // Güncelleme VAR
                manifest -> {
                    // "Bu sürümü atla" kontrolü.
                    //
                    // ÖNEMLI: Yalnızca sürüm numarası yeniyse atla.
                    // Sürüm AYNI ama hash farklıysa (aynı sürüme yama = hotfix)
                    // kullanıcı daha önce "atla" demiş olsa bile göster;
                    // çünkü bu farklı bir içerik değişikliğidir.
                    String skipped     = prefs.get(PREF_SKIPPED_VERSION, "");
                    boolean isHotfix   = UpdateManager.sameVersion(
                            manifest.getVersion(), skipped);
                    boolean wasSkipped = skipped.equals(manifest.getVersion());

                    if (wasSkipped && !isHotfix) {
                        // Kullanıcı bu sürümü atladı ve bu bir hotfix değil
                        log.info("Sürüm {} daha önce atlandı, gösterilmiyor.", manifest.getVersion());
                        userDecision.countDown();
                        return;
                    }

                    // Hotfix ise skipped kaydını sil — kullanıcı tekrar sorulsun
                    if (wasSkipped && isHotfix) {
                        prefs.remove(PREF_SKIPPED_VERSION);
                        log.info("Sürüm {} için hotfix tespit edildi, atla kaydı sıfırlandı.",
                                manifest.getVersion());
                    }

                    log.info("Splash'te güncelleme bulundu: v{}", manifest.getVersion());

                    SwingUtilities.invokeLater(() -> showSplashPanel(splash, manifest, userDecision));
                },

                // Güncelleme YOK
                () -> {
                    log.info("Uygulama güncel (splash kontrolü).");
                    userDecision.countDown();
                },

                // HATA (sessiz — splash devam eder)
                e -> {
                    log.warn("Splash güncelleme kontrolü başarısız (sessiz): {}", e.getMessage());
                    userDecision.countDown();
                }
        ), "servicio-splash-update-check");
        checker.setDaemon(true);
        checker.start();

        // Arka plan iş parçacığı (Servicio constructor thread'i) burada bekler.
        // Splash'in paint thread'i (EDT) ayrı olduğu için splash donmaz.
        try {
            // Maksimum 12 sn bekle; ağ çok yavaşsa takılmasın
            userDecision.await(12, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ─── AŞAMA 2: Periyodik kontrol ──────────────────────────────────────────

    /**
     * Uygulama başladıktan sonra periyodik kontrolü aktif eder.
     * Servicio.run() içinde launchMainUI() çağrısından sonra çağrılmalıdır.
     *
     * @param frame  Güncelleme diyaloğunun sahibi olacak ana pencere.
     */
    public void startPeriodicCheck(JFrame frame) {
        this.ownerFrame = frame;

        long intervalSec = PERIODIC_INTERVAL_HOURS * 3600L;
        scheduler.scheduleAtFixedRate(this::performPeriodicCheck, intervalSec, intervalSec, TimeUnit.SECONDS);

        log.info("Periyodik güncelleme kontrolü başlatıldı (her {}h).", PERIODIC_INTERVAL_HOURS);
    }

    /**
     * Manuel kontrol — MainUI "Yardım → Güncellemeleri Kontrol Et" menüsünden.
     * "Bu sürümü atla" filtresini sıfırlar.
     */
    public void checkNow() {
        prefs.remove(PREF_SKIPPED_VERSION);
        scheduler.submit(this::performPeriodicCheck);
    }

    /** Zamanlayıcıyı durdurur. Servicio.shutdown() içinde çağrılır. */
    public void stop() {
        scheduler.shutdownNow();
        log.info("Güncelleme zamanlayıcısı durduruldu.");
    }

    // ─── İç: Splash panel gösterimi ──────────────────────────────────────────

    /**
     * SplashUpdatePanel'i splash ekranının JLayeredPane'ine ekler.
     * Panel kendi içinde "Şimdi Değil" / "Güncelle" kararını yönetir.
     * Karar verilince latch serbest bırakılır.
     */
    private void showSplashPanel(final AppSplashScreen splash,
                                 final UpdateManifest manifest,
                                 final CountDownLatch latch) {

        // Sürüm aynıysa ama hash farklıysa bu bir hotfix/yamadır
        boolean isHotfix = UpdateManager.sameVersion(manifest.getVersion(), currentVersion());

        SplashUpdatePanel panel = new SplashUpdatePanel(manifest, manager, isHotfix);

        // Splash'in tam boyutunu kapla
        panel.setBounds(0, 0, splash.getWidth(), splash.getHeight());

        // "Şimdi Değil" veya "Sürümü Atla" → paneli kaldır, devam et
        panel.setOnSkip(() -> {
            SwingUtilities.invokeLater(() -> {
                splash.getLayeredPane().remove(panel);
                splash.getLayeredPane().revalidate();
                splash.getLayeredPane().repaint();
            });
            latch.countDown();
        });

        // "Bu sürümü atla" → kaydet
        panel.setOnSkipVersion(version -> {
            prefs.put(PREF_SKIPPED_VERSION, version);
            log.info("Sürüm {} atlandı olarak işaretlendi.", version);
        });

        // "Güncelle" tamamlandıktan sonra System.exit(0) SplashUpdatePanel içinde çağrılır.
        // Latch serbest bırakmaya gerek yok çünkü JVM kapanır.

        // Splash'in layered pane'ine en üste ekle
        splash.getLayeredPane().add(panel, JLayeredPane.PALETTE_LAYER);
        splash.getLayeredPane().revalidate();
        splash.getLayeredPane().repaint();

        log.info("SplashUpdatePanel gösterildi.");
    }

    // ─── İç: Periyodik kontrol ───────────────────────────────────────────────

    private void performPeriodicCheck() {
        manager.checkForUpdates(
                manifest -> {
                    String  skipped   = prefs.get(PREF_SKIPPED_VERSION, "");
                    boolean wasSkipped = skipped.equals(manifest.getVersion());
                    boolean isHotfix  = UpdateManager.sameVersion(
                            manifest.getVersion(), skipped);

                    // Hotfix ise skipped'i sıfırla, yine göster
                    if (wasSkipped && isHotfix) {
                        prefs.remove(PREF_SKIPPED_VERSION);
                        log.info("Periyodik: hotfix tespit edildi, atla kaydı sıfırlandı.");
                    } else if (wasSkipped) {
                        log.debug("Periyodik: sürüm {} atlandı, gösterilmiyor.", manifest.getVersion());
                        return;
                    }

                    SwingUtilities.invokeLater(() -> {
                        if (ownerFrame != null) {
                            boolean hotfix = UpdateManager.sameVersion(
                                    manifest.getVersion(), currentVersion());
                            UpdateDialog dialog = new UpdateDialog(
                                    ownerFrame, manifest, manager, hotfix);
                            dialog.setOnSkipVersion(new Consumer<String>() {
                                @Override
                                public void accept(String version) {
                                    prefs.put(PREF_SKIPPED_VERSION, version);
                                }
                            });
                            dialog.setVisible(true);
                        }
                    });
                },
                () -> log.debug("Periyodik kontrol: güncel."),
                e -> log.warn("Periyodik güncelleme kontrolü başarısız: {}", e.getMessage())
        );
    }

    // ─── Yardımcı: UpdateManager fabrikası ───────────────────────────────────

    private static UpdateManager buildManager() {
        String manifestUrl = readProp("manifest.url",
                "https://raw.githubusercontent.com/KULLANICI/REPO/main/update-manifest.json");
        String version     = readProp("version", "0.0.0");
        File   appRoot     = resolveAppRoot();

        log.info("UpdateManager | sürüm={} | appRoot={}", version, appRoot.getAbsolutePath());
        return new UpdateManager(manifestUrl, version, appRoot);
    }

    /** Çalışan uygulamanın sürümünü version.properties'ten okur. */
    private static String currentVersion() {
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
            if (is != null) try { is.close(); } catch (IOException ignored) { }
        }
    }

    private static File resolveAppRoot() {
        try {
            File f = new File(UpdateChecker.class
                    .getProtectionDomain().getCodeSource().getLocation().toURI());
            return f.isFile() ? f.getParentFile() : new File(".");
        } catch (Exception e) {
            return new File(".");
        }
    }
}