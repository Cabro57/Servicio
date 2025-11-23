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

    // Hem 'T' hem ' ' (boşluk) ayracını, hem saniyeli hem saniyesiz formatları destekleyen esnek formatlayıcı
    private static final DateTimeFormatter FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd")
            .optionalStart().appendLiteral('T').optionalEnd() // T olabilir
            .optionalStart().appendLiteral(' ').optionalEnd() // Veya boşluk olabilir
            .appendPattern("HH:mm")
            .optionalStart().appendPattern(":ss").optionalEnd() // Saniye olmayabilir
            .parseDefaulting(ChronoField.SECOND_OF_MINUTE, 0) // Saniye yoksa 0 kabul et
            .toFormatter();

    @Override
    public LocalDateTime map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
        // Sürücünün parse etmesine izin vermeden String olarak alıyoruz
        String s = r.getString(columnNumber);

        if (s == null || s.trim().isEmpty()) {
            return null;
        }

        try {
            return LocalDateTime.parse(s, FORMATTER);
        } catch (Exception e) {
            // Parse edilemeyen bozuk veri varsa null dön veya logla
            return null;
        }
    }
}