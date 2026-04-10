package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;
import tr.cabro.servicio.model.contract.Visualizable;

import java.util.Arrays;

@Getter
@EnumByName
public enum CustomerType implements Visualizable {
    NORMAL("Normal", "icons/user.svg", BadgeColor.BLUE),
    BE_CAREFUL("Dikkat Et", "icons/triangle-alert.svg", BadgeColor.RED),
    DOING_BUSINESS("İş Yapma", "icons/triangle-alert.svg", BadgeColor.RED),
    SMALL_BUSINESS("Esnaf",  "icons/store.svg", BadgeColor.YELLOW),
    DEALER("Bayi", "icons/handshake.svg", BadgeColor.GREEN),
    PROBLEM("Problemli", "icons/triangle-alert.svg", BadgeColor.RED);

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    CustomerType(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    public static CustomerType of(String name) {
        if (name == null) return NORMAL;
        return Arrays.stream(values())
                .filter(ct -> ct.name().equalsIgnoreCase(name) || ct.displayName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(NORMAL);
    }

    @Override
    public String toString() {
        return displayName;
    }
}