package tr.cabro.servicio.updater;

import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Güncelleme motorunun çekirdeği.
 *
 * Akış:
 *  1. checkForUpdates()   → manifest indir, sürüm karşılaştır
 *  2. downloadUpdate()    → sadece SHA-256'sı değişen dosyaları indir
 *  3. applyUpdate()       → .update-tmp/ → uygulama köküne taşı
 *  4. writeLauncherScript() → OS'a göre .bat veya .sh yaz; uygulama bu script'i çalıştırıp kapanır
 *
 * Güncelleme uygulandıktan sonra Servicio.shutdown() çağrılır,
 * launcher script JVM kapandıktan sonra yeni sürümü başlatır.
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    // ─── Yapılandırma ─────────────────────────────────────────────────────────

    /** Manifest URL'si; GitHub raw veya kendi sunucu. version.properties'ten okunur. */
    private final String manifestUrl;

    /** Mevcut uygulama sürümü. */
    private final String currentVersion;

    /** Uygulamanın kök dizini (servicio.jar ve libs/ burada). */
    private final File appRoot;

    /** İndirilen dosyaların geçici olarak yazıldığı dizin. */
    private final File tempDir;

    // ─── Durum ────────────────────────────────────────────────────────────────

    @Getter
    private volatile boolean cancelRequested = false;

    /** İndirme tamamlandığında çağrılacak; parametre: toplam indirilen bayt. */
    @Setter
    private Consumer<Long> completionCallback;

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    /**
     * @param manifestUrl    Manifest JSON URL'si.
     * @param currentVersion Çalışan sürüm (version.properties'ten).
     * @param appRoot        servicio.jar'ın bulunduğu dizin.
     */
    public UpdateManager(String manifestUrl, String currentVersion, File appRoot) {
        this.manifestUrl    = manifestUrl;
        this.currentVersion = currentVersion;
        this.appRoot        = appRoot;
        this.tempDir        = new File(appRoot, ".update-tmp");
    }

    // ─── Genel API ────────────────────────────────────────────────────────────

    /**
     * Arka planda manifest indirir ve sürüm karşılaştırır.
     *
     * @param onUpdateAvailable Yeni sürüm varsa manifest ile çağrılır (EDT değil!).
     * @param onUpToDate        Güncel olduğunda çağrılır.
     * @param onError           Ağ hatası vb. durumda çağrılır.
     */
    public void checkForUpdates(final Consumer<UpdateManifest> onUpdateAvailable,
                                final Runnable onUpToDate,
                                final Consumer<Exception> onError) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("Güncelleme kontrolü yapılıyor: {}", manifestUrl);
                    String json = downloadText(manifestUrl);
                    UpdateManifest manifest = UpdateManifest.fromJson(json);
                    log.info("Uzak sürüm: {}  |  Yerel sürüm: {}", manifest.getVersion(), currentVersion);

                    if (isNewerVersion(manifest.getVersion(), currentVersion)) {
                        onUpdateAvailable.accept(manifest);
                    } else {
                        onUpToDate.run();
                    }
                } catch (Exception e) {
                    log.warn("Güncelleme kontrolü başarısız: {}", e.getMessage());
                    onError.accept(e);
                }
            }
        }, "servicio-update-checker");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Sadece SHA-256'sı farklı olan dosyaları indirir.
     * Aynı sürüme yama yüklense bile değişmeyen dosyalar tekrar indirilmez.
     *
     * @param manifest       Sunucudan alınan manifest.
     * @param onProgress     (dosyaAdı, 0.0–1.0) ilerleme bildirimi.
     * @param onFileSkipped  Hash aynı → atlandı.
     * @param onFileDone     Bir dosya başarıyla indirildi.
     * @param onDone         Tüm indirmeler tamamlandı.
     * @param onError        Hata oluştu.
     */
    public void downloadUpdate(final UpdateManifest manifest,
                               final BiConsumer<String, Double> onProgress,
                               final Consumer<String> onFileSkipped,
                               final Consumer<String> onFileDone,
                               final Runnable onDone,
                               final Consumer<Exception> onError) {
        cancelRequested = false;

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    tempDir.mkdirs();

                    List<UpdateManifest.FileEntry> toDownload = resolveFilesToDownload(manifest);

                    // Değişmeyen dosyaları bildir
                    List<UpdateManifest.FileEntry> allFiles = manifest.getFiles();
                    if (allFiles != null) {
                        for (UpdateManifest.FileEntry entry : allFiles) {
                            if (!toDownload.contains(entry)) {
                                log.info("Atlandı (hash aynı): {}", entry.name);
                                onFileSkipped.accept(entry.name);
                            }
                        }
                    }

                    long totalDownloaded = 0;

                    for (UpdateManifest.FileEntry entry : toDownload) {
                        if (cancelRequested) {
                            log.info("İndirme iptal edildi.");
                            return;
                        }

                        File dest = new File(tempDir, entry.path);
                        dest.getParentFile().mkdirs();

                        log.info("İndiriliyor: {}  ({})", entry.name, formatSize(entry.size));

                        final String entryName = entry.name;
                        final long   entrySize = entry.size;

                        downloadFile(entry.url, dest, new Consumer<Long>() {
                            @Override
                            public void accept(Long bytesRead) {
                                double pct = entrySize > 0 ? (double) bytesRead / entrySize : 0.0;
                                onProgress.accept(entryName, pct);
                            }
                        });

                        // Hash doğrula
                        String actualHash = sha256(dest);
                        if (!actualHash.equalsIgnoreCase(entry.sha256)) {
                            dest.delete();
                            throw new IOException(
                                    "Hash doğrulaması başarısız: " + entry.name
                                            + "\n  Beklenen : " + entry.sha256
                                            + "\n  Hesaplanan: " + actualHash);
                        }

                        totalDownloaded += dest.length();
                        log.info("Doğrulandı: {}", entry.name);
                        onFileDone.accept(entry.name);
                    }

                    if (!cancelRequested) {
                        if (completionCallback != null) completionCallback.accept(totalDownloaded);
                        onDone.run();
                    }

                } catch (Exception e) {
                    log.error("İndirme hatası: {}", e.getMessage(), e);
                    onError.accept(e);
                }
            }
        }, "servicio-update-downloader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * .update-tmp/ dizinindeki dosyaları uygulama köküne taşır.
     * Mevcut dosyalar .bak uzantısıyla yedeklenir.
     * Launcher script'ten ÖNCE çağrılmalıdır.
     */
    public void applyUpdate() throws IOException {
        log.info("Güncelleme uygulanıyor...");

        Files.walkFileTree(tempDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path src, BasicFileAttributes attrs) throws IOException {
                Path relative = tempDir.toPath().relativize(src);
                File dest     = new File(appRoot, relative.toString());
                dest.getParentFile().mkdirs();

                // Yedek al
                if (dest.exists()) {
                    File bak = new File(dest.getParent(), dest.getName() + ".bak");
                    if (bak.exists()) bak.delete();
                    dest.renameTo(bak);
                }

                Files.move(src, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Taşındı: {}", relative);
                return FileVisitResult.CONTINUE;
            }
        });

        deleteDirectory(tempDir);
        log.info("Güncelleme tamamlandı, geçici dizin temizlendi.");
    }

    /**
     * OS'a göre launcher script üretir ve döner.
     *
     * Windows → update-restart.bat
     * Linux/Mac → update-restart.sh
     *
     * Script, JVM tamamen kapandıktan sonra uygulamayı yeniden başlatır.
     * Servicio, bu script'i ProcessBuilder ile başlatıp ardından shutdown() çağırır.
     *
     * @param jarName  Ana jar dosyasının adı, örn: "servicio.jar"
     * @param jvmArgs  JVM argümanları, örn: "-Xmx512m" (boş bırakılabilir)
     * @return         Oluşturulan script dosyası.
     */
    public File writeLauncherScript(String jarName, String jvmArgs) throws IOException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows) {
            return writeBatScript(jarName, jvmArgs);
        } else {
            return writeShScript(jarName, jvmArgs);
        }
    }

    // ─── Launcher Script Yardımcıları ─────────────────────────────────────────

    private File writeBatScript(String jarName, String jvmArgs) throws IOException {
        File script = new File(appRoot, "update-restart.bat");
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8));
            pw.println("@echo off");
            pw.println(":: Servicio Guncelleme Launcher");
            // Eski JVM kapanana kadar bekle
            pw.println(":WAIT");
            pw.println("tasklist /fi \"PID eq %1\" 2>nul | find /i \"java.exe\" >nul");
            pw.println("if not errorlevel 1 (");
            pw.println("    timeout /t 1 /nobreak >nul");
            pw.println("    goto WAIT");
            pw.println(")");
            // Yeniden başlat
            pw.println("cd /d \"%~dp0\"");
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                pw.println("start javaw " + jvmArgs + " -jar " + jarName);
            } else {
                pw.println("start javaw -jar " + jarName);
            }
            pw.println("del \"%~f0\"");  // Script kendini siler
        } finally {
            if (pw != null) pw.close();
        }
        return script;
    }

    private File writeShScript(String jarName, String jvmArgs) throws IOException {
        File script = new File(appRoot, "update-restart.sh");
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8));
            pw.println("#!/bin/sh");
            pw.println("# Servicio Guncelleme Launcher");
            pw.println("OLD_PID=$1");
            // Eski JVM kapanana kadar bekle
            pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do");
            pw.println("    sleep 1");
            pw.println("done");
            // Uygulama dizinine git
            pw.println("SCRIPT_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"");
            pw.println("cd \"$SCRIPT_DIR\"");
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                pw.println("java " + jvmArgs + " -jar " + jarName + " &");
            } else {
                pw.println("java -jar " + jarName + " &");
            }
            pw.println("rm -- \"$0\"");  // Script kendini siler
        } finally {
            if (pw != null) pw.close();
        }
        // chmod +x
        script.setExecutable(true);
        return script;
    }

    /**
     * Launcher script'i arka planda başlatır.
     * Ardından Servicio.getInstance().shutdown() çağrılmalıdır.
     *
     * @param script  writeLauncherScript() ile üretilen dosya.
     */
    public void launchAndExit(File script) throws IOException {
        String pid = getCurrentPid();
        log.info("Launcher script başlatılıyor: {}  (mevcut PID: {})", script.getName(), pid);

        ProcessBuilder pb;
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");

        if (isWindows) {
            pb = new ProcessBuilder("cmd", "/c", "start", "", script.getAbsolutePath(), pid);
        } else {
            pb = new ProcessBuilder("sh", script.getAbsolutePath(), pid);
        }
        pb.directory(appRoot);
        pb.start(); // Detached — JVM kapanınca script devam eder
    }

    // ─── Yardımcı Metotlar ────────────────────────────────────────────────────

    /**
     * Manifestteki dosyaları yerel hash'lerle karşılaştırır.
     * Sonuç: sadece değişen / eksik dosyalar.
     */
    private List<UpdateManifest.FileEntry> resolveFilesToDownload(UpdateManifest manifest)
            throws IOException, NoSuchAlgorithmException {

        List<UpdateManifest.FileEntry> needed = new ArrayList<UpdateManifest.FileEntry>();
        List<UpdateManifest.FileEntry> files  = manifest.getFiles();
        if (files == null) return needed;

        for (UpdateManifest.FileEntry entry : files) {
            File local = new File(appRoot, entry.path);

            if (!local.exists()) {
                log.debug("Eksik dosya, indirilecek: {}", entry.path);
                needed.add(entry);
                continue;
            }

            String localHash = sha256(local);
            if (!localHash.equalsIgnoreCase(entry.sha256)) {
                log.debug("Hash farklı, indirilecek: {}  yerel={}", entry.name, localHash.substring(0, 8));
                needed.add(entry);
            } else {
                log.debug("Hash aynı, atlanıyor: {}", entry.name);
            }
        }
        return needed;
    }

    /** "2.1.0" > "1.9.3" → true */
    public static boolean isNewerVersion(String remote, String current) {
        int[] r = parseSemver(remote);
        int[] c = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] != c[i]) return r[i] > c[i];
        }
        return false;
    }

    private static int[] parseSemver(String v) {
        String[] parts = v == null ? new String[0] : v.split("\\.");
        int[] nums = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { nums[i] = Integer.parseInt(parts[i].trim()); }
            catch (NumberFormatException ignored) { }
        }
        return nums;
    }

    private String downloadText(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
            conn.disconnect();
        }
    }

    private void downloadFile(String urlStr, File dest, Consumer<Long> onBytesRead) throws IOException {
        HttpURLConnection conn = openConnection(urlStr);
        conn.setReadTimeout(120_000);

        InputStream  in  = null;
        OutputStream out = null;
        try {
            in  = conn.getInputStream();
            out = Files.newOutputStream(dest.toPath());
            byte[] buf = new byte[16384];
            long total = 0;
            int  n;
            while ((n = in.read(buf)) != -1) {
                if (cancelRequested) return;
                out.write(buf, 0, n);
                total += n;
                onBytesRead.accept(total);
            }
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) { }
            if (in  != null) try { in.close();  } catch (IOException ignored) { }
            conn.disconnect();
        }
    }

    private HttpURLConnection openConnection(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("User-Agent", "Servicio-Updater/" + currentVersion);
        // GitHub redirects → follow
        conn.setInstanceFollowRedirects(true);
        return conn;
    }

    public static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = null;
        try {
            in = Files.newInputStream(file.toPath());
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private void deleteDirectory(File dir) throws IOException {
        if (dir == null || !dir.exists()) return;
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path f, BasicFileAttributes a) throws IOException {
                Files.delete(f);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String getCurrentPid() {
        // Java 8 uyumlu PID alma: ManagementFactory
        String name = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        // Format: "12345@hostname"
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : name;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024)               return bytes + " B";
        if (bytes < 1024 * 1024)        return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    public void cancel()                                  { cancelRequested = true; }

}