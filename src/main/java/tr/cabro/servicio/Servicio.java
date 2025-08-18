package tr.cabro.servicio;

import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.fonts.roboto.FlatRobotoFont;
import com.formdev.flatlaf.util.FontUtils;
import com.formdev.flatlaf.util.UIScale;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.exception.OkaeriException;
import eu.okaeri.configs.json.gson.JsonGsonConfigurer;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.application.ui.MainUI;
import tr.cabro.servicio.database.BackupScheduler;
import tr.cabro.servicio.database.DatabaseInitializer;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.DatabaseType;
import tr.cabro.servicio.model.BackupMode;
import tr.cabro.servicio.settings.Settings;
import tr.cabro.servicio.settings.Theme;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.sql.SQLException;

public final class Servicio {

    @Getter
    private final File dataFolder;

    @Getter
    private static Servicio instance;

    @Getter
    private static Settings settings;

    @Getter
    private MainUI frame;

    @Getter
    private static final Logger logger = LoggerFactory.getLogger(Servicio.class);

    private boolean isBeingRun = false;
    private String appVersion;

    public Servicio(File dataFolder) {
        if (!LauncherAccessContext.isAllowed()) {
            throw new SecurityException("Bu uygulama sadece launcher tarafından çalıştırılabilir.");
        }

        instance = this;
        this.dataFolder = new File(dataFolder, ".servicio");
        if (!this.dataFolder.exists()) {
            if (this.dataFolder.mkdirs()) logger.info("Dosya oluşturuldu!");
        }

        onLoad();
    }

    public void run() {
        if (!isBeingRun) {
            logger.info("Uygulama Çalışıtırılıyor!");
            isBeingRun = true;
            onRun();
        }
    }

    public void onLoad() {
        try {
            setupSettingsFile();
            DatabaseManager.connect(DatabaseType.SQLite);

            DatabaseInitializer.migrate();
        } catch (SQLException | OkaeriException e) {
            logger.error(e.toString());
        }
    }

    private void onRun() {
        BackupMode mode = settings.getBackup().getMode();
        if (mode == BackupMode.ON_START || mode == BackupMode.ON_START_AND_EXIT) {
            DatabaseManager.backup();
        }

        BackupScheduler.start();

        FlatLaf.registerCustomDefaultsSource("themes");
        Theme.apply(Theme.selected());

        UIScale.getUserScaleFactor();

        int scaledFontSize = UIScale.scale(12);

        Font newFont = FontUtils.getCompositeFont(
                FlatRobotoFont.FAMILY,
                Font.PLAIN,
                scaledFontSize
        );

        UIManager.put("defaultFont", newFont);

        EventQueue.invokeLater(() -> {
            frame = new MainUI();
            frame.setVisible(true);
        });

    }

    private void setupSettingsFile() {
        settings = ConfigManager.create(Settings.class, (it) -> {
            it.withConfigurer(new JsonGsonConfigurer());
            it.withBindFile(new File(getDataFolder(), "config.json"));
            it.withRemoveOrphans(true);
            it.saveDefaults();
            it.load(true);
        });
    }

    public String getAppVersion() {
        if (appVersion == null) {
            try {
                java.util.Properties props = new java.util.Properties();
                props.load(Servicio.class.getResourceAsStream("/version.properties"));
                appVersion = props.getProperty("version", "v0.0.0");
            } catch (Exception e) {
                appVersion = "v0.0.0";
            }
        }
        return appVersion;
    }

    public void disable() {
        settings.setFull_size(frame.getExtendedState() == JFrame.MAXIMIZED_BOTH);
        settings.save();

        frame.closeApplication();

        try {
            BackupMode mode = settings.getBackup().getMode();
            if (mode == BackupMode.ON_EXIT || mode == BackupMode.ON_START_AND_EXIT) {
                DatabaseManager.backup();
            }
            BackupScheduler.stop();
            DatabaseManager.disconnect();
        } catch (SQLException e) {
            logger.error(e.toString());
        }
    }

    public static void main(String[] args) {

        LauncherAccessContext.allow();

        Servicio servicio = new Servicio(new File("."));
        servicio.run();
    }
}

