package tr.cabro.servicio.application.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import tr.cabro.servicio.Servicio;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class UpdateManager {

    // GÜNCELLEME: GitHub URL'i yerine S3 URL'inizi buraya yazın
    // Örnek: https://<BUCKET_ADI>.s3.<BOLGE>.amazonaws.com/update.json
    private static final String UPDATE_URL = "https://servicio-app-updates.s3.eu-north-1.amazonaws.com/update.json";

    private static final String EXE_NAME = "Servicio.exe";

    public static void checkForUpdates(Component parent, boolean silent) {
        new Thread(() -> {
            try {
                // S3'ten JSON dosyasını okur
                String jsonResponse = readStringFromURL(UPDATE_URL);
                JsonObject json = new JsonParser().parse(jsonResponse).getAsJsonObject();

                String latestVersion = json.get("version").getAsString();
                String downloadUrl = json.get("downloadUrl").getAsString();
                String releaseNotes = json.has("notes") ? json.get("notes").getAsString() : "";

                // Mevcut sürüm (Örn: 2.0-beta.9 -> 2.0-beta.9)
                String currentVersion = Servicio.getInstance().getAppVersion().replace("v", "");

                // Sürümler farklıysa güncelleme öner
                if (!currentVersion.equalsIgnoreCase(latestVersion)) {
                    SwingUtilities.invokeLater(() -> showUpdateDialog(parent, latestVersion, releaseNotes, downloadUrl));
                } else if (!silent) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, "Uygulamanız güncel (v" + currentVersion + ")", "Bilgi", JOptionPane.INFORMATION_MESSAGE));
                }

            } catch (Exception e) {
                if (!silent) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, "Güncelleme sunucusuna ulaşılamadı.\nLütfen internet bağlantınızı kontrol edin.", "Hata", JOptionPane.ERROR_MESSAGE));
                }
                e.printStackTrace();
            }
        }).start();
    }

    // ... (Sınıfın geri kalanı, showUpdateDialog, performUpdate, applyUpdateAndRestart metotları ÖNCEKİ CEVAPTAKİ GİBİ AYNEN KALACAK) ...

    // update.bat veya update.sh oluşturma ve yeniden başlatma mantığı aynıdır.
    // Aşağıdaki yardımcı metotlar da aynı kalır:

    private static void showUpdateDialog(Component parent, String version, String notes, String url) {
        String message = "Yeni sürüm mevcut: v" + version + "\n\nNotlar:\n" + notes + "\n\nŞimdi güncellemek ister misiniz?";
        int choice = JOptionPane.showConfirmDialog(parent, message, "Güncelleme Mevcut", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            performUpdate(parent, url);
        }
    }

    private static void performUpdate(Component parent, String downloadUrl) {
        ProgressMonitor monitor = new ProgressMonitor(parent, "Güncelleme indiriliyor...", "", 0, 100);

        new Thread(() -> {
            try {
                File currentJar = new File(Servicio.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                File appDir = currentJar.getParentFile();
                File newJar = new File(appDir, "update_temp.jar");

                downloadFile(downloadUrl, newJar, monitor);

                if (monitor.isCanceled()) {
                    newJar.delete();
                    return;
                }

                SwingUtilities.invokeLater(() -> {
                    int restart = JOptionPane.showConfirmDialog(parent,
                            "İndirme tamamlandı. Uygulamanın güncellenmesi için yeniden başlatılması gerekiyor.",
                            "Tamamlandı", JOptionPane.YES_NO_OPTION);

                    if (restart == JOptionPane.YES_OPTION) {
                        try {
                            applyUpdateAndRestart(currentJar, newJar, appDir);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });

            } catch (Exception e) {
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent, "İndirme hatası: " + e.getMessage()));
            }
        }).start();
    }

    private static void applyUpdateAndRestart(File currentJar, File newJar, File appDir) throws IOException {
        String os = System.getProperty("os.name").toLowerCase();
        File scriptFile;
        File exeFile = new File(appDir, EXE_NAME);
        boolean runExe = exeFile.exists();

        if (os.contains("win")) {
            scriptFile = new File(appDir, "update.bat");
            try (PrintWriter writer = new PrintWriter(scriptFile, "UTF-8")) {
                writer.println("@echo off");
                writer.println("timeout /t 2 /nobreak > NUL");
                writer.println("del \"" + currentJar.getName() + "\"");
                writer.println("ren \"" + newJar.getName() + "\" \"" + currentJar.getName() + "\"");
                if (runExe) {
                    writer.println("start \"\" \"" + EXE_NAME + "\"");
                } else {
                    writer.println("start \"\" javaw -jar \"" + currentJar.getName() + "\"");
                }
                writer.println("del \"%~f0\"");
            }
        } else {
            scriptFile = new File(appDir, "update.sh");
            try (PrintWriter writer = new PrintWriter(scriptFile)) {
                writer.println("#!/bin/bash");
                writer.println("sleep 2");
                writer.println("mv \"" + newJar.getAbsolutePath() + "\" \"" + currentJar.getAbsolutePath() + "\"");
                if(runExe) {
                    // Linux'ta exe çalışmaz ama script mantığı buraya konabilir
                    writer.println("java -jar \"" + currentJar.getAbsolutePath() + "\" &");
                } else {
                    writer.println("java -jar \"" + currentJar.getAbsolutePath() + "\" &");
                }
                writer.println("rm -- \"$0\"");
            }
            scriptFile.setExecutable(true);
        }

        if (os.contains("win")) {
            Runtime.getRuntime().exec("cmd /c start \"\" \"" + scriptFile.getAbsolutePath() + "\"");
        } else {
            Runtime.getRuntime().exec(new String[]{"/bin/bash", scriptFile.getAbsolutePath()});
        }
        System.exit(0);
    }

    private static void downloadFile(String urlStr, File dest, ProgressMonitor monitor) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        // S3 public dosyalarda bazen User-Agent gerekebilir, opsiyonel
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");

        long totalSize = conn.getContentLength();
        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(dest)) {
            byte[] data = new byte[4096];
            long count = 0;
            int n;
            while ((n = in.read(data)) != -1) {
                if (monitor.isCanceled()) return;
                out.write(data, 0, n);
                count += n;
                if (totalSize > 0) monitor.setProgress((int) (count * 100 / totalSize));
            }
        }
    }

    private static String readStringFromURL(String url) throws IOException {
        try (java.util.Scanner scanner = new java.util.Scanner(new URL(url).openStream(), StandardCharsets.UTF_8.name())) {
            return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
        }
    }
}