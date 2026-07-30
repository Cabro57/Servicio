package tr.cabro.servicio.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

/**
 * Uygulamanın aynı anda yalnızca bir örneğinin çalışmasını sağlayan dosya kilidi.
 * <p>
 * Kilit dosyası veri klasörünün ({@code .servicio}) içinde tutulur — kullanıcının ana dizininde
 * değil. Böylece kilidin kapsamı "bu veriyi kullanan uygulama" olur: portable bir kopya ile
 * kurulu bir sürüm farklı veri klasörleri kullandığından artık birbirini engellemez, aynı veri
 * klasörünü açan iki örnek ise doğru şekilde engellenir.
 * <p>
 * {@code Servicio} nesnesi henüz oluşmadan (bkz. {@code ApplicationBootstrap.launch}) çağrıldığı
 * için yolu {@link DataDirResolver} üzerinden kendisi çözer ve kendi logger'ını kullanır.
 */
public class AppLock {

    private static final Logger log = LoggerFactory.getLogger(AppLock.class);

    private static FileChannel channel;
    private static FileLock lock;

    public static boolean acquireLock() {
        try {
            File dataFolder = new File(resolveBaseFolder(), ".servicio");
            if (!dataFolder.exists() && !dataFolder.mkdirs()) {
                log.warn("Veri klasörü oluşturulamadı, kilit dosyası açılamayabilir: {}", dataFolder);
            }

            File file = new File(dataFolder, "app.lock");

            // Dosyaya erişim kanalı açılır
            channel = new RandomAccessFile(file, "rw").getChannel();

            // Dosyayı işletim sistemi seviyesinde kilitlemeyi dene
            lock = channel.tryLock();

            if (lock == null) {
                // Eğer lock null dönerse, başka bir uygulama örneği bu dosyayı zaten kilitlemiş demektir.
                channel.close();
                return false;
            }

            // Uygulama normal yollarla kapatıldığında kilidi temizlemek için bir tetikleyici ekle
            Runtime.getRuntime().addShutdownHook(new Thread(AppLock::releaseLock));
            return true;

        } catch (Exception e) {
            log.error("Uygulama kilidi oluşturulurken hata: ", e);
            return false;
        }
    }

    /**
     * Veri klasörünün kökü. Launcher bunu zaten hesaplayıp sistem özelliğine yazmıştır;
     * doğrudan {@code Servicio.main} ile çalıştırılan geliştirme senaryosunda yeniden hesaplanır.
     */
    private static File resolveBaseFolder() {
        String resolved = System.getProperty("servicio.baseDir");
        return (resolved != null) ? new File(resolved) : DataDirResolver.resolveBaseFolder();
    }

    private static void releaseLock() {
        try {
            if (lock != null) {
                lock.release();
            }
            if (channel != null) {
                channel.close();
            }
        } catch (Exception e) {
            log.error("Kilit serbest bırakılırken hata: ", e);
        }
    }
}
