package tr.cabro.servicio.importer.mapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public abstract class BaseCsvMapper<T> {

    /** Beklenen sütun sayısı (alt sınıflar override eder) */
    protected abstract int expectedFieldCount();

    /** Asıl mapping işlemi (alt sınıflar override eder) */
    protected abstract T mapRow(String[] fields);

    /** Map akışı: önce alan kontrolü sonra mapping */
    public final T map(String[] fields) {
        validateFields(fields);
        return mapRow(fields);
    }

    /** Alan uzunluğu doğrulaması */
    protected void validateFields(String[] fields) {
        int expected = expectedFieldCount();
        int actual = (fields == null) ? 0 : fields.length;
        if (actual != expected) {
            throw new IllegalArgumentException("Alan sayısı hatalı: Beklenen " + expected + ", gelen " + actual);
        }
    }

    protected String clean(String val) {
        return (val == null || val.trim().isEmpty() || "-".equals(val.trim())) ? null : val.trim();
    }

    protected int parseInt(String val) {
        try { return Integer.parseInt(val.trim()); }
        catch (Exception e) { return 0; }
    }

    protected double parseDouble(String val) {
        try { return Double.parseDouble(val.trim()); }
        catch (Exception e) { return 0.0; }
    }

    protected LocalDate parseDate(String val, DateTimeFormatter formatterWithTime) {
        try {
            if (val == null || val.trim().isEmpty()) return null;
            return LocalDateTime.parse(val.trim(), formatterWithTime).toLocalDate();
        } catch (Exception e) {
            return null;
        }
    }


    protected LocalDateTime parseDateTime(String val, DateTimeFormatter formatter) {
        try {
            if (val == null || val.trim().isEmpty()) return null;
            return LocalDateTime.parse(val.trim(), formatter);
        } catch (Exception e) {
            return null;
        }
    }

    protected String normalizePhone(String val) {
        val = clean(val);
        if (val == null) return null;
        return val.startsWith("0") ? val.substring(1) : val;
    }

}
