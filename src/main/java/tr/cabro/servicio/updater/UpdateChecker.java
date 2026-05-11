package tr.cabro.servicio.updater;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.prefs.Preferences;

/**
 * Arka plan güncelleme kontrol servisi.

 * Özellikler:
 *  • Uygulama açılışında güncelleme kontrolü (2 sn gecikmeyle, UI donmasın diye).
 *  • Periyodik kontrol (varsayılan: her 6 saatte bir).
 *  • System tray bildirimi desteği.
 *  • "Bu sürümü atla" desteği (java.util.prefs.Preferences ile saklanır).

 * Java 8 uyumlu.
 */
public class UpdateChecker {

    // ─── Yapılandırma ─────────────────────────────────────────────────────────

    private static final long   STARTUP_DELAY_SECONDS = 2;
    private static final long   CHECK_INTERVAL_HOURS  = 6;
    private static final String PREF_SKIPPED_VERSION  = "skipped_update_version";

    // ─── Bağımlılıklar ────────────────────────────────────────────────────────

    private final UpdateManager updateManager;
    private final JFrame        ownerFrame;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(new java.util.concurrent.ThreadFactory() {
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "update-scheduler");
                    t.setDaemon(true);
                    return t;
                }
            });

    private final Preferences prefs = Preferences.userNodeForPackage(UpdateChecker.class);

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    /**
     * @param updateManager  Yapılandırılmış UpdateManager örneği.
     * @param ownerFrame     Güncelleme diyaloğunun sahibi olacak ana pencere.
     */
    public UpdateChecker(UpdateManager updateManager, JFrame ownerFrame) {
        this.updateManager = updateManager;
        this.ownerFrame    = ownerFrame;
    }

    // ─── Genel API ────────────────────────────────────────────────────────────

    /**
     * Hem açılış kontrolünü hem de periyodik kontrolü başlatır.
     * Uygulamanın main() veya ana pencerenin constructor'ından çağrılır.
     */
    public void start() {
        // Açılış kontrolü: 2 sn gecikme
        scheduler.schedule(new Runnable() {
            @Override
            public void run() {
                performCheck();
            }
        }, STARTUP_DELAY_SECONDS, TimeUnit.SECONDS);

        // Periyodik kontrol
        long intervalSeconds = CHECK_INTERVAL_HOURS * 3600;
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                performCheck();
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Anlık, manuel kontrol.
     * "Yardım → Güncellemeleri Kontrol Et" menüsü için kullanın.
     * "Bu sürümü atla" filtresini sıfırlar.
     */
    public void checkNow() {
        prefs.remove(PREF_SKIPPED_VERSION);
        scheduler.submit(new Runnable() {
            @Override
            public void run() {
                performCheck();
            }
        });
    }

    /** Servisi durdurur. Uygulama kapanırken çağrılabilir. */
    public void stop() {
        scheduler.shutdownNow();
    }

    // ─── İç İşleyiş ──────────────────────────────────────────────────────────

    private void performCheck() {
        updateManager.checkForUpdates(
                new Consumer<UpdateManifest>() {
                    @Override
                    public void accept(final UpdateManifest manifest) {
                        // Kullanıcı bu sürümü atlattı mı?
                        String skipped = prefs.get(PREF_SKIPPED_VERSION, "");
                        if (skipped.equals(manifest.getVersion())) return;

                        // Diyaloğu Swing thread'inde aç
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
                        System.out.println("[UpdateChecker] Uygulama güncel.");
                    }
                }
        );
    }

    private void showUpdateDialog(UpdateManifest manifest) {
//        UpdateDialog dialog = new UpdateDialog(ownerFrame, manifest, updateManager);
//
//        dialog.setOnSkipVersion(new Consumer<String>() {
//            @Override
//            public void accept(String version) {
//                prefs.put(PREF_SKIPPED_VERSION, version);
//            }
//        });
//
//        dialog.setVisible(true);
    }

    // ─── System Tray Bildirimi (isteğe bağlı) ────────────────────────────────

    /**
     * System tray desteği varsa bildirim gösterir.
     * Uygulamanızda TrayIcon kuruluysa bu metodu güncelleme bulunduğunda çağırın.
     */
    public static void showTrayNotification(String title, String message,
                                            TrayIcon.MessageType type) {
        if (!SystemTray.isSupported()) return;
        try {
            TrayIcon[] icons = SystemTray.getSystemTray().getTrayIcons();
            if (icons.length > 0) {
                icons[0].displayMessage(title, message, type);
            }
        } catch (Exception ignored) {}
    }
}