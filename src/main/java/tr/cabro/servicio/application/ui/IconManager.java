package tr.cabro.servicio.application.ui;

import tr.cabro.servicio.application.utils.Ikon;

/**
 * Enum/durum ikonlarını tek yerden üretir.
 * <p>
 * <b>Örnek paylaşmaz, bilerek.</b> Burada bir zamanlar {@code ConcurrentHashMap} ile
 * {@link Ikon} örnekleri cache'leniyordu; amaç SVG parse maliyetini bir kez ödemekti.
 * O maliyet zaten FlatLaf tarafından ödeniyor: {@code FlatSVGIcon} ayrıştırılmış
 * SVG belgelerini kendi statik {@code SoftCache}'inde tutar, yani aynı yol için ikinci
 * {@code new Ikon(...)} çağrısı belgeyi yeniden ayrıştırmaz.
 * <p>
 * Buna karşılık {@code Ikon} değiştirilebilir bir nesnedir — renk filtresi ve
 * {@code colorKey} sonradan değiştirilebilir. Paylaşılan örnek dağıtmak bu yüzden
 * hatalıydı: rozet renderer'ı ortak örneğin tema-duyarlı filtresini kalıcı olarak
 * eziyor, aynı ikonu filtre kurmadan kullanan {@code StatCard} gibi yerler de son
 * çizilen rozetin rengini miras alıyordu. Cache anahtarı ({@code yol + boyut}) rengi
 * hiç içermediği için bu çakışma ayrıştırılamıyordu.
 * <p>
 * Kendi örneğini değiştirmek isteyen kod (ör. hover rengi) doğrudan {@code new Ikon(...)}
 * kurabilir; paylaşılmadığı sürece sorun değildir.
 */
public class IconManager {

    private IconManager() {
    }

    /** Verilen yol ve boyut için yeni bir {@link Ikon} üretir; {@code null}/boş yolda {@code null} döner. */
    public static Ikon getIcon(String path, int size) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        return new Ikon(path, size);
    }
}
