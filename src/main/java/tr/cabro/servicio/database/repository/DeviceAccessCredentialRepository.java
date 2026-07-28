package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.DeviceAccessCredential;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@code secret} alanı bu katmanda HER ZAMAN şifreli (base64) haldedir — şifreleme/çözme işi
 * {@code DeviceAccessCredentialService}'te yapılır, repository ham veriyle çalışır.
 */
@RegisterBeanMapper(DeviceAccessCredential.class)
public interface DeviceAccessCredentialRepository {

    @SqlUpdate("INSERT INTO device_access_credentials (work_order_id, access_type, secret_encrypted, set_at, created_at) " +
            "VALUES (:workOrderId, :accessType, :secret, :setAt, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean DeviceAccessCredential credential);

    @SqlUpdate("UPDATE device_access_credentials SET access_type=:accessType, secret_encrypted=:secret, set_at=:setAt, purged_at=NULL " +
            "WHERE id=:id")
    void update(@BindBean DeviceAccessCredential credential);

    @SqlQuery("SELECT id, work_order_id, access_type, secret_encrypted AS secret, set_at, purged_at, created_at " +
            "FROM device_access_credentials WHERE work_order_id=:workOrderId ORDER BY id DESC LIMIT 1")
    Optional<DeviceAccessCredential> findActiveByWorkOrderId(@Bind("workOrderId") Long workOrderId);

    @SqlQuery("SELECT dac.id, dac.work_order_id, dac.access_type, dac.secret_encrypted AS secret, dac.set_at, dac.purged_at, dac.created_at " +
            "FROM device_access_credentials dac " +
            "JOIN work_orders wo ON wo.id = dac.work_order_id " +
            "WHERE dac.secret_encrypted IS NOT NULL AND dac.purged_at IS NULL " +
            "AND wo.delivery_date IS NOT NULL AND wo.delivery_date <= :cutoff")
    List<DeviceAccessCredential> findExpiredSince(@Bind("cutoff") LocalDateTime cutoff);

    @SqlUpdate("UPDATE device_access_credentials SET secret_encrypted=NULL, purged_at=:purgedAt WHERE id=:id")
    void purge(@Bind("id") Long id, @Bind("purgedAt") LocalDateTime purgedAt);
}
