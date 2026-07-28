package tr.cabro.servicio.settings;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Variable;
import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.enums.BackupMode;

import java.io.File;


@Getter
@Setter
public class Settings extends OkaeriConfig {

    private boolean full_size = false;
    private boolean skipExitConfirmation = false;

    private int autoLockTimeoutMinutes = 5;

    // Cihaz ekran kilidi erişim kodu (PIN/şifre/desen), servis teslim edildikten kaç saat sonra
    // veritabanından kalıcı olarak silinir (bkz. DeviceAccessCredentialService.purgeExpired)
    private int deviceAccessPurgeHours = 24;

    @Variable("barcode_prefix")
    private String barcodePrefix = "123456";

    // Liste ekranlarında sayfa başına gösterilen kayıt sayısı (uygulama yeniden başlatılsa da korunur)
    private int workOrderPageSize = 10;
    private int partPageSize = 10;
    private int customerPageSize = 10;
    private int devicePageSize = 10;
    private int supplierPageSize = 10;

    private BackupSettings backup = new BackupSettings();

    @Getter @Setter
    public static class BackupSettings extends OkaeriConfig {

        private String path = new File(Servicio.getInstance().getDataFolder(), "backups").getAbsolutePath();
        private BackupMode mode = BackupMode.ON_START;
        private int interval = 15;


    }
}
