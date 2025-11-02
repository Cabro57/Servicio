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
            throw new SecurityException("Bu uygulama sadece launcher tarafından çalıştırılabilir.");
        }

        instance = this;

        this.dataFolder = new File(baseFolder, ".servicio");
        if (!this.dataFolder.exists() && this.dataFolder.mkdirs()) {
            logger.info("Data folder created at {}", this.dataFolder.getAbsolutePath());
        }

        initSettings();
        initDatabase();

        inactivityListener = new InactivityListener();
    }

    public void run() {
        if (running) return;
        running = true;
        logger.info("Uygulama Çalıştırılıyor!");

        runBackupIfNeeded(BackupMode.ON_START, BackupMode.ON_START_AND_EXIT);
        BackupScheduler.start();
        DemoPreferences.init();
        setupUI();
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

    private void initDatabase() {
        try {
            DatabaseConfig.init(DatabaseType.SQLite);
            DatabaseInitializer.migrate();
        } catch (Exception e) {
            logger.error("Veritabanı başlatma hatası", e);
        }
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
        BackupMode mode = settings.getBackup().getMode();
        for (BackupMode m : modes) {
            if (mode == m) {
                DatabaseManager.backup();
                break;
            }
        }
    }

    public void disable() {
        try {
            settings.setFull_size(frame.getExtendedState() == JFrame.MAXIMIZED_BOTH);
            settings.save();
            deviceSettings.save();
            inactivityListener.stop();

            frame.closeApplication();
            runBackupIfNeeded(BackupMode.ON_EXIT, BackupMode.ON_START_AND_EXIT);

            BackupScheduler.stop();
            DatabaseConfig.close();
        } catch (Exception e) {
            logger.error("Kapatma sırasında hata", e);
        }
    }

    public String getAppVersion() {
        if (appVersion != null) return appVersion;

        try (InputStream is = Servicio.class.getResourceAsStream("/version.properties")) {
            Properties props = new Properties();
            props.load(is);
            appVersion = props.getProperty("version", "v0.0.0");
        } catch (Exception e) {
            appVersion = "v0.0.0";
        }
        return appVersion;
    }

    public static void main(String[] args) {
        LauncherAccessContext.allow();
        new Servicio(new File(".")).run();
    }
}
