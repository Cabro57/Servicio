package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.DocumentTemplate;
import tr.cabro.servicio.model.enums.TemplateType;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(DocumentTemplate.class)
public interface DocumentTemplateRepository {

    @SqlUpdate("INSERT INTO document_templates (template_type, name, body, created_at, updated_at) " +
            "VALUES (:type, :name, :body, :createdAt, :updatedAt)")
    @GetGeneratedKeys
    Long insert(@BindBean DocumentTemplate template);

    @SqlUpdate("UPDATE document_templates SET template_type=:type, name=:name, body=:body, updated_at=:updatedAt WHERE id=:id")
    void update(@BindBean DocumentTemplate template);

    @SqlUpdate("DELETE FROM document_templates WHERE id=:id")
    void delete(@Bind("id") Long id);

    @SqlQuery("SELECT id, template_type AS type, name, body, created_at, updated_at FROM document_templates WHERE id=:id")
    Optional<DocumentTemplate> findById(@Bind("id") Long id);

    @SqlQuery("SELECT id, template_type AS type, name, body, created_at, updated_at FROM document_templates WHERE template_type=:type ORDER BY name")
    List<DocumentTemplate> findByType(@Bind("type") TemplateType type);

    @SqlQuery("SELECT id, template_type AS type, name, body, created_at, updated_at FROM document_templates ORDER BY template_type, name")
    List<DocumentTemplate> findAll();
}
