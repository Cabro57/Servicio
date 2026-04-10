package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;
import tr.cabro.servicio.model.contract.Visualizable;

import java.util.Arrays;

@Getter
@EnumByName
public enum ServiceStatus implements Visualizable {
    UNDER_REPAIR("Tamirde", "icons/wrench.svg", BadgeColor.YELLOW),
    READY("Hazır", "icons/thumbs-up.svg", BadgeColor.DARK_GREEN),
    ANOTHER_SERVICE("Başka Serviste", "icons/users.svg", BadgeColor.BLUE),
    DELIVERED("Teslim Edildi", "icons/package-check.svg", BadgeColor.GREEN),
    RETURN("İade", "icons/undo-2.svg", BadgeColor.RED),
    WAITING_FOR_PART("Parça Bekliyor", "icons/hourglass.svg", BadgeColor.GREEN),;

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    ServiceStatus(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    public static ServiceStatus of(String name) {
        if (name == null) return UNDER_REPAIR;
        return Arrays.stream(values())
                .filter(status -> status.name().equalsIgnoreCase(name) || status.displayName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(UNDER_REPAIR);
    }
}