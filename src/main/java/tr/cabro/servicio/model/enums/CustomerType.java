package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;
import tr.cabro.servicio.model.contract.Visualizable;

import java.util.Arrays;

@Getter
@EnumByName
public enum CustomerType implements Visualizable {
    BIREYSEL("Bireysel", "icons/user.svg", BadgeColor.BLUE),
    KURUMSAL("Kurumsal", "icons/store.svg", BadgeColor.GREEN);

    private final String displayName;
    private final String iconPath;
    private final BadgeColor badgeColor;

    CustomerType(String displayName, String iconPath, BadgeColor badgeColor) {
        this.displayName = displayName;
        this.iconPath = iconPath;
        this.badgeColor = badgeColor;
    }

    public static CustomerType of(String name) {
        if (name == null) return BIREYSEL;
        return Arrays.stream(values())
                .filter(ct -> ct.name().equalsIgnoreCase(name) || ct.displayName.equalsIgnoreCase(name))
                .findFirst()
                .orElse(BIREYSEL);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
