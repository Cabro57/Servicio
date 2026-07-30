package tr.cabro.servicio.settings;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * {@code config.json}'u okuyup yazan katman.
 * <p>
 * Neden kütüphane değil de elle: önceki çözüm (okaeri-configs) sınıftaki bir alanın adı değiştiğinde
 * kullanıcının o ayarını sessizce sıfırlıyordu ve doğrudan hedef dosyanın üzerine yazdığı için
 * kaydetme sırasındaki bir çökme dosyayı bozabiliyordu. Buradaki üç davranış bilinçli:
 * <ul>
 *   <li><b>Atomik yazma:</b> önce {@code .tmp} dosyasına yazılır, sonra tek adımda taşınır.</li>
 *   <li><b>Bozuk dosyada kurtarma:</b> okunamayan dosya {@code .bak} olarak saklanır, uygulama
 *       varsayılanlarla açılır — kullanıcı açılamayan bir uygulamayla baş başa kalmaz.</li>
 *   <li><b>Sürümlü şema:</b> {@code schemaVersion} sayesinde yapı değiştiğinde eski değerler
 *       {@link ConfigMigrations} ile taşınır, sıfırlanmaz.</li>
 * </ul>
 */
public class JsonConfigStore {

    private static final Logger log = LoggerFactory.getLogger(JsonConfigStore.class);

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .serializeNulls()
            .create();

    private final File file;

    public JsonConfigStore(File file) {
        this.file = file;
    }

    /**
     * Ayarları yükler. Dosya yoksa varsayılanlarla oluşturur, bozuksa yedekleyip varsayılana döner.
     * Her iki durumda da eski depolardan (registry) taşınacak değer varsa alınır.
     */
    public AppConfig load() {
        AppConfig config;
        boolean needsSave;

        if (!file.exists()) {
            log.info("Ayar dosyası bulunamadı, varsayılanlarla oluşturuluyor: {}", file.getAbsolutePath());
            config = new AppConfig();
            needsSave = true;
        } else {
            try {
                String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
                JsonObject root = JsonParser.parseString(json).getAsJsonObject();

                ConfigMigrations.Result result = ConfigMigrations.migrate(root, GSON);
                config = result.config();
                needsSave = result.changed();

                // Yapı değiştiyse eski dosyayı sakla: taşınan işletme ayarları veritabanına ancak
                // servisler ayağa kalkınca yazılıyor; arada bir çökme olursa elde kaynak kalsın.
                if (needsSave) {
                    keepPreMigrationCopy(json);
                }
            } catch (Exception e) {
                log.error("Ayar dosyası okunamadı, yedeklenip varsayılanlara dönülüyor: {}", file.getAbsolutePath(), e);
                backupCorruptFile();
                config = new AppConfig();
                needsSave = true;
            }
        }

        needsSave |= ConfigMigrations.importFromLegacyPreferences(config);

        if (needsSave) {
            save(config);
        }
        return config;
    }

    /** Ayarları diske yazar. Hata durumunda uygulama akışı kesilmez, yalnızca loglanır. */
    public synchronized void save(AppConfig config) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("Ayar klasörü oluşturulamadı: {}", parent.getAbsolutePath());
            }

            Path target = file.toPath();
            Path temp = target.resolveSibling(file.getName() + ".tmp");

            Files.writeString(temp, GSON.toJson(config), StandardCharsets.UTF_8);
            moveIntoPlace(temp, target);

        } catch (Exception e) {
            log.error("Ayarlar kaydedilemedi: {}", file.getAbsolutePath(), e);
        }
    }

    /**
     * Geçici dosyayı hedefin üzerine taşır. Atomik taşıma bazı dosya sistemlerinde
     * (ör. Windows'ta ağ sürücüleri) desteklenmez — o durumda normal taşımaya düşülür.
     */
    private void moveIntoPlace(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Şema yükseltmesinden önceki içeriği {@code .pre-migration.bak} olarak saklar. */
    private void keepPreMigrationCopy(String originalJson) {
        try {
            Path backup = file.toPath().resolveSibling(file.getName() + ".pre-migration.bak");
            if (!Files.exists(backup)) {
                Files.writeString(backup, originalJson, StandardCharsets.UTF_8);
                log.info("Taşıma öncesi ayar dosyası yedeklendi: {}", backup);
            }
        } catch (Exception e) {
            log.warn("Taşıma öncesi yedek alınamadı: {}", e.getMessage());
        }
    }

    /** Okunamayan dosyayı {@code .bak} olarak saklar; kullanıcı isterse elle kurtarabilir. */
    private void backupCorruptFile() {
        try {
            Path backup = file.toPath().resolveSibling(file.getName() + ".bak");
            Files.move(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
            log.info("Bozuk ayar dosyası yedeklendi: {}", backup);
        } catch (Exception e) {
            log.warn("Bozuk ayar dosyası yedeklenemedi: {}", e.getMessage());
        }
    }
}
