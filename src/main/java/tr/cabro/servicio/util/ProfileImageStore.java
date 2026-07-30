package tr.cabro.servicio.util;

import com.formdev.flatlaf.util.SystemFileChooser;
import tr.cabro.servicio.Servicio;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Kullanıcının seçtiği görselleri (profil fotoğrafı, işletme logosu) veri klasörüne kopyalar.
 * <p>
 * Veritabanında yalnızca dosya ADI saklanır, tam yol değil — böylece veri klasörü taşınsa da
 * görseller bulunabilir. Aynı akış hem ilk kurulum ekranında hem profil/ayar panellerinde
 * kullanılıyordu; tek yerde toplandı.
 */
public final class ProfileImageStore {

    /** Profil fotoğraflarının tutulduğu alt klasör. */
    public static final String PROFILES_DIR = "profiles";

    /** İşletme logosunun tutulduğu alt klasör. */
    public static final String LOGOS_DIR = "logos";

    private ProfileImageStore() {}

    /** Görselin bulunduğu klasör (yoksa oluşturulmaz — yalnızca yol döner). */
    public static File directory(String subDir) {
        return new File(Servicio.getInstance().getDataFolder(), subDir);
    }

    /** Kayıtlı dosya adına karşılık gelen görsel dosyası. */
    public static File resolve(String subDir, String fileName) {
        return new File(directory(subDir), fileName);
    }

    /**
     * Dosya seçme penceresi açar ve seçilen görseli hedef klasöre kopyalar.
     *
     * @return veritabanına yazılacak dosya adı; kullanıcı vazgeçtiyse {@code null}
     * @throws IOException kopyalama başarısız olursa (çağıran kullanıcıya bildirmeli)
     */
    public static String chooseAndStore(Component parent, String dialogTitle, String subDir) throws IOException {
        SystemFileChooser chooser = new SystemFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setFileFilter(new SystemFileChooser.FileNameExtensionFilter(
                "Resim Dosyaları (*.jpg, *.png)", "jpg", "png", "jpeg"));

        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return null;

        File source = chooser.getSelectedFile();
        if (source == null) return null;

        File dir = directory(subDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IOException("Klasör oluşturulamadı: " + dir.getAbsolutePath());
        }

        Files.copy(source.toPath(), new File(dir, source.getName()).toPath(),
                StandardCopyOption.REPLACE_EXISTING);
        return source.getName();
    }
}
