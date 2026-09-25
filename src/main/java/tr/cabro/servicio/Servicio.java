package tr.cabro.servicio;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.application.ApplicationBootstrap;
import tr.cabro.servicio.application.MainUI;
import tr.cabro.servicio.application.component.AppSplashScreen;
import tr.cabro.servicio.application.listeners.InactivityMonitor;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.database.*;
import tr.cabro.servicio.model.enums.BackupMode;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.updater.UpdateService;

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

    @Getter private final File dataFolder;
    @Getter private MainUI frame;
    @Getter private static final Logger logger = LoggerFactory.getLogger(Servicio.class);
    @Getter private static InactivityMonitor inactivityMonitor;
    @Getter private final UpdateService updateService;

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

        // ── Veri klasörü ──────────────────────────────────────────────────────
        splash.updateMessage("Klasör yapıları kontrol ediliyor...");
        this.dataFolder = new File(baseFolder, ".servicio");
        if (!this.dataFolder.exists() && this.dataFolder.mkdirs()) {
            logger.info("Veri klasörü oluşturuldu: {}", this.dataFolder.getAbsolutePath());
        }

        // ── Ayarlar ───────────────────────────────────────────────────────────
        // Her şeyden önce yüklenir: güncelleme kontrolü "atlanan sürüm"ü, tema kurulumu Look&Feel'i,
        // yedekleme politikası da modunu buradan okuyor.
        splash.updateMessage("Ayarlar yükleniyor...");
        AppSettings.init(this.dataFolder);
        AppLocale.init();

        // ── Güncelleme kontrolü (bloklamaz) ───────────────────────────────────
        updateService = new UpdateService();
        updateService.checkOnSplash(splash);

        // ── Veritabanı ────────────────────────────────────────────────────────
        splash.updateMessage("Veritabanı bağlantısı kuruluyor...");
        DatabaseManager.initialize();

        // ── Servisler ─────────────────────────────────────────────────────────
        splash.updateMessage("Servis yöneticileri başlatılıyor...");
        ServiceManager.initialize();

        // ── Aktivite İzleyici ─────────────────────────────────────────────────
        splash.updateMessage("Arka plan işlemleri hazırlanıyor...");
        initInactivityMonitor();
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
        DeviceAccessPurgeScheduler.start();

        EventQueue.invokeLater(() -> {
            // Look & Feel kurulumu
            ApplicationBootstrap.setupLookAndFeel();

            // Ana pencereyi aç
            splash.updateMessage("Arayüz hazırlanıyor...");
            frame = new MainUI();
            frame.setVisible(true);

            // Splash kapat
            splash.dispose();

            // Periyodik sessiz güncelleme denetimi; sonuç yalnızca alt çubukta gösterilir.
            updateService.startAutomaticChecks();
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
                AppSettings.get().getUi().setFullSize(frame.getExtendedState() == JFrame.MAXIMIZED_BOTH);
                frame.dispose();
            }

            AppSettings.save();

            if (inactivityMonitor != null) inactivityMonitor.stop();
            if (updateService     != null) updateService.stop();
            BackupScheduler.stop();
            DeviceAccessPurgeScheduler.stop();

            runBackupIfNeeded(BackupMode.ON_EXIT, BackupMode.ON_START_AND_EXIT);

            DatabaseManager.shutdown();

            // İndirilmiş ama "Sonra" denmiş güncelleme: yeniden açmadan kur, bir sonraki açılışta hazır olsun.
            if (updateService != null) updateService.installOnExitIfReady();

            logger.info("Güle güle!");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Kapatma sırasında kritik hata", e);
            System.exit(1);
        }
    }


    private void initInactivityMonitor() {
        Action lockAction = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FormManager.lockForInactivity();
            }
        };
        inactivityMonitor = new InactivityMonitor(lockAction);
        inactivityMonitor.setTimeout(ServiceManager.getAppSettingService().getAutoLockMinutes());
    }

    private void runBackupIfNeeded(BackupMode... modes) {
        BackupMode current = AppSettings.get().getBackup().getMode();
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
