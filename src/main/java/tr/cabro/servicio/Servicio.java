package tr.cabro.servicio;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.util.FontUtils;
import com.formdev.flatlaf.util.UIScale;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.json.gson.JsonGsonConfigurer;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import raven.modal.ModalDialog;
import raven.modal.system.FormManager;
import raven.modal.utils.DemoPreferences;
import tr.cabro.servicio.application.MainUI;
import tr.cabro.servicio.application.component.AppSplashScreen;
import tr.cabro.servicio.application.listeners.InactivityMonitor;
import tr.cabro.servicio.database.*;
import tr.cabro.servicio.model.enums.BackupMode;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.settings.Settings;
import tr.cabro.servicio.util.AppLock;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.InputStream;
import java.util.Properties;

public final class Servicio {

    @Getter private static Servicio instance;
    @Getter private static Settings settings;
    @Getter private static DeviceSettings deviceSettings;
    @Getter private final File dataFolder;
    @Getter private MainUI frame;
    @Getter private static final Logger logger = LoggerFactory.getLogger(Servicio.class);
    @Getter private static InactivityMonitor inactivityMonitor;

    private boolean running = false;
    private String appVersion;

    // CONSTRUCTOR (Yapıcı Metot): Artık Splash Screen nesnesini parametre olarak alıyor
    public Servicio(File baseFolder, AppSplashScreen splash) {
        if (!LauncherAccessContext.isAllowed()) {
            throw new SecurityException("Erişim reddedildi: Sadece Launcher yetkilidir.");
        }

        instance = this;

        splash.updateProgress(10, "Klasör yapıları kontrol ediliyor...");
        this.dataFolder = new File(baseFolder, ".servicio");
        if (!this.dataFolder.exists() && this.dataFolder.mkdirs()) {
            logger.info("Veri klasörü oluşturuldu: {}", this.dataFolder.getAbsolutePath());
        }

        splash.updateProgress(25, "Ayarlar yükleniyor...");
        initSettings();

        splash.updateProgress(45, "Veritabanı bağlantısı kuruluyor...");
        DatabaseManager.initialize();

        splash.updateProgress(65, "Servis yöneticileri başlatılıyor...");
        ServiceManager.initialize();

        splash.updateProgress(80, "Arka plan işlemleri hazırlanıyor...");
        Action sifreEkraniniGetir = new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                FormManager.logout();
                ModalDialog.closeAllModal();
                inactivityMonitor.start();
            }
        };

        inactivityMonitor = new InactivityMonitor(sifreEkraniniGetir);
        int timeout = settings.getAutoLockTimeoutMinutes();
        inactivityMonitor.setTimeout(timeout);

        DemoPreferences.init();
    }

    public void run(AppSplashScreen splash) {
        if (running) return;
        running = true;
        logger.info("Servicio başlatılıyor (v{})...", getAppVersion());

        splash.updateProgress(90, "Yedekleme politikaları kontrol ediliyor...");
        runBackupIfNeeded(BackupMode.ON_START, BackupMode.ON_START_AND_EXIT);
        BackupScheduler.start();

        splash.updateProgress(95, "Kullanıcı arayüzü çiziliyor...");

        // Arayüz işlemlerinin her zaman EDT (Event Dispatch Thread) üzerinde yapılması şarttır.
        EventQueue.invokeLater(() -> {
            setupUI();
            splash.updateProgress(100, "Tamamlandı!");

            // Ana ekranı aç ve Splash'i yok et
            launchMainUI();
            splash.dispose();
        });
    }

    private void initSettings() {
        File configFile = new File(getDataFolder(), "config.json");
        File deviceFile = new File(getDataFolder(), "device_config.json");

        deviceSettings = ConfigManager.create(DeviceSettings.class, cfg -> {
            cfg.withConfigurer(new JsonGsonConfigurer())
                    .withBindFile(deviceFile)
                    .withRemoveOrphans(true)
                    .saveDefaults()
                    .load(true);
        });

        settings = ConfigManager.create(Settings.class, cfg -> {
            cfg.withConfigurer(new JsonGsonConfigurer())
                    .withBindFile(configFile)
                    .withRemoveOrphans(true)
                    .saveDefaults()
                    .load(true);
        });
    }

    private void setupUI() {
        FlatRobotoFont.install();
        FlatLaf.registerCustomDefaultsSource("themes");
        DemoPreferences.setupLaf();
        int scaledFontSize = UIScale.scale(12);
        UIManager.put("defaultFont", FontUtils.getCompositeFont(FlatRobotoFont.FAMILY, Font.PLAIN, scaledFontSize));
    }

    private void launchMainUI() {
        frame = new MainUI();
        frame.setVisible(true);
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

    public void shutdown() {
        try {
            logger.info("Kapatma prosedürü başlatıldı...");

            // 1. UI Durumunu Kaydet
            if (frame != null) {
                settings.setFull_size(frame.getExtendedState() == JFrame.MAXIMIZED_BOTH);
                frame.dispose(); // Pencereyi yok et
            }

            // 2. Ayarları Diske Yaz
            settings.save();
            deviceSettings.save();

            // 3. Arka plan işlemlerini durdur
            if (inactivityMonitor != null) inactivityMonitor.stop();
            BackupScheduler.stop();

            // 4. Kapanış yedeği al
            runBackupIfNeeded(BackupMode.ON_EXIT, BackupMode.ON_START_AND_EXIT);

            // 5. Veritabanını kapat
            DatabaseManager.shutdown();

            logger.info("Güle güle!");
            System.exit(0);

        } catch (Exception e) {
            logger.error("Kapatma sırasında kritik hata", e);
            System.exit(1);
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
        // 1. Kilit kontrolü (Uygulamanın 2 kere açılmasını önle)
        if (!AppLock.acquireLock()) {
            JOptionPane.showMessageDialog(null,
                    "Servicio uygulaması şu anda zaten çalışıyor!\nLütfen açık olan pencereyi kontrol edin.",
                    "Sistem Uyarısı", JOptionPane.WARNING_MESSAGE);
            System.exit(0);
        }
        LauncherAccessContext.allow();

        // 2. Açılış Ekranını (Splash Screen) hemen göster
        AppSplashScreen splash = new AppSplashScreen();
        SwingUtilities.invokeLater(() -> splash.setVisible(true));

        // 3. Ağır işlemleri Arka Plana (Background Thread) at
        // Böylece işlemler yapılırken Splash Screen'deki bar akıcı şekilde ilerler.
        new Thread(() -> {
            try {
                Servicio app = new Servicio(new File("."), splash);
                app.run(splash);
            } catch (Exception e) {
                logger.error("Başlatma sırasında kritik hata!", e);
                JOptionPane.showMessageDialog(null, "Uygulama başlatılamadı:\n" + e.getMessage(), "Hata", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        }).start();
    }
}
