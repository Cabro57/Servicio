package tr.cabro.servicio.model;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;

import java.util.Arrays;

@Getter
@EnumByName
public enum CustomerType {
    NORMAL("Normal", "icons/customer.svg"),
    BE_CAREFUL("Dikkat Et", "icons/attention.svg"),
    DOING_BUSINESS("İş Yapma", "icons/not_work.svg"),
    SMALL_BUSINESS("Esnaf", "icons/esnaf.svg"),
    DEALER("Bayi", "icons/business.svg"),
    PROBLEM("Problemli", "icons/problematic.svg");

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