package tr.cabro.servicio.build;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
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
 * groupId çözümleme kaynakları (öncelik sırasıyla):
 *   1. target/dependency-list.txt  — mvn dependency:list çıktısı (transitifler dahil)
 *   2. pom.xml                     — doğrudan bağımlılıklar
 *   3. KNOWN_GROUPS tablosu        — elle tanımlı bilinen eşlemeler (fallback)
 * <p>
 * Argümanlar:
 *   0 → appVersion         (örn: "2.1.0")
 *   1 → githubReleaseUrl   (örn: "https://github.com/USER/REPO/releases/download/v2.1.0")
 *   2 → mainJarPath        (örn: "target/servicio.jar")
 *   3 → libsDir            (örn: "target/libs")
 *   4 → outputPath         (örn: "target/update-manifest.json")
 *   5 → pomPath            (örn: "pom.xml")
 *   6 → depListPath        (örn: "target/dependency-list.txt")
 */
public class ManifestGenerator {

    // ─── Maven repo sabitleri ─────────────────────────────────────────────────

    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";

    /**
     * Özel repolar: groupId prefix → repo URL.
     * pom.xml'deki <repositories> bölümünü buraya yansıtın.
     */
    private static final Map<String, String> CUSTOM_REPOS = new LinkedHashMap<>();
    static {
        CUSTOM_REPOS.put("eu.okaeri", "https://storehouse.okaeri.eu/repository/maven-public");
    }

    /**
     * Bilinen transitif bağımlılıklar için sabit eşleme tablosu.
     * "artifactId" → "groupId:artifactId"
     * <p>
     * Maven dependency:list bunu otomatik çözüyor; bu tablo yalnızca
     * dependency-list.txt üretilemediğinde devreye giren fallback'tir.
     */
    private static final Map<String, String> KNOWN_GROUPS = new LinkedHashMap<String, String>();
    static {
        // artifactId  →  groupId
        KNOWN_GROUPS.put("filters",         "com.jhlabs");
        KNOWN_GROUPS.put("geantyref",       "io.leangen.geantyref");
        KNOWN_GROUPS.put("gson",            "com.google.code.gson");
        KNOWN_GROUPS.put("jsvg",            "com.github.weisj");
        KNOWN_GROUPS.put("logback-core",    "ch.qos.logback");
        KNOWN_GROUPS.put("miglayout-core",  "com.miglayout");
        KNOWN_GROUPS.put("miglayout-swing", "com.miglayout");
        KNOWN_GROUPS.put("swing-worker",    "org.swinglabs");
        // Gerekirse buraya eklemeye devam edin
    }

    // ─── main ─────────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // Windows konsolunda UTF-8 çıktı (encoding bozukluğunu önler)
        PrintStream out = new PrintStream(System.out, true, "UTF-8");
        PrintStream err = new PrintStream(System.err, true, "UTF-8");

        if (args.length < 6) {
            err.println("Kullanim: ManifestGenerator <version> <githubReleaseUrl> " +
                    "<mainJar> <libsDir> <output> <pomPath> [depListPath]");
            System.exit(1);
        }

        String appVersion  = args[0];
        String githubBase  = args[1];
        File   mainJar     = new File(args[2]);
        File   libsDir     = new File(args[3]);
        File   outputFile  = new File(args[4]);
        File   pomFile     = new File(args[5]);
        File   depListFile = args.length > 6 ? new File(args[6]) : null;

        out.println("=== ManifestGenerator v" + appVersion + " ===");

        // ── groupId haritası oluştur ───────────────────────────────────────────
        // Öncelik: dependency-list.txt > pom.xml > KNOWN_GROUPS

        // 3. Öncelik: KNOWN_GROUPS (en düşük, üzerine yazılabilir)
        Map<String, String> artifactGroupMap = new LinkedHashMap<>(KNOWN_GROUPS);
        out.println("Bilinen transitifler: " + KNOWN_GROUPS.size() + " kayıt");

