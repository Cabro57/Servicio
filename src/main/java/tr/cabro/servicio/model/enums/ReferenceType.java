package tr.cabro.servicio.model.enums;

public enum ReferenceType {
    WORK_ORDER("Servis İşi"),
    WORK_ORDER_CANCEL("Servis İptali"),
    PURCHASE("Satın Alma"),
    SALE("Satış"),
    ADJUSTMENT("Düzeltme"),
    RETURN("İade");

    private final String displayName;

    ReferenceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
