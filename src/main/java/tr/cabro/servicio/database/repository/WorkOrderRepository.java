package tr.cabro.servicio.database.repository;

import org.jdbi.v3.core.mapper.reflect.BeanMapper;
import org.jdbi.v3.sqlobject.SqlObject;
import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.database.filter.ColumnFilterValue;
import tr.cabro.servicio.database.filter.SqlWhereBuilder;
import tr.cabro.servicio.model.WorkOrder;
import tr.cabro.servicio.model.dto.ChartDataDto;
import tr.cabro.servicio.model.dto.PageResult;
import tr.cabro.servicio.model.enums.ServiceStatus;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RegisterBeanMapper(WorkOrder.class)
public interface WorkOrderRepository extends SqlObject {

    @SqlUpdate("INSERT INTO work_orders (customer_id, device_id, technician_id, reported_fault, " +
            "urgency_status, service_status, warranty_end_date, delivery_date, created_at, updated_at, status_changed_at) " +
            "VALUES (:customerId, :deviceId, :technicianId, :reportedFault, " +
            ":urgencyStatus, :serviceStatus, :warrantyEndDate, :deliveryDate, :createdAt, :updatedAt, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean WorkOrder workOrder);

    @SqlUpdate("UPDATE work_orders SET " +
            "customer_id=:customerId, device_id=:deviceId, technician_id=:technicianId, " +
            "reported_fault=:reportedFault, updated_at=:updatedAt " +
            "WHERE id=:id")
    void update(@BindBean WorkOrder workOrder);

    @SqlUpdate("UPDATE work_orders SET service_status=:status, delivery_date=:deliveryDate, updated_at=:updatedAt, " +
            "status_changed_at=CASE WHEN service_status IS :status THEN status_changed_at ELSE :updatedAt END WHERE id=:id")
    void updateStatus(@Bind("id") Long id,
                      @Bind("status") ServiceStatus status,
                      @Bind("deliveryDate") LocalDateTime deliveryDate,
                      @Bind("updatedAt") LocalDateTime updatedAt);

    @SqlUpdate("UPDATE work_orders SET detected_fault=:detectedFault, updated_at=:updatedAt WHERE id=:id")
    void updateDetectedFault(@Bind("id") Long id,
                             @Bind("detectedFault") String detectedFault,
                             @Bind("updatedAt") LocalDateTime updatedAt);

    @SqlQuery("SELECT * FROM work_orders WHERE id = :id")
    Optional<WorkOrder> findById(@Bind("id") Long id);

    @SqlUpdate("DELETE FROM work_orders WHERE id = :id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT * FROM work_orders ORDER BY created_at DESC")
    List<WorkOrder> findAll();

    @SqlQuery("SELECT * FROM work_orders WHERE customer_id = :customerId ORDER BY created_at DESC")
    List<WorkOrder> findByCustomerId(@Bind("customerId") Long customerId);

    @SqlQuery("SELECT * FROM work_orders WHERE device_id = :deviceId ORDER BY created_at DESC")
    List<WorkOrder> findByDeviceId(@Bind("deviceId") Long deviceId);

    @SqlQuery("SELECT DISTINCT wo.* FROM work_orders wo " +
            "INNER JOIN work_order_items woi ON woi.service_id = wo.id " +
            "WHERE woi.part_id = :partId ORDER BY wo.created_at DESC")
    List<WorkOrder> findByPartId(@Bind("partId") Long partId);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status IN (<statuses>) ORDER BY created_at DESC")
    List<WorkOrder> findByStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC")
    List<WorkOrder> findByStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    // Arama alanı sadece şikayet/notlarla sınırlı DEĞİL: müşteri adı/telefonu, cihaz marka/model/seri no
    // ve kayıt ID'si de kapsanmalı — sayfalama öncesi client-side arama tüm kolonları tarıyordu, bu kapsamı korur.
    String SEARCH_SELECT = "SELECT s.* ";
    String SEARCH_FROM = "FROM work_orders s " +
            "LEFT JOIN customers c ON c.id = s.customer_id " +
            "LEFT JOIN devices d ON d.id = s.device_id " +
            "LEFT JOIN device_brands db ON db.id = d.brand_id ";
    String SEARCH_WHERE = "WHERE (" +
            "CAST(s.id AS TEXT) LIKE :search " +
            "OR s.reported_fault LIKE :search " +
            "OR c.first_name LIKE :search OR c.last_name LIKE :search OR c.business_name LIKE :search OR c.phone_number_1 LIKE :search " +
            "OR db.name LIKE :search OR d.model LIKE :search OR d.serial_no LIKE :search " +
            "OR EXISTS (SELECT 1 FROM work_order_notes sn WHERE sn.service_id = s.id AND sn.note LIKE :search)" +
            ") ";

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY s.created_at DESC")
    List<WorkOrder> search(@Bind("search") String searchTerm);

    // =========================================================================
    // SAYFALAMA (LIMIT/OFFSET) — DB-tabanlı liste ekranları ve dashboard için
    // =========================================================================

    @SqlQuery("SELECT * FROM work_orders ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findAllPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders")
    long countAll();

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "ORDER BY s.created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> searchPaged(@Bind("search") String searchTerm, @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE)
    long countSearch(@Bind("search") String searchTerm);

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "AND s.service_status IN (<statuses>) ORDER BY s.created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> searchPagedByStatuses(@Bind("search") String searchTerm, @BindList("statuses") List<ServiceStatus> statuses,
                                          @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE + "AND s.service_status IN (<statuses>)")
    long countSearchByStatuses(@Bind("search") String searchTerm, @BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery(SEARCH_SELECT + SEARCH_FROM + SEARCH_WHERE + "AND s.service_status NOT IN (<statuses>) ORDER BY s.created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> searchPagedByStatusesExcluded(@Bind("search") String searchTerm, @BindList("statuses") List<ServiceStatus> statuses,
                                                   @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) " + SEARCH_FROM + SEARCH_WHERE + "AND s.service_status NOT IN (<statuses>)")
    long countSearchByStatusesExcluded(@Bind("search") String searchTerm, @BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status IN (<statuses>) ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findByStatusesPaged(@BindList("statuses") List<ServiceStatus> statuses,
                                        @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders WHERE service_status IN (<statuses>)")
    long countByStatuses(@BindList("statuses") List<ServiceStatus> statuses);

    @SqlQuery("SELECT * FROM work_orders WHERE service_status NOT IN (<statuses>) ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findByStatusesExcludedPaged(@BindList("statuses") List<ServiceStatus> statuses,
                                                 @Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders WHERE service_status NOT IN (<statuses>)")
    long countByStatusesExcluded(@BindList("statuses") List<ServiceStatus> statuses);

    // Ana sayfadaki servis hattı: açık (teslim/iade edilmemiş) iş emirlerinin duruma göre adetleri.
    // label = service_status (enum name), value = adet.
    @RegisterBeanMapper(ChartDataDto.class)
    @SqlQuery("SELECT service_status AS label, COUNT(*) AS value FROM work_orders " +
            "WHERE service_status NOT IN ('DELIVERED', 'RETURN') GROUP BY service_status")
    List<ChartDataDto> countOpenGroupedByStatus();

    @SqlQuery("SELECT COUNT(*) FROM work_orders WHERE created_at >= :start AND created_at < :end")
    long countCreatedBetween(@Bind("start") LocalDateTime start, @Bind("end") LocalDateTime end);

    // "Bekleyen Tahsilatlar": kalan tutar > 0 olan iş emirleri. Kalan tutar v_document_balances
    // view'ından gelir (bkz. V19 migration) — belge toplamı - o belgeye tahsis edilmiş ödeme toplamı.
    @SqlQuery("SELECT wo.* FROM work_orders wo " +
            "JOIN v_document_balances vb ON vb.document_type = 'WORK_ORDER' AND vb.document_id = wo.id " +
            "WHERE vb.remaining_amount > 0 " +
            "ORDER BY wo.created_at DESC LIMIT :limit OFFSET :offset")
    List<WorkOrder> findWithDebtPaged(@Bind("limit") int limit, @Bind("offset") int offset);

    @SqlQuery("SELECT COUNT(*) FROM work_orders wo " +
            "JOIN v_document_balances vb ON vb.document_type = 'WORK_ORDER' AND vb.document_id = wo.id " +
            "WHERE vb.remaining_amount > 0")
    long countWithDebt();

    // =========================================================================
    // TABLO BAŞLIĞI FİLTRESİ — dinamik WHERE (SqlWhereBuilder), JDBI SqlObject default metot.
    // Kolon adları ColumnDef.enumFilter/dateRangeFilter çağrılarında sabit tanımlı (FormWorkOrders),
    // kullanıcıdan gelmez; değerler her zaman bind parametresi (bkz. SqlWhereBuilder).
    // =========================================================================

    /** Servis listesinin sıralama seçenekleri; SQL parçası sabittir, kullanıcıdan gelmez. */
    enum Sort {
        NEWEST("s.created_at DESC"),
        OLDEST("s.created_at ASC"),
        LONGEST_IN_STATUS("COALESCE(s.status_changed_at, s.created_at) ASC"),
        RECENT_ACTIVITY("COALESCE(s.status_changed_at, s.updated_at, s.created_at) DESC"),
        REMAINING_DESC("(SELECT COALESCE(vb.remaining_amount, 0) FROM v_document_balances vb "
                + "WHERE vb.document_type = 'WORK_ORDER' AND vb.document_id = s.id) DESC, s.created_at DESC"),
        CUSTOMER("c.first_name COLLATE NOCASE ASC, c.last_name COLLATE NOCASE ASC, s.created_at DESC");

        final String orderBy;

        Sort(String orderBy) {
            this.orderBy = orderBy;
        }
    }

    /** Ödeme durumu süzgeci; belge toplamı/kalanı v_document_balances'tan gelir (kalemi olmayan iş = ücret yok). */
    enum PayFilter {
        ALL(null),
        PAID("(bal.total > 0 AND bal.remaining <= 0)"),
        PARTIAL("(bal.total > 0 AND bal.remaining > 0 AND bal.remaining < bal.total)"),
        UNPAID("(bal.total > 0 AND bal.remaining >= bal.total)"),
        FREE("(bal.total = 0)");

        final String sql;

        PayFilter(String sql) {
            this.sql = sql;
        }
    }

    default PageResult<WorkOrder> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                       int page, int pageSize) {
        return searchFilteredPaged(searchTerm, filters, page, pageSize, Sort.NEWEST, PayFilter.ALL);
    }

    default PageResult<WorkOrder> searchFilteredPaged(String searchTerm, Map<String, ColumnFilterValue> filters,
                                                       int page, int pageSize, Sort sort, PayFilter pay) {
        SqlWhereBuilder.Result where = SqlWhereBuilder.build(filters);
        String from = SEARCH_FROM;
        String whereClause = SEARCH_WHERE + where.getWhereFragment();
        if (pay != null && pay.sql != null) {
            from += "LEFT JOIN (SELECT document_id, COALESCE(total_amount, 0) AS total, COALESCE(remaining_amount, 0) AS remaining "
                    + "FROM v_document_balances WHERE document_type = 'WORK_ORDER') bal ON bal.document_id = s.id ";
            // Kalemi olmayan iş görünümde hiç yer almayabilir: bal.* NULL ise toplam 0 sayılır.
            String fixed = pay.sql.replace("bal.total", "COALESCE(bal.total, 0)").replace("bal.remaining", "COALESCE(bal.remaining, 0)");
            whereClause += "AND " + fixed + " ";
        }

        Map<String, Object> countParams = new HashMap<>(where.getParams());
        countParams.put("search", (searchTerm != null && !searchTerm.isBlank()) ? "%" + searchTerm.trim() + "%" : "%");

        Map<String, Object> params = new HashMap<>(countParams);
        params.put("limit", pageSize);
        params.put("offset", (page - 1) * pageSize);

        String listSql = SEARCH_SELECT + from + whereClause + " ORDER BY " + (sort != null ? sort : Sort.NEWEST).orderBy + " LIMIT :limit OFFSET :offset";
        String countSql = "SELECT COUNT(*) " + from + whereClause;

        List<WorkOrder> items = getHandle().createQuery(listSql).bindMap(params).map(BeanMapper.of(WorkOrder.class)).list();
        long total = getHandle().createQuery(countSql).bindMap(countParams).mapTo(Long.class).one();
        return new PageResult<>(items, page, pageSize, total);
    }
}