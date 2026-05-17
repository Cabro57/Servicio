package tr.cabro.servicio.updater;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * update-manifest.json modeli.
 * <p>
 * Patch notları bu dosyada TUTULMAZ.
 * GitHub Releases sayfasındaki release body'den çekilir.
 * Bkz: GitHubReleaseInfo
 * <p>
 * Dosya türleri:
 *   source="github" → GitHub Releases URL
 *   source="maven"  → Maven Central koordinatı
 *   source="url"    → Özel HTTP kaynağı
 */
@Getter
public class UpdateManifest {

    @SerializedName("version")
    private String version;

    @SerializedName("releaseDate")
    private String releaseDate;

    @SerializedName("minRequiredVersion")
    private String minRequiredVersion;

    /**
     * GitHub Releases API URL'si — patch notları buradan çekilir.
     * Örn: https://api.github.com/repos/Cabro57/Servicio/releases/latest
     * veya belirli bir tag için:
     *      https://api.github.com/repos/Cabro57/Servicio/releases/tags/v2.1.0
     */
    @SerializedName("releasesApiUrl")
    private String releasesApiUrl;

    @SerializedName("files")
    private List<FileEntry> files;

    // ─── İç: Dosya Kaydı ─────────────────────────────────────────────────────

    public static class FileEntry {

        @SerializedName("source")
        public String source;       // "maven" | "github" | "url"

        @SerializedName("name")
        public String name;

        @SerializedName("path")
        public String path;

        // maven alanları
        @SerializedName("groupId")
        public String groupId;

        @SerializedName("artifactId")
        public String artifactId;

        @SerializedName("libVersion")
        public String libVersion;

        @SerializedName("repository")
        public String repository;

        // github / url alanları
        @SerializedName("url")
        public String url;

        @SerializedName("sha256")
        public String sha256;

        @SerializedName("size")
        public long size;

        public String resolveDownloadUrl() {
            if ("maven".equalsIgnoreCase(source)) return buildMavenUrl();
            return url;
        }

        public String resolveFileName() {
            if ("maven".equalsIgnoreCase(source)) return artifactId + "-" + libVersion + ".jar";
            return name;
        }

        private String buildMavenUrl() {
            String base = (repository != null && !repository.trim().isEmpty())
                    ? repository.trim().replaceAll("/$", "")
                    : "https://repo1.maven.org/maven2";
            String groupPath = groupId.replace('.', '/');
            String fileName  = artifactId + "-" + libVersion + ".jar";
            return base + "/" + groupPath + "/" + artifactId + "/" + libVersion + "/" + fileName;
        }
    }

    // ─── GitHub Release Bilgisi ───────────────────────────────────────────────

    /**
     * GitHub Releases API yanıtından parse edilen release bilgisi.
     * UpdateManager tarafından doldurulur, manifest dosyasında saklanmaz.
     */
    public static class GitHubReleaseInfo {

        /** Release başlığı, örn: "Servicio v2.1.0" */
        public final String name;

        /** Tag adı, örn: "v2.1.0" */
        public final String tagName;

        /** Yayın tarihi, örn: "2025-05-12" */
        public final String publishedAt;

        /** GitHub Releases sayfasının Markdown formatındaki açıklama metni */
        public final String body;

        /** body'den ayrıştırılmış değişiklik satırları */
        public final List<String> changeLines;

        public GitHubReleaseInfo(String name, String tagName,
                                 String publishedAt, String body) {
            this.name        = name;
            this.tagName     = tagName;
            this.publishedAt = publishedAt != null
                    ? publishedAt.substring(0, Math.min(publishedAt.length(), 10))
                    : "";
            this.body        = body != null ? body : "";
            this.changeLines = parseBodyToLines(this.body);
        }

        /**
         * GitHub release body'sini (Markdown) satır listesine dönüştürür.
         * - Boş satırlar atlanır
         * - "## Başlık" satırları başlık olarak işaretlenir
         * - "- madde" veya "* madde" satırları değişiklik olarak alınır
         * - Diğer satırlar düz metin olarak alınır
         */
        private static List<String> parseBodyToLines(String body) {
            List<String> lines = new ArrayList<String>();
            if (body == null || body.trim().isEmpty()) return lines;

            for (String raw : body.split("\\r?\\n")) {
                String line = raw.trim();
                if (line.isEmpty()) continue;

                if (line.startsWith("## ") || line.startsWith("# ")) {
                    // Başlık satırı — ## kaldır, **kalın** formatı koru
                    String heading = line.replaceFirst("^#{1,6}\\s*", "").trim();
                    lines.add("**" + heading + "**");
                } else if (line.startsWith("- ") || line.startsWith("* ")) {
                    lines.add(line.substring(2).trim());
                } else if (line.startsWith("### ")) {
                    lines.add(line.replaceFirst("^#{1,6}\\s*", "").trim());
                } else {
                    lines.add(line);
                }
            }
            return lines;
        }

        /** JSON yanıtından GitHubReleaseInfo parse et (Gson kullanmadan). */
        public static GitHubReleaseInfo fromJson(String json) {
            JsonObject obj = new Gson().fromJson(json, JsonObject.class);
            return new GitHubReleaseInfo(
                    getStr(obj, "name"),
                    getStr(obj, "tag_name"),
                    getStr(obj, "published_at"),
                    getStr(obj, "body")
            );
        }

        /** JSON array'inden birden fazla release parse et. */
        public static List<GitHubReleaseInfo> fromJsonArray(String json) {
            List<GitHubReleaseInfo> list = new ArrayList<GitHubReleaseInfo>();
            try {
                JsonArray arr = new Gson().fromJson(json, JsonArray.class);
                for (JsonElement el : arr) {
                    JsonObject obj = el.getAsJsonObject();
                    list.add(new GitHubReleaseInfo(
                            getStr(obj, "name"),
                            getStr(obj, "tag_name"),
                            getStr(obj, "published_at"),
                            getStr(obj, "body")
                    ));
                }
            } catch (Exception ignored) {}
            return list;
        }

        private static String getStr(JsonObject obj, String key) {
            JsonElement el = obj.get(key);
            return (el == null || el.isJsonNull()) ? "" : el.getAsString();
        }
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    // ─── Factory ─────────────────────────────────────────────────────────────

    public static UpdateManifest fromJson(String json) {
        return new Gson().fromJson(json, UpdateManifest.class);
    }
}