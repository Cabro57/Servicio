package tr.cabro.servicio.updater;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * update-manifest.json modeli.
 * <p>
 * Dosya türleri:
 * <p>
 *  1. JAR (ana uygulama) — GitHub Releases'ten indirilir.
 *     "source": "github"
 *     "url": "https://github.com/USER/REPO/releases/download/vX.Y.Z/servicio.jar"
 * <p>
 *  2. Kütüphane — Maven Central'dan koordinatla indirilir.
 *     "source": "maven"
 *     "groupId": "com.formdev"
 *     "artifactId": "flatlaf"
 *     "version": "3.7.1"
 *     → URL çalışma zamanında otomatik üretilir:
 *       https://repo1.maven.org/maven2/com/formdev/flatlaf/3.7.1/flatlaf-3.7.1.jar
 * <p>
 *  3. Özel URL — herhangi bir HTTP kaynağından.
 *     "source": "url"
 *     "url": "https://updates.example.com/dosya.jar"
 * <p>
 * sha256 ve size her zaman zorunludur (hash doğrulama + ilerleme çubuğu).
 * path: uygulamanın köküne göre hedef konum, örn: "libs/flatlaf-3.7.1.jar"
 */
@Getter
public class UpdateManifest {

    @SerializedName("version")
    private String version;

    @SerializedName("releaseDate")
    private String releaseDate;

    @SerializedName("minRequiredVersion")
    private String minRequiredVersion;

    @SerializedName("patchNotes")
    private List<PatchNote> patchNotes;

    @SerializedName("files")
    private List<FileEntry> files;

    // ─── İç: Patch Notu ──────────────────────────────────────────────────────

    @Getter
    public static class PatchNote {

        @SerializedName("version")
        private String version;

        @SerializedName("date")
        private String date;

        @SerializedName("changes")
        private List<String> changes;

    }

    // ─── İç: Dosya Kaydı ─────────────────────────────────────────────────────

    public static class FileEntry {

        /**
         * İndirme kaynağı:
         *   "maven"  → Maven Central koordinatlarıyla
         *   "github" → Doğrudan URL (GitHub Releases)
         *   "url"    → Doğrudan URL (özel sunucu)
         */
        @SerializedName("source")
        public String source;

        // ── Maven alanları (source="maven") ──────────────────────────────────

        @SerializedName("groupId")
        public String groupId;

        @SerializedName("artifactId")
        public String artifactId;

        @SerializedName("libVersion")
        public String libVersion;

        /**
         * Alternatif Maven repository URL'si.
         * Boşsa varsayılan olarak Maven Central kullanılır:
         *   https://repo1.maven.org/maven2
         * Okaeri gibi özel repo'lar için:
         *   https://storehouse.okaeri.eu/repository/maven-public
         */
        @SerializedName("repository")
        public String repository;

        // ── Doğrudan URL alanları (source="github" veya "url") ───────────────

        @SerializedName("url")
        public String url;

        // ── Ortak alanlar ─────────────────────────────────────────────────────

        /** Görünen ad, örn: "flatlaf-3.7.1.jar" */
        @SerializedName("name")
        public String name;

        /** Uygulama köküne göre hedef yol, örn: "libs/flatlaf-3.7.1.jar" */
        @SerializedName("path")
        public String path;

        /** SHA-256 hex digest — her zaman zorunlu */
        @SerializedName("sha256")
        public String sha256;

        /** Bayt cinsinden boyut */
        @SerializedName("size")
        public long size;

        // ── Yardımcı ─────────────────────────────────────────────────────────

        /**
         * İndirme URL'sini çözümler.
         * <p>
         * source="maven"  → Maven Central (veya özel repo) URL'si üretilir.
         * source="github" veya "url" → url alanı doğrudan döner.
         */
        public String resolveDownloadUrl() {
            if ("maven".equalsIgnoreCase(source)) {
                return buildMavenUrl();
            }
            return url;
        }

        /**
         * Maven Central URL formatı:
         * https://repo1.maven.org/maven2/{group/path}/{artifactId}/{version}/{artifactId}-{version}.jar
         * <p>
         * groupId nokta → / dönüşümü yapılır.
         * Örn: "com.formdev" + "flatlaf" + "3.7.1"
         *   → https://repo1.maven.org/maven2/com/formdev/flatlaf/3.7.1/flatlaf-3.7.1.jar
         */
        private String buildMavenUrl() {
            String base = (repository != null && !repository.trim().isEmpty())
                    ? repository.trim().replaceAll("/$", "")   // sondaki / kaldır
                    : "https://repo1.maven.org/maven2";

            String groupPath = groupId.replace('.', '/');
            String fileName  = artifactId + "-" + libVersion + ".jar";

            return base + "/" + groupPath + "/" + artifactId + "/" + libVersion + "/" + fileName;
        }

        /** Maven koordinatlarından standart JAR adı üretir: "flatlaf-3.7.1.jar" */
        public String resolveFileName() {
            if ("maven".equalsIgnoreCase(source)) {
                return artifactId + "-" + libVersion + ".jar";
            }
            return name;
        }

        @Override
        public String toString() {
            return "FileEntry{name='" + name + "', source='" + source + "', path='" + path + "'}";
        }
    }

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static UpdateManifest fromJson(String json) {
        return new Gson().fromJson(json, UpdateManifest.class);
    }
}