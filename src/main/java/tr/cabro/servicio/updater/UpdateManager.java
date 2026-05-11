package tr.cabro.servicio.updater;

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
 * Sorumlulukları:
 *  1. Manifest URL'sinden (GitHub raw veya kendi sunucu) JSON indir ve parse et.
 *  2. Yerel sürümü uzak sürümle karşılaştır.
 *  3. Her dosyanın SHA-256 hash'ini yerel dosyayla karşılaştır.
 *     → Eşleşiyorsa atla  (aynı sürüme hotfix yüklenmiş olsa bile doğru çalışır).
 *     → Farklıysa indir.
 *  4. İndirilen dosyaları .update-tmp/ altına yaz.
 *  5. applyUpdate() çağrısıyla dosyaları yerine taşı, .bak yedek al.
 *
 * Java 8 uyumlu. Ek bağımlılık gerektirmez (Gson zaten projede var).
 */
public class UpdateManager {

    private static final Logger log = LoggerFactory.getLogger(UpdateManager.class);

    // ─── Yapılandırma ─────────────────────────────────────────────────────────

    /** Manifest JSON'unun tam URL'si */
    private final String manifestUrl;

    /** Uygulamanın mevcut sürümü (Servicio.getAppVersion() ile alınır) */
    private final String currentVersion;

    /** Uygulamanın kök dizini (servicio.jar ve libs/ burada) */
    private final Path appRoot;

    /** Geçici indirme dizini */
    private final Path tempDir;

    // ─── Geri Çağrılar ────────────────────────────────────────────────────────

    @Setter
    private Consumer<Long> completionCallback;

    // ─── Durum ────────────────────────────────────────────────────────────────

    private volatile boolean cancelRequested = false;

    // ─── Oluşturucu ───────────────────────────────────────────────────────────

    public UpdateManager(String manifestUrl, String currentVersion, Path appRoot) {
        this.manifestUrl    = manifestUrl;
        this.currentVersion = currentVersion;
        this.appRoot        = appRoot;
        this.tempDir        = appRoot.resolve(".update-tmp");
    }

    // ─── Genel API ────────────────────────────────────────────────────────────

