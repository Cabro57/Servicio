package tr.cabro.servicio.model.enums;

import lombok.Getter;
import org.jdbi.v3.core.enums.EnumByName;

@Getter
@EnumByName
public enum TemplateType {
    WHATSAPP_MESSAGE("WhatsApp Mesajı");

    private final String displayName;

    TemplateType(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
