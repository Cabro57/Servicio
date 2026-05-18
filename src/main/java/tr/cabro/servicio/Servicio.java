package tr.cabro.servicio;

import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.json.gson.JsonGsonConfigurer;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.modal.ModalDialog;
import tr.cabro.servicio.application.ApplicationBootstrap;
import tr.cabro.servicio.application.MainUI;
import tr.cabro.servicio.application.component.AppSplashScreen;
import tr.cabro.servicio.application.listeners.InactivityMonitor;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.DemoPreferences;
import tr.cabro.servicio.database.*;
import tr.cabro.servicio.model.enums.BackupMode;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.Settings;
import tr.cabro.servicio.updater.UpdateChecker;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;

/**
 * Servicio uygulamasının çekirdek sınıfı.
 * <p>
 * Sorumluluklar:
 *   - Bağımlılıkları başlat (DB, Settings, Services)
 *   - Yaşam döngüsünü yönet (run, shutdown)
 *   - Güncelleme kontrolünü başlat (non-blocking)
 * <p>
 * UI mantığı → ApplicationBootstrap
 * Başlatma akışı → ApplicationBootstrap.launch()
 */
public final class Servicio {

    @Getter private static Servicio instance;

    @Getter private static Settings settings;
    @Getter private final File dataFolder;
    @Getter private MainUI frame;
    @Getter private static final Logger logger = LoggerFactory.getLogger(Servicio.class);
    @Getter private static InactivityMonitor inactivityMonitor;
    @Getter private final UpdateChecker updateChecker;

    private boolean running = false;
    private String appVersion = null;

    /**
     * Tüm ağır init işlemlerini yapar.
     * Arka plan thread'inde çağrılır (EDT değil).
     *
     * @param baseFolder Uygulama kök dizini.
     * @param splash     Mesaj güncellemesi için splash ekranı.
     */
    public Servicio(File baseFolder, AppSplashScreen splash) {
        instance = this;

        splash.updateMessage("Güncelleme kontrol ediliyor...");
        updateChecker = new UpdateChecker();
        updateChecker.checkOnSplash(splash);

        // ── Veri klasörü ──────────────────────────────────────────────────────
        splash.updateMessage("Klasör yapıları kontrol ediliyor...");
        this.dataFolder = new File(baseFolder, ".servicio");
        if (!this.dataFolder.exists() && this.dataFolder.mkdirs()) {
            logger.info("Veri klasörü oluşturuldu: {}", this.dataFolder.getAbsolutePath());
        }

        // ── Ayarlar ───────────────────────────────────────────────────────────
        splash.updateMessage("Ayarlar yükleniyor...");
        initSettings();

        // ── Veritabanı ────────────────────────────────────────────────────────
        splash.updateMessage("Veritabanı bağlantısı kuruluyor...");
        DatabaseManager.initialize();

        // ── Servisler ─────────────────────────────────────────────────────────
        splash.updateMessage("Servis yöneticileri başlatılıyor...");
        ServiceManager.initialize();

        // ── Aktivite İzleyici ─────────────────────────────────────────────────
        splash.updateMessage("Arka plan işlemleri hazırlanıyor...");
        initInactivityMonitor();

        DemoPreferences.init();
    }

    /**
     * Uygulamayı çalıştırır; UI'yi EDT üzerinde açar.
     * Constructor tamamlandıktan hemen sonra çağrılır.
     */
    public void run(AppSplashScreen splash) {
        if (running) return;
        running = true;
        logger.info("Servicio başlatılıyor (v{})...", getAppVersion());

        splash.updateMessage("Yedekleme politikaları kontrol ediliyor...");
        runBackupIfNeeded(BackupMode.ON_START, BackupMode.ON_START_AND_EXIT);
        BackupScheduler.start();

        EventQueue.invokeLater(() -> {
            // Look & Feel kurulumu
            ApplicationBootstrap.setupLookAndFeel();

            // Ana pencereyi aç
            splash.updateMessage("Arayüz hazırlanıyor...");
            frame = new MainUI();
            frame.setVisible(true);

            // Splash kapat
            splash.dispose();

            // Periyodik güncelleme kontrolünü başlat
            updateChecker.startPeriodicCheck(frame);

            // Splash kontrolünden kalan güncelleme var mı?
            // hasPendingUpdate() true ise MainUI kendi içinde
            // Servicio.getInstance().getUpdateChecker().getPendingUpdate()
            // ile manifesti alır ve diyaloğu gösterir.
            if (updateChecker.hasPendingUpdate()) {
                logger.info("Splash'ten bekleyen güncelleme var: v{}",
                updateChecker.getPendingUpdate().getVersion());
                // MainUI bu bilgiyle istediği UI'yi gösterebilir.
                // Örnek: frame.showUpdateNotification();
            }
        });
    }

    /**
     * Uygulamayı güvenli şekilde kapatır.
     * Ayarları kaydeder, yedek alır, DB'yi kapatır.
     */
    public void shutdown() {
        try {
            logger.info("Kapatma prosedürü başlatıldı...");

            if (frame != null) {
                settings.setFull_size(frame.getExtendedState() == JFrame.MAXIMIZED_BOTH);
                frame.dispose();
            }

            settings.save();

            if (inactivityMonitor != null) inactivityMonitor.stop();
            if (updateChecker     != null) updateChecker.stop();
            BackupScheduler.stop();

            runBackupIfNeeded(BackupMode.ON_EXIT, BackupMode.ON_START_AND_EXIT);

            DatabaseManager.shutdown();

            logger.info("Güle güle!");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Kapatma sırasında kritik hata", e);
            System.exit(1);
        }
    }


    private void initSettings() {
        File configFile = new File(getDataFolder(), "config.json");

        settings = ConfigManager.create(Settings.class, cfg -> {
            cfg.withConfigurer(new JsonGsonConfigurer())
                    .withBindFile(configFile)
                    .withRemoveOrphans(true)
                    .saveDefaults()
                    .load(true);
        });
    }

    private void initInactivityMonitor() {
        Action lockAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FormManager.lock();
                ModalDialog.closeAllModalImmediately();
            }
        };
        inactivityMonitor = new InactivityMonitor(lockAction);
        inactivityMonitor.setTimeout(settings.getAutoLockTimeoutMinutes());
    }

    private void runBackupIfNeeded(BackupMode... modes) {
        BackupMode current = settings.getBackup().getMode();
        for (BackupMode m : modes) {
            if (current == m) {
                DatabaseManager.backup();
                break;
            }
        }
    }

    public String getAppVersion() {
        if (appVersion != null) return appVersion;

        try (InputStream is = Servicio.class.getResourceAsStream("/version.properties")) {
            Properties props = new Properties();
            props.load(is);
            appVersion = props.getProperty("version", "0.0.0");
        } catch (Exception e) {
            appVersion = "dev-build";
        }
        return appVersion;
    }

    public static void main(String[] args) {
        ApplicationBootstrap.launch();
    }
}
