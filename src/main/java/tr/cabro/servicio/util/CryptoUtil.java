package tr.cabro.servicio.util;

import tr.cabro.servicio.Servicio;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Geri döndürülebilir (reversible) şifreleme yardımcı sınıfı.
 * <p>
 * {@link PasswordUtil} tek yönlü (BCrypt hash) çalışır ve kullanıcı girişi doğrulaması için uygundur;
 * ama cihaz ekran kilidi (PIN/şifre/desen) gibi verilerde teknisyenin gerçek değeri sonradan
 * <b>geri okuyabilmesi</b> gerekir — bu yüzden burada AES-256-GCM ile simetrik, geri döndürülebilir
 * bir şifreleme kullanılıyor. Anahtar, uygulamanın veri klasöründe saklanır (ilk çalıştırmada üretilir).
 */
public final class CryptoUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final String KEY_FILE_NAME = "device-access.key";

    private static SecretKey cachedKey;

    private CryptoUtil() {}

    public static String encrypt(String plainText) {
        if (plainText == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // IV + şifreli veri tek base64 dizide birleştirilir — çözme sırasında ayrıştırılır
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Şifreleme başarısız oldu.", e);
        }
    }

    public static String decrypt(String base64CipherText) {
        if (base64CipherText == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(base64CipherText);
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            byte[] cipherText = new byte[combined.length - GCM_IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, iv.length);
            System.arraycopy(combined, iv.length, cipherText, 0, cipherText.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] plainText = cipher.doFinal(cipherText);
            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Şifre çözme başarısız oldu.", e);
        }
    }

    private static synchronized SecretKey getOrCreateKey() throws Exception {
        if (cachedKey != null) return cachedKey;

        File keyFile = new File(Servicio.getInstance().getDataFolder(), KEY_FILE_NAME);
        if (keyFile.exists()) {
            byte[] keyBytes = Base64.getDecoder().decode(Files.readString(keyFile.toPath()).trim());
            cachedKey = new SecretKeySpec(keyBytes, ALGORITHM);
            return cachedKey;
        }

        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(256);
        SecretKey newKey = keyGen.generateKey();

        String encoded = Base64.getEncoder().encodeToString(newKey.getEncoded());
        Files.writeString(keyFile.toPath(), encoded);
        try {
            // Mümkünse anahtar dosyasını sadece uygulama kullanıcısı okuyabilsin (Linux/macOS)
            keyFile.setReadable(false, false);
            keyFile.setReadable(true, true);
            keyFile.setWritable(false, false);
            keyFile.setWritable(true, true);
        } catch (Exception ignored) {
            // Windows'ta bu API'ler sınırlı çalışır — dosya zaten kullanıcının veri klasöründe
        }

        cachedKey = newKey;
        return cachedKey;
    }
}
