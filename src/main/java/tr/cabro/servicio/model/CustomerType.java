package tr.cabro.servicio.model;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;

import java.util.Arrays;

@Getter
public enum CustomerType {
    NORMAL("Normal", "icon/customer.svg"),
    DIKKAT("Dikkat Et", "icon/attention.svg"),
    IS_YAPMA("İş Yapma", "icon/not_work.svg"),
    ESNAF("Esnaf", "icon/esnaf.svg"),
    BAYI("Bayi", "icon/business.svg"),
    PROBLEMLI("Problemli", "icon/problematic.svg");

    private final String displayName;
    private final FlatSVGIcon icon;

    CustomerType(String displayName, String iconPath) {
        this.displayName = displayName;
        this.icon = new FlatSVGIcon(iconPath, 16, 16);
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
