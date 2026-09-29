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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final int DOWNLOAD_ATTEMPTS   = 3;

    /** "https://github.com/SAHIP/REPO/releases/latest/download/DOSYA" → (SAHIP/REPO, DOSYA) */
    private static final Pattern GITHUB_RELEASE_URL =
            Pattern.compile("https://github\\.com/([^/]+/[^/]+)/releases/latest/download/([^/?]+)");

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
     * @param beta true → ön sürümler (GitHub pre-release) de aday; en yüksek sürümün manifesti okunur.
     * @return CompletableFuture — tamamlandığında onUpdateAvailable veya onUpToDate çağrılır.
     */
    public CompletableFuture<Void> checkForUpdates(
            boolean beta,
            Consumer<UpdateManifest> onUpdateAvailable,
            Runnable onUpToDate,
            Consumer<Exception> onError) {

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = beta ? resolveBetaManifestUrl() : manifestUrl;
                log.info("Manifest indiriliyor: {}", url);
                // İmza manifestin birebir baytlarına aittir; satır sonu dönüşümü yapılmadan okunur.
                byte[] bytes = downloadBytes(url);
                String signature = new String(downloadBytes(url + ".sig"), StandardCharsets.US_ASCII);
                if (!ManifestSignature.verify(bytes, signature)) {
                    throw new IOException("Manifest imzası doğrulanamadı; güncelleme reddedildi.");
                }
                return UpdateManifest.fromJson(new String(bytes, StandardCharsets.UTF_8));
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
     * GitHub Releases API'sinden yerel sürümden sonraki tüm sürümlerin notlarını çeker
     * (yeniden eskiye). Birkaç sürüm atlayan kullanıcı ara sürümlerin notlarını da görür.
     * Aynı sürüme yeniden yayın (hotfix) durumunda yalnızca o sürümün notu döner.
     * Patch notları manifest.json'da değil, burada saklanır.
     */
    public CompletableFuture<List<UpdateManifest.GitHubReleaseInfo>> fetchReleaseNotes(
            UpdateManifest manifest,
            boolean beta,
            Consumer<List<UpdateManifest.GitHubReleaseInfo>> onSuccess,
            Consumer<Exception> onError) {

        return CompletableFuture.supplyAsync(() -> {
            String apiUrl = manifest.getReleasesApiUrl();
            if (apiUrl == null || apiUrl.trim().isEmpty()) {
                throw new RuntimeException(new IllegalStateException(
                        "manifest.json'da releasesApiUrl tanımlı değil."));
            }

            try {
                List<UpdateManifest.GitHubReleaseInfo> all = downloadReleaseList(apiUrl, beta);

                String target = manifest.getVersion();
                List<UpdateManifest.GitHubReleaseInfo> result = new ArrayList<>();
                for (UpdateManifest.GitHubReleaseInfo info : all) {
                    String v = info.tagName;
                    // Eski "v2.0-beta.N" gibi etiketler semver'e uymaz; karşılaştırmayı yanıltmasın.
                    if (!v.matches("[vV]?\\d+\\.\\d+\\.\\d+")) continue;
                    if (isNewerVersion(v, target)) continue;
                    if (isNewerVersion(v, currentVersion) || sameVersion(v, target)) result.add(info);
                }
                result.sort((a, b) -> isNewerVersion(a.tagName, b.tagName) ? -1
                        : sameVersion(a.tagName, b.tagName) ? 0 : 1);

                if (result.isEmpty()) throw new IOException("Sürüm notu bulunamadı.");
                log.info("{} sürümün notu alındı (v{} → v{})", result.size(), currentVersion, target);
                return result;

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }, POOL).thenApply(list -> {
            onSuccess.accept(list);
            return list;
        }).exceptionally(ex -> {
            Exception cause = ex.getCause() instanceof Exception
                    ? (Exception) ex.getCause() : new Exception(ex);
            log.warn("fetchReleaseNotes hatası: {}", cause.getMessage());
            onError.accept(cause);
            return null;
        });
    }

    /**
     * Tüm sürümlerin notlarını getirir. Önce release'e eklenen release-notes.json okunur
     * (manifestle aynı yerde; GitHub API'nin girişsiz 60 istek/saat sınırına takılmaz),
     * o yoksa GitHub API'sinin sürüm listesi kullanılır.
     */
    private List<UpdateManifest.GitHubReleaseInfo> downloadReleaseList(String apiUrl, boolean includePrerelease)
            throws IOException {
        int slash = manifestUrl.lastIndexOf('/');
        if (slash > 0) {
            String notesUrl = manifestUrl.substring(0, slash + 1) + "release-notes.json";
            try {
                log.info("Sürüm notları dosyası indiriliyor: {}", notesUrl);
                List<UpdateManifest.GitHubReleaseInfo> list =
                        UpdateManifest.GitHubReleaseInfo.fromJsonArray(downloadText(notesUrl), includePrerelease);
                if (!list.isEmpty()) return list;
            } catch (IOException e) {
                log.info("Sürüm notları dosyası alınamadı ({}), GitHub API'sine geçiliyor.", e.getMessage());
            }
        }
        // Manifest eski istemciler için tek sürüm adresini (/releases/latest) taşır;
        // ara sürümler için liste uç noktası kullanılır.
        String listUrl = apiUrl.replaceFirst("/releases/(latest|tags/.*)$", "/releases") + "?per_page=50";
        log.info("GitHub sürüm notları çekiliyor: {}", listUrl);
        return UpdateManifest.GitHubReleaseInfo.fromJsonArray(downloadGitHubApi(listUrl), includePrerelease);
    }

    /**
     * Beta kanalı: ön sürümler dahil en yüksek sürümün manifest adresi. Manifest adresi GitHub
     * release biçiminde değilse (kendi sunucu) ya da liste alınamazsa kararlı adrese düşülür;
     * beta açık diye güncelleme denetimi hiç çalışmaz hale gelmesin.
     */
    private String resolveBetaManifestUrl() {
        Matcher m = GITHUB_RELEASE_URL.matcher(manifestUrl);
        if (!m.matches()) return manifestUrl;
        String repo = m.group(1);
        try {
            UpdateManifest.GitHubReleaseInfo newest = null;
            for (UpdateManifest.GitHubReleaseInfo info : downloadReleaseList(
                    "https://api.github.com/repos/" + repo + "/releases/latest", true)) {
                if (!info.tagName.matches("[vV]?\\d+\\.\\d+\\.\\d+")) continue;
                if (newest == null || isNewerVersion(info.tagName, newest.tagName)) newest = info;
            }
            if (newest == null || !newest.prerelease) return manifestUrl;
            log.info("Beta kanalı: ön sürüm {} denetleniyor.", newest.tagName);
            return "https://github.com/" + repo + "/releases/download/" + newest.tagName + "/" + m.group(2);
        } catch (IOException e) {
            log.warn("Beta sürüm listesi alınamadı ({}), kararlı manifest kullanılıyor.", e.getMessage());
            return manifestUrl;
        }
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
        return writeLauncherScript(jarName, jvmArgs, true);
    }

    /**
     * @param restart false → script dosyaları taşır ama uygulamayı yeniden açmaz
     *                (kullanıcı uygulamayı kapatırken bekleyen güncellemenin kurulması).
     */
    public File writeLauncherScript(String jarName, String jvmArgs, boolean restart) throws IOException {
        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase().contains("win");
        return isWindows
                ? writeBatScript(jarName, jvmArgs, restart)
                : writeShScript(jarName, jvmArgs, restart);
    }

    /** Launcher script'i başlatır. Ardından Servicio.shutdown() çağrılmalıdır. */
    public void launchAndExit(File script) throws IOException {
        String pid = getCurrentPid();
        File logFile = restartLogFile();
        Files.deleteIfExists(logFile.toPath()); // yalnızca son çalıştırma tutulur
        log.info("Launcher başlatılıyor: {}  PID={}  günlük={}", script, pid, logFile);

        boolean isWindows = System.getProperty("os.name", "")
                .toLowerCase().contains("win");

        ProcessBuilder pb = isWindows
                // .bat gizli (pencere stili 0) VBS sarmalayıcısıyla, kullanıcının normal yetkisiyle
                // çalışır; yönetici izni gerekiyorsa yalnızca dosya taşıma adımı için UAC istenir.
                ? new ProcessBuilder("wscript.exe", writeHiddenLauncherVbs(script, pid).getAbsolutePath())
                : new ProcessBuilder("sh", script.getAbsolutePath(), pid);
        pb.directory(script.getParentFile());
        // Çıktı JVM'e pipe edilmez: JVM kapandıktan sonra kırık pipe'a yazan süreçler ölmesin.
        // Windows'ta .bat günlüğe kendisi yazar; açık bir tanıtıcı devralınırsa cmd'nin ">>"
        // yönlendirmesi paylaşım hatası verir, o yüzden orada çıktı atılır.
        pb.redirectErrorStream(true);
        if (isWindows) {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        } else {
            pb.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile));
            pb.redirectInput(ProcessBuilder.Redirect.from(new File("/dev/null")));
        }
        pb.start();
    }

    /**
     * .bat launcher'ını gizli (pencere stili 0) çalıştıran VBS sarmalayıcı üretir.
     * Böylece güncelleme sırasında hiçbir konsol penceresi görünmez.
     * VBS dosyasını .bat kendini silmeden önce siler (writeBatScript).
     */
    private File writeHiddenLauncherVbs(File batScript, String pid) throws IOException {
        File vbs = new File(batScript.getParentFile(), "update-restart.vbs");
        // VBS string literali için tırnakları ikiye katla
        String batPath = batScript.getAbsolutePath().replace("\"", "\"\"");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(vbs.toPath()), StandardCharsets.UTF_8))) {
            pw.println("Set WshShell = CreateObject(\"WScript.Shell\")");
            pw.println("WshShell.Run \"cmd /c \"\"" + batPath + "\"\" " + pid + "\", 0, False");
        }
        return vbs;
    }

    /**
     * Script'lerin yazıldığı klasör. appRoot yazılamıyorsa veri klasörü ({@code .servicio}) —
     * asla {@code update-tmp}'nin içi değil: script o klasörü silerken kendini de silmemeli.
     */
    private File scriptDir() {
        return appRootWritable ? appRoot : tempDir.getParentFile();
    }

    /** Son güncelleme script'inin günlüğü: {@code .servicio/logs/update-restart.log}. */
    private static File restartLogFile() {
        String base = System.getProperty("servicio.baseDir");
        File logs = new File(new File(base != null ? new File(base) : DataDirResolver.resolveBaseFolder(),
                ".servicio"), "logs");
        logs.mkdirs();
        return new File(logs, "update-restart.log");
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

    /** Yanıtı baytı baytına döner (imza doğrulaması için satır sonları korunur). */
    private byte[] downloadBytes(String urlStr) throws IOException {
        String bustedUrl = urlStr + (urlStr.contains("?") ? "&" : "?")
                + "_t=" + System.currentTimeMillis();
        HttpURLConnection conn = followRedirects(bustedUrl, READ_TIMEOUT_MS, false);
        try (InputStream in = conn.getInputStream()) {
            return in.readAllBytes();
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

    /**
     * Dosyayı indirir; bağlantı kopması gibi geçici hatalarda artan bekleme ile
     * {@link #DOWNLOAD_ATTEMPTS} kez dener. İptal edilirse yeniden denenmez.
     */
    private void downloadFile(String urlStr, File dest,
                              Consumer<Long> onBytesRead) throws IOException {
        for (int attempt = 1; ; attempt++) {
            try {
                downloadFileOnce(urlStr, dest, onBytesRead);
                return;
            } catch (IOException e) {
                if (cancelRequested || attempt >= DOWNLOAD_ATTEMPTS) throw e;
                log.warn("İndirme hatası (deneme {}/{}): {} — yeniden denenecek",
                        attempt, DOWNLOAD_ATTEMPTS, e.getMessage());
                try {
                    Thread.sleep(2_000L * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private void downloadFileOnce(String urlStr, File dest,
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

    /**
     * Uygulamayı yeniden başlatacak komutun çalıştırılabilir dosyası: jpackage kurulumunda native
     * launcher (Servicio.exe / bin/Servicio), aksi halde bu JVM'in kendi java(w)'sı. PATH'teki
     * "java"ya güvenilmez — sistem PATH'inde eski bir Java (ör. Java 8) önce gelebiliyor ve yeni
     * sürüm açılır açılmaz UnsupportedClassVersionError ile kapanıyordu; kurulu sürümlerde ise
     * hiç Java olmayabilir.
     *
     * @return {@code [çalıştırılabilir, native launcher mı ("true"/"false")]}
     */
    private static String[] resolveRestartExecutable(boolean windows) {
        String cmd = ProcessHandle.current().info().command().orElse(null);
        if (cmd != null) {
            String name = new File(cmd).getName().toLowerCase();
            if (!name.startsWith("java")) {
                return new String[]{cmd, "true"};
            }
        }
        File bin = new File(System.getProperty("java.home"), "bin");
        File java = new File(bin, windows ? "javaw.exe" : "java");
        return new String[]{java.isFile() ? java.getAbsolutePath() : (windows ? "javaw" : "java"), "false"};
    }

    /**
     * Uygulamayı açan komut. Script her zaman kullanıcının normal yetkisiyle çalıştığı için
     * uygulama da yönetici olarak açılmaz. Linux'ta script bittikten sonra da yaşaması ve
     * çıktısının hiçbir yere bağlı kalmaması için nohup + /dev/null.
     */
    private static String buildRestartCommand(String jarName, String jvmArgs, boolean windows) {
        String[] exe = resolveRestartExecutable(windows);
        boolean nativeLauncher = Boolean.parseBoolean(exe[1]);
        String args = nativeLauncher ? ""
                : ((jvmArgs != null && !jvmArgs.isEmpty() ? jvmArgs + " " : "") + "-jar \"" + jarName + "\"");
        String cmd = "\"" + exe[0] + "\"" + (args.isEmpty() ? "" : " " + args);
        return windows
                ? "start \"\" " + cmd
                : "nohup " + cmd + " </dev/null >/dev/null 2>&1 &";
    }

    private File writeBatScript(String jarName, String jvmArgs, boolean restart) throws IOException {
        String startCmd = restart ? buildRestartCommand(jarName, jvmArgs, true) : "";
        String logPath  = restartLogFile().getAbsolutePath();

        if (appRootWritable) {
            File   script = new File(appRoot, "update-restart.bat");
            String tmpJar = ".update-tmp\\" + jarName;

            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                    Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
                pw.println("@echo off");
                pw.println("chcp 65001 >nul");
                pw.println(":: Servicio Guncelleme Launcher");
                pw.println("set OLD_PID=%1");
                pw.println("set \"LOG=" + logPath + "\"");
                pw.println("cd /d \"%~dp0\"");
                pw.println("echo [%date% %time%] Eski surec bekleniyor, PID %OLD_PID% >> \"%LOG%\"");
                pw.println("");
                // PID ile beklenir: jpackage kurulumunda süreç adı "Servicio.exe", "java" içermez.
                pw.println(":WAIT_JVM");
                pw.println("tasklist /fi \"PID eq %OLD_PID%\" /fo csv /nh 2>nul | find \"\"\"%OLD_PID%\"\"\" >nul");
                pw.println("if not errorlevel 1 (");
                pw.println("    timeout /t 1 /nobreak >nul");
                pw.println("    goto WAIT_JVM");
                pw.println(")");
                pw.println("");
                pw.println("if exist \"" + tmpJar + "\" (");
                pw.println("    if exist \"" + jarName + ".bak\" del /f \"" + jarName + ".bak\"");
                pw.println("    if exist \"" + jarName + "\" ren \"" + jarName + "\" \"" + jarName + ".bak\"");
                pw.println("    move /y \"" + tmpJar + "\" \"" + jarName + "\" >> \"%LOG%\" 2>&1");
                pw.println(")");
                pw.println("");
                pw.println("if exist \".update-tmp\" rd /s /q \".update-tmp\"");
                pw.println("echo [%date% %time%] Guncelleme kuruldu >> \"%LOG%\"");
                pw.println("");
                pw.println(startCmd);
                pw.println("");
                pw.println("if exist \"update-restart.vbs\" del /f \"update-restart.vbs\"");
                pw.println("del \"%~f0\"");
            }
            return script;
        }

        // appRoot yazılamıyor (ör. Program Files). İki script, ikisi de update-tmp'nin DIŞINDA:
        //   update-install.bat → yalnızca dosya taşıma; UAC ile yönetici olarak çalışır.
        //   update-restart.bat → kullanıcının normal yetkisiyle: eski süreci bekler, kurulumu
        //                        yükseltilmiş başlatıp bitmesini bekler, sonra uygulamayı açar.
        // Eskiden tek script update-tmp'nin içindeydi ve "rd update-tmp" ile kendini siliyordu;
        // cmd batch'i satır satır diskten okuduğu için sonraki "start" satırına hiç gelinmiyor,
        // güncelleme kurulup uygulama açılmıyordu.
        File dir     = scriptDir();
        File install = new File(dir, "update-install.bat");
        // Ayrı günlük: PowerShell satırı ana günlüğü açık tuttuğu için yükseltilmiş süreç ona yazamıyor.
        String installLog = new File(restartLogFile().getParentFile(), "update-install.log").getAbsolutePath();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(install.toPath()), StandardCharsets.UTF_8))) {
            pw.println("@echo off");
            pw.println("chcp 65001 >nul");
            pw.println(":: Servicio Guncelleme - dosya tasima (yonetici)");
            pw.println("robocopy \"" + tempDir.getAbsolutePath() + "\" \"" + appRoot.getAbsolutePath() + "\""
                    + " /E /MOVE /IS /IT /R:3 /W:1 /NFL /NDL /NJH /NJS /NP > \"" + installLog + "\" 2>&1");
            pw.println("exit %errorlevel%");
        }

        // Start-Process -Verb RunAs: UAC istemi; reddedilirse hata fırlatır → 1223 (ERROR_CANCELLED).
        String installPs = install.getAbsolutePath().replace("'", "''");
        File script = new File(dir, "update-restart.bat");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
            pw.println("@echo off");
            pw.println("chcp 65001 >nul");
            pw.println(":: Servicio Guncelleme Launcher");
            pw.println("set OLD_PID=%1");
            pw.println("set \"LOG=" + logPath + "\"");
            pw.println("set \"TEMPDIR=" + tempDir.getAbsolutePath() + "\"");
            pw.println("cd /d \"" + appRoot.getAbsolutePath() + "\"");
            pw.println("echo [%date% %time%] Eski surec bekleniyor, PID %OLD_PID% >> \"%LOG%\"");
            pw.println("");
            // PID ile beklenir: jpackage kurulumunda süreç adı "Servicio.exe", "java" içermez.
            pw.println(":WAIT_JVM");
            pw.println("tasklist /fi \"PID eq %OLD_PID%\" /fo csv /nh 2>nul | find \"\"\"%OLD_PID%\"\"\" >nul");
            pw.println("if not errorlevel 1 (");
            pw.println("    timeout /t 1 /nobreak >nul");
            pw.println("    goto WAIT_JVM");
            pw.println(")");
            pw.println("");
            pw.println("echo [%date% %time%] Dosyalar yonetici izniyle tasiniyor >> \"%LOG%\"");
            pw.println("powershell -NoProfile -ExecutionPolicy Bypass -Command \"try { $p = Start-Process -FilePath '"
                    + installPs + "' -Verb RunAs -WindowStyle Hidden -Wait -PassThru; exit $p.ExitCode }"
                    + " catch { Write-Output $_.Exception.Message; exit 1223 }\" >> \"%LOG%\" 2>&1");
            pw.println("set RC=%errorlevel%");
            pw.println("if exist \"" + installLog + "\" (");
            pw.println("    type \"" + installLog + "\" >> \"%LOG%\"");
            pw.println("    del /f \"" + installLog + "\"");
            pw.println(")");
            // robocopy: 0-7 başarı, 8+ hata
            pw.println("if %RC% LSS 8 (");
            pw.println("    echo [%date% %time%] Guncelleme kuruldu, kod %RC% >> \"%LOG%\"");
            pw.println("    if exist \"%TEMPDIR%\" rd /s /q \"%TEMPDIR%\"");
            pw.println(") else (");
            pw.println("    echo [%date% %time%] Guncelleme kurulamadi, kod %RC% >> \"%LOG%\"");
            pw.println(")");
            pw.println("");
            pw.println(startCmd);
            pw.println("");
            pw.println("if exist \"%~dp0update-restart.vbs\" del /f \"%~dp0update-restart.vbs\"");
            pw.println("if exist \"%~dp0update-install.bat\" del /f \"%~dp0update-install.bat\"");
            pw.println("del \"%~f0\"");
        }
        return script;
    }

    private File writeShScript(String jarName, String jvmArgs, boolean restart) throws IOException {
        String javaCmd = restart ? buildRestartCommand(jarName, jvmArgs, false) : "";
        String stamp   = "echo \"$(date '+%Y-%m-%d %H:%M:%S')\"";

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
                pw.println(stamp + " \"Eski surec bekleniyor, PID $OLD_PID\"");
                pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do sleep 1; done");
                pw.println("if [ -f \"" + tmpJar + "\" ]; then");
                pw.println("    mv -f \"" + jarName + "\" \"" + jarName + ".bak\" 2>/dev/null");
                pw.println("    mv -f \"" + tmpJar + "\" \"" + jarName + "\"");
                pw.println("fi");
                pw.println("rm -rf .update-tmp");
                pw.println(stamp + " \"Guncelleme kuruldu\"");
                pw.println(javaCmd);
                pw.println("rm -f -- \"$0\"");
            }
            script.setExecutable(true);
            return script;
        }

        // appRoot yazılamıyor (ör. /opt) → kopyalama pkexec ile (polkit parola istemi), uygulama
        // yine kullanıcının normal yetkisiyle açılır. Script update-tmp'nin dışında durur.
        File script = new File(scriptDir(), "update-restart.sh");
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(script.toPath()), StandardCharsets.UTF_8))) {
            pw.println("#!/bin/sh");
            pw.println("# Servicio Guncelleme Launcher (appRoot yazilamiyor)");
            pw.println("OLD_PID=$1");
            pw.println("TEMPDIR=\"" + tempDir.getAbsolutePath() + "\"");
            pw.println("APPROOT=\"" + appRoot.getAbsolutePath() + "\"");
            pw.println("cd \"$APPROOT\"");
            pw.println(stamp + " \"Eski surec bekleniyor, PID $OLD_PID\"");
            pw.println("while kill -0 \"$OLD_PID\" 2>/dev/null; do sleep 1; done");
            pw.println(stamp + " \"Dosyalar yonetici izniyle kopyalaniyor\"");
            pw.println("if command -v pkexec >/dev/null 2>&1 && pkexec cp -rf \"$TEMPDIR\"/. \"$APPROOT\"/; then");
            pw.println("    " + stamp + " \"Guncelleme kuruldu\"");
            pw.println("    rm -rf \"$TEMPDIR\"");
            pw.println("else");
            pw.println("    " + stamp + " \"Guncelleme kurulamadi: pkexec yok ya da izin verilmedi\"");
            pw.println("fi");
            pw.println(javaCmd);
            pw.println("rm -f -- \"$0\"");
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