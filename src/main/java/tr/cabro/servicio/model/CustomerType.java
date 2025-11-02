package tr.cabro.servicio.model;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum CustomerType {
    NORMAL("Normal", "icons/customer.svg"),
    DIKKAT("Dikkat Et", "icons/attention.svg"),
    IS_YAPMA("İş Yapma", "icons/not_work.svg"),
    ESNAF("Esnaf", "icons/esnaf.svg"),
    BAYI("Bayi", "icons/business.svg"),
    PROBLEMLI("Problemli", "icons/problematic.svg");

    private final String displayName;
    private final String iconPath;
    private final FlatSVGIcon icon;

    CustomerType(String displayName, String iconPath) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.icon = new FlatSVGIcon(iconPath, 16, 16);
    }

    public FlatSVGIcon getIcon(int width, int height) {
        return new FlatSVGIcon(iconPath, width, height);
    }

    public static CustomerType of(String name) {
        if (name == null) return NORMAL;
        return Arrays.stream(values())
                .filter(ct -> ct.displayName.equalsIgnoreCase(name) || ct.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(NORMAL);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
