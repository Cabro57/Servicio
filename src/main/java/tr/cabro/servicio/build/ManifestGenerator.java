package tr.cabro.servicio.build;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * ManifestGenerator — Derleme zamanı aracı.

 * maven-antrun-plugin tarafından mvn package fazında çalıştırılır.
 * servicio.jar ve tüm libs/*.jar için SHA-256 hesaplar,
 * update-manifest.json dosyasını üretir veya günceller.

 * ÇALIŞMA MANTIĞI:
 *   - Eğer hedef dizinde eski bir manifest.json varsa patch notları korunur,
 *     yeni sürüm başa eklenir.
 *   - Değişmeyen dosyaların hash'i güncellenir (aynı sürüme yama desteği).

 * KONUMU: src/main/java/tr/cabro/servicio/build/ManifestGenerator.java

 * NOT: Bu sınıf sadece derleme zamanında çalışır, son jar'a dahil edilmez.
 *      İsterseniz maven-antrun exec yerine exec-maven-plugin ile de çağırabilirsiniz.

 * Argümanlar (sırasıyla):
 *   0 → version        (örn: "2.1.0")
 *   1 → downloadBase   (örn: "https://github.com/user/repo/releases/download/v2.1.0")
 *   2 → mainJarPath    (örn: "target/servicio.jar")
 *   3 → libsDir        (örn: "target/libs")
 *   4 → outputPath     (örn: "target/update-manifest.json")
 */
