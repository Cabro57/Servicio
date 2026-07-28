package tr.cabro.servicio.database.filter;

import lombok.Getter;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Aktif kolon filtrelerinden ({@code Map<sqlColumn, ColumnFilterValue>}) parametrik bir SQL WHERE
 * eki ve bağlanacak parametreleri üretir.
 * <p>
 * Kolon adları her zaman kod içinde ({@code ColumnDef.enumFilter/dateRangeFilter} çağrılarında)
 * sabit tanımlıdır, kullanıcıdan gelmez — SQL enjeksiyonu riski yoktur. Kullanıcıdan gelen tüm
 * değerler (enum seçimleri, tarihler) her zaman bind parametresi olarak geçirilir, hiçbir zaman
 * string concat ile SQL'e gömülmez.
 */
public final class SqlWhereBuilder {

    private SqlWhereBuilder() {}

    @Getter
    public static class Result {
        private final String whereFragment; // boş veya " AND ..." ile başlayan ek
        private final Map<String, Object> params;

        Result(String whereFragment, Map<String, Object> params) {
            this.whereFragment = whereFragment;
            this.params = params;
        }
    }

    public static Result build(Map<String, ColumnFilterValue> filters) {
        StringBuilder sql = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        int paramIndex = 0;

        if (filters != null) {
            for (Map.Entry<String, ColumnFilterValue> entry : new LinkedHashMap<>(filters).entrySet()) {
                String column = entry.getKey();
                ColumnFilterValue value = entry.getValue();
                if (value == null || !value.isActive()) continue;

                if (value.getEnumValues() != null && !value.getEnumValues().isEmpty()) {
                    StringBuilder placeholders = new StringBuilder();
                    for (String enumValue : value.getEnumValues()) {
                        String paramName = "f" + (paramIndex++);
                        if (placeholders.length() > 0) placeholders.append(",");
                        placeholders.append(":").append(paramName);
                        params.put(paramName, enumValue);
                    }
                    sql.append(" AND ").append(column).append(" IN (").append(placeholders).append(")");
                }

                if (value.getDateFrom() != null) {
                    String paramName = "f" + (paramIndex++);
                    params.put(paramName, LocalDateTime.of(value.getDateFrom(), LocalTime.MIN));
                    sql.append(" AND ").append(column).append(" >= :").append(paramName);
                }
                if (value.getDateTo() != null) {
                    String paramName = "f" + (paramIndex++);
                    params.put(paramName, LocalDateTime.of(value.getDateTo(), LocalTime.MAX));
                    sql.append(" AND ").append(column).append(" <= :").append(paramName);
                }
            }
        }

        return new Result(sql.toString(), params);
    }
}
