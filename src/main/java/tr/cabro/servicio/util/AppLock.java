package tr.cabro.servicio.util;

import tr.cabro.servicio.Servicio;
import javax.swing.*;
import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

public class AppLock {

    private static FileChannel channel;
    private static FileLock lock;

    public static boolean acquireLock() {
        try {
            // Kullanıcının ana dizinine (C:\Users\Kullanici) gizli bir kilit dosyası oluşturulur
            File file = new File(System.getProperty("user.home"), ".servicio_app.lock");

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
            Servicio.getLogger().error("Uygulama kilidi oluşturulurken hata: ", e);
            return false;
        }
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
            Servicio.getLogger().error("Kilit serbest bırakılırken hata: ", e);
        }
    }
}