public class ManifestGenerator {

    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Kullanım: ManifestGenerator <version> <downloadBase> <mainJar> <libsDir> <output>");
            System.exit(1);
        }

        String version      = args[0];
        String downloadBase = args[1];
        File   mainJar      = new File(args[2]);
        File   libsDir      = new File(args[3]);
        File   outputFile   = new File(args[4]);

        System.out.println("[ManifestGenerator] Sürüm: " + version);
        System.out.println("[ManifestGenerator] Çıktı: " + outputFile.getAbsolutePath());

        // ── Mevcut manifest varsa patch notlarını oku ──────────────────────────
        List<String> existingPatchNotes = new ArrayList<String>();
        if (outputFile.exists()) {
            existingPatchNotes = extractExistingPatchNotes(outputFile, version);
        }

        // ── Dosya listesi oluştur ──────────────────────────────────────────────
        List<FileEntry> entries = new ArrayList<FileEntry>();

        // Ana JAR
        if (mainJar.exists()) {
            entries.add(buildEntry(mainJar, mainJar.getName(), downloadBase));
            System.out.println("[ManifestGenerator] ✔ " + mainJar.getName());
        } else {
            System.err.println("[ManifestGenerator] UYARI: Ana JAR bulunamadı: " + mainJar);
        }

        // libs/ klasöründeki tüm JAR'lar
        if (libsDir.exists() && libsDir.isDirectory()) {
            File[] libs = libsDir.listFiles(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String name) {
                    return name.endsWith(".jar");
                }
            });
            if (libs != null) {
                Arrays.sort(libs);  // Kararlı sıralama
                for (File lib : libs) {
                    entries.add(buildEntry(lib, "libs/" + lib.getName(), downloadBase));
                    System.out.println("[ManifestGenerator] ✔ libs/" + lib.getName());
                }
            }
        }

        // ── JSON üret ─────────────────────────────────────────────────────────
        String today = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String json  = buildJson(version, today, entries, existingPatchNotes);

        // Çıktı dizini yoksa oluştur
        outputFile.getParentFile().mkdirs();

        PrintWriter pw = null;
        try {
            pw = new PrintWriter(new OutputStreamWriter(Files.newOutputStream(outputFile.toPath()), StandardCharsets.UTF_8));
            pw.print(json);
        } finally {
            if (pw != null) pw.close();
        }

        System.out.println("[ManifestGenerator] Tamamlandı. " + entries.size() + " dosya işlendi.");
    }

    // ─── Yardımcılar ──────────────────────────────────────────────────────────

    private static FileEntry buildEntry(File file, String relativePath,
                                        String downloadBase) throws Exception {
        FileEntry e = new FileEntry();
        e.name         = file.getName();
        e.path         = relativePath;
        e.url          = downloadBase + "/" + relativePath;
        e.sha256       = sha256(file);
        e.size         = file.length();
        return e;
    }

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

    /**
     * Mevcut manifest.json'dan patch notlarını raw string olarak çıkarır.
     * Gson kullanmadan basit string arama ile yapar (derleme zamanında Gson yok).
     * Yeni sürüme ait not zaten varsa üzerine yazılmaz (duplicate önleme).
     */
    private static List<String> extractExistingPatchNotes(File manifest,
                                                          String newVersion) throws IOException {
        // Tüm dosyayı oku
        StringBuilder sb = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(Files.newInputStream(manifest.toPath()), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append('\n');
        } finally {
            if (br != null) br.close();
        }

        String content = sb.toString();

        // "patchNotes" bloğunu bul: [ ... ]
        int start = content.indexOf("\"patchNotes\"");
        if (start < 0) return new ArrayList<String>();

        int arrStart = content.indexOf('[', start);
        if (arrStart < 0) return new ArrayList<String>();

        // Eşleşen kapanış köşeli parantezini bul
        int depth = 0;
        int arrEnd = -1;
        for (int i = arrStart; i < content.length(); i++) {
            char ch = content.charAt(i);
            if (ch == '[') depth++;
            else if (ch == ']') {
                depth--;
                if (depth == 0) { arrEnd = i; break; }
            }
        }

        if (arrEnd < 0) return new ArrayList<String>();

        // Bireysel patch notu objelerini { } arasından ayıkla
        String arr = content.substring(arrStart + 1, arrEnd).trim();
        List<String> noteObjects = splitJsonObjects(arr);

        // Yeni sürümle aynı olanlari çıkar (yeniden eklenecek)
        List<String> filtered = new ArrayList<String>();
        for (String obj : noteObjects) {
            if (!obj.contains("\"" + newVersion + "\"")) {
                filtered.add(obj.trim());
            }
        }
        return filtered;
    }

    /** JSON obje dizisini { } seviyesinde böler. */
    private static List<String> splitJsonObjects(String arr) {
        List<String> result = new ArrayList<String>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arr.length(); i++) {
            char ch = arr.charAt(i);
            if (ch == '{') {
                if (depth == 0) start = i;
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    result.add(arr.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return result;
    }

    // ─── JSON Üretici ─────────────────────────────────────────────────────────

    private static String buildJson(String version, String date,
                                    List<FileEntry> entries,
                                    List<String> oldPatchNotes) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": ").append(q(version)).append(",\n");
        sb.append("  \"releaseDate\": ").append(q(date)).append(",\n");
        sb.append("  \"minRequiredVersion\": \"2.0.0\",\n");

        // Patch notları
        sb.append("  \"patchNotes\": [\n");
        // Yeni sürüm notu — changes alanını elle doldurun veya boş bırakın
        sb.append("    {\n");
        sb.append("      \"version\": ").append(q(version)).append(",\n");
        sb.append("      \"date\": ").append(q(date)).append(",\n");
        sb.append("      \"changes\": [\n");
        sb.append("        \"Güncelleme notları buraya eklenecek\"\n");
        sb.append("      ]\n");
        sb.append("    }");

        for (String old : oldPatchNotes) {
            sb.append(",\n    ").append(old);
        }
        sb.append("\n  ],\n");

        // Dosyalar
        sb.append("  \"files\": [\n");
        for (int i = 0; i < entries.size(); i++) {
            FileEntry e = entries.get(i);
            sb.append("    {\n");
            sb.append("      \"name\": ").append(q(e.name)).append(",\n");
            sb.append("      \"path\": ").append(q(e.path)).append(",\n");
            sb.append("      \"url\": ").append(q(e.url)).append(",\n");
            sb.append("      \"sha256\": ").append(q(e.sha256)).append(",\n");
            sb.append("      \"size\": ").append(e.size).append("\n");
            sb.append("    }");
            if (i < entries.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String q(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    // ─── Inner ────────────────────────────────────────────────────────────────

    private static class FileEntry {
        String name;
        String path;
        String url;
        String sha256;
        long   size;
    }
}