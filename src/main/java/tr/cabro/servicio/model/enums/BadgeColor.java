package tr.cabro.servicio.model.enums;

import lombok.Getter;

/**
 * Rozet renk çiftleri. Her renk hem açık hem koyu tema için ayrı zemin/yazı hex'i taşır —
 * tek çift yeterli değildi: açık tema için seçilen pastel zeminler koyu temada parlıyor,
 * koyu tema için seçilen derin zeminler açık temada kara blok gibi duruyordu.
 * <p>
 * Bu enum yalnızca veridir; hangi çiftin kullanılacağına
 * {@code tr.cabro.servicio.application.themes.BadgePalette} karar verir — tema bilgisi
 * model katmanına sızmasın diye.
 * <p>
 * Renkler anlam taşır, süs değildir: aynı anlamı taşıyan iki durum aynı rengi,
 * farklı anlam taşıyan iki durum farklı rengi almalıdır.
 * <p>
 * Koyu çiftler açık çiftle aynı aileden türetilir: açık yazı renginin OKLCH tonu korunur; zemin
 * derin ve az doygun (L 0.30), yazı o zeminde ≥5:1 okunacak kadar açık. Eskiden koyu çiftler başka
 * bir paletten geliyordu ve açık temadaki rozetle aynı rengi taşımıyordu.
 */
@Getter
public enum BadgeColor {

    /** Bekleyen / bloke — dikkat ister ama hata değildir. */
    YELLOW("#FFF3CD", "#856404", "#392C0F", "#DABD81"),
    /** Süregelen / bilgilendirici. */
    BLUE("#D0E2FF", "#0043CE", "#1D2D4C", "#A0C1FF"),
    /** Tamamlanmış / olumlu. */
    GREEN("#D4EDDA", "#155724", "#1D3420", "#9ACD9F"),
    /** Olumsuz / iade / borç. */
    RED("#F8D7DA", "#721C24", "#452222", "#FCABAA"),
    /** Kasaya girmiş para gibi "kesinleşmiş olumlu" durumlar. */
    DARK_GREEN("#E8F5E9", "#2E7D32", "#193519", "#92D091"),
    /** Nötr / bizim elimizde olmayan. */
    GRAY("#E2E8F0", "#1E293B", "#292E36", "#B6C2D4"),
    /** Kullanıcıdan hamle bekleyen — "hazır, müşteriyi ara" gibi eylem çağrısı taşıyan durumlar. */
    PURPLE("#EDE4FF", "#5B21B6", "#2F2749", "#C6B9FF"),
    /** Yeni gelen, sıraya alınmış — henüz üzerinde çalışılmıyor ("Kabul Edildi"). */
    TEAL("#CCFBF1", "#115E59", "#1A3331", "#94CAC4"),
    ;

    private final String backgroundHex;
    private final String foregroundHex;
    private final String darkBackgroundHex;
    private final String darkForegroundHex;

    BadgeColor(String backgroundHex, String foregroundHex, String darkBackgroundHex, String darkForegroundHex) {
        this.backgroundHex = backgroundHex;
        this.foregroundHex = foregroundHex;
        this.darkBackgroundHex = darkBackgroundHex;
        this.darkForegroundHex = darkForegroundHex;
    }
}
