package tr.cabro.servicio.database.mapper;

import org.jdbi.v3.core.mapper.ColumnMapper;
import org.jdbi.v3.core.statement.StatementContext;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * sqlite-jdbc'nin ResultSet.getBigDecimal(int) metodu diğer tüm tipik okuyucuların
 * (getString, getLong, getObject...) kullandığı markCol() yerine checkCol() kullanıyor;
 * bu yüzden hemen ardından jdbi'nin varsayılan mapper'ının çağırdığı wasNull(),
 * son erişilen kolonu (lastCol) yanlış/-1 olarak bulup "column -1 out of bounds"
 * hatası fırlatabiliyor (özellikle bir BigDecimal alanı, bir satırın ilk okunan
 * kolonu olduğunda). getString() markCol() kullandığı için güvenli — BigDecimal'i
 * onun üzerinden okuyoruz (bkz. SQLiteDateTimeMapper — aynı sqlite-jdbc kaçağına
 * karşı aynı yaklaşım).
 */
public class SQLiteBigDecimalMapper implements ColumnMapper<BigDecimal> {

    @Override
    public BigDecimal map(ResultSet r, int columnNumber, StatementContext ctx) throws SQLException {
        String s = r.getString(columnNumber);
        if (s == null || s.trim().isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            throw new SQLException("Geçersiz BigDecimal değeri: '" + s + "'", e);
        }
    }
}
