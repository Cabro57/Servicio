package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;
import tr.cabro.servicio.model.contract.Visualizable;

import java.util.Arrays;

@Getter
@EnumByName
public enum PaymentType implements Visualizable {
    CASH("Nakit", "icons/banknote.svg", BadgeColor.DARK_GREEN),           // Nakit
    CREDIT_CARD("Kredi&Banka Kartı", "icons/credit-card.svg", BadgeColor.GREEN),    // Kredi Kartı / Banka Kartı (Debit)
    TRANSFER("Havale/EFT", "icons/landmark.svg", BadgeColor.BLUE), // Havale / EFT
    OTHER("Diğer", "icons/gem.svg", BadgeColor.GRAY);        // Diğer (Örn: Çek, Kripto, vb.)

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    PaymentType(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    public static PaymentType of(String name) {
        if (name == null) return OTHER;
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(name) || status.displayName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(OTHER);
    }
}