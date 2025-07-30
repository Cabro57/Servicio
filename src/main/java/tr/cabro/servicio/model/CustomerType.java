package tr.cabro.servicio.model;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum CustomerType {
    NORMAL("Normal", "icon/customer.svg"),
    DIKKAT("Dikkat Et", "icon/customer.svg"),
    IS_YAPMA("İş Yapma", "icon/customer.svg"),
    ESNAF("Esnaf", "icon/customer.svg"),
    BAYI("Bayi", "icon/customer.svg"),
    PROBLEMLI("Problemli", "icon/customer.svg");

    private final String displayName;
    private final FlatSVGIcon icon;

    CustomerType(String displayName, String iconPath) {
        this.displayName = displayName;
        this.icon = new FlatSVGIcon(iconPath);
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
