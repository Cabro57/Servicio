package tr.cabro.servicio.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tr.cabro.servicio.util.DataDirResolver;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Güncelleme indirme motoru.
 * <p>
 * Tüm ağ ve dosya işlemleri CompletableFuture üzerinde yürütülür.
 * Ortak bir daemon thread pool kullanılır.
 * <p>
 * İndirme kaynakları:
 *   source="github" → GitHub Releases URL
 *   source="maven"  → Maven Central veya özel repo koordinatı
 *   source="url"    → Özel HTTP/HTTPS kaynağı
 * <p>
 * Akış:
 *   checkForUpdates()    → manifest indir, sürüm + hash karşılaştır
 *   downloadUpdate()     → değişen dosyaları indir (.update-tmp/)
 *   applyUpdate()        → libs/ dosyalarını uygulama köküne taşı
 *                          (ana JAR kilitli olduğu için launcher script'e bırakılır)
 *   writeLauncherScript() → JVM kapandıktan sonra ana JAR'ı taşıyan script
 *   launchAndExit()      → script'i başlat
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    // ─── Sabitler ────────────────────────────────────────────────────────────

    private static final int CONNECT_TIMEOUT_MS  = 15_000;
    private static final int READ_TIMEOUT_MS     = 30_000;
    private static final int DOWNLOAD_TIMEOUT_MS = 120_000;
    private static final int MAX_REDIRECTS       = 8;

    // ─── Thread Pool ─────────────────────────────────────────────────────────

    /** Tüm async operasyonlar bu pool'da çalışır. */
    private static final Executor POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "servicio-updater");
        t.setDaemon(true);
        return t;
    });

    // ─── Yapılandırma ─────────────────────────────────────────────────────────

    private final String  manifestUrl;
    private final String  currentVersion;
    private final File    appRoot;
    private final File    tempDir;

    /**
     * false → appRoot (ör. Program Files\Servicio\app) admin olmayan kullanıcıyla
     * yazılamıyor. Bu durumda indirme kullanıcı veri dizinine yapılır ve dosyaları
     * appRoot'a taşıyan adım yönetici izniyle (UAC) çalıştırılır.
     */
    private final boolean appRootWritable;

    /**
     * true  → IDE/geliştirme ortamı; hash kontrolü atlanır.
     * false → Üretim; tam hash kontrolü.
     */
    private final boolean devMode;

    // ─── Durum ───────────────────────────────────────────────────────────────

    private volatile boolean cancelRequested = false;

    // ─── Oluşturucu ──────────────────────────────────────────────────────────

    public UpdateManager(String manifestUrl, String currentVersion, File appRoot) {
        this(manifestUrl, currentVersion, appRoot, false);
    }

    public UpdateManager(String manifestUrl, String currentVersion,
                         File appRoot, boolean devMode) {
        this.manifestUrl     = manifestUrl;
        this.currentVersion  = currentVersion;
        this.appRoot         = appRoot;
        this.appRootWritable = isWritable(appRoot);
        this.tempDir         = this.appRootWritable
                ? new File(appRoot, ".update-tmp")
                : new File(DataDirResolver.resolveBaseFolder(), ".servicio/update-tmp");
        this.devMode         = devMode;
    }

    private static boolean isWritable(File dir) {
        try {
            if (!dir.isDirectory()) return dir.mkdirs() || dir.isDirectory();
            File probe = new File(dir, ".write-test-" + System.nanoTime());
            if (probe.createNewFile()) {
                probe.delete();
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Genel API ───────────────────────────────────────────────────────────

    /**
     * Arka planda manifest indirir ve sürüm/hash kontrolü yapar.
     * <p>
     * Geliştirme modunda (devMode=true) sadece sürüm numarası karşılaştırılır;
     * hash kontrolü atlanır (appRoot'ta libs/ dizini olmadığı için).
     *
     * @return CompletableFuture — tamamlandığında onUpdateAvailable veya onUpToDate çağrılır.
     */
    public CompletableFuture<Void> checkForUpdates(
            Consumer<UpdateManifest> onUpdateAvailable,
            Runnable onUpToDate,
            Consumer<Exception> onError) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                log.info("Manifest indiriliyor: {}", manifestUrl);
                String json = downloadText(manifestUrl);
                return UpdateManifest.fromJson(json);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, POOL).thenAccept(manifest -> {
            log.info("Uzak: v{}  |  Yerel: v{}", manifest.getVersion(), currentVersion);

            if (isNewerVersion(manifest.getVersion(), currentVersion)) {
                log.info("Yeni sürüm tespit edildi → güncelleme mevcut.");
                onUpdateAvailable.accept(manifest);

            } else if (sameVersion(manifest.getVersion(), currentVersion)) {

                if (devMode) {
                    // Geliştirme modunda hash atla
                    log.info("Geliştirme modu: sürüm aynı, hash kontrolü atlandı → güncel.");
                    onUpToDate.run();
                    return;
                }

                // Sürüm aynı — dosya hash'lerini karşılaştır (hotfix/yama desteği)
                try {
                    List<UpdateManifest.FileEntry> changed = resolveFilesToDownload(manifest);
                    if (!changed.isEmpty()) {
                        log.info("Sürüm aynı (v{}) ama {} dosyada hash farkı var → yama.",
                                manifest.getVersion(), changed.size());
                        onUpdateAvailable.accept(manifest);
                    } else {
                        log.info("Sürüm aynı, tüm hash'ler eşleşiyor → güncel.");
                        onUpToDate.run();
                    }
                } catch (Exception e) {
                    log.warn("Hash karşılaştırma hatası: {}", e.getMessage());
                    onUpToDate.run();
                }

            } else {
                log.info("Uzak sürüm yerel sürümden eski → güncelleme yok.");
                onUpToDate.run();
            }

        }).exceptionally(ex -> {
            log.warn("checkForUpdates hatası: {}", ex.getCause() != null
                    ? ex.getCause().getMessage() : ex.getMessage());
            onError.accept(ex.getCause() instanceof Exception
                    ? (Exception) ex.getCause() : new Exception(ex));
            return null;
        });
    }

    /**
     * Değişen dosyaları indirir.
     *
     * @param onProgress    (dosyaAdı, 0.0–1.0) ilerleme bildirimi.
     * @param onFileSkipped Hash aynı → atlandı.
     * @param onFileDone    Bir dosya başarıyla indirildi ve doğrulandı.
     * @param onDone        Tüm indirmeler tamamlandı.
     * @param onError       İndirme veya hash hatası.
     */
    public CompletableFuture<Void> downloadUpdate(
            UpdateManifest manifest,
            BiConsumer<String, Double> onProgress,
            Consumer<String> onFileSkipped,
            Consumer<String> onFileDone,
            Runnable onDone,
            Consumer<Exception> onError) {

        cancelRequested = false;

        return CompletableFuture.runAsync(() -> {
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

                for (UpdateManifest.FileEntry entry : toDownload) {
                    if (cancelRequested) {
                        log.info("İndirme iptal edildi.");
                        return;
                    }

                    String downloadUrl = entry.resolveDownloadUrl();
                    String displayName = (entry.name != null && !entry.name.isEmpty())
                            ? entry.name : entry.resolveFileName();

                    log.info("İndiriliyor [{}]: {} → {}", entry.source, displayName, downloadUrl);

                    File dest = new File(tempDir, entry.path);
                    dest.getParentFile().mkdirs();

                    final long entrySize = entry.size;
                    downloadFile(downloadUrl, dest,
                            bytesRead -> onProgress.accept(displayName,
                                    entrySize > 0 ? (double) bytesRead / entrySize : 0.0));

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
                    onFileDone.accept(displayName);
                }

                if (!cancelRequested) onDone.run();

            } catch (Exception e) {
                log.error("downloadUpdate hatası", e);
                onError.accept(e);
            }
        }, POOL);
    }

    /**
     * GitHub Releases API'sinden release bilgisini çeker.
     * Patch notları manifest.json'da değil, burada saklanır.
     */
    public CompletableFuture<UpdateManifest.GitHubReleaseInfo> fetchReleaseInfo(
            UpdateManifest manifest,
            Consumer<UpdateManifest.GitHubReleaseInfo> onSuccess,
            Consumer<Exception> onError) {

        return CompletableFuture.supplyAsync(() -> {
            String apiUrl = manifest.getReleasesApiUrl();
            if (apiUrl == null || apiUrl.trim().isEmpty()) {
                throw new RuntimeException(new IllegalStateException(
                        "manifest.json'da releasesApiUrl tanımlı değil."));
            }

            try {
                log.info("GitHub Release bilgisi çekiliyor: {}", apiUrl);
                String json = downloadGitHubApi(apiUrl);

                UpdateManifest.GitHubReleaseInfo info;
                if (apiUrl.contains("/releases/latest") || apiUrl.contains("/releases/tags/")) {
                    info = UpdateManifest.GitHubReleaseInfo.fromJson(json);
                } else {
                    List<UpdateManifest.GitHubReleaseInfo> list =
                            UpdateManifest.GitHubReleaseInfo.fromJsonArray(json);
                    info = list.isEmpty() ? null : list.get(0);
                }

                if (info == null) throw new IOException("Release bilgisi parse edilemedi.");
                log.info("Release bilgisi alındı: {} ({})", info.tagName, info.publishedAt);
                return info;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, POOL).thenApply(info -> {
            onSuccess.accept(info);
            return info;
        }).exceptionally(ex -> {
            Exception cause = ex.getCause() instanceof Exception
                    ? (Exception) ex.getCause() : new Exception(ex);
            log.warn("fetchReleaseInfo hatası: {}", cause.getMessage());
            onError.accept(cause);
            return null;
        });
    }

    /**
     * İndirilen dosyaları uygulama dizinine taşır.
     * <p>
     * Windows'ta çalışan JVM kendi JAR'ını kilitler; ana JAR ATLANIR.
     * Ana JAR launcher script tarafından JVM kapandıktan sonra taşınır.
     * libs/ klasöründeki JAR'lar kilitli olmadığı için doğrudan taşınır.
     */
    public void applyUpdate() throws IOException {
        if (!appRootWritable) {
            log.info("appRoot yazılamıyor ({}) → dosya taşıma yönetici izniyle "
                    + "launcher script'e bırakıldı.", appRoot);
            return;
        }

        log.info("Güncelleme uygulanıyor: {} → {}", tempDir, appRoot);
        final String mainJarName = resolveMainJarName();

        Files.walkFileTree(tempDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path src, BasicFileAttributes attrs)
                    throws IOException {
                Path   relative = tempDir.toPath().relativize(src);
                String relStr   = relative.toString().replace("\\", "/");

                // Ana JAR → launcher script'e bırak (kilitli)
                if (relStr.equalsIgnoreCase(mainJarName)) {
                    log.info("Ana JAR launcher'a bırakıldı: {}", relStr);
                    return FileVisitResult.CONTINUE;
                }

                File dest = new File(appRoot, relStr);
                dest.getParentFile().mkdirs();

                if (dest.exists()) {
                    File bak = new File(dest.getParent(), dest.getName() + ".bak");
                    if (bak.exists()) bak.delete();
                    dest.renameTo(bak);
                }

                Files.move(src, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("Taşındı: {}", relStr);
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("libs/ taşıma tamamlandı. Ana JAR launcher'a bırakıldı.");
    }

    /**
     * OS'a göre launcher script üretir.
     * Script: JVM kapandıktan sonra ana JAR'ı taşır ve uygulamayı yeniden başlatır.
     */
    public File writeLauncherScript(String jarName, String jvmArgs) throws IOException {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase().contains("win");
        return isWindows
                ? writeBatScript(jarName, jvmArgs)
                : writeShScript(jarName, jvmArgs);
    }

    /** Launcher script'i başlatır. Ardından Servicio.shutdown() çağrılmalıdır. */
    public void launchAndExit(File script) throws IOException {
        String pid = getCurrentPid();
        log.info("Launcher başlatılıyor: {}  PID={}", script.getName(), pid);

        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase().contains("win");

        if (isWindows) {
            File vbs = writeHiddenLauncherVbs(script, pid);
            if (appRootWritable) {
                // appRoot yazılabilir → yükseltme gerekmez, gizli (pencere stili 0)
                // wscript sarmalayıcısıyla doğrudan çalıştır.
                ProcessBuilder pb = new ProcessBuilder("wscript.exe", vbs.getAbsolutePath());
                pb.directory(appRoot);
                pb.start();
            } else {
                // appRoot (ör. Program Files) admin olmayan kullanıcıyla yazılamıyor →
                // VBS'i ShellExecute "runas" ile başlatarak tek seferlik UAC istemi tetiklenir;
                // script bu izinle appRoot'a dosya taşıyabilir.
                ProcessBuilder pb = new ProcessBuilder("wscript.exe", vbs.getAbsolutePath());
                pb.directory(tempDir);
                pb.start();
            }
        } else {
            ProcessBuilder pb = new ProcessBuilder("sh", script.getAbsolutePath(), pid);
            pb.directory(appRootWritable ? appRoot : tempDir);
            pb.start();
        }
    }

    /**
     * .bat launcher'ını gizli (pencere stili 0) çalıştıran VBS sarmalayıcı üretir.
     * Böylece güncelleme sırasında hiçbir konsol penceresi görünmez.
     * <p>
     * appRoot yazılamıyorsa (ör. Program Files kurulumu), Shell.Application'ın
     * ShellExecute "runas" fiiliyle çalıştırılır — bu, tek seferlik bir UAC istemi
     * tetikler ve .bat script'i yönetici izniyle çalışır (dosyaları appRoot'a taşıyabilir).
     * VBS dosyasını .bat kendini silmeden önce siler (writeBatScript).
     */
    private File writeHiddenLauncherVbs(File batScript, String pid) throws IOException {
        File vbs = new File(appRootWritable ? appRoot : tempDir, "update-restart.vbs");
        // VBS string literali için tırnakları ikiye katla
        String batPath = batScript.getAbsolutePath().replace("\"", "\"\"");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(vbs.toPath()), StandardCharsets.UTF_8))) {
            if (appRootWritable) {
                pw.println("Set WshShell = CreateObject(\"WScript.Shell\")");
                pw.println("WshShell.Run \"cmd /c \"\"" + batPath + "\"\" " + pid + "\", 0, False");
            } else {
                pw.println("Set objShell = CreateObject(\"Shell.Application\")");
                pw.println("objShell.ShellExecute \"cmd.exe\", \"/c \"\"" + batPath
                        + "\"\" " + pid + "\", \"\", \"runas\", 0");
            }
        }
        return vbs;
    }

    public void cancel() { cancelRequested = true; }

    // ─── Hash Karşılaştırma ───────────────────────────────────────────────────

    private List<UpdateManifest.FileEntry> resolveFilesToDownload(UpdateManifest manifest)
            throws IOException, NoSuchAlgorithmException {

        List<UpdateManifest.FileEntry> needed = new ArrayList<>();
        List<UpdateManifest.FileEntry> files  = manifest.getFiles();
        if (files == null) return needed;

        for (UpdateManifest.FileEntry entry : files) {
            File local = new File(appRoot, entry.path);
            if (!local.exists()) {
                log.debug("Eksik → indirilecek: {}", entry.path);
                needed.add(entry);
                continue;
            }
            String localHash = sha256(local);
            if (!localHash.equalsIgnoreCase(entry.sha256)) {
                log.debug("Hash farklı → indirilecek: {}  (yerel={}…)",
                        entry.name, localHash.substring(0, 8));
                needed.add(entry);
            } else {
                log.debug("Hash aynı → atlıyor: {}", entry.name);
            }
        }
        return needed;
    }

    // ─── Sürüm Karşılaştırma ─────────────────────────────────────────────────

    public static boolean isNewerVersion(String remote, String current) {
        int[] r = parseSemver(remote);
        int[] c = parseSemver(current);
        for (int i = 0; i < 3; i++) {
            if (r[i] != c[i]) return r[i] > c[i];
        }
        return false;
    }

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
            catch (NumberFormatException ignored) {}
        }
        return nums;
    }

    // ─── HTTP Yardımcıları ────────────────────────────────────────────────────

    private String downloadText(String urlStr) throws IOException {
        // Cache-bust: GitHub CDN'in eski içerik dönmesini engeller
        String bustedUrl = urlStr + (urlStr.contains("?") ? "&" : "?")
                + "_t=" + System.currentTimeMillis();

        HttpURLConnection conn = followRedirects(bustedUrl, READ_TIMEOUT_MS, false);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private String downloadGitHubApi(String urlStr) throws IOException {
        String bustedUrl = urlStr + (urlStr.contains("?") ? "&" : "?")
                + "_t=" + System.currentTimeMillis();
        HttpURLConnection conn = followRedirects(bustedUrl, READ_TIMEOUT_MS, true);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        } finally {
            conn.disconnect();
        }
    }

    private void downloadFile(String urlStr, File dest,
                              Consumer<Long> onBytesRead) throws IOException {
        HttpURLConnection conn = followRedirects(urlStr, DOWNLOAD_TIMEOUT_MS, false);
        try (InputStream in  = conn.getInputStream();
             OutputStream out = Files.newOutputStream(dest.toPath())) {
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
        }
    }

    /**
     * Redirect zincirini takip ederek HTTP 200 bağlantısı döner.
     * GitHub Releases ve Maven Central her ikisi de redirect kullanır.
     *
     * @param githubApi true → GitHub API header'ları eklenir.
     */
    private HttpURLConnection followRedirects(String urlStr, int readTimeout,
                                              boolean githubApi) throws IOException {
        String current = urlStr;

        for (int i = 0; i <= MAX_REDIRECTS; i++) {
            HttpURLConnection conn = (HttpURLConnection) new URL(current).openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(readTimeout);
            conn.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate");
            conn.setRequestProperty("Pragma",        "no-cache");
            conn.setRequestProperty("Expires",       "0");
            conn.setRequestProperty("User-Agent",    "Servicio-Updater/" + currentVersion);
            conn.setUseCaches(false);
            conn.setInstanceFollowRedirects(false);

            if (githubApi) {
                conn.setRequestProperty("Accept", "application/vnd.github+json");
            }

            int code = conn.getResponseCode();

            if (code == HttpURLConnection.HTTP_OK) return conn;

            if (code == 301 || code == 302 || code == 307 || code == 308) {
                String loc = conn.getHeaderField("Location");
                conn.disconnect();
                if (loc == null || loc.isEmpty())
                    throw new IOException("Redirect konumu boş: " + current);
                if (!loc.startsWith("http")) {
                    URL base = new URL(current);
                    loc = base.getProtocol() + "://" + base.getHost() + loc;
                }
                current = loc;
                log.debug("Redirect {} → {}", code, current);
                continue;
            }

            if (code == 403) {
                conn.disconnect();
                throw new IOException("HTTP 403 — GitHub API rate limit aşıldı: " + current);
            }

            conn.disconnect();
            throw new IOException("HTTP " + code + " : " + current);
        }

        throw new IOException("Çok fazla redirect: " + urlStr);
    }

    // ─── Launcher Script ──────────────────────────────────────────────────────

    private File writeBatScript(String jarName, String jvmArgs) throws IOException {
        String startCmd = (jvmArgs != null && !jvmArgs.isEmpty())
                ? "start javaw " + jvmArgs + " -jar \"" + jarName + "\""
                : "start javaw -jar \"" + jarName + "\"";

        if (appRootWritable) {
            File   script = new File(appRoot, "update-restart.bat");
            String tmpJar = ".update-tmp\\" + jarName;

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
                pw.println("@echo off");
                pw.println("chcp 65001 >nul");
                pw.println(":: Servicio Guncelleme Launcher");
                pw.println("set OLD_PID=%1");
                pw.println("cd /d \"%~dp0\"");
                pw.println("");
                pw.println(":WAIT_JVM");
                pw.println("tasklist /fi \"PID eq %OLD_PID%\" 2>nul | find /i \"java\" >nul");
                pw.println("if not errorlevel 1 (");
                pw.println("    timeout /t 1 /nobreak >nul");
                pw.println("    goto WAIT_JVM");
                pw.println(")");
                pw.println("");
                pw.println("if exist \"" + tmpJar + "\" (");
                pw.println("    if exist \"" + jarName + ".bak\" del /f \"" + jarName + ".bak\"");
                pw.println("    if exist \"" + jarName + "\" ren \"" + jarName + "\" \"" + jarName + ".bak\"");
                pw.println("    move /y \"" + tmpJar + "\" \"" + jarName + "\"");
                pw.println(")");
                pw.println("");
                pw.println("if exist \".update-tmp\" rd /s /q \".update-tmp\"");
                pw.println("");
                pw.println(startCmd);
                pw.println("");
                pw.println("if exist \"update-restart.vbs\" del /f \"update-restart.vbs\"");
                pw.println("del \"%~f0\"");
            }
            return script;
        }

        // appRoot yazılamıyor (ör. Program Files) → script yönetici izniyle çalıştırılacak.
        // tüm indirilen dosyalar (ana JAR dahil) robocopy ile appRoot'a taşınır — eski JVM
        // zaten kapanmış olduğu için ana JAR'ın da tek adımda taşınması güvenlidir.
        File script = new File(tempDir, "update-restart.bat");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
            pw.println("@echo off");
            pw.println("chcp 65001 >nul");
            pw.println(":: Servicio Guncelleme Launcher (yukseltilmis)");
            pw.println("set OLD_PID=%1");
            pw.println("set TEMPDIR=" + tempDir.getAbsolutePath());
            pw.println("set APPROOT=" + appRoot.getAbsolutePath());
            pw.println("cd /d \"%APPROOT%\"");
            pw.println("");
            pw.println(":WAIT_JVM");
            pw.println("tasklist /fi \"PID eq %OLD_PID%\" 2>nul | find /i \"java\" >nul");
            pw.println("if not errorlevel 1 (");
            pw.println("    timeout /t 1 /nobreak >nul");
            pw.println("    goto WAIT_JVM");
            pw.println(")");
            pw.println("");
            pw.println("robocopy \"%TEMPDIR%\" \"%APPROOT%\" /E /MOVE /IS /IT /R:3 /W:1 /NFL /NDL /NJH /NJS"
                    + " /XF update-restart.bat update-restart.vbs");
            pw.println("rd /s /q \"%TEMPDIR%\" 2>nul");
            pw.println("");
            pw.println(startCmd);
        }
        return script;
    }

    private File writeShScript(String jarName, String jvmArgs) throws IOException {
        String javaCmd = (jvmArgs != null && !jvmArgs.isEmpty())
                ? "java " + jvmArgs + " -jar \"" + jarName + "\" &"
                : "java -jar \"" + jarName + "\" &";

        if (appRootWritable) {
            File   script = new File(appRoot, "update-restart.sh");
            String tmpJar = ".update-tmp/" + jarName;

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
                pw.println("#!/bin/sh");
                pw.println("# Servicio Guncelleme Launcher");
                pw.println("OLD_PID=$1");
                pw.println("SCRIPT_DIR=\"$(cd \"$(dirname \"$0\")\" && pwd)\"");
                pw.println("cd \"$SCRIPT_DIR\"");
                pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do sleep 1; done");
                pw.println("if [ -f \"" + tmpJar + "\" ]; then");
                pw.println("    mv -f \"" + jarName + "\" \"" + jarName + ".bak\" 2>/dev/null");
                pw.println("    mv -f \"" + tmpJar + "\" \"" + jarName + "\"");
                pw.println("fi");
                pw.println("rm -rf .update-tmp");
                pw.println(javaCmd);
                pw.println("rm -- \"$0\"");
            }
            script.setExecutable(true);
            return script;
        }

        // appRoot yazılamıyor → indirilenler kullanıcı veri dizininden mutlak yollarla taşınır.
        File script = new File(tempDir, "update-restart.sh");
        String tempDirAbs = tempDir.getAbsolutePath();
        String appRootAbs = appRoot.getAbsolutePath();

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
            pw.println("#!/bin/sh");
            pw.println("# Servicio Guncelleme Launcher (appRoot yazilamiyor)");
            pw.println("OLD_PID=$1");
            pw.println("TEMPDIR=\"" + tempDirAbs + "\"");
            pw.println("APPROOT=\"" + appRootAbs + "\"");
            pw.println("cd \"$APPROOT\"");
            pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do sleep 1; done");
            pw.println("cp -a \"$TEMPDIR\"/. \"$APPROOT\"/ && rm -rf \"$TEMPDIR\"");
            pw.println("rm -f \"$APPROOT/update-restart.sh\"");
            pw.println(javaCmd);
        }
        script.setExecutable(true);
        return script;
    }

    // ─── Genel Yardımcılar ───────────────────────────────────────────────────

    public static String sha256(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private String resolveMainJarName() {
        try {
            File f = new File(getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (f.isFile()) return f.getName();
        } catch (Exception ignored) {}
        return "servicio.jar";
    }

    private static String getCurrentPid() {
        String name = java.lang.management.ManagementFactory
                .getRuntimeMXBean().getName();
        int at = name.indexOf('@');
        return at > 0 ? name.substring(0, at) : name;
    }
}