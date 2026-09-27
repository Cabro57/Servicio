package tr.cabro.servicio.model.enums;

/**
 * Stoğu tutulan kalem türü. Parça (servis) ve ürün (POS) defterleri ayrı tablolardadır ama
 * aynı yapıdadır (bkz. V21, V25); servis ve repository kodu tabloyu bu değerden seçer.
 */
public enum StockItemKind {
    PART("stock_movements", "part_stock_levels", "part_id", "parts"),
    PRODUCT("product_stock_movements", "product_stock_levels", "product_id", "products");

    private final String movementTable;
    private final String levelTable;
    private final String itemColumn;
    private final String itemTable;

    StockItemKind(String movementTable, String levelTable, String itemColumn, String itemTable) {
        this.movementTable = movementTable;
        this.levelTable = levelTable;
        this.itemColumn = itemColumn;
        this.itemTable = itemTable;
    }

    public String movementTable() { return movementTable; }
    public String levelTable() { return levelTable; }
    public String itemColumn() { return itemColumn; }
    public String itemTable() { return itemTable; }
}
