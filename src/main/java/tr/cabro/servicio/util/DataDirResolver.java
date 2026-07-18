package tr.cabro.servicio.util;

import java.io.File;

/**
 * Veri klasörünün ({@code .servicio/}) oluşturulacağı kök dizini belirler.
 * <p>
 * ÖNEMLİ: Bu sınıf kasıtlı olarak hiçbir logger (slf4j) kullanmaz — {@link tr.cabro.servicio.Launcher}
 * tarafından, logback henüz konfigüre edilmeden (ilk log satırından ÖNCE) çağrılmalıdır.
 * Aksi halde logback kendi varsayılan göreli yolunu ("./.servicio/logs") kullanarak bu sınıfın
 * "taşınabilir mi?" kontrolünü yanıltır (log dizini erken oluşur, her zaman "taşınabilir" sanılır).
 */
public final class DataDirResolver {

    private DataDirResolver() {}

    /**
     * Geriye dönük uyumluluk: çalıştırılabilir dosyanın (jar/exe) bulunduğu dizinde
     * zaten bir {@code .servicio} klasörü varsa (mevcut kurulumlar, portable app-image,
     * geliştirme ortamı) onu kullanmaya devam eder.
     * <p>
     * Yeni kurulumlarda (ör. MSI/EXE ile {@code C:\Program Files\Servicio\} altına
     * kurulum) çalışma dizini standart kullanıcılar için yazılamaz olabilir — bu
     * durumda işletim sistemine özgü, her zaman yazılabilir kullanıcı veri dizini
     * kullanılır: dataFolder ({@code .servicio}) doğrudan bu dizinin altına kurulur
     * (Windows: {@code %LOCALAPPDATA%\.servicio}, Linux/macOS:
     * {@code $XDG_DATA_HOME/.servicio} ya da {@code ~/.local/share/.servicio}) —
     * araya ekstra bir "Servicio" klasörü konmaz.
     */
    public static File resolveBaseFolder() {
        File portable = new File(".");
        File portableData = new File(portable, ".servicio");
        if (portableData.isDirectory()) {
            return portable;
        }

        File userBase;
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            String localAppData = System.getenv("LOCALAPPDATA");
            userBase = (localAppData != null)
                    ? new File(localAppData)
                    : new File(System.getProperty("user.home"), "AppData\\Local");
        } else {
            String xdgDataHome = System.getenv("XDG_DATA_HOME");
            userBase = (xdgDataHome != null)
                    ? new File(xdgDataHome)
                    : new File(System.getProperty("user.home"), ".local/share");
        }

        if (userBase.isDirectory() || userBase.mkdirs()) {
            return userBase;
        }
        return portable; // son çare: eski davranış (yazılamazsa yine hata verecek)
    }
}
