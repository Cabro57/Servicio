package tr.cabro.servicio.database.argument;

import org.jdbi.v3.core.argument.AbstractArgumentFactory;
import org.jdbi.v3.core.argument.Argument;
import org.jdbi.v3.core.config.ConfigRegistry;

import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LocalDateTimeArgumentFactory extends AbstractArgumentFactory<LocalDateTime> {

    public LocalDateTimeArgumentFactory() {
        super(Types.VARCHAR);
    }

    @Override
    protected Argument build(LocalDateTime value, ConfigRegistry config) {
        return (position, statement, ctx) -> {
            // Saniye hassasiyetiyle String'e çevir (örn: 2023-10-25T15:30:00)
            // value.toString() genellikle yeterlidir ancak formatı garantilemek iyidir.
            String formatted = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            statement.setString(position, formatted);
        };
    }
}