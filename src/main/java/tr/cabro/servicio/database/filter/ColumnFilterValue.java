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

    public boolean isActive() {
        return (enumValues != null && !enumValues.isEmpty()) || dateFrom != null || dateTo != null;
    }
}
