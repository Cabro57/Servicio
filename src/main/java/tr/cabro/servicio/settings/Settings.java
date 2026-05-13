package tr.cabro.servicio.settings;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Variable;
import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.enums.BackupMode;


@Getter
@Setter
public class Settings extends OkaeriConfig {

    private boolean full_size = false;
    private boolean skipExitConfirmation = false;

    private int autoLockTimeoutMinutes = 5;

    @Variable("barcode_prefix")
    private String barcodePrefix = "123456";

    private BackupSettings backup = new BackupSettings();

    @Getter @Setter
    public static class BackupSettings extends OkaeriConfig {

        private String path = Servicio.getInstance().getDataFolder().getAbsolutePath() + "\\backups";
        private BackupMode mode = BackupMode.ON_START;
        private int interval = 15;


    }
}
