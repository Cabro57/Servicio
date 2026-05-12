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
 * Güncelleme indirme motoru.
 * <p>
 * İndirme kaynakları:
 *   • source="maven"  → Maven Central veya özel repo'dan koordinatla
 *   • source="github" → GitHub Releases doğrudan URL
 *   • source="url"    → Özel HTTP/HTTPS kaynağı
 * <p>
 * Akış:
 *   1. checkForUpdates()    → manifest.json indir, sürüm karşılaştır
 *   2. downloadUpdate()     → hash'i değişen dosyaları indir (.update-tmp/)
 *   3. applyUpdate()        → .update-tmp/ → uygulama köküne taşı
 *   4. writeLauncherScript() → OS'a göre .bat / .sh yaz
 *   5. launchAndExit()      → script'i çalıştır, JVM'i kapat
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    // Bağlantı zaman aşımları
    private static final int CONNECT_TIMEOUT_MS  = 15_000;
    private static final int READ_TIMEOUT_MS     = 30_000;
    private static final int DOWNLOAD_TIMEOUT_MS = 120_000;
    // Maven redirect zincirleri için maksimum yönlendirme
    private static final int MAX_REDIRECTS       = 5;

    // ─── Yapılandırma ────────────────────────────────────────────────────────

    private final String manifestUrl;
    private final String currentVersion;
    private final File   appRoot;
    private final File   tempDir;

    // ─── Durum ───────────────────────────────────────────────────────────────

    @Getter
    private volatile boolean  cancelRequested    = false;
    @Setter
    private Consumer<Long>    completionCallback = null;

    // ─── Oluşturucu ──────────────────────────────────────────────────────────

    public UpdateManager(String manifestUrl, String currentVersion, File appRoot) {
        this.manifestUrl    = manifestUrl;
        this.currentVersion = currentVersion;
        this.appRoot        = appRoot;
        this.tempDir        = new File(appRoot, ".update-tmp");
    }

    // ─── Genel API ───────────────────────────────────────────────────────────

    public void checkForUpdates(final Consumer<UpdateManifest> onUpdateAvailable,
                                final Runnable onUpToDate,
                                final Consumer<Exception> onError) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("Manifest indiriliyor: {}", manifestUrl);
                    String json = downloadText(manifestUrl);
                    UpdateManifest manifest = UpdateManifest.fromJson(json);
                    log.info("Uzak: v{}  |  Yerel: v{}", manifest.getVersion(), currentVersion);

                    if (isNewerVersion(manifest.getVersion(), currentVersion)) {
                        onUpdateAvailable.accept(manifest);
                    } else {
                        onUpToDate.run();
                    }
                } catch (Exception e) {
                    log.warn("checkForUpdates hatası: {}", e.getMessage());
                    onError.accept(e);
                }
            }
        }, "servicio-update-checker");
        t.setDaemon(true);
        t.start();
    }

    public void downloadUpdate(final UpdateManifest manifest,
                               final BiConsumer<String, Double> onProgress,
                               final Consumer<String> onFileSkipped,
                               final Consumer<String> onFileDone,
                               final Runnable onDone,
                               final Consumer<Exception> onError) {
        cancelRequested = false;

        Thread t = new Thread(() -> {
            try {
                tempDir.mkdirs();

                List<UpdateManifest.FileEntry> toDownload = resolveFilesToDownload(manifest);

                // Atlanacakları bildir
                List<UpdateManifest.FileEntry> allFiles = manifest.getFiles();
                if (allFiles != null) {
                    for (UpdateManifest.FileEntry entry : allFiles) {
                        if (!toDownload.contains(entry)) {
                            log.info("Hash aynı, atlandı: {}", entry.name);
                            onFileSkipped.accept(entry.name);
                        }
                    }
                }

                long totalDownloaded = 0;

                for (UpdateManifest.FileEntry entry : toDownload) {
                    if (cancelRequested) {
                        log.info("İndirme kullanıcı tarafından iptal edildi.");
                        return;
                    }

                    // İndirme URL'sini çözümle (maven / github / url)
                    String downloadUrl = entry.resolveDownloadUrl();
                    String displayName = entry.name != null ? entry.name : entry.resolveFileName();

                    log.info("İndiriliyor [{}]: {} → {}",
                            entry.source, displayName, downloadUrl);

                    File dest = new File(tempDir, entry.path);
                    dest.getParentFile().mkdirs();

                    final String finalDisplayName = displayName;
                    final long   entrySize        = entry.size;

                    downloadFile(downloadUrl, dest, (Consumer<Long>) bytesRead -> {
                        double pct = entrySize > 0 ? (double) bytesRead / entrySize : 0.0;
                        onProgress.accept(finalDisplayName, pct);
                    });

                    // Hash doğrula
                    String actualHash = sha256(dest);
                    if (!actualHash.equalsIgnoreCase(entry.sha256)) {
                        dest.delete();
                        throw new IOException(
                                "Hash doğrulaması başarısız: " + displayName
                                        + "\n  Beklenen:   " + entry.sha256
                                        + "\n  Hesaplanan: " + actualHash
                                        + "\n  Kaynak:     " + downloadUrl);
                    }

                    log.info("✔ Doğrulandı: {}", displayName);
                    totalDownloaded += dest.length();
                    onFileDone.accept(displayName);
                }

                if (!cancelRequested) {
                    if (completionCallback != null) completionCallback.accept(totalDownloaded);
                    onDone.run();
                }

            } catch (Exception e) {
                log.error("downloadUpdate hatası", e);
                onError.accept(e);
            }
        }, "servicio-update-downloader");
        t.setDaemon(true);
        t.start();
    }

    public void applyUpdate() throws IOException {
        log.info("Güncelleme uygulanıyor: {} → {}", tempDir, appRoot);

        Files.walkFileTree(tempDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path src, BasicFileAttributes attrs) throws IOException {
                Path relative = tempDir.toPath().relativize(src);
                File dest     = new File(appRoot, relative.toString());
                dest.getParentFile().mkdirs();

                if (dest.exists()) {
                    File bak = new File(dest.getParent(), dest.getName() + ".bak");
                    if (bak.exists()) bak.delete();
                    dest.renameTo(bak);
                    log.debug("Yedeklendi: {}.bak", dest.getName());
                }

                Files.move(src, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Taşındı: {}", relative);
                return FileVisitResult.CONTINUE;
            }
        });

        deleteDirectory(tempDir);
        log.info("Geçici dizin temizlendi.");
    }

    public File writeLauncherScript(String jarName, String jvmArgs) throws IOException {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        return isWindows ? writeBatScript(jarName, jvmArgs) : writeShScript(jarName, jvmArgs);
    }

    public void launchAndExit(File script) throws IOException {
        String pid = getCurrentPid();
        log.info("Launcher başlatılıyor: {}  PID={}", script.getName(), pid);

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb;

        if (isWindows) {
            pb = new ProcessBuilder("cmd", "/c", "start", "", script.getAbsolutePath(), pid);
        } else {
            pb = new ProcessBuilder("sh", script.getAbsolutePath(), pid);
        }
        pb.directory(appRoot);
        pb.start();
    }

    // ─── Hash Karşılaştırma ───────────────────────────────────────────────────

    /**
     * Her dosyanın SHA-256'sını yerel dosyayla karşılaştırır.
     * Hash aynıysa atlar (değişmeyen kütüphaneler tekrar indirilmez).
     */
    private List<UpdateManifest.FileEntry> resolveFilesToDownload(UpdateManifest manifest)
            throws IOException, NoSuchAlgorithmException {

        List<UpdateManifest.FileEntry> needed = new ArrayList<UpdateManifest.FileEntry>();
        List<UpdateManifest.FileEntry> files  = manifest.getFiles();
        if (files == null) return needed;

        for (UpdateManifest.FileEntry entry : files) {
            File local = new File(appRoot, entry.path);

            if (!local.exists()) {
                log.debug("Yok → indirilecek: {}", entry.path);
                needed.add(entry);
                continue;
            }

            String localHash = sha256(local);
            if (!localHash.equalsIgnoreCase(entry.sha256)) {
                log.debug("Hash farklı → indirilecek: {}  (yerel={}…)",
                        entry.name, localHash.substring(0, 8));
                needed.add(entry);
            } else {
                log.debug("Hash aynı → atlanıyor: {}", entry.name);
            }
        }
        return needed;
    }

    // ─── İndirme Yardımcıları ────────────────────────────────────────────────

    private String downloadText(String urlStr) throws IOException {
        HttpURLConnection conn = openConnection(urlStr, READ_TIMEOUT_MS);
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) { }
            conn.disconnect();
        }
    }

    /**
     * Dosyayı HTTP(S) üzerinden indirir.
     * Maven Central ve GitHub Releases her ikisi de 302 redirect kullanır;
     * manuel redirect takibi yapılır (http → https geçişlerinde de çalışır).
     */
    private void downloadFile(String urlStr, File dest,
                              Consumer<Long> onBytesRead) throws IOException {

        // Redirect zincirini takip et
        String currentUrl = urlStr;
        HttpURLConnection conn = null;

        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            conn = openConnection(currentUrl, DOWNLOAD_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false); // Manuel takip ediyoruz

            int code = conn.getResponseCode();

            if (code == HttpURLConnection.HTTP_OK) {
                break; // Bulduk
            }

            if (code == HttpURLConnection.HTTP_MOVED_PERM  // 301
                    || code == HttpURLConnection.HTTP_MOVED_TEMP  // 302
                    || code == 307 || code == 308) {

                String location = conn.getHeaderField("Location");
                conn.disconnect();

                if (location == null || location.isEmpty()) {
                    throw new IOException("Redirect konumu boş: " + currentUrl);
                }

                // Göreli URL'yi mutlak yap
                if (!location.startsWith("http")) {
                    URL base = new URL(currentUrl);
                    location = base.getProtocol() + "://" + base.getHost() + location;
                }

                log.debug("Redirect ({}) → {}", code, location);
                currentUrl = location;

            } else {
                conn.disconnect();
                throw new IOException("HTTP " + code + " : " + currentUrl);
            }
        }

        // conn burada HTTP 200 bağlantısı
        InputStream  in  = null;
        OutputStream out = null;
        try {
            in  = conn.getInputStream();
            out = Files.newOutputStream(dest.toPath());

            byte[] buf   = new byte[16384];
            long   total = 0;
            int    n;

            while ((n = in.read(buf)) != -1) {
                if (cancelRequested) return;
                out.write(buf, 0, n);
                total += n;
                onBytesRead.accept(total);
            }
        } finally {
            conn.disconnect();
            if (out != null) try { out.close(); } catch (IOException ignored) { }
            if (in  != null) try { in.close();  } catch (IOException ignored) { }
        }
    }

    private HttpURLConnection openConnection(String urlStr, int readTimeout) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(readTimeout);
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("User-Agent", "Servicio-Updater/" + currentVersion);
        return conn;
    }

    // ─── Launcher Script ──────────────────────────────────────────────────────

    private File writeBatScript(String jarName, String jvmArgs) throws IOException {
        File script = new File(appRoot, "update-restart.bat");
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(
                    Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8));
            pw.println("@echo off");
            pw.println(":: Servicio Guncelleme Launcher");
            pw.println(":WAIT");
            pw.println("tasklist /fi \"PID eq %1\" 2>nul | find /i \"java.exe\" >nul");
            pw.println("if not errorlevel 1 (");
            pw.println("    timeout /t 1 /nobreak >nul");
            pw.println("    goto WAIT");
            pw.println(")");
            pw.println("cd /d \"%~dp0\"");
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                pw.println("start javaw " + jvmArgs + " -jar " + jarName);
            } else {
                pw.println("start javaw -jar " + jarName);
            }
            pw.println("del \"%~f0\"");
        } finally {
            if (pw != null) pw.close();
        }
        return script;
    }

    private File writeShScript(String jarName, String jvmArgs) throws IOException {
        File script = new File(appRoot, "update-restart.sh");
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(
                    Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8));
            pw.println("#!/bin/sh");
            pw.println("# Servicio Guncelleme Launcher");
            pw.println("OLD_PID=$1");
            pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do sleep 1; done");
            pw.println("SCRIPT_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"");
            pw.println("cd \"$SCRIPT_DIR\"");
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                pw.println("java " + jvmArgs + " -jar " + jarName + " &");
            } else {
                pw.println("java -jar " + jarName + " &");
            }
            pw.println("rm -- \"$0\"");
        } finally {
            if (pw != null) pw.close();
        }
        script.setExecutable(true);
        return script;
    }

    // ─── Yardımcı Metodlar ───────────────────────────────────────────────────

    public static boolean isNewerVersion(String remote, String current) {
        int[] r = parseSemver(remote);
        int[] c = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] != c[i]) return r[i] > c[i];
        }
        return false;
    }

    private static int[] parseSemver(String v) {
        if (v == null) return new int[3];
        String[] parts = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] nums = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try { nums[i] = Integer.parseInt(parts[i]); }
            catch (NumberFormatException ignored) { }
        }
        return nums;
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
                Files.delete(f); return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException e) throws IOException {
                Files.delete(d); return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String getCurrentPid() {
        String name = java.lang.management.ManagementFactory
                .getRuntimeMXBean().getName(); // "12345@hostname"
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : name;
    }

    // ─── Setter / Kontrol ─────────────────────────────────────────────────────

    public void cancel()                                 { cancelRequested = true;  }

}