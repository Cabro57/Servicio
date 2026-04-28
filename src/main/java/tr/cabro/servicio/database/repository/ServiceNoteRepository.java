package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.WorkOrderNote;

import java.util.List;

@RegisterBeanMapper(WorkOrderNote.class)
public interface ServiceNoteRepository {

    @SqlUpdate("INSERT INTO work_order_notes (service_id, technician_id, note, created_at) " +
            "VALUES (:serviceId, :technicianId, :note, :createdAt)")
    @GetGeneratedKeys
    Long insert(@BindBean WorkOrderNote note);

    // Bir servise ait tüm notları kronolojik olarak (en yeni en üstte) getirir
    @SqlQuery("SELECT * FROM service_notes WHERE work_order_id = :serviceId ORDER BY created_at DESC")
    List<WorkOrderNote> findByServiceId(@Bind("serviceId") Long serviceId);

    // Yanlış yazılan bir notu silmek için
    @SqlUpdate("DELETE FROM work_order_notes WHERE id = :id")
    void delete(@Bind("id") Long id);
}