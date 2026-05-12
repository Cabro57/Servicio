package tr.cabro.servicio.build;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ManifestGenerator — Derleme zamanı aracı.
 * <p>
 * mvn package sonrasında maven-antrun-plugin tarafından çalıştırılır.
 * <p>
 * Yaptıkları:
 *   1. Ana JAR (servicio.jar) → SHA-256 hesapla, GitHub Releases URL'si ile kaydet.
 *   2. target/libs/*.jar → Her biri için:
 *        a. Dosya adından groupId/artifactId/version çıkar
 *           (maven-dependency-plugin dosyaları "artifactId-version.jar" şeklinde kopyalar)
 *        b. Eğer target/libs/ yanında .pom dosyası varsa groupId oradan okunur.
 *        c. Maven Central'dan indirilip indirilemeyeceğini kontrol eder.
 *           → İndirilebiliyorsa: source="maven" koordinatlarıyla yaz.
 *           → İndirilemiyorsa (özel repo / yerel): source="url", fallback URL ile yaz.
 *        d. SHA-256 hesaplanır.
 *   3. Mevcut manifest varsa eski patch notları korunur.
 *   4. Çıktı: target/update-manifest.json
 *
 * KONUMU: src/main/java/tr/cabro/servicio/build/ManifestGenerator.java
 *
 * Argümanlar:
 *   0 → appVersion       (örn: "2.1.0")
 *   1 → githubReleaseUrl (örn: "https://github.com/USER/REPO/releases/download/v2.1.0")
 *   2 → mainJarPath      (örn: "target/servicio.jar")
 *   3 → libsDir          (örn: "target/libs")
 *   4 → outputPath       (örn: "target/update-manifest.json")
 *   5 → pomPath          (örn: "pom.xml")   ← groupId eşleme için
 */
public class ManifestGenerator {

    // Maven Central kontrol URL'si
    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";

    // Özel repolar — bu listedeki groupId'ler için alternatif repo URL kullanılır
    // pom.xml'deki <repositories> bölümünü buraya yansıtın
    private static final Map<String, String> CUSTOM_REPOS = new LinkedHashMap<>();
    static {
        // groupId prefix → repo URL
        CUSTOM_REPOS.put("eu.okaeri",       "https://storehouse.okaeri.eu/repository/maven-public");
        CUSTOM_REPOS.put("io.github.dj-raven", MAVEN_CENTRAL_BASE); // Maven Central'da var
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 6) {
            System.err.println("Kullanim: ManifestGenerator <version> <githubReleaseUrl> " +
                    "<mainJar> <libsDir> <output> <pomPath>");
            System.exit(1);
        }

        String appVersion      = args[0];
        String githubBase      = args[1];  // örn: .../releases/download/v2.1.0
        File   mainJar         = new File(args[2]);
        File   libsDir         = new File(args[3]);
        File   outputFile      = new File(args[4]);
        File   pomFile         = new File(args[5]);

        System.out.println("=== ManifestGenerator v" + appVersion + " ===");

        // pom.xml'den groupId → artifactId eşlemesi oku
        Map<String, String> artifactGroupMap = parsePomDependencies(pomFile);
        System.out.println("POM'dan " + artifactGroupMap.size() + " bağımlılık okundu.");

        // Mevcut manifest patch notlarını oku
        List<String> oldPatchNotes = new ArrayList<>();
        if (outputFile.exists()) {
            oldPatchNotes = extractPatchNotes(outputFile, appVersion);
            System.out.println("Eski patch notları korunuyor: " + oldPatchNotes.size() + " sürüm");
        }

        List<FileRecord> records = new ArrayList<>();

        // ── 1. Ana JAR — GitHub Releases ──────────────────────────────────────
        if (mainJar.exists()) {
            FileRecord rec = new FileRecord();
            rec.source  = "github";
            rec.name    = mainJar.getName();
            rec.path    = mainJar.getName();
            rec.url     = githubBase + "/" + mainJar.getName();
            rec.sha256  = sha256(mainJar);
            rec.size    = mainJar.length();
            records.add(rec);
            System.out.println("[JAR]    " + rec.name + "  sha256=" + rec.sha256.substring(0, 12) + "...");
        } else {
            System.err.println("[UYARI] Ana JAR bulunamadı: " + mainJar);
        }

