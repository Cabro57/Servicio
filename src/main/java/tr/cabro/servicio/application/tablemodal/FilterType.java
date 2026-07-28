package tr.cabro.servicio.application.tablemodal;

/**
 * Bir {@link ColumnDef} kolonunun tablo başlığından nasıl filtrelenebileceğini belirtir.
 * İleride sözlük tabanlı (Java enum olmayan) alanlar için DYNAMIC_LIST türü eklenebilir.
 */
public enum FilterType {
    NONE,
    ENUM,
    DATE_RANGE
}
