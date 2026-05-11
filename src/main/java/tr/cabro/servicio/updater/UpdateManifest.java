package tr.cabro.servicio.updater;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import lombok.Getter;

import java.util.List;

/**
 * GitHub veya kendi sunucudan indirilen update-manifest.json modelidir.

 * Gson zaten projenizde okaeri-configs-json-gson üzerinden mevcut,
 * ek bağımlılık gerekmez.
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

    // ─── Inner: Patch Notu ────────────────────────────────────────────────────

    @Getter
    public static class PatchNote {

        @SerializedName("version")
        private String version;

        @SerializedName("date")
        private String date;

        @SerializedName("changes")
        private List<String> changes;

    }

    // ─── Inner: Dosya Kaydı ───────────────────────────────────────────────────

    public static class FileEntry {

        /** Görünen ad, örn: "servicio.jar" */
        @SerializedName("name")
        public String name;

        /** Uygulama köküne göre göreceli yol, örn: "libs/flatlaf.jar" */
        @SerializedName("path")
        public String path;

        /** İndirme URL'si (GitHub veya kendi sunucu) */
        @SerializedName("url")
        public String url;

        /** SHA-256 hex digest */
        @SerializedName("sha256")
        public String sha256;

        /** Bayt cinsinden boyut (ilerleme çubuğu için) */
        @SerializedName("size")
        public long size;
    }

    // ─── Getters ──────────────────────────────────────────────────────────────

    // ─── Factory ──────────────────────────────────────────────────────────────

    public static UpdateManifest fromJson(String json) {
        return new Gson().fromJson(json, UpdateManifest.class);
    }
}