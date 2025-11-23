package tr.cabro.servicio.database.mapper;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class SQLiteDateMapper implements ColumnMapper<LocalDate> {

    @Override
    public LocalDate map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
        // Sürücünün "getTimestamp" veya "getDate" ile hata vermesini engellemek için
        // veriyi ham String olarak çekiyoruz.
        String s = r.getString(columnNumber);

        if (s == null || s.trim().isEmpty()) {
            return null;
        }

        try {
            // Eğer veritabanında saat bilgisi de varsa (örn: "2025-11-04 15:30:00")
            // sadece tarih kısmını (ilk 10 karakter) alıyoruz.
            if (s.length() > 10) {
                s = s.substring(0, 10);
            }

            // Standart ISO formatını (yyyy-MM-dd) parse et
            return LocalDate.parse(s);
        } catch (Exception e) {
            // Format bozuksa null dön, uygulamayı çökertme
            return null;
        }
    }
}