package tr.cabro.servicio.model;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum ServiceStatus {
    UNDER_REPAIR("Tamirde", "icons/under_repair.svg"),
    READY("Hazır", "icons/ready.svg"),
    ANOTHER_SERVICE("Başka Serviste", "icons/another_service.svg"),
    DELIVERED("Teslim edildi", "icons/delivered.svg"),
    RETURN("İade", "icons/return.svg"),
    WAITING_FOR_PART("Parça Bekliyor", "icons/waiting_for_part.svg");

    private final String displayName;
    private final String iconPath;
    private final FlatSVGIcon icon;

    ServiceStatus(String displayName, String iconPath) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.icon = new FlatSVGIcon(iconPath, 16, 16);
    }

    public FlatSVGIcon getIcon(int width, int height) {
        return new FlatSVGIcon(iconPath, width, height);
    }

    public static ServiceStatus of(String name) {
        if (name == null) return UNDER_REPAIR;
        return Arrays.stream(values())
                .filter(ct -> ct.displayName.equalsIgnoreCase(name) || ct.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(UNDER_REPAIR);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
