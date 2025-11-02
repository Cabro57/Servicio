package tr.cabro.servicio.model;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum PaymentType {
    CASH("Nakit", "icons/cash.svg"),
    CARD("Banka/Kredi Kartı", "icons/card.svg"),
    TRANSFER("Banka Havale/EFT", "icons/transfer.svg"),
    ON_ACCOUNT("Veresiye", "icons/on_account.svg");

    private final String displayName;
    private final String iconPath;
    private final FlatSVGIcon icon;

    PaymentType(String displayName, String iconPath) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.icon = new FlatSVGIcon(iconPath, 16, 16);
    }

    public FlatSVGIcon getIcon(int width, int height) {
        return new FlatSVGIcon(iconPath, width, height);
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
