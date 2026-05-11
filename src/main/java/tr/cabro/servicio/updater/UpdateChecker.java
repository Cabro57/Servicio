package tr.cabro.servicio.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Arka plan güncelleme kontrol servisi.
 *
 * Entegrasyon:
 *   - Servicio.run() içinde UpdateChecker.start() çağrılır.
 *   - MainUI'de "Yardım → Güncellemeleri Kontrol Et" menüsü için checkNow() kullanılır.
 *
 * Özellikler:
 *   - Uygulama açılışında 3 sn gecikmeli kontrol (splash kapandıktan sonra)
 *   - Her 6 saatte bir periyodik kontrol
 *   - "Bu sürümü atla" java.util.prefs.Preferences ile saklanır
 *   - System tray bildirimi (destekleniyorsa)
 *   - Manifest URL'si ve sürüm version.properties'ten otomatik okunur
 */
public class UpdateChecker {

    private static final Logger log = LoggerFactory.getLogger(UpdateChecker.class);

    // ─── Sabitler ─────────────────────────────────────────────────────────────

    private static final long   STARTUP_DELAY_SEC    = 3;
    private static final long   CHECK_INTERVAL_HOURS = 6;
    private static final String PREF_SKIPPED_VERSION = "update.skipped_version";
    private static final String PREF_NODE            = "tr/cabro/servicio";

    // ─── Durum ────────────────────────────────────────────────────────────────

    private final UpdateManager manager;
    private final JFrame        ownerFrame;
    private final Preferences   prefs;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "servicio-update-scheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    private UpdateChecker(JFrame ownerFrame) {
        this.ownerFrame = ownerFrame;
        this.prefs      = Preferences.userRoot().node(PREF_NODE);

        String manifestUrl  = readProperty("manifest.url",  "https://example.com/update-manifest.json");
        String version      = readProperty("version",       "0.0.0");
        File   appRoot      = resolveAppRoot();

        this.manager = new UpdateManager(manifestUrl, version, appRoot);

