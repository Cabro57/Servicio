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
 */
@Getter
public enum BadgeColor {

    /** Bekleyen / bloke — dikkat ister ama hata değildir. */
    YELLOW("#FFF3CD", "#856404", "#3D2E05", "#FFD666"),
    /** Süregelen / bilgilendirici. */
    BLUE("#D0E2FF", "#0043CE", "#0B2A5B", "#8AB8FF"),
    /** Tamamlanmış / olumlu. */
    GREEN("#D4EDDA", "#155724", "#0B3A24", "#6EE7A8"),
    /** Olumsuz / iade / borç. */
    RED("#F8D7DA", "#721C24", "#45191C", "#FF9A9A"),
    /** Kasaya girmiş para gibi "kesinleşmiş olumlu" durumlar. */
    DARK_GREEN("#E8F5E9", "#2E7D32", "#10352A", "#4ADE80"),
    /** Nötr / bizim elimizde olmayan. */
    GRAY("#E2E8F0", "#1E293B", "#2A2F3A", "#CBD5E1"),
    /** Kullanıcıdan hamle bekleyen — "hazır, müşteriyi ara" gibi eylem çağrısı taşıyan durumlar. */
    PURPLE("#EDE4FF", "#5B21B6", "#2E1E52", "#C4B0FF"),
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
