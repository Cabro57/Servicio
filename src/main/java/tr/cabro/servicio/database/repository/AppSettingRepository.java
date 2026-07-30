package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * İşletme ayarlarının ({@code app_settings}) anahtar-değer deposu.
 * Hangi ayarın buraya, hangisinin {@code config.json}'a ait olduğu için
 * bkz. {@link tr.cabro.servicio.settings.SettingKeys}.
 */
public interface AppSettingRepository extends SqlObject {

    @SqlUpdate("INSERT INTO app_settings (key, value, updated_at) VALUES (:key, :value, CURRENT_TIMESTAMP) " +
            "ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = CURRENT_TIMESTAMP")
    void upsert(@Bind("key") String key, @Bind("value") String value);

    @SqlUpdate("DELETE FROM app_settings WHERE key = :key")
    void delete(@Bind("key") String key);

    /** Tüm ayarları tek seferde çeker — servis bunları bellekte önbelleğe alır. */
    default Map<String, String> findAll() {
        Map<String, String> result = new LinkedHashMap<>();
        getHandle().createQuery("SELECT key, value FROM app_settings")
                .map((rs, ctx) -> Map.entry(rs.getString("key"), rs.getString("value") == null ? "" : rs.getString("value")))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }
}
