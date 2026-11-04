package tr.cabro.servicio.database;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tüm Flyway migration'larının boş bir SQLite veritabanına baştan sona
 * uygulanabildiğini ve kritik şema kurallarının yerinde olduğunu doğrular.
 * DatabaseManager'daki Flyway ayarlarını (classpath:db/migration, baselineOnMigrate) taklit eder.
 */
class MigrationTest {

    @TempDir
    Path tempDir;

    private String url;

    @BeforeEach
    void migrate() {
        url = "jdbc:sqlite:" + tempDir.resolve("test.db").toAbsolutePath();
        Flyway.configure()
                .dataSource(url, null, null)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load()
                .migrate();
    }

    @Test
    void kritikTablolarOlusturulur() throws SQLException {
        try (Connection c = DriverManager.getConnection(url)) {
            assertThat(tableExists(c, "device_loans")).isTrue();
            assertThat(tableExists(c, "payments")).isTrue();
            assertThat(tableExists(c, "payment_allocations")).isTrue();
        }
    }

    @Test
    void ayniCihazaIkinciAcikEmanetReddedilir() throws SQLException {
        // foreign_keys kapalı (SQLite varsayılanı): yalnızca kısmi UNIQUE index'i sınıyoruz
        try (Connection c = DriverManager.getConnection(url)) {
            insertOpenLoan(c, 1);
            assertThatThrownBy(() -> insertOpenLoan(c, 1))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("UNIQUE");

            // İade edilmiş emanet index'e girmez; aynı cihaza yeni emanet açılabilmeli
            try (Statement st = c.createStatement()) {
                st.executeUpdate("UPDATE device_loans SET returned_at = CURRENT_TIMESTAMP WHERE device_id = 1");
            }
            insertOpenLoan(c, 1);
        }
    }

    private static void insertOpenLoan(Connection c, long deviceId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO device_loans (device_id, customer_id, direction, given_at) "
                        + "VALUES (?, 1, 'OUT', CURRENT_TIMESTAMP)")) {
            ps.setLong(1, deviceId);
            ps.executeUpdate();
        }
    }

    private static boolean tableExists(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
