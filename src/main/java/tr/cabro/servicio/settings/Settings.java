package tr.cabro.servicio.settings;

import eu.okaeri.configs.OkaeriConfig;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.BackupMode;
import tr.cabro.servicio.util.barcode.BarcodeConfig;

import java.util.*;

@Getter
@Setter
public class Settings extends OkaeriConfig {

    private Template template = new Template();
    private String path = "";
    private boolean full_size = false;
    private boolean deviceMigrated = false;
    private boolean confirmExitDialog = false;

    private BackupSettings backup = new BackupSettings();

    @Getter @Setter
    public static class BackupSettings extends OkaeriConfig {

        private String path = Servicio.getInstance().getDataFolder().getAbsolutePath() + "\\backups";
        private BackupMode mode = BackupMode.ON_START;
        private int interval = 15;

    }

    private PinConfig pinConfig = new PinConfig();

    @Getter @Setter
    public static class PinConfig extends OkaeriConfig {

        private int pin = 1234;
        private int timeout = 1;
    }

    private BarcodeConfig barcode = new BarcodeConfig();

    @Getter @Setter
    public static class Template extends OkaeriConfig {
        private String selected_theme = "Light";
        private Map<String, String> themes = new HashMap<>();

        private Template() {
            themes.put("Light", "com.formdev.flatlaf.FlatLightLaf");
            themes.put("Dark", "com.formdev.flatlaf.FlatDarkLaf");
            themes.put("IntelliJ", "com.formdev.flatlaf.FlatIntelliJLaf");
            themes.put("Darcula", "com.formdev.flatlaf.FlatDarculaLaf");
            themes.put("macOS Light v3", "com.formdev.flatlaf.themes.FlatMacLightLaf");
            themes.put("macOS Dark v3", "com.formdev.flatlaf.themes.FlatMacDarkLaf");
        }
    }

    private Database database = new Database();

    @Getter @Setter
    public static class Database extends OkaeriConfig {
        private int MaximumPoolSize = 200;
    }
}
