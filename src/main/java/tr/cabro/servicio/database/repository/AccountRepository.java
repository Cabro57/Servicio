package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.SqlObject;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.filter.SqlWhereBuilder;
import tr.cabro.servicio.model.dto.PageResult;
import java.util.HashMap;
import java.util.Map;
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
public interface AccountRepository extends SqlObject {

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

    // =========================================================================
    // LİSTE SAYFASI (FormAccounts): görünüm sekmesi koşulu + arama + sayfalama
    // =========================================================================

    String FILTER_SEARCH = " AND (c.first_name LIKE :search OR c.last_name LIKE :search " +
            "OR c.business_name LIKE :search OR c.phone_number_1 LIKE :search)";

    default PageResult<CustomerBalanceDto> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                               int page, int pageSize) {
        return searchFilteredPaged(searchTerm, filters, page, pageSize, null);
    }

    /** Sıralama anahtarları ({@link #SORTS}) sabit bir beyaz listedir; SQL parçası kullanıcıdan gelmez. */
    java.util.Map<String, String> SORTS = java.util.Map.of("NEWEST", "ABS(vb.balance) DESC", "DEBT", "vb.balance DESC", "CREDIT", "vb.balance ASC", "NAME", "c.first_name COLLATE NOCASE ASC, c.last_name COLLATE NOCASE ASC");

    default PageResult<CustomerBalanceDto> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                               int page, int pageSize, String sortKey) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        boolean searching = searchTerm != null && !searchTerm.isBlank();
        String whereClause = "WHERE c.is_deleted = 0" + where.getWhereFragment() + (searching ? FILTER_SEARCH : "");

        Map<String, Object> countParams = new HashMap<>(where.getParams());
        if (searching) countParams.put("search", "%" + searchTerm.trim() + "%");
        Map<String, Object> params = new HashMap<>(countParams);
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);

        List<CustomerBalanceDto> items = getHandle().createQuery(SEARCH_SELECT + SEARCH_FROM + whereClause
                        + " ORDER BY " + SORTS.getOrDefault(sortKey == null ? "NEWEST" : sortKey, SORTS.get("NEWEST")) + " LIMIT :limit OFFSET :offset")
                .bindMap(params).mapToBean(CustomerBalanceDto.class).list();
        long total = getHandle().createQuery("SELECT COUNT(*) " + SEARCH_FROM + whereClause)
                .bindMap(countParams).mapTo(Long.class).one();
        return new PageResult<>(items, page, pageSize, total);
    }

    /** Süzgeçle eşleşen hesapların bakiye toplamı. double: bkz. sumPositiveBalances notu. */
    default double sumBalances(Map<String, ColumnFilterValue> filters) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        return getHandle().createQuery("SELECT COALESCE(SUM(vb.balance), 0) " + SEARCH_FROM
                        + "WHERE c.is_deleted = 0" + where.getWhereFragment())
                .bindMap(where.getParams()).mapTo(Double.class).one();
    }
}
