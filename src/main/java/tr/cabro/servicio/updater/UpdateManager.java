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

    /**
     * Geliştirme modu bayrağı.
     * true  → IDE'den çalışılıyor; hash kontrolü atlanır, yalnızca sürüm numarası karşılaştırılır.
     * false → Gerçek JAR ortamı; tam hash kontrolü uygulanır.
     */
    private final boolean devMode;

    // ─── Oluşturucu ──────────────────────────────────────────────────────────

    /** Üretim ortamı constructor'ı (devMode=false). */
    public UpdateManager(String manifestUrl, String currentVersion, File appRoot) {
        this(manifestUrl, currentVersion, appRoot, false);
    }

    /** devMode=true → IDE ortamı; hash kontrolü yapılmaz. */
    public UpdateManager(String manifestUrl, String currentVersion, File appRoot, boolean devMode) {
        this.manifestUrl    = manifestUrl;
        this.currentVersion = currentVersion;
        this.appRoot        = appRoot;
        this.tempDir        = new File(appRoot, ".update-tmp");
        this.devMode        = devMode;
    }

    // ─── Genel API ───────────────────────────────────────────────────────────

    public void checkForUpdates(final Consumer<UpdateManifest> onUpdateAvailable,
                                final Runnable onUpToDate,
                                final Consumer<Exception> onError) {
        Thread t = new Thread(() -> {
            try {
                log.info("Manifest indiriliyor: {}", manifestUrl);
                String json = downloadText(manifestUrl);
                UpdateManifest manifest = UpdateManifest.fromJson(json);
                log.info("Uzak: v{}  |  Yerel: v{}", manifest.getVersion(), currentVersion);

                if (isNewerVersion(manifest.getVersion(), currentVersion)) {
                    // Sürüm numarası yeni → kesinlikle güncelleme var
                    log.info("Yeni sürüm tespit edildi → güncelleme mevcut.");
                    onUpdateAvailable.accept(manifest);

                } else if (sameVersion(manifest.getVersion(), currentVersion)) {
                    // Sürüm aynı — ama dosyalar değişmiş olabilir (aynı sürüme yama).
                    // Hash'leri kontrol et; en az bir fark varsa güncelleme say.
                    List<UpdateManifest.FileEntry> changed = resolveFilesToDownload(manifest);
                    if (!changed.isEmpty()) {
                        log.info("Sürüm aynı (v{}) ama {} dosyada hash farkı var → yama güncelleme.",
                                manifest.getVersion(), changed.size());
                        onUpdateAvailable.accept(manifest);
                    } else {
                        log.info("Sürüm aynı, tüm hash'ler eşleşiyor → güncel.");
                        onUpToDate.run();
                    }

                } else {
                    // Uzak sürüm daha eski (downgrade) → güncelleme yok
                    log.info("Uzak sürüm yerel sürümden eski → güncelleme yok.");
                    onUpToDate.run();
                }

            } catch (Exception e) {
                log.warn("checkForUpdates hatası: {}", e.getMessage());
                onError.accept(e);
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

    /**
     * İndirilen dosyaları uygulama dizinine taşır.
     * <p>
     * Windows kısıtlaması: Çalışan JVM kendi JAR'ını kilitler, üzerine yazılamaz.
     * Bu nedenle ana JAR (.update-tmp/servicio.jar) bu metodda ATLANIR.
     * Ana JAR'ı launcher script taşır — JVM kapandıktan sonra.
     * <p>
     * libs/ klasöründeki JAR'lar çalışan JVM tarafından kilitlenmez (URLClassLoader
     * onları belleğe yükler ve dosyayı serbest bırakır), doğrudan taşınabilir.
     */
    public void applyUpdate() throws IOException {
        log.info("Güncelleme uygulanıyor: {} → {}", tempDir, appRoot);

        final String mainJarName = getMainJarName();

        Files.walkFileTree(tempDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path src, BasicFileAttributes attrs) throws IOException {
                Path relative = tempDir.toPath().relativize(src);
                String relStr = relative.toString();

                // Ana JAR'ı atla — JVM tarafından kilitli, launcher script taşıyacak
                if (relStr.equalsIgnoreCase(mainJarName)
                        || relStr.equalsIgnoreCase(mainJarName.replace("/", File.separator))) {
                    log.info("Ana JAR launcher script'e bırakıldı: {}", relStr);
                    return FileVisitResult.CONTINUE;
                }

                File dest = new File(appRoot, relStr);
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

        // tempDir'i silme — ana JAR hâlâ orada, launcher script onu taşıyacak
        log.info("libs/ taşıma tamamlandı. Ana JAR launcher'a bırakıldı.");
    }

    /** Ana JAR'ın göreceli yolunu döner. Örn: "servicio.jar" */
    private String getMainJarName() {
        // MANIFEST.MF'den okumayı dene
        try {
            java.net.URL url = getClass().getResource("/META-INF/MANIFEST.MF");
            if (url != null) {
                java.util.jar.Manifest mf = new java.util.jar.Manifest(url.openStream());
                String cp = mf.getMainAttributes().getValue("Class-Path");
                // Class-Path: libs/a.jar libs/b.jar → ana JAR listede değil
                // Alternatif: Start-Class veya Main-Class'tan değil, JAR adını al
            }
        } catch (Exception ignored) {}

        // Fallback: codeSource'dan JAR adını al
        try {
            File f = new File(getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (f.isFile()) return f.getName();
        } catch (Exception ignored) {}

        return "servicio.jar"; // son fallback
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
        // GitHub CDN önbelleğini atlatmak için timestamp parametresi ekle.
        // raw.githubusercontent.com bazen dakikalarca eski içerik döner;
        // query string eklenmesi CDN'in farklı bir cache key kullanmasını sağlar.

        // Redirect zincirini takip et (raw.githubusercontent.com → objects.githubusercontent.com)
        String currentUrl = urlStr
                + (urlStr.contains("?") ? "&" : "?")
                + "_t=" + System.currentTimeMillis();
        HttpURLConnection conn = null;

        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            conn = openConnection(currentUrl, READ_TIMEOUT_MS);
            conn.setInstanceFollowRedirects(false);
            int code = conn.getResponseCode();

            if (code == 200) break;

            if (code == 301 || code == 302 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isEmpty())
                    throw new IOException("Manifest redirect konumu bos");
                if (!loc.startsWith("http")) {
                    URL base = new URL(currentUrl);
                    loc = base.getProtocol() + "://" + base.getHost() + loc;
                }
                currentUrl = loc;
                continue;
            }

            conn.disconnect();
            throw new IOException("Manifest HTTP " + code + " : " + currentUrl);
        }

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
            if (out != null) try { out.close(); } catch (IOException ignored) { }
            if (in  != null) try { in.close();  } catch (IOException ignored) { }
            conn.disconnect();
        }
    }

    private HttpURLConnection openConnection(String urlStr, int readTimeout) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
        conn.setReadTimeout(readTimeout);
        // Tüm önbellek mekanizmalarını devre dışı bırak
        conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
        conn.setRequestProperty("Pragma",        "no-cache");
        conn.setRequestProperty("Expires",       "0");
        conn.setRequestProperty("User-Agent",    "Servicio-Updater/" + currentVersion);
        conn.setUseCaches(false);
        return conn;
    }

    // ─── Launcher Script ──────────────────────────────────────────────────────

    private File writeBatScript(String jarName, String jvmArgs) throws IOException {
        File   script = new File(appRoot, "update-restart.bat");
        // Ana JAR geçici dizinde bekliyor (.update-tmp\servicio.jar)
        String tmpJar = ".update-tmp\\" + jarName;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
            pw.println("@echo off");
            pw.println("chcp 65001 >nul");
            pw.println(":: Servicio Guncelleme Launcher");
            pw.println("set OLD_PID=%1");
            pw.println("cd /d \"%~dp0\"");
            pw.println("");
            pw.println(":: Eski JVM tamamen kapanana kadar bekle");
            pw.println(":WAIT_JVM");
            pw.println("tasklist /fi \"PID eq %OLD_PID%\" 2>nul | find /i \"java\" >nul");
            pw.println("if not errorlevel 1 (");
            pw.println("    timeout /t 1 /nobreak >nul");
            pw.println("    goto WAIT_JVM");
            pw.println(")");
            pw.println("");
            pw.println(":: Ana JAR artik serbest, gecici dizinden tasi");
            pw.println("if exist \"" + tmpJar + "\" (");
            pw.println("    if exist \"" + jarName + ".bak\" del /f \"" + jarName + ".bak\"");
            pw.println("    if exist \"" + jarName + "\" ren \"" + jarName + "\" \"" + jarName + ".bak\"");
            pw.println("    move /y \"" + tmpJar + "\" \"" + jarName + "\"");
            pw.println(")");
            pw.println("");
            pw.println(":: Gecici dizini temizle");
            pw.println("if exist \".update-tmp\" rd /s /q \".update-tmp\"");
            pw.println("");
            pw.println(":: Uygulamayi yeniden baslat");
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                pw.println("start javaw " + jvmArgs + " -jar \"" + jarName + "\"");
            } else {
                pw.println("start javaw -jar \"" + jarName + "\"");
            }
            pw.println("");
            pw.println("del \"%~f0\"");
        }
        return script;
    }

    private File writeShScript(String jarName, String jvmArgs) throws IOException {
        File script = new File(appRoot, "update-restart.sh");
        // Ana JAR geçici dizinde bekliyor (.update-tmp/servicio.jar)
        String tmpJar = ".update-tmp/" + jarName;
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
            pw.println("#!/bin/sh");
            pw.println("# Servicio Guncelleme Launcher");
            pw.println("OLD_PID=$1");
            pw.println("SCRIPT_DIR=\"$(cd \"$(dirname \"$0\"))\" && pwd)\"");
            pw.println("cd \"$SCRIPT_DIR\"");
            pw.println("");
            pw.println("# Eski JVM kapanana kadar bekle");
            pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do sleep 1; done");
            pw.println("");
            pw.println("# Ana JAR artik serbest, gecici dizinden tasi");
            pw.println("if [ -f \"" + tmpJar + "\" ]; then");
            pw.println("    mv -f \"" + jarName + "\" \"" + jarName + ".bak\" 2>/dev/null");
            pw.println("    mv -f \"" + tmpJar + "\" \"" + jarName + "\"");
            pw.println("fi");
            pw.println("");
            pw.println("# Gecici dizini temizle");
            pw.println("rm -rf .update-tmp");
            pw.println("");
            pw.println("# Uygulamayi yeniden baslat");
            if (jvmArgs != null && !jvmArgs.isEmpty()) {
                pw.println("java " + jvmArgs + " -jar \"" + jarName + "\" &");
            } else {
                pw.println("java -jar \"" + jarName + "\" &");
            }
            pw.println("rm -- \"$0\"");
        }
        script.setExecutable(true);
        return script;
    }

    // ─── Yardımcı Metodlar ───────────────────────────────────────────────────

    /** Uzak sürüm yerelden daha yeni mi? "2.1.0" > "1.9.3" → true */
    public static boolean isNewerVersion(String remote, String current) {
        int[] r = parseSemver(remote);
        int[] c = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] != c[i]) return r[i] > c[i];
        }
        return false;
    }

    /**
     * Sürüm numaraları aynı mı?
     * Aynı sürüme yama yüklendiğinde (hotfix) hash kontrolüne geçmek için kullanılır.
     * "2.1.0" == "2.1.0" → true
     */
    public static boolean sameVersion(String remote, String current) {
        int[] r = parseSemver(remote);
        int[] c = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] != c[i]) return false;
        }
        return true;
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

    public void cancel() {
        cancelRequested = true;
    }
}