        // 2. Öncelik: pom.xml
        Map<String, String> pomMap = parsePomDependencies(pomFile);
        artifactGroupMap.putAll(pomMap);
        out.println("POM'dan okunan: " + pomMap.size() + " bağımlılık");

        // 1. Öncelik: dependency-list.txt (transitifler dahil, en güvenilir kaynak)
        if (depListFile != null && depListFile.exists()) {
            Map<String, String> depMap = parseDependencyList(depListFile);
            artifactGroupMap.putAll(depMap);
            out.println("dependency-list.txt'ten okunan: " + depMap.size() + " bağımlılık");
        } else {
            out.println("[BILGI] dependency-list.txt bulunamadi, POM + KNOWN_GROUPS kullaniliyor.");
            out.println("        Tum transifler icin pom-additions.xml'deki dependency:list hedefini ekleyin.");
        }

        out.println("Toplam esleme: " + artifactGroupMap.size() + " kayit");
        out.println();

        // Patch notları artık manifest.json'da tutulmuyor — GitHub Releases API'sinden çekilir.

        List<FileRecord> records  = new ArrayList<FileRecord>();
        int mavenCount  = 0;
        int urlCount    = 0;
        int warnCount   = 0;

        // ── Ana JAR ─────────────────────────────────────────────────────────
        // Hash doğrudan derleme çıktısından (target/servicio.jar) alınır.
        // Bu dosya GitHub Release'e yüklenen dosyanın ta kendisidir;
        // ayrıca GitHub'dan indirip hash'lemeye gerek yoktur.
        if (mainJar.exists()) {
            FileRecord rec = new FileRecord();
            rec.source = "github";
            rec.name   = mainJar.getName();
            rec.path   = mainJar.getName();
            rec.url    = githubBase + "/" + mainJar.getName();
            rec.sha256 = sha256(mainJar);
            rec.size   = mainJar.length();
            records.add(rec);
            out.printf("[GITHUB]   %s  sha256=%s...%n", rec.name, rec.sha256.substring(0, 12));
        } else {
            err.println("[HATA] Ana JAR bulunamadı: " + mainJar.getAbsolutePath());
            err.println("       Önce 'mvn package' komutunu çalıştırın.");
            System.exit(1);
        }

        // ── Kütüphaneler ──────────────────────────────────────────────────────
        if (libsDir.exists()) {
            File[] libs = libsDir.listFiles((dir, name) -> name.endsWith(".jar"));

            if (libs != null) {
                Arrays.sort(libs);
                for (File lib : libs) {
                    FileRecord rec = processLibrary(lib, artifactGroupMap, err);
                    records.add(rec);

                    if ("maven".equals(rec.source)) {
                        out.printf("[MAVEN]    %s:%s:%s%n",
                                rec.groupId, rec.artifactId, rec.libVersion);
                        mavenCount++;
                    } else {
                        if (rec.url == null || rec.url.isEmpty()) {
                            err.printf("[UYARI]    %s → groupId bulunamadi, url bos!%n", rec.name);
                            warnCount++;
                        } else {
                            out.printf("[URL]      %s%n", rec.name);
                        }
                        urlCount++;
                    }
                }
            }
        }

        // ── Özet ──────────────────────────────────────────────────────────────
        out.println();
        out.printf("Sonuc: %d maven, %d url, %d uyari%n", mavenCount, urlCount, warnCount);
        if (warnCount > 0) {
            err.println();
            err.println("COZUM: Bos URL'li JAR'lar icin:");
            err.println("  pom-additions.xml'deki dependency:list hedefini ekleyin (otomatik cozer)");
            err.println("  VEYA ManifestGenerator.KNOWN_GROUPS'a manuel ekleyin.");
        }

        // ── JSON üret ─────────────────────────────────────────────────────────
        String today          = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String releasesApiUrl = deriveReleasesApiUrl(githubBase);
        out.println("releasesApiUrl: " + releasesApiUrl);
        String json  = buildJson(appVersion, today, releasesApiUrl, records);