        // ── 2. Kütüphaneler — Maven Central veya özel repo ───────────────────
        if (libsDir.exists()) {
            File[] libs = libsDir.listFiles((dir, name) -> name.endsWith(".jar"));

            if (libs != null) {
                Arrays.sort(libs);
                for (File lib : libs) {
                    FileRecord rec = processLibrary(lib, artifactGroupMap);
                    records.add(rec);

                    String sourceTag = "[" + rec.source.toUpperCase() + "]";
                    if ("maven".equals(rec.source)) {
                        System.out.printf("%-10s %s:%s:%s%n",
                                sourceTag, rec.groupId, rec.artifactId, rec.libVersion);
                    } else {
                        System.out.printf("%-10s %s  (url=%s)%n",
                                sourceTag, rec.name, rec.url);
                    }
                }
            }
        }

        // ── 3. JSON üret ──────────────────────────────────────────────────────
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String json  = buildJson(appVersion, today, records, oldPatchNotes);

        outputFile.getParentFile().mkdirs();
        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(
                    Files.newOutputStream(outputFile.toPath()), StandardCharsets.UTF_8));
            pw.print(json);
        } finally {
            if (pw != null) pw.close();
        }

        System.out.println("=== Tamamlandı: " + records.size() + " dosya → " + outputFile + " ===");
    }

    // ─── Kütüphane İşleme ────────────────────────────────────────────────────

    /**
     * Tek bir kütüphane JAR dosyasını inceler.
     * maven-dependency-plugin dosyaları "artifactId-version.jar" formatında kopyalar.
     */
    private static FileRecord processLibrary(File lib,
                                             Map<String, String> artifactGroupMap) throws Exception {
        FileRecord rec = new FileRecord();
        rec.sha256 = sha256(lib);
        rec.size   = lib.length();
        rec.name   = lib.getName();
        rec.path   = "libs/" + lib.getName();

        // Dosya adından artifactId ve version çıkar
        // Örn: "flatlaf-3.7.1.jar" → artifactId="flatlaf", version="3.7.1"
        String baseName   = lib.getName().replaceAll("\\.jar$", "");
        String artifactId = extractArtifactId(baseName);
        String version    = extractVersion(baseName);

        if (artifactId == null || version == null) {
            // Parse edilemedi → doğrudan URL olarak işaretle
            rec.source = "url";
            rec.url    = "";   // Kullanıcı doldurur
            System.err.println("[UYARI] Dosya adı parse edilemedi: " + lib.getName());
            return rec;
        }

        rec.artifactId = artifactId;
        rec.libVersion = version;

        // groupId'yi POM eşlemesinden al
        String groupId = artifactGroupMap.get(artifactId);
        if (groupId == null) {
            // Bilinmiyor — URL olarak kaydet
            rec.source = "url";
            rec.url    = "";
            System.err.println("[UYARI] groupId bulunamadı: " + artifactId + " → url kaynağı");
            return rec;
        }
        rec.groupId = groupId;

        // Hangi repo'dan indirileceğini belirle
        String repoUrl = resolveRepository(groupId);
        rec.repository = repoUrl.equals(MAVEN_CENTRAL_BASE) ? null : repoUrl;

        // Maven repo'da gerçekten var mı? HEAD isteği ile kontrol et
        String mavenUrl = buildMavenUrl(repoUrl, groupId, artifactId, version);
        if (isUrlReachable(mavenUrl)) {
            rec.source = "maven";
        } else {
            // Repo'da yok → doğrudan URL olarak kaydet (fallback)
            rec.source = "url";
            rec.url    = mavenUrl;
            System.err.println("[FALLBACK] Maven'da bulunamadı, url kaynağı: " + mavenUrl);
        }

        return rec;
    }

    /**
     * groupId'ye göre doğru repo URL'sini döner.
     * CUSTOM_REPOS'ta prefix eşleşmesi arar.
     */
    private static String resolveRepository(String groupId) {
        for (Map.Entry<String, String> entry : CUSTOM_REPOS.entrySet()) {
            if (groupId.startsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return MAVEN_CENTRAL_BASE;
    }

    /** Maven artifact URL'si üretir. */
    private static String buildMavenUrl(String repoBase, String groupId,
                                        String artifactId, String version) {
        String groupPath = groupId.replace('.', '/');
        String fileName  = artifactId + "-" + version + ".jar";
        return repoBase.replaceAll("/$", "")
                + "/" + groupPath + "/" + artifactId + "/" + version + "/" + fileName;
    }

    /** HTTP HEAD isteği ile URL'nin erişilebilir olduğunu kontrol eder. */
    private static boolean isUrlReachable(String urlStr) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "Servicio-ManifestGenerator");
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            conn.disconnect();
            return code == 200 || code == 301 || code == 302;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── POM Parse ───────────────────────────────────────────────────────────

    /**
     * pom.xml'den tüm <dependency> bloklarını okur.
     * Çıktı: artifactId → groupId eşlemesi.
     * Harici kütüphane kullanmaz; basit string arama ile parse eder.
     */
    private static Map<String, String> parsePomDependencies(File pom) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!pom.exists()) return map;

        String content = readFile(pom);

        // <dependency> bloklarını ayıkla
        int search = 0;
        while (true) {
            int start = content.indexOf("<dependency>", search);
            if (start < 0) break;
            int end = content.indexOf("</dependency>", start);
            if (end < 0) break;
            String block = content.substring(start, end + "</dependency>".length());
            search = end + 1;

            String groupId    = extractXmlTag(block, "groupId");
            String artifactId = extractXmlTag(block, "artifactId");

            if (groupId != null && artifactId != null) {
                // artifactId → groupId
                map.put(artifactId.trim(), groupId.trim());

                // Bazı artifactId'ler bileşik olabilir (örn: jdbi3-core)
                // Temel kısmını da ekle: "jdbi3" → groupId
                String base = artifactId.split("[-.]")[0];
                if (!base.equals(artifactId)) {
                    map.putIfAbsent(base, groupId.trim());
                }
            }
        }
        return map;
    }

    private static String extractXmlTag(String block, String tag) {
        String open  = "<" + tag + ">";
        String close = "</" + tag + ">";
        int s = block.indexOf(open);
        int e = block.indexOf(close);
        if (s < 0 || e < 0) return null;
        return block.substring(s + open.length(), e).trim();
    }

    // ─── Dosya Adı Parse ─────────────────────────────────────────────────────

    /**
     * "flatlaf-3.7.1" → "flatlaf"
     * "slf4j-api-1.7.36" → "slf4j-api"
     * "jdbi3-core-3.37.1" → "jdbi3-core"
     * <p>
     * Strateji: sağdan ilk semver benzeri parçayı bul, öncesi artifactId'dir.
     */
    private static String extractArtifactId(String baseName) {
        // Sağdan tara; ilk rakamla başlayan (semver) parçayı bul
        String[] parts = baseName.split("-");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+.*")) {
                // i öncesi artifactId
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < i; j++) {
                    if (j > 0) sb.append('-');
                    sb.append(parts[j]);
                }
                return sb.length() > 0 ? sb.toString() : null;
            }
        }
        return null;
    }

    /**
     * "flatlaf-3.7.1" → "3.7.1"
     * "slf4j-api-1.7.36" → "1.7.36"
     */
    private static String extractVersion(String baseName) {
        String[] parts = baseName.split("-");
        StringBuilder version = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+.*")) {
                if (version.length() > 0) version.insert(0, '-');
                version.insert(0, parts[i]);
            } else {
                break;
            }
        }
        return version.length() > 0 ? version.toString() : null;
    }

    // ─── Patch Notu Koruma ────────────────────────────────────────────────────

    private static List<String> extractPatchNotes(File manifest, String newVersion) throws IOException {
        String content = readFile(manifest);
        int start = content.indexOf("\"patchNotes\"");
        if (start < 0) return new ArrayList<>();

        int arrStart = content.indexOf('[', start);
        if (arrStart < 0) return new ArrayList<>();

        int depth = 0, arrEnd = -1;
        for (int i = arrStart; i < content.length(); i++) {
            if (content.charAt(i) == '[') depth++;
            else if (content.charAt(i) == ']') {
                if (--depth == 0) { arrEnd = i; break; }
            }
        }
        if (arrEnd < 0) return new ArrayList<>();

        List<String> objects = splitJsonObjects(content.substring(arrStart + 1, arrEnd));
        List<String> result  = new ArrayList<>();
        for (String obj : objects) {
            if (!obj.contains("\"" + newVersion + "\"")) result.add(obj.trim());
        }
        return result;
    }

    private static List<String> splitJsonObjects(String arr) {
        List<String> result = new ArrayList<>();
        int depth = 0, start = -1;
        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if (ch == '{') { if (depth++ == 0) start = i; }
            else if (ch == '}') {
                if (--depth == 0 && start >= 0) {
                    result.add(arr.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    // ─── JSON Üretici ─────────────────────────────────────────────────────────

    private static String buildJson(String version, String date,
                                    List<FileRecord> records,
                                    List<String> oldPatchNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(q(version)).append(",\n");
        sb.append("  \"releaseDate\": ").append(q(date)).append(",\n");
        sb.append("  \"minRequiredVersion\": \"2.0.0\",\n");

        // Patch notları
        sb.append("  \"patchNotes\": [\n");
        sb.append("    {\n");
        sb.append("      \"version\": ").append(q(version)).append(",\n");
        sb.append("      \"date\": ").append(q(date)).append(",\n");
        sb.append("      \"changes\": [\n");
        sb.append("        \"Degisiklikleri buraya yazin\"\n");
        sb.append("      ]\n");
        sb.append("    }");
        for (String old : oldPatchNotes) sb.append(",\n    ").append(old);
        sb.append("\n  ],\n");

        // Dosyalar
        sb.append("  \"files\": [\n");
        for (int i = 0; i < records.size(); i++) {
            FileRecord r = records.get(i);
            sb.append("    {\n");
            sb.append("      \"source\": ").append(q(r.source)).append(",\n");
            sb.append("      \"name\": ").append(q(r.name != null ? r.name : "")).append(",\n");
            sb.append("      \"path\": ").append(q(r.path)).append(",\n");

            if ("maven".equals(r.source)) {
                sb.append("      \"groupId\": ").append(q(r.groupId)).append(",\n");
                sb.append("      \"artifactId\": ").append(q(r.artifactId)).append(",\n");
                sb.append("      \"libVersion\": ").append(q(r.libVersion)).append(",\n");
                if (r.repository != null) {
                    sb.append("      \"repository\": ").append(q(r.repository)).append(",\n");
                }
            } else {
                sb.append("      \"url\": ").append(q(r.url != null ? r.url : "")).append(",\n");
            }

            sb.append("      \"sha256\": ").append(q(r.sha256)).append(",\n");
            sb.append("      \"size\": ").append(r.size).append("\n");
            sb.append("    }");
            if (i < records.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    // ─── Dosya / Hash Yardımcıları ────────────────────────────────────────────

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        InputStream in = null;
        try {
            in = Files.newInputStream(file.toPath());
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) != -1) digest.update(buf, 0, n);
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) { }
        }
        StringBuilder hex = new StringBuilder();
        for (byte b : digest.digest()) hex.append(String.format("%02x", b));
        return hex.toString();
    }

    private static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder((int) f.length());
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(Files.newInputStream(f.toPath()), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignored) { }
        }
        return sb.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ─── Inner ───────────────────────────────────────────────────────────────

    private static class FileRecord {
        String source;      // "maven" | "github" | "url"
        String name;
        String path;
        String url;         // source=github/url
        String groupId;     // source=maven
        String artifactId;  // source=maven
        String libVersion;  // source=maven
        String repository;  // source=maven, null=Maven Central
        String sha256;
        long   size;
    }
}