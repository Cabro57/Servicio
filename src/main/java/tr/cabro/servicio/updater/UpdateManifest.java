package tr.cabro.servicio.updater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * GitHub (veya kendi sunucunuz) üzerindeki update-manifest.json dosyasının Java modeli.
 *
 * Gson zaten pom.xml'de okaeri-configs-json-gson üzerinden geliyor,
 * ayrıca bağımlılık eklemenize gerek yok.
 *
 * Java 8 uyumlu.
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

    // ─── İç Modeller ─────────────────────────────────────────────────────────

    @Getter
    public static class PatchNote {
        @SerializedName("version")
        private String version;

        @SerializedName("date")
        private String date;

        @SerializedName("changes")
        private List<String> changes;

    }

    public static class FileEntry {
        @SerializedName("name")
        public String name;

        /** Uygulamanın kök dizinine göre göreceli yol: "servicio.jar" veya "libs/gson.jar" */
        @SerializedName("path")
        public String path;

        @SerializedName("url")
        public String url;

        /** SHA-256 hex digest */
        @SerializedName("sha256")
        public String sha256;

        /** Bayt cinsinden dosya boyutu */
        @SerializedName("size")
        public long size;

        @Override
        public String toString() {
            return "FileEntry{name='" + name + "', sha256='" + sha256 + "'}";
        }
    }

    // ─── Getter'lar ──────────────────────────────────────────────────────────

    // ─── Factory ─────────────────────────────────────────────────────────────

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static UpdateManifest fromJson(String json) {
        return GSON.fromJson(json, UpdateManifest.class);
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    @Override
    public String toString() {
        return "UpdateManifest{version='" + version + "', files=" +
                (files != null ? files.size() : 0) + "}";
    }
}