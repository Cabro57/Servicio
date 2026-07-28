package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;

/**
 * Cihazın ekran kilidini açmak için kullanılan erişim yönteminin türü.
 * Telefon/tablet üreticilerinin sunduğu kilit türlerinin hepsini kapsar.
 */
@Getter
@EnumByName
public enum DeviceAccessType {
    NONE("Kilit Yok"),
    PIN("PIN"),
    PASSWORD("Şifre"),
    PATTERN("Desen");

    private final String displayName;

    DeviceAccessType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