    /**
     * Arka planda güncelleme kontrolü yapar.
     * Thread-safe; Swing EDT'yi bloklamaz.
     *
     * @param onUpdateAvailable  Güncelleme bulunduğunda manifest nesnesiyle çağrılır
     * @param onUpToDate         Güncel olduğunda çağrılır
     */
    public void checkForUpdates(final Consumer<UpdateManifest> onUpdateAvailable,
                                final Runnable onUpToDate) {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    log.info("[Updater] Manifest kontrol ediliyor: {}", manifestUrl);
                    String json = downloadText(manifestUrl);
                    UpdateManifest manifest = UpdateManifest.fromJson(json);
                    log.info("[Updater] Uzak sürüm: {}  |  Yerel sürüm: {}",
                            manifest.getVersion(), currentVersion);

                    if (isNewerVersion(manifest.getVersion(), currentVersion)) {
                        log.info("[Updater] Yeni sürüm bulundu!");
                        onUpdateAvailable.accept(manifest);
                    } else {
                        log.info("[Updater] Uygulama güncel.");
                        onUpToDate.run();
                    }
                } catch (Exception e) {
                    // Ağ hatası olsa da uygulama çalışmaya devam etsin
                    log.warn("[Updater] Manifest indirilemedi: {}", e.getMessage());
                }
            }
        }, "servicio-update-checker");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Güncellemeyi indirir. Yalnızca SHA-256 hash'i farklı olan dosyaları indirir.
     * Hash eşleşen dosyalar atlanır → aynı sürüme hotfix/yama yüklenmiş olsa bile çalışır.
     *
     * @param manifest       Sunucudan alınan manifest
     * @param onProgress     (dosyaAdı, 0.0–1.0) ilerleme geri çağrısı
     * @param onFileSkipped  Dosya atlandığında (aynı hash) dosya adıyla çağrılır
     * @param onFileDone     Dosya başarıyla indirildiğinde dosya adıyla çağrılır
     * @param onDone         Tüm indirmeler tamamlandığında çağrılır
     * @param onError        Hata oluştuğunda çağrılır
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
                    Files.createDirectories(tempDir);

                    List<UpdateManifest.FileEntry> allFiles = manifest.getFiles();
                    if (allFiles == null) allFiles = new ArrayList<UpdateManifest.FileEntry>();

                    // Hash karşılaştırması yap → indirilmesi gerekenleri bul
                    List<UpdateManifest.FileEntry> toDownload = resolveFilesToDownload(allFiles);

                    // Atlanacak dosyaları bildir
                    for (UpdateManifest.FileEntry entry : allFiles) {
                        if (!toDownload.contains(entry)) {
                            log.info("[Updater] Atlanıyor (hash eşleşti): {}", entry.name);
                            onFileSkipped.accept(entry.name);
                        }
                    }

                    // İndir
                    long downloadedBytes = 0;
                    for (UpdateManifest.FileEntry entry : toDownload) {
                        if (cancelRequested) {
                            log.info("[Updater] İptal edildi.");
                            break;
                        }

                        log.info("[Updater] İndiriliyor: {} ({})", entry.name,
                                humanReadableSize(entry.size));

                        Path dest = tempDir.resolve(entry.path);
                        Files.createDirectories(dest.getParent());

                        final String entryName = entry.name;
                        final long   entrySize = entry.size;

                        downloadFile(entry.url, dest, new Consumer<Long>() {
                            @Override
                            public void accept(Long bytesRead) {
                                double progress = entrySize > 0
                                        ? (double) bytesRead / entrySize
                                        : 1.0;
                                onProgress.accept(entryName, progress);
                            }
                        });

                        // Hash doğrula
                        String actualHash = sha256(dest);
                        if (!actualHash.equalsIgnoreCase(entry.sha256)) {
                            throw new IOException(
                                    "Hash doğrulaması başarısız: " + entry.name
                                            + "\n  Beklenen : " + entry.sha256
                                            + "\n  Alınan   : " + actualHash);
                        }

                        downloadedBytes += Files.size(dest);
                        log.info("[Updater] Tamamlandı: {}", entry.name);
                        onFileDone.accept(entry.name);
                    }

                    if (!cancelRequested) {
                        if (completionCallback != null) {
                            completionCallback.accept(downloadedBytes);
                        }
                        onDone.run();
                    }

                } catch (Exception e) {
                    log.error("[Updater] İndirme hatası", e);
                    onError.accept(e);
                }
            }
        }, "servicio-update-downloader");
        t.setDaemon(true);
        t.start();
    }

    /**
     * Geçici dizindeki dosyaları uygulama dizinine taşır.
     * Mevcut dosyalar .bak olarak yedeklenir.
     * Uygulamanın yeniden başlatılmasından ÖNCE çağrılmalıdır.
     *
     * @return Taşınan dosya yolları listesi
     */
    public List<Path> applyUpdate() throws IOException {
        final List<Path> applied = new ArrayList<Path>();

        Files.walkFileTree(tempDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path src, BasicFileAttributes attrs)
                    throws IOException {
                Path relative = tempDir.relativize(src);
                Path dest     = appRoot.resolve(relative);
                Files.createDirectories(dest.getParent());

                if (Files.exists(dest)) {
                    Path bak = dest.resolveSibling(dest.getFileName().toString() + ".bak");
                    Files.copy(dest, bak, StandardCopyOption.REPLACE_EXISTING);
                }

                Files.move(src, dest, StandardCopyOption.REPLACE_EXISTING);
                applied.add(dest);
                log.info("[Updater] Taşındı: {}", dest);
                return FileVisitResult.CONTINUE;
            }
        });

        deleteDirectory(tempDir);
        log.info("[Updater] {} dosya uygulandı.", applied.size());
        return applied;
    }

    /** İndirmeyi iptal eder. */
    public void cancel() {
        cancelRequested = true;
    }

    // ─── Yardımcı Metotlar ────────────────────────────────────────────────────

    /**
     * Dosya listesini SHA-256 hash'leri karşılaştırarak filtreler.
     * Dosya yoksa veya hash farklıysa listeye alınır.
     */
    private List<UpdateManifest.FileEntry> resolveFilesToDownload(
            List<UpdateManifest.FileEntry> files)
            throws IOException, NoSuchAlgorithmException {

        List<UpdateManifest.FileEntry> needed = new ArrayList<UpdateManifest.FileEntry>();

        for (UpdateManifest.FileEntry entry : files) {
            Path localFile = appRoot.resolve(entry.path);

            if (!Files.exists(localFile)) {
                log.info("[Updater] Dosya yok, indirilecek: {}", entry.path);
                needed.add(entry);
                continue;
            }

            String localHash = sha256(localFile);
            if (!localHash.equalsIgnoreCase(entry.sha256)) {
                log.info("[Updater] Hash farklı, indirilecek: {}", entry.name);
                needed.add(entry);
            }
        }

        return needed;
    }

    /**
     * Basit semver karşılaştırması.
     * "2.1.0" > "1.9.0" → true
     */
    public static boolean isNewerVersion(String remote, String local) {
        int[] r = parseSemver(remote);
        int[] l = parseSemver(local);
        for (int i = 0; i < 3; i++) {
            if (r[i] != l[i]) return r[i] > l[i];
        }
        return false;
    }

    private static int[] parseSemver(String version) {
        if (version == null) return new int[]{0, 0, 0};
        // "2.0.0-SNAPSHOT" gibi suffix'leri temizle
        String clean = version.split("-")[0].trim();
        String[] parts = clean.split("\\.");
        int[] nums = new int[3];
        for (int i = 0; i < Math.min(parts.length, 3); i++) {
            try {
                nums[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException ignored) {
                nums[i] = 0;
            }
        }
        return nums;
    }

    /** URL'den UTF-8 metin indirir. */
    private String downloadText(String urlStr) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(15000);
        conn.setRequestProperty("Cache-Control", "no-cache");
        conn.setRequestProperty("User-Agent", "Servicio-Updater/1.0");

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        } finally {
            if (reader != null) {
                try { reader.close(); } catch (IOException ignored) {}
            }
        }
    }

    /** Dosyayı belirtilen yola indirir; her chunk'tan sonra ilerlemeyi bildirir. */
    private void downloadFile(String urlStr, Path dest,
                              Consumer<Long> onBytesRead) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Servicio-Updater/1.0");

        InputStream in   = null;
        OutputStream out = null;
        try {
            in  = conn.getInputStream();
            out = Files.newOutputStream(dest,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            byte[] buf = new byte[8192];
            long totalRead = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelRequested) return;
                out.write(buf, 0, n);
                totalRead += n;
                onBytesRead.accept(totalRead);
            }
        } finally {
            if (in  != null) try { in.close();  } catch (IOException ignored) {}
            if (out != null) try { out.close(); } catch (IOException ignored) {}
        }
    }

    /** Dosyanın SHA-256 hex digest'ini hesaplar. */
    public static String sha256(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = null;
        try {
            in = Files.newInputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                digest.update(buf, 0, n);
            }
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    /** Dizini ve içeriğini tamamen siler. */
    private void deleteDirectory(Path dir) throws IOException {
        if (!Files.exists(dir)) return;
        Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path d, IOException exc)
                    throws IOException {
                Files.delete(d);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static String humanReadableSize(long bytes) {
        if (bytes < 1024)       return bytes + " B";
        if (bytes < 1048576)    return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1048576.0);
    }

}