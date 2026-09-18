package tr.cabro.servicio.application.themes;

import javax.swing.*;
import java.awt.*;

/**
 * Anlamsal renk token'larına (başarı/tehlike/uyarı/bilgi) tek erişim noktası.
 * <p>
 * Token'lar {@code /themes/FlatLaf.properties} içinde {@code [light]}/{@code [dark]}
 * önekleriyle tanımlıdır; FlatLaf tema değiştiğinde bunları kendisi yeniden çözer.
 * Bu yüzden UI kodunda ham {@code new Color(...)} veya {@code #rrggbb} yazmak yerine
 * buradan okuyun — aksi halde renk, uygulamanın gönderdiği açık/koyu/OLED temalardan
 * en az birinde mutlaka yanlış olur.
 * <p>
 * FlatLaf stil dizeleri ({@code putClientProperty(STYLE, "foreground: ...")}) hex bekler;
 * o durumda {@link #hex(Color)} ile dönüştürün.
 */
public final class SemanticColor {

    private SemanticColor() {}

    /** Olumlu/tamamlanmış durum: tahsil edilmiş tutar, sıfırlanan bakiye, para üstü. */
    public static Color success() {
        return get("Servicio.successColor", 0x0E7C4A);
    }

    /** Olumsuz/riskli durum: borç, yetersiz stok, başarısız işlem. */
    public static Color danger() {
        return get("Servicio.dangerColor", 0xB42318);
    }

    /** Dikkat gerektiren ama engelleyici olmayan durum: bekleyen/bloke iş. */
    public static Color warning() {
        return get("Servicio.warningColor", 0xB54708);
    }

    /** Nötr bilgilendirme ve devam eden süreç. */
    public static Color info() {
        return get("Servicio.infoColor", 0x175CD3);
    }

    /** Eylem bekleyen durum — "hazır, müşteriyi ara" gibi kullanıcıdan hamle isteyen adımlar. */
    public static Color action() {
        return get("Servicio.actionColor", 0x6941C6);
    }

    /** Token'ın FlatLaf stil dizelerinde kullanılabilecek {@code #rrggbb} karşılığı. */
    public static String hex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }

    private static Color get(String key, int fallbackRgb) {
        Color color = UIManager.getColor(key);
        return color != null ? color : new Color(fallbackRgb);
    }
}
