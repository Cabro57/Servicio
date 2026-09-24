package tr.cabro.servicio.database.filter;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Set;

/**
 * Tablo başlığından seçilen tek bir kolon filtresinin değeri.
 * Hangi alanların dolu olduğu, filtrenin ENUM mu yoksa TARİH ARALIĞI mı olduğunu belirler
 * (bkz. {@link SqlWhereBuilder}) — ayrıca bir filtre türü parametresi taşımaya gerek yoktur.
 */
@Getter @Setter
public class ColumnFilterValue {

    /** ENUM filtresi için seçilen değerler (enum {@code name()}'leri). */
    private Set<String> enumValues;

    /** TARİH ARALIĞI filtresi için başlangıç/bitiş (her ikisi de opsiyonel, tek taraflı aralık mümkün). */
    private LocalDate dateFrom;
    private LocalDate dateTo;

    /**
     * Liste sayfası görünüm sekmelerinin hazır koşulu (ör. {@code p.stock_quantity <= p.min_stock_level}).
     * YALNIZCA kod içinde sabit yazılmış SQL için; kullanıcı girdisi asla buraya konmaz. Anahtar
     * (map key) bu durumda kolon değil, koşulun adıdır ve SQL'e eklenmez.
     */
    private String condition;

    /** LOOKUP filtresi için seçilen kayıt kimlikleri (Long olduğundan SQL'e güvenle gömülür). */
    private Set<Long> ids;

    public static ColumnFilterValue condition(String trustedSql) {
        ColumnFilterValue v = new ColumnFilterValue();
        v.condition = trustedSql;
        return v;
    }

    public static ColumnFilterValue enumOf(String... names) {
        ColumnFilterValue v = new ColumnFilterValue();
        v.enumValues = new java.util.LinkedHashSet<>(java.util.Arrays.asList(names));
        return v;
    }

    public boolean isActive() {
        return (enumValues != null && !enumValues.isEmpty()) || (ids != null && !ids.isEmpty()) || dateFrom != null || dateTo != null
                || (condition != null && !condition.isBlank());
    }
}
