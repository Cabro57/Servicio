package tr.cabro.servicio.application.manager;

import org.update4j.Configuration;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class UpdateManager {

    private static String UPDATE_API_URL;
    private static String ZIP_NAME;
    private static String EXE_NAME;

    static {
        try (InputStream is = UpdateManager.class.getResourceAsStream("/config.properties")) {
            Properties props = new Properties();
            if (is != null) {
                props.load(is);
                // Yeni properties isimlerine dikkat!
                UPDATE_API_URL = props.getProperty("api.update.url");
                ZIP_NAME = props.getProperty("update.zip.name", "update_patch.zip");
                EXE_NAME = props.getProperty("update.exe.name", "Servicio.exe");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void checkForUpdates(Component parent, boolean silent) {
        if (UPDATE_API_URL == null) return;

        new Thread(() -> {
            try {
                // 1. Sunucudaki Dinamik API'ye sor (v2.0.2 olduğumuzu belirterek de gidebiliriz)
                URL url = new URL(UPDATE_API_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) ServicioApp/1.0");

                // API'den dönen config.xml içeriğini doğrudan Update4j'ye oku
                Configuration remoteConfig;
                try (InputStreamReader isr = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)) {
                    remoteConfig = Configuration.read(isr);
                }

                // 2. Hash karşılaştırması yap
                if (remoteConfig.requiresUpdate()) {
                    // Sürüm notları artık config.xml içindeki property'den veya versiyondan gelebilir
                    String version = remoteConfig.getBaseUri().toString(); // Klasör adından versiyonu anlar
                    String notes = "Yeni özellikler ve hata düzeltmeleri içerir.";

                    SwingUtilities.invokeLater(() -> showUpdateDialog(parent, "Yeni Sürüm", notes, remoteConfig));
                } else if (!silent) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, "Uygulamanız güncel."));
                }

            } catch (Exception e) {
                if (!silent) e.printStackTrace();
            }
        }).start();
    }

    private static void showUpdateDialog(Component parent, String version, String notes, Configuration remoteConfig) {
        String message = "Sisteminiz için yeni bir güncelleme mevcut.\n\n" +
                "Notlar:\n" + notes + "\n\n" +
                "Güncelleme indirilsin mi?";

        int choice = JOptionPane.showConfirmDialog(parent, message, "Servicio Güncelleme",
                JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            performBackgroundDownload(parent, remoteConfig);
        }
    }

    private static void performBackgroundDownload(Component parent, Configuration remoteConfig) {
        ProgressMonitor monitor = new ProgressMonitor(parent, "Dosyalar indiriliyor...", "", 0, 100);

        new Thread(() -> {
            try {
                // ÇÖZÜM: 1.4.x serisinde doğrudan indirme yapmak için
                // parametresiz update() metodunu kullanıyoruz.
                // Bu metod, config.xml'de belirtilen yollara dosyaları tek tek indirir.

                boolean success = remoteConfig.update();

                if (success) {
                    SwingUtilities.invokeLater(() -> {
                        monitor.close();
                        int restart = JOptionPane.showConfirmDialog(parent,
                                "İndirme tamamlandı. Yeniden başlatılsın mı?",
                                "Güncelleme Hazır", JOptionPane.YES_NO_OPTION);

                        if (restart == JOptionPane.YES_OPTION) {
                            try {
                                applyPatchAndRestart();
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> {
                    monitor.close();
                    JOptionPane.showMessageDialog(parent, "Güncelleme hatası: " + e.getMessage());
                });
            }
        }).start();
    }

    private static void applyPatchAndRestart() throws IOException {
        File appDir = new File(System.getProperty("user.dir"));
        File scriptFile = new File(appDir, "update.bat");

        // ÖNEMLİ: JAR ismini sabitliyoruz (servicio.jar)
        String jarName = "servicio.jar";

        try (PrintWriter writer = new PrintWriter(scriptFile, "UTF-8")) {
            writer.println("@echo off");
            writer.println("timeout /t 1 /nobreak > NUL");
            // ZIP'ten çıkarırken -Force ile üzerine yazar
            writer.println("powershell -command \"Expand-Archive -Path '" + ZIP_NAME + "' -DestinationPath '.' -Force\"");
            writer.println("del \"" + ZIP_NAME + "\"");

            if (new File(appDir, EXE_NAME).exists()) {
                writer.println("start \"\" \"" + EXE_NAME + "\"");
            } else {
                writer.println("start \"\" jre\\bin\\javaw -jar \"" + jarName + "\"");
            }
            writer.println("del \"%~f0\"");
        }

        Runtime.getRuntime().exec("cmd /c start \"\" \"" + scriptFile.getAbsolutePath() + "\"");
        System.exit(0);
    }
}