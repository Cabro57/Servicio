package tr.cabro.servicio.model.enums;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;
import tr.cabro.servicio.model.contract.Visualizable;

import java.util.Arrays;

@Getter
@EnumByName
public enum PaymentType implements Visualizable {
    CASH("Nakit", "icons/banknote.svg", BadgeColor.DARK_GREEN),
    CARD("Banka/Kredi Kartı", "icons/credit-card.svg",  BadgeColor.GREEN),
    TRANSFER("Banka Havale/EFT", "icons/landmark.svg", BadgeColor.BLUE),
    ON_ACCOUNT("Veresiye", "icons/hand-coins.svg", BadgeColor.YELLOW),;

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    PaymentType(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    public static PaymentType of(String name) {
        if (name == null) return CASH;
        return Arrays.stream(values())
                .filter(pt -> pt.displayName.equalsIgnoreCase(name) || pt.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(CASH);
    }

    @Override
    public String toString() {
        return displayName;
    }
}