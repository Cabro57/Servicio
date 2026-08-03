package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.dictionary.ExchangeRate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(ExchangeRate.class)
public interface ExchangeRateRepository {

    @SqlQuery("SELECT * FROM exchange_rates ORDER BY currency_code")
    List<ExchangeRate> findAll();

    @SqlQuery("SELECT * FROM exchange_rates WHERE currency_code = :code")
    Optional<ExchangeRate> findByCode(@Bind("code") String code);

    @SqlUpdate("INSERT INTO exchange_rates (currency_code, currency_name, rate, source, updated_at) " +
            "VALUES (:code, :name, :rate, :source, :updatedAt) " +
            "ON CONFLICT(currency_code) DO UPDATE SET " +
            "currency_name = excluded.currency_name, rate = excluded.rate, " +
            "source = excluded.source, updated_at = excluded.updated_at")
    void upsert(@Bind("code") String code, @Bind("name") String name, @Bind("rate") BigDecimal rate,
                @Bind("source") String source, @Bind("updatedAt") LocalDateTime updatedAt);
}
