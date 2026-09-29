package tr.cabro.servicio.build;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * Güncelleme manifestini Ed25519 ile imzalayan derleme-zamanı aracı (JAR'a dahil edilmez).
 * <p>
 * Manifest dosya hash'lerini taşıdığı için tek başına güven kaynağı olamaz: GitHub hesabı
 * ele geçerse hem JAR hem manifest değiştirilebilir. İmza yalnızca yayıncının makinesindeki
 * özel anahtarla üretilir; uygulama gömülü açık anahtarla doğrular
 * (bkz. {@code tr.cabro.servicio.updater.ManifestSignature}).
 * <p>
 * Kullanım:
 * <pre>
 *   generate &lt;özelAnahtarDosyası&gt;                      → anahtar çifti üretir, açık anahtarı yazdırır
 *   sign     &lt;özelAnahtarDosyası&gt; &lt;manifest&gt; &lt;imza&gt;   → manifestin Base64 imzasını yazar
 * </pre>
 */
public final class ManifestSigner {

    private ManifestSigner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && "generate".equals(args[0])) {
            generate(Path.of(args[1]));
        } else if (args.length == 4 && "sign".equals(args[0])) {
            sign(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        } else {
            System.err.println("Kullanım: generate <anahtar> | sign <anahtar> <manifest> <imza>");
            System.exit(2);
        }
    }

    private static void generate(Path keyFile) throws Exception {
        if (Files.exists(keyFile)) {
            // Anahtarın üzerine yazmak, kurulu tüm sürümlerin yeni manifesti reddetmesi demektir.
            System.err.println("Anahtar dosyası zaten var, üzerine yazılmadı: " + keyFile);
            System.exit(1);
        }
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Files.createDirectories(keyFile.toAbsolutePath().getParent());
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()),
                StandardCharsets.US_ASCII);
        System.out.println("Özel anahtar yazıldı: " + keyFile.toAbsolutePath());
        System.out.println("Açık anahtar (ManifestSignature.PUBLIC_KEY):");
        System.out.println(Base64.getEncoder().encodeToString(pair.getPublic().getEncoded()));
    }

    private static void sign(Path keyFile, Path manifest, Path signatureFile) throws Exception {
        byte[] encoded = Base64.getDecoder().decode(Files.readString(keyFile, StandardCharsets.US_ASCII).trim());
        PrivateKey key = KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(encoded));
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(key);
        signer.update(Files.readAllBytes(manifest));
        Files.writeString(signatureFile, Base64.getEncoder().encodeToString(signer.sign()),
                StandardCharsets.US_ASCII);
        System.out.println("Manifest imzalandı: " + signatureFile.toAbsolutePath());
    }
}
