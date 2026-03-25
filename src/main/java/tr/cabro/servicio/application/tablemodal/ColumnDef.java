package tr.cabro.servicio.application.tablemodal;

import lombok.Getter;

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
}