        log.info("UpdateChecker hazır | sürüm={} | manifest={}", version, manifestUrl);
    }

    // ─── Factory ──────────────────────────────────────────────────────────────

    /**
     * Servicio.run() içinden çağrılır.
     * Tek örnek oluşturur, açılış + periyodik kontrolü başlatır.
     */
    public static UpdateChecker createAndStart(JFrame ownerFrame) {
        UpdateChecker checker = new UpdateChecker(ownerFrame);
        checker.start();
        return checker;
    }

    // ─── Genel API ────────────────────────────────────────────────────────────

    /**
     * Açılış ve periyodik kontrolü başlatır.
     * Servicio.run() → EventQueue.invokeLater içinde launchMainUI() sonrasında çağrılmalıdır.
     */
    public void start() {
        // Açılış kontrolü: splash kapandıktan 3 sn sonra
        scheduler.schedule(new Runnable() {
            @Override
            public void run() { performCheck(false); }
        }, STARTUP_DELAY_SEC, TimeUnit.SECONDS);

        // Periyodik kontrol
        long intervalSec = CHECK_INTERVAL_HOURS * 3600L;
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() { performCheck(false); }
        }, intervalSec, intervalSec, TimeUnit.SECONDS);

        log.info("Güncelleme zamanlayıcısı başlatıldı.");
    }

    /**
     * Manuel, anlık kontrol.
     * "Bu sürümü atla" filtresini sıfırlar.
     * MainUI → Yardım menüsünden çağrılır.
     */
    public void checkNow() {
        prefs.remove(PREF_SKIPPED_VERSION);
        scheduler.submit(new Runnable() {
            @Override
            public void run() { performCheck(true); }
        });
    }

    /** Zamanlayıcıyı durdurur. Servicio.shutdown() içinde çağrılabilir. */
    public void stop() {
        scheduler.shutdownNow();
        log.info("Güncelleme zamanlayıcısı durduruldu.");
    }

    /** UpdateManager'e doğrudan erişim (UpdateDialog içinden). */
    public UpdateManager getManager() { return manager; }

    // ─── İç İşleyiş ──────────────────────────────────────────────────────────

    private void performCheck(final boolean forceShow) {
        manager.checkForUpdates(
                new Consumer<UpdateManifest>() {
                    @Override
                    public void accept(final UpdateManifest manifest) {
                        // "Bu sürümü atla" kontrolü (manuel checkNow'da bypass)
                        if (!forceShow) {
                            String skipped = prefs.get(PREF_SKIPPED_VERSION, "");
                            if (skipped.equals(manifest.getVersion())) {
                                log.info("Sürüm {} daha önce atlandı, bildirim gösterilmiyor.",
                                        manifest.getVersion());
                                return;
                            }
                        }

                        // System tray bildirimi (opsiyonel)
                        showTrayNotification(
                                "Servicio Güncellemesi",
                                "v" + manifest.getVersion() + " sürümü hazır!",
                                TrayIcon.MessageType.INFO
                        );

                        // Diyaloğu EDT'de göster
                        SwingUtilities.invokeLater(new Runnable() {
                            @Override
                            public void run() {
                                showUpdateDialog(manifest);
                            }
                        });
                    }
                },
                new Runnable() {
                    @Override
                    public void run() {
                        log.info("Uygulama güncel, güncelleme yok.");
                        // Manuel kontrolse kullanıcıya bildir
                        if (forceShow) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    JOptionPane.showMessageDialog(
                                            ownerFrame,
                                            "Servicio zaten en güncel sürümde!",
                                            "Güncelleme Kontrolü",
                                            JOptionPane.INFORMATION_MESSAGE
                                    );
                                }
                            });
                        }
                    }
                },
                new Consumer<Exception>() {
                    @Override
                    public void accept(Exception e) {
                        // Sessiz başarısızlık (ağ yoksa kullanıcıyı rahatsız etme)
                        // Manuel kontrolse hata göster
                        if (forceShow) {
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    JOptionPane.showMessageDialog(
                                            ownerFrame,
                                            "Güncelleme sunucusuna ulaşılamadı.\nLütfen internet bağlantınızı kontrol edin.",
                                            "Bağlantı Hatası",
                                            JOptionPane.WARNING_MESSAGE
                                    );
                                }
                            });
                        }
                    }
                }
        );
    }

    private void showUpdateDialog(UpdateManifest manifest) {
        UpdateDialog dialog = new UpdateDialog(ownerFrame, manifest, manager);

        dialog.setOnSkipVersion(new Consumer<String>() {
            @Override
            public void accept(String version) {
                prefs.put(PREF_SKIPPED_VERSION, version);
                log.info("Sürüm {} atlandı olarak işaretlendi.", version);
            }
        });

        dialog.setVisible(true);
    }

    // ─── Yardımcı Metotlar ────────────────────────────────────────────────────

    /**
     * version.properties'ten anahtar okur.
     * Dosya şu an: src/main/resources/version.properties
     * manifest.url=https://raw.githubusercontent.com/...
     * version=${project.version}   ← Maven filtering ile otomatik doldurulur
     */
    private static String readProperty(String key, String defaultValue) {
        InputStream is = null;
        try {
            is = UpdateChecker.class.getResourceAsStream("/version.properties");
            if (is == null) return defaultValue;
            Properties props = new Properties();
            props.load(is);
            String value = props.getProperty(key, defaultValue);
            return value.trim().isEmpty() ? defaultValue : value.trim();
        } catch (Exception e) {
            return defaultValue;
        } finally {
            if (is != null) try { is.close(); } catch (IOException ignored) { }
        }
    }

    /**
     * Uygulamanın çalıştığı dizini döner.
     * JAR içinden çalışıyorsa JAR'ın bulunduğu dizin,
     * IDE'den çalışıyorsa proje kökü döner.
     */
    private static File resolveAppRoot() {
        try {
            File jarFile = new File(
                    UpdateChecker.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            );
            // JAR mı yoksa classes/ klasörü mü?
            if (jarFile.isFile()) {
                return jarFile.getParentFile();   // JAR'ın yanındaki dizin
            }
            return new File(".");                 // IDE / geliştirme ortamı
        } catch (Exception e) {
            return new File(".");
        }
    }

    // ─── System Tray ─────────────────────────────────────────────────────────

    private static void showTrayNotification(String title, String msg, TrayIcon.MessageType type) {
        if (!SystemTray.isSupported()) return;
        try {
            TrayIcon[] icons = SystemTray.getSystemTray().getTrayIcons();
            if (icons.length > 0) {
                icons[0].displayMessage(title, msg, type);
            }
        } catch (Exception ignored) { }
    }
}