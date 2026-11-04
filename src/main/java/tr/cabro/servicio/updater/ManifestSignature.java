package tr.cabro.servicio.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Güncelleme manifestinin Ed25519 imzasını doğrular.
 * <p>
 * Manifest indirilecek dosyaların hash'lerini taşır; imza olmadan GitHub hesabını ele geçiren
 * biri JAR'la birlikte hash'i de değiştirip tüm kurulumlara kod dağıtabilirdi. İmza yalnızca
 * yayıncının makinesindeki özel anahtarla üretilir (bkz. {@code build.ManifestSigner}, release.ps1).
 */
final class ManifestSignature {

    private static final Logger log = LoggerFactory.getLogger(ManifestSignature.class);

    /**
     * Güvenilen açık anahtarlar (X.509, Base64). Manifest, listedeki anahtarlardan HERHANGİ biriyle
     * doğrulanırsa kabul edilir.
     * <p>
     * İlk eleman release.ps1'in kullandığı asıl özel anahtarın eşidir. Bir anahtar listeden
     * çıkarılırsa bu sürümle kurulmuş uygulamalar o anahtarla imzalanan manifestleri reddeder;
     * anahtar değişimi ancak eski anahtarla imzalı, yeni anahtarı da listeye ekleyen bir köprü
     * sürümüyle yapılabilir.
     * <p>
     * YEDEK ANAHTAR EKLEME (ileriki bir sürümde): Ayrı, güvenli bir yerde saklanan yedek özel anahtar
     * üretildiğinde onun yalnızca AÇIK anahtarını ({@code build.ManifestSigner} çıktısındaki Base64
     * X.509 değeri) aşağıdaki listeye ikinci eleman olarak ekleyin, örneğin:
     * <pre>
     *     "MCowBQYDK2VwAyEA...yedek-acik-anahtar...="  // yedek anahtar (eklenme: vX.Y.Z)
     * </pre>
     * Özel anahtar asla koda veya repo'ya konmaz. Yedek anahtarı içeren sürüm yayınlandıktan sonra
     * asıl anahtar kaybolsa bile o sürümü almış kurulumlar yedekle imzalı manifestleri kabul eder.
     */
    private static final List<String> PUBLIC_KEYS = List.of(
            "MCowBQYDK2VwAyEAyt4TeRtAzvKytrdqrCK5NwRTIs3kBOX2f3Dx10FAI7c=", // asıl anahtar (update-signing.key)
            "MCowBQYDK2VwAyEAuzWPJeIJU36K5arro+ODK7gkpRN4yVUQonpqy1MrMkk="  // yedek anahtar (update-signing-backup.key, eklenme: v2.17.0)
    );

    private ManifestSignature() {
    }

    /**
     * İmza manifestin birebir baytlarına aitse ve listedeki anahtarlardan en az biriyle doğrulanıyorsa
     * true; biçim hatası dahil her durumda aksi false.
     */
    static boolean verify(byte[] manifest, String signatureBase64) {
        byte[] signature;
        try {
            signature = Base64.getDecoder().decode(signatureBase64.trim());
        } catch (Exception e) {
            log.warn("Manifest imzası okunamadı: {}", e.getMessage());
            return false;
        }
        for (String encodedKey : PUBLIC_KEYS) {
            try {
                PublicKey key = KeyFactory.getInstance("Ed25519")
                        .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(encodedKey)));
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(key);
                verifier.update(manifest);
                if (verifier.verify(signature)) {
                    return true;
                }
            } catch (Exception e) {
                log.warn("Manifest imzası okunamadı: {}", e.getMessage());
            }
        }
        return false;
    }
}
