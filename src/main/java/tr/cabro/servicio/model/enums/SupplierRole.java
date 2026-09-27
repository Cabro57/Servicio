package tr.cabro.servicio.model.enums;

/**
 * Firmanın rolü: parça tedarikçisi (servis), ürün toptancısı (POS) ya da ikisi birden.
 * Parça formunda tedarikçiler, ürün formunda toptancılar seçilir (bkz. V26).
 */
public enum SupplierRole {
    SUPPLIER("Tedarikçi"),
    WHOLESALER("Toptancı"),
    BOTH("Tedarikçi ve toptancı");

    private final String label;

    SupplierRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Parça formundaki "Tedarikçi" listesinde çıkar. */
    public boolean suppliesParts() {
        return this != WHOLESALER;
    }

    /** Ürün formundaki "Toptancı" listesinde çıkar. */
    public boolean suppliesProducts() {
        return this != SUPPLIER;
    }

    public static SupplierRole of(boolean parts, boolean products) {
        if (parts && products) return BOTH;
        return products ? WHOLESALER : SUPPLIER;
    }
}
