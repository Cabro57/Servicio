package tr.cabro.servicio.settings;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Variable;
import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.BackupMode;


@Getter
@Setter
public class Settings extends OkaeriConfig {

    private String path = "";
    private boolean full_size = false;
    private boolean deviceMigrated = false;
    private boolean skipExitConfirmation = false;

    private BackupSettings backup = new BackupSettings();

    @Getter @Setter
    public static class BackupSettings extends OkaeriConfig {

        private String path = Servicio.getInstance().getDataFolder().getAbsolutePath() + "\\backups";
        private BackupMode mode = BackupMode.ON_START;
        private int interval = 15;

    }

    private PinConfig pin = new PinConfig();

    @Getter @Setter
    public static class PinConfig extends OkaeriConfig {

        private int pin = 1234;
        private int timeout = 1;
    }

    @Variable("barcode_prefix")
    private String barcodePrefix = "123456";

    private Database database = new Database();

    @Getter @Setter
    public static class Database extends OkaeriConfig {
        private int MaximumPoolSize = 200;
    }
}
