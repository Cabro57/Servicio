package tr.cabro.servicio.model;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;

import java.util.Arrays;

@Getter
@EnumByName
public enum CustomerType {
    NORMAL("Normal"),
    BE_CAREFUL("Dikkat Et"),
    DOING_BUSINESS("İş Yapma"),
    SMALL_BUSINESS("Esnaf"),
    DEALER("Bayi"),
    PROBLEM("Problemli");

    private final String name;

    CustomerType(String displayName) {
        this.name = displayName;
    }

    public static CustomerType of(String name) {
        if (name == null) return NORMAL;
        return Arrays.stream(values())
                .filter(ct -> ct.name.equalsIgnoreCase(name) || ct.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse(NORMAL);
    }

    @Override
    public String toString() {
        return name;
    }
}