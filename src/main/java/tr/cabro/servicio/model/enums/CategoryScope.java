package tr.cabro.servicio.model.enums;

/** Bir kategorinin hangi katalogda seçilebildiği (part_categories.scope). */
public enum CategoryScope {
    PART("Parça"),
    PRODUCT("Ürün"),
    BOTH("Parça ve ürün");

    private final String label;

    CategoryScope(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Bu kapsamdaki kategori, {@code catalog} (PART ya da PRODUCT) ekranında seçilebilir mi? */
    public boolean appliesTo(CategoryScope catalog) {
        return this == BOTH || catalog == BOTH || this == catalog;
    }
}
