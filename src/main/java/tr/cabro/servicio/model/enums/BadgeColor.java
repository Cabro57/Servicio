package tr.cabro.servicio.model.enums;

import lombok.Getter;

@Getter
public enum BadgeColor {

    YELLOW("#FFF3CD", "#856404"),
    BLUE("#D0E2FF", "#0043CE"),
    GREEN("#D4EDDA", "#155724"),
    RED("#F8D7DA", "#721C24"),
    DARK_GREEN("#E8F5E9", "#2E7D32"),
    GRAY("#E2E8F0", "#1E293B"),;

    private final String backgroundHex;
    private final String foregroundHex;

    BadgeColor(String backgroundHex, String foregroundHex) {
        this.backgroundHex = backgroundHex;
        this.foregroundHex = foregroundHex;
    }
}
