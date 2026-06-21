package tr.cabro.servicio.util;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtil {

    private static final int BCRYPT_ROUNDS = 10;

    public static String hash(String plainPin) {
        return BCrypt.hashpw(plainPin, BCrypt.gensalt(BCRYPT_ROUNDS));
    }

    public static boolean verify(String plainPin, String stored) {
        if (stored == null || stored.isEmpty() || plainPin == null) return false;
        if (isHashed(stored)) {
            try {
                return BCrypt.checkpw(plainPin, stored);
            } catch (Exception e) {
                return false;
            }
        }
        // Düz metin (eski kayıt) — doğrudan karşılaştır
        return stored.equals(plainPin);
    }

    public static boolean isHashed(String password) {
        return password != null
                && (password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$"));
    }
}