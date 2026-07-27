package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.enums.TemplateType;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class DocumentTemplate {

    private Long id;

    @ColumnName("template_type")
    private TemplateType type;

    private String name;
    private String body;

    @ColumnName("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @ColumnName("updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Override
    public String toString() {
        return name;
    }
}
