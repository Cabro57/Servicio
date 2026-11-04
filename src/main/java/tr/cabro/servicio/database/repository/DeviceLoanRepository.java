package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterRowMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.database.mapper.DeviceLoanRowMapper;
import tr.cabro.servicio.model.DeviceLoan;

import java.util.List;
import java.util.Optional;

/** Emanet kayıtları ({@code device_loans}); açık emanet = {@code returned_at IS NULL}. */
@RegisterRowMapper(DeviceLoanRowMapper.class)
public interface DeviceLoanRepository {

    String BASE_SELECT = "SELECT l.id, l.device_id, l.customer_id, l.work_order_id, l.direction, l.given_at, l.due_at, " +
            "l.deposit, l.deposit_payment_type, l.condition_out, l.accessories, l.note, l.returned_at, l.condition_in, " +
            "l.damage_charge, l.deposit_refund, l.created_at AS l_created_at, l.updated_at AS l_updated_at, " +
            "d.model AS d_model, d.serial_no AS d_serial_no, d.accessory AS d_accessory, d.is_loaner AS d_is_loaner, " +
            "dt.id AS dt_id, dt.name AS dt_name, db.id AS db_id, db.name AS db_name, " +
            "c.customer_type AS c_type, c.business_name AS c_business_name, " +
            "c.first_name AS c_first_name, c.last_name AS c_last_name, c.phone_number_1 AS c_phone_number_1 " +
            "FROM device_loans l " +
            "JOIN devices d ON l.device_id = d.id " +
            "JOIN device_types dt ON d.device_type_id = dt.id " +
            "JOIN device_brands db ON d.brand_id = db.id " +
            "JOIN customers c ON l.customer_id = c.id ";

    @SqlUpdate("INSERT INTO device_loans (device_id, customer_id, work_order_id, direction, given_at, due_at, deposit, " +
            "deposit_payment_type, condition_out, accessories, note, created_at, updated_at) " +
            "VALUES (:deviceId, :customerId, :workOrderId, :direction, :givenAt, :dueAt, :deposit, " +
            ":depositPaymentType, :conditionOut, :accessories, :note, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean DeviceLoan loan);

    /** Emaneti kapatır; yalnızca açıksa günceller (çift kapatmaya karşı), etkilenen satır sayısını döndürür. */
    @SqlUpdate("UPDATE device_loans SET returned_at = :returnedAt, condition_in = :conditionIn, " +
            "damage_charge = :damageCharge, deposit_refund = :depositRefund, updated_at = :updatedAt " +
            "WHERE id = :id AND returned_at IS NULL AND is_deleted = 0")
    int close(@BindBean DeviceLoan loan);

    @SqlQuery(BASE_SELECT + "WHERE l.id = :id AND l.is_deleted = 0")
    Optional<DeviceLoan> findById(@Bind("id") Long id);

    @SqlQuery(BASE_SELECT + "WHERE l.device_id = :deviceId AND l.returned_at IS NULL AND l.is_deleted = 0")
    Optional<DeviceLoan> findOpenByDevice(@Bind("deviceId") Long deviceId);

    /** Cihaz geçmişi için: cihazın tüm emanetleri, en yeni önce. */
    @SqlQuery(BASE_SELECT + "WHERE l.device_id = :deviceId AND l.is_deleted = 0 ORDER BY l.given_at DESC, l.id DESC")
    List<DeviceLoan> findByDevice(@Bind("deviceId") Long deviceId);

    /** Müşteri detayı için: müşterinin tüm emanetleri, açıklar önce, sonra en yeni. */
    @SqlQuery(BASE_SELECT + "WHERE l.customer_id = :customerId AND l.is_deleted = 0 " +
            "ORDER BY CASE WHEN l.returned_at IS NULL THEN 0 ELSE 1 END, l.given_at DESC, l.id DESC")
    List<DeviceLoan> findByCustomer(@Bind("customerId") Long customerId);

    /** İptal: kayıt gizlenir (kasa hareketleri servis katmanında ters kayıtla kapatılır). */
    @SqlUpdate("UPDATE device_loans SET is_deleted = 1, updated_at = CURRENT_TIMESTAMP WHERE id = :id AND is_deleted = 0")
    int softDelete(@Bind("id") Long id);

    /** Tüm açık emanetler, iade tarihi en yakın önce. */
    @SqlQuery(BASE_SELECT + "WHERE l.returned_at IS NULL AND l.is_deleted = 0 " +
            "ORDER BY CASE WHEN l.due_at IS NULL THEN 1 ELSE 0 END, l.due_at ASC, l.given_at ASC")
    List<DeviceLoan> findOpen();
}
