package tr.cabro.servicio.model.enums;

import org.jdbi.v3.core.enums.EnumByName;

/** {@code sales.type}. RETURN, {@code parent_sale_id} ile orijinal satışa bağlı, negatif tutarlı bir kayıttır. */
@EnumByName
public enum SaleType {
    SALE,
    RETURN
}