        outputFile.getParentFile().mkdirs();
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(
                Files.newOutputStream(outputFile.toPath()), StandardCharsets.UTF_8))) {
            pw.print(json);
        }

        out.println("=== Tamamlandi: " + records.size() + " dosya -> " + outputFile + " ===");
    }

    // ─── Kütüphane İşleme ────────────────────────────────────────────────────

    private static FileRecord processLibrary(File lib,
                                             Map<String, String> artifactGroupMap,
                                             PrintStream err) throws Exception {
        FileRecord rec = new FileRecord();
        rec.sha256 = sha256(lib);
        rec.size   = lib.length();
        rec.name   = lib.getName();
        rec.path   = "libs/" + lib.getName();

        String baseName   = lib.getName().replaceAll("\\.jar$", "");
        String artifactId = extractArtifactId(baseName);
        String version    = extractVersion(baseName);

        if (artifactId == null || version == null) {
            rec.source = "url";
            rec.url    = "";
            err.println("[UYARI] Dosya adi parse edilemedi: " + lib.getName());
            return rec;
        }

        rec.artifactId = artifactId;
        rec.libVersion = version;

        // groupId ara — tam eşleşme veya prefix eşleşmesi
        String groupId = findGroupId(artifactId, artifactGroupMap);

        if (groupId == null) {
            rec.source = "url";
            rec.url    = "";
            // Uyarı çağıran tarafta basılıyor
            return rec;
        }

        rec.groupId = groupId;

        String repoUrl = resolveRepository(groupId);
        rec.repository = repoUrl.equals(MAVEN_CENTRAL_BASE) ? null : repoUrl;

        String mavenUrl = buildMavenUrl(repoUrl, groupId, artifactId, version);
        if (isUrlReachable(mavenUrl)) {
            rec.source = "maven";
        } else {
            rec.source = "url";
            rec.url    = mavenUrl;
            err.println("[FALLBACK] Maven'da bulunamadi, url kaynagi: " + mavenUrl);
        }

        return rec;
    }

    /**
     * artifactId için groupId arar.
     * Tam eşleşme, ardından kısmi eşleşme (prefix/suffix) dener.
     */
    private static String findGroupId(String artifactId, Map<String, String> map) {
        // 1. Tam eşleşme
        if (map.containsKey(artifactId)) return map.get(artifactId);

        // 2. Kısmi eşleşme: haritadaki anahtar artifactId'nin prefix'i mi?
        //    Örn: "logback" → "logback-core" ve "logback-classic" için
        for (Map.Entry<String, String> e : map.entrySet()) {
            String key = e.getKey();
            if (artifactId.startsWith(key) || key.startsWith(artifactId)) {
                return e.getValue();
            }
        }

        return null;
    }

    // ─── dependency-list.txt Parse ───────────────────────────────────────────

    /**
     * mvn dependency:list -DoutputFile=target/dependency-list.txt çıktısını parse eder.
     * <p>
     * Format (her satır):
     *   groupId:artifactId:jar:version:scope
     *   örn: com.google.code.gson:gson:jar:2.9.1:compile
     * <p>
     * Çıktı: artifactId → groupId eşlemesi (tüm transitifler dahil).
     */
    private static Map<String, String> parseDependencyList(File file) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(
                    Files.newInputStream(file.toPath()), detectCharset(file)));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                // Boş satır veya başlık satırı atla
                if (line.isEmpty() || line.startsWith("The following")) continue;
                // groupId:artifactId:jar:version:scope formatını ayrıştır
                String[] parts = line.split(":");
                if (parts.length >= 4) {
                    String groupId    = parts[0].trim();
                    String artifactId = parts[1].trim();
                    if (!groupId.isEmpty() && !artifactId.isEmpty()) {
                        map.put(artifactId, groupId);
                        // Kısa kök da ekle: "logback-core" → "logback" → ch.qos.logback
                        String base = artifactId.split("-")[0];
                        if (!base.equals(artifactId) && !map.containsKey(base)) {
                            map.put(base, groupId);
                        }
                    }
                }
            }
        } finally {
            if (br != null) try { br.close(); } catch (IOException ignored) { }
        }
        return map;
    }

    /**
     * Dosyanın encoding'ini tahmin eder.
     * Maven bazı sistemlerde platform encoding kullanır.
     */
    private static String detectCharset(File file) {
        // BOM kontrolü
        try {
            FileInputStream fis = new FileInputStream(file);
            byte[] bom = new byte[3];
            int read = fis.read(bom);
            fis.close();
            if (read >= 3 && bom[0] == (byte)0xEF && bom[1] == (byte)0xBB && bom[2] == (byte)0xBF) {
                return "UTF-8";
            }
        } catch (IOException ignored) { }
        // Varsayılan: sistem encoding
        return Charset.defaultCharset().name();
    }

    // ─── POM Parse ───────────────────────────────────────────────────────────

    private static Map<String, String> parsePomDependencies(File pom) throws IOException {
        Map<String, String> map = new LinkedHashMap<>();
        if (!pom.exists()) return map;

        String content = readFile(pom);
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
                map.put(artifactId.trim(), groupId.trim());
                // Kısa kök: "jdbi3-core" → "jdbi3"
                String base = artifactId.split("[-.]")[0];
                if (!base.equals(artifactId.trim())) {
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

    // ─── Repo / URL Yardımcıları ─────────────────────────────────────────────

    private static String resolveRepository(String groupId) {
        for (Map.Entry<String, String> entry : CUSTOM_REPOS.entrySet()) {
            if (groupId.startsWith(entry.getKey())) return entry.getValue();
        }
        return MAVEN_CENTRAL_BASE;
    }

    private static String buildMavenUrl(String repoBase, String groupId,
                                        String artifactId, String version) {
        String groupPath = groupId.replace('.', '/');
        String fileName  = artifactId + "-" + version + ".jar";
        return repoBase.replaceAll("/$", "")
                + "/" + groupPath + "/" + artifactId + "/" + version + "/" + fileName;
    }

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

    // ─── Dosya Adı Parse ─────────────────────────────────────────────────────

    private static String extractArtifactId(String baseName) {
        String[] parts = baseName.split("-");
        for (int i = parts.length - 1; i >= 0; i--) {
            if (parts[i].matches("\\d+.*")) {
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

    // Patch notları artık manifest.json'da saklanmıyor — GitHub Releases API kullanılıyor.

    // ─── JSON Üretici ─────────────────────────────────────────────────────────

    /**
     * GitHub Release download URL'inden Releases API URL'i türetir.
     * <p>
     * Girdi:  https://github.com/Cabro57/Servicio/releases/download/v2.0-dev
     * Çıktı:  https://api.github.com/repos/Cabro57/Servicio/releases/latest
     * <p>
     * Ayrıştırılamazsa placeholder döner (build durmaz).
     */
    private static String deriveReleasesApiUrl(String githubBase) {
        final String fallback = "https://api.github.com/repos/KULLANICI/REPO/releases/latest";
        if (githubBase == null) return fallback;
        int idx = githubBase.indexOf("github.com/");
        if (idx < 0) return fallback;
        String rest = githubBase.substring(idx + "github.com/".length());
        String[] parts = rest.split("/");
        if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) return fallback;
        return "https://api.github.com/repos/" + parts[0] + "/" + parts[1]
                + "/releases/latest";
    }

    private static String buildJson(String version, String date,
                                    String releasesApiUrl, List<FileRecord> records) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(q(version)).append(",\n");
        sb.append("  \"releaseDate\": ").append(q(date)).append(",\n");
        sb.append("  \"minRequiredVersion\": \"2.0.0\",\n");

        // patchNotes artık manifest.json'da YOK.
        // Patch notları GitHub Releases API'sinden (releasesApiUrl) çekilir.
        // Bkz: UpdateManifest.GitHubReleaseInfo ve UpdateManager.fetchReleaseInfo()
        sb.append("  \"releasesApiUrl\": ").append(q(releasesApiUrl)).append(",\n");

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

    // ─── Genel Yardımcılar ────────────────────────────────────────────────────

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
        String source;
        String name;
        String path;
        String url;
        String groupId;
        String artifactId;
        String libVersion;
        String repository;
        String sha256;
        long   size;
    }
}