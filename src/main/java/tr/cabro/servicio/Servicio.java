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
import raven.modal.utils.DemoPreferences;
import tr.cabro.servicio.application.listeners.InactivityListener;
import tr.cabro.servicio.application.MainUI;
import tr.cabro.servicio.database.*;
import tr.cabro.servicio.model.BackupMode;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.DeviceSettings;
import tr.cabro.servicio.settings.Settings;

import javax.swing.*;
import java.awt.*;
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
    @Getter private static InactivityListener inactivityListener;

    private boolean running = false;
    private String appVersion;

    public Servicio(File baseFolder) {
        if (!LauncherAccessContext.isAllowed()) {
            throw new SecurityException("Erişim reddedildi: Sadece Launcher yetkilidir.");
        }

        instance = this;

        this.dataFolder = new File(baseFolder, ".servicio");
        if (!this.dataFolder.exists() && this.dataFolder.mkdirs()) {
            logger.info("Veri klasörü oluşturuldu: {}", this.dataFolder.getAbsolutePath());
        }

        initSettings();
        DatabaseManager.initialize();
        ServiceManager.initialize();

        DemoPreferences.init();

        inactivityListener = new InactivityListener();
    }

    public void run() {
        if (running) return;
        running = true;
        logger.info("Servicio başlatılıyor (v{})...", getAppVersion());

        // Açılış yedeği
        runBackupIfNeeded(BackupMode.ON_START, BackupMode.ON_START_AND_EXIT);

        // Zamanlayıcıyı başlat
        BackupScheduler.start();

        // UI Hazırlığı
        setupUI();

        // UI'ı EDT (Event Dispatch Thread) üzerinde başlat
        EventQueue.invokeLater(this::launchMainUI);
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
            if (inactivityListener != null) inactivityListener.stop();
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
        LauncherAccessContext.allow();
        new Servicio(new File(".")).run();
    }
}
