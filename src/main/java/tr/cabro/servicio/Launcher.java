package tr.cabro.servicio;

import tr.cabro.servicio.util.DataDirResolver;

import java.io.File;

/**
 * JAR'ın gerçek giriş noktası (pom.xml: {@code mainClass}).
 * <p>
 * ÖNEMLİ: Bu sınıf kasıtlı olarak hiçbir logger (slf4j) kullanmaz ve {@link Servicio} sınıfını
 * yalnızca {@link #main} içinde, sistem özelliği ayarlandıktan SONRA referans eder. Java bir sınıfı
 * ilk kullanıldığı anda (statik alanlarıyla birlikte) initialize eder — {@code Servicio.logger} gibi
 * statik logger alanları burada erkenden tetiklenirse logback, henüz {@code servicio.baseDir} özelliği
 * yokken varsayılan göreli yolla ("./.servicio/logs") konfigüre olur ve bu da {@link DataDirResolver}'ın
 * "taşınabilir kurulum mu?" kontrolünü yanıltır.
 */
public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        File baseFolder = DataDirResolver.resolveBaseFolder();
        System.setProperty("servicio.baseDir", baseFolder.getAbsolutePath());
        Servicio.main(args);
    }
}
