package tr.cabro.servicio.updater;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

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
     * release.ps1'in kullandığı özel anahtarın eşi (X.509, Base64). Değiştirilirse bu anahtarla
     * derlenmiş kurulumlar yeni anahtarla imzalanan manifestleri reddeder; anahtar değişimi ancak
     * eski anahtarla imzalı bir köprü sürümüyle yapılabilir.
     */
    private static final String PUBLIC_KEY = "MCowBQYDK2VwAyEAyt4TeRtAzvKytrdqrCK5NwRTIs3kBOX2f3Dx10FAI7c=";

    private ManifestSignature() {
    }

    /** İmza manifestin birebir baytlarına aitse true; biçim hatası dahil her durumda aksi false. */
    static boolean verify(byte[] manifest, String signatureBase64) {
        try {
            PublicKey key = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY)));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(key);
            verifier.update(manifest);
            return verifier.verify(Base64.getDecoder().decode(signatureBase64.trim()));
        } catch (Exception e) {
            log.warn("Manifest imzası okunamadı: {}", e.getMessage());
            return false;
        }
    }
}
