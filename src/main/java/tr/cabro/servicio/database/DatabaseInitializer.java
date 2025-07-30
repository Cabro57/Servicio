package tr.cabro.servicio.database;

import tr.cabro.servicio.Servicio;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.Statement;
import java.util.stream.Collectors;

public class DatabaseInitializer {

    public static void initializeFromClasspath(Connection conn, String resourcePath) {
        try (
                InputStream is = DatabaseInitializer.class.getClassLoader().getResourceAsStream(resourcePath);
                BufferedReader reader = new BufferedReader(new InputStreamReader(is))
        ) {
            String sql = reader.lines().collect(Collectors.joining("\n"));
            Statement stmt = conn.createStatement();
            stmt.executeUpdate(sql);
            stmt.close();
        } catch (Exception e) {
            Servicio.getInstance().getLogger().severe(e.getMessage());
        }
    }
}
