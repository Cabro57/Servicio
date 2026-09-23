package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import tr.cabro.servicio.model.dto.CustomerBalanceDto;
import tr.cabro.servicio.model.dto.OpenDocumentDto;

import java.util.List;
import java.util.Optional;

/**
 * Cari hesap (müşteri bakiyesi/açık belgeler) için salt-okunur sorgular — V19 migration'daki
 * {@code v_document_balances}/{@code v_customer_balances} view'larını okur, hiçbir tabloya yazmaz.
 */
public interface AccountRepository {

    @RegisterBeanMapper(OpenDocumentDto.class)
    @SqlQuery("SELECT document_type, document_id, customer_id, document_date, total_amount, allocated_amount, remaining_amount " +
            "FROM v_document_balances WHERE customer_id = :customerId AND remaining_amount > 0 ORDER BY document_date ASC")
    List<OpenDocumentDto> findOpenDocumentsByCustomer(@Bind("customerId") Long customerId);

    @RegisterBeanMapper(CustomerBalanceDto.class)
    @SqlQuery("SELECT customer_id, total_debt, total_paid, balance FROM v_customer_balances " +
            "WHERE balance > 0 ORDER BY balance DESC LIMIT :limit OFFSET :offset")
    List<CustomerBalanceDto> findCustomersWithBalancePaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM v_customer_balances WHERE balance > 0")
    long countCustomersWithBalance();

    // Toplam açık alacak (borçlu müşterilerin bakiyeleri toplamı). double döner: sqlite-jdbc'nin
    // SUM sonucu için getBigDecimal davranışına güvenilmiyor, çağıran taraf BigDecimal'e çevirir.
    @SqlQuery("SELECT COALESCE(SUM(balance), 0) FROM v_customer_balances WHERE balance > 0")
    double sumPositiveBalances();

    @RegisterBeanMapper(CustomerBalanceDto.class)
    @SqlQuery("SELECT customer_id, total_debt, total_paid, balance FROM v_customer_balances WHERE customer_id = :customerId")
    Optional<CustomerBalanceDto> findBalanceByCustomer(@Bind("customerId") Long customerId);

    // Arama alanı müşteri adını da kapsar; bu yüzden customers ile JOIN edilir.
    String SEARCH_SELECT = "SELECT vb.customer_id, vb.total_debt, vb.total_paid, vb.balance ";
    String SEARCH_FROM = "FROM v_customer_balances vb JOIN customers c ON c.id = vb.customer_id ";
    String SEARCH_WHERE = "WHERE vb.balance > 0 AND (c.first_name LIKE :search OR c.last_name LIKE :search " +
            "OR c.business_name LIKE :search OR c.phone_number_1 LIKE :search) ";

    @RegisterBeanMapper(CustomerBalanceDto.class)
    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY vb.balance DESC LIMIT :limit OFFSET :offset")
    List<CustomerBalanceDto> searchCustomersWithBalancePaged(@Bind("search") String search, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE)
    long countSearchCustomersWithBalance(@Bind("search") String search);
}
