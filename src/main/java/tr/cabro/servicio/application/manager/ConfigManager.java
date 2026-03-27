package tr.cabro.servicio.application.manager;

import tr.cabro.servicio.Servicio;
import java.io.InputStream;
import java.util.Properties;

public class ConfigManager {
    private static final Properties properties = new Properties();

    static {
        // Program başlarken resources içindeki config.properties dosyasını bulur ve okur
        try (InputStream input = ConfigManager.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                Servicio.getLogger().error("Kritik Hata: config.properties dosyası bulunamadı!");
            } else {
                properties.load(input);
            }
        } catch (Exception ex) {
            Servicio.getLogger().error("Config dosyası okunurken hata oluştu: ", ex);
        }
    }

    // İstediğimiz ayarı çağırmak için kullanacağımız metot
    public static String get(String key) {
        return properties.getProperty(key);
    }
}