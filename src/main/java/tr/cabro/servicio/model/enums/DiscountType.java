package tr.cabro.servicio.model.enums;

import org.jdbi.v3.core.enums.EnumByName;

/** Satır veya fiş toplamı indirim türü. */
@EnumByName
public enum DiscountType {
    AMOUNT,
    PERCENT
}
