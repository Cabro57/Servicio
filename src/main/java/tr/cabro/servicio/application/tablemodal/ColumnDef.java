package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;
import tr.cabro.servicio.application.renderer.ActionButtonRenderer;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.renderer.UniversalVisualizableRenderer;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Jenerik tablo modelimiz için sütun konfigürasyonunu tutar.
 * @param <T> Tabloda gösterilecek veri tipi (Örn: Customer, Device)
 */
public class ColumnDef<T> {
    @Getter
    private final String name;
    @Getter
    private final Class<?> type;
    private final Function<T, Object> valueProvider;

    // --- Tablo başlığı filtresi (opsiyonel — varsayılan: filtrelenemez) ---
    @Getter
    private String filterColumn;
    @Getter
    private FilterType filterType = FilterType.NONE;
    @Getter
    private Class<? extends Enum<?>> enumClass;

    // --- Renderer/alignment/editable (opsiyonel — verilmezse form kendi configureTableColumns()'ında elle atar) ---
    @Getter
    private TableCellRenderer renderer;
    @Getter
    private Integer alignment;
    @Getter
    private boolean editable;

    /**
     * @param name Sütun başlığı (Örn: "Müşteri Adı")
     * @param type Sütunun veri tipi (Örn: String.class, Integer.class). Renderer'lar için önemlidir.
     * @param valueProvider Nesneden veriyi çekecek fonksiyon (Örn: Customer::getName)
     */
    public ColumnDef(String name, Class<?> type, Function<T, Object> valueProvider) {
        this.name = name;
        this.type = type;
        this.valueProvider = valueProvider;
    }

    public Object getValue(T item) {
        // Fonksiyonu çalıştırarak nesneden ilgili veriyi alırız
        return valueProvider.apply(item);
    }

    // -------------------------------------------------------------------------
    // Filtre opt-in metotları (fluent — mevcut çağırımları bozmaz)
    // -------------------------------------------------------------------------

    /** Bu kolonu, verilen SQL kolonu/ifadesi ve enum türü üzerinden ENUM (checkbox listesi) filtresine açar. */
    public ColumnDef<T> enumFilter(String sqlColumn, Class<? extends Enum<?>> enumClass) {
        this.filterColumn = sqlColumn;
        this.filterType = FilterType.ENUM;
        this.enumClass = enumClass;
        return this;
    }

    /** Bu kolonu, verilen SQL kolonu/ifadesi üzerinden TARİH ARALIĞI filtresine açar. */
    public ColumnDef<T> dateRangeFilter(String sqlColumn) {
        this.filterColumn = sqlColumn;
        this.filterType = FilterType.DATE_RANGE;
        return this;
    }

    /** Bir seçim listesi seçeneği (kimlik + görünen ad). */
    public static final class Option {
        public final Long id;
        public final String label;

        public Option(Long id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    /**
     * Bu kolonu, verilen SQL kolonu (kimlik alanı) üzerinden LOOKUP filtresine açar; seçenekler
     * popup açılırken {@code options} ile (asenkron) yüklenir. Başlık adı popup başlığı olur.
     */
    public ColumnDef<T> lookupFilter(String sqlColumn,
                                     java.util.function.Supplier<java.util.concurrent.CompletableFuture<java.util.List<Option>>> options) {
        this.filterColumn = sqlColumn;
        this.filterType = FilterType.LOOKUP;
        this.lookupOptions = options;
        return this;
    }

    @Getter
    private java.util.function.Supplier<java.util.concurrent.CompletableFuture<java.util.List<Option>>> lookupOptions;

    public boolean isFilterable() {
        return filterType != FilterType.NONE;
    }

    // -------------------------------------------------------------------------
    // Renderer/alignment opt-in metotları
    // -------------------------------------------------------------------------

    public ColumnDef<T> withRenderer(TableCellRenderer renderer, int alignment) {
        this.renderer = renderer;
        this.alignment = alignment;
        return this;
    }

    /**
     * Kolonu düzenlenebilir işaretler — {@code DynamicActionColumnSupport} gibi kendi buton
     * setini kuran, {@link #actionColumn}'ın sabit "İşlem" adı/3-buton düzenine bağlı olmak
     * istemeyen çağrılar için. {@code GenericTableModel.isCellEditable()} bu bayrağı (ya da
     * kolon adının tam olarak "İşlem" olmasını) kontrol eder — ikisi de yoksa JTable hücre
     * editörünü hiç tetiklemez, tablodaki butonlar tıklansa bile hiçbir şey olmaz.
     */
    public ColumnDef<T> editable(boolean editable) {
        this.editable = editable;
        return this;
    }

    /** Sadece hizalama belirtir, renderer'a dokunmaz — kolon kendi bespoke renderer'ını forma özel kodda alacaksa kullanılır. */
    public ColumnDef<T> alignment(int alignment) {
        this.alignment = alignment;
        return this;
    }

    // -------------------------------------------------------------------------
    // Sık tekrarlanan kolon kalıpları için statik factory'ler
    // -------------------------------------------------------------------------

    /** Para birimi kolonu — sağa yaslı, {@link CurrencyTableCellRenderer} ile biçimlenir. */
    public static <T> ColumnDef<T> currency(String name, Function<T, Object> valueProvider) {
        return new ColumnDef<>(name, BigDecimal.class, valueProvider)
                .withRenderer(new CurrencyTableCellRenderer(), SwingConstants.TRAILING);
    }

    /** Enum/durum rozeti kolonu — ortalı, {@link UniversalVisualizableRenderer} ile biçimlenir (Visualizable enum'lar için). */
    public static <T> ColumnDef<T> badge(String name, Class<?> type, Function<T, Object> valueProvider) {
        return new ColumnDef<>(name, type, valueProvider)
                .withRenderer(new UniversalVisualizableRenderer(SwingConstants.CENTER), SwingConstants.CENTER);
    }

    /** "İşlem" (detay/düzenle/sil) butonu kolonu — ortalı, düzenlenebilir, {@link ActionButtonRenderer} ile biçimlenir. */
    public static <T> ColumnDef<T> actionColumn(String name) {
        ColumnDef<T> column = new ColumnDef<T>(name, String.class, t -> "Detay")
                .withRenderer(new ActionButtonRenderer(), SwingConstants.CENTER);
        column.editable = true;
        return column;
    }
}