package tr.cabro.servicio.database.mapper;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

public class SQLiteDateTimeMapper implements ColumnMapper<LocalDateTime> {

    // GÜNCELLENDİ: Milisaniye desteği (.appendFraction) eklendi.
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral('T').optionalEnd() // 'T' ayırıcı
            .optionalStart().appendLiteral(' ').optionalEnd() // ' ' ayırıcı
            .appendPattern("HH:mm")                           // Saat ve Dakika (Zorunlu)
            .optionalStart()
            .appendLiteral(':')
            .appendValue(ChronoField.SECOND_OF_MINUTE, 2) // Saniye (Opsiyonel)
            .optionalStart()
            // Burası KRİTİK NOKTA: Nokta ve sonrası (Milisaniye/Nanosaniye)
            // 0 ile 9 hane arasındaki tüm kesirli saniyeleri kabul eder (.1, .123, .123456 vb.)
            // 'true' parametresi, ondalık nokta (.) kullanımını zorunlu kılar.
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .optionalEnd()
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0) // Saniye yoksa 0 say
            .toFormatter();

    @Override
    public LocalDateTime map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
        String s = r.getString(columnNumber);

        if (s == null || s.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(s, FORMATTER);
        } catch (Exception e) {
            // Hata durumunda loga basıp yine de null dönmemek için exception'ı detaylı görebilirsin
            System.err.println("SQLite Date Parse Error for value: '" + s + "' -> " + e.getMessage());
            return null;
        }
    }
}