package tr.cabro.servicio.application.manager;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class AuthManager {

    // Kendi Coolify sunucu adresinle değiştireceksin. Test için localhost:3000
    private static final String API_URL = ConfigManager.get("api.base.url") + "/api/auth/login";

    // Oturum boyunca yetki gerektiren işlemlerde kullanmak için token'ı saklıyoruz
    public static String currentUserToken;
    public static String currentUsername;

    /**
     * API'ye istek atar. Başarılı olursa veritabanını çözecek olan 'dbKey'i döndürür.
     * Başarısız olursa Exception fırlatır (Hata mesajını UI'da göstermek için).
     */
    public static String login(String username, String password) throws Exception {
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        // Java'yı gerçek bir tarayıcı gibi gösteriyoruz ki Cloudflare engeline takılmasın
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        // Gönderilecek JSON verisini oluşturuyoruz
        String jsonInputString = String.format("{\"email\": \"%s\", \"password\": \"%s\"}", username, password);

        // İsteği gönder
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int responseCode = conn.getResponseCode();

        // Başarılı Giriş (HTTP 200 OK)
        if (responseCode == 200) {
            InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
            JsonObject responseJson = JsonParser.parseReader(reader).getAsJsonObject();

            JsonObject data = responseJson.getAsJsonObject("data");

            // Bilgileri RAM'de tut
            currentUserToken = data.get("token").getAsString();
            currentUsername = data.get("email").getAsString();

            // Veritabanını açacak gizli anahtarı geri döndür
            return data.get("dbKey").getAsString();
        }
        // Hatalı Giriş (HTTP 401, 403, 404 vb.)
        else {
            InputStreamReader errorReader = new InputStreamReader(conn.getErrorStream(), StandardCharsets.UTF_8);
            JsonObject errorJson = JsonParser.parseReader(errorReader).getAsJsonObject();
            String errorMessage = errorJson.get("message").getAsString();
            throw new Exception(errorMessage);
        }
    }
}