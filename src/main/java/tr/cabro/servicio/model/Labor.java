package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
public class Labor {

    private Long id;
    private String name;
    private String description;
    private String category;

    @ColumnName("default_price")
    private BigDecimal defaultPrice = BigDecimal.ZERO;

    @ColumnName("device_type_id")
    private Long deviceTypeId;

    // Yalnızca listeleme sorgularında JOIN ile doldurulur, insert/update'te kullanılmaz.
    @ColumnName("device_type_name")
    private String deviceTypeName;

    @ColumnName("is_deleted")
    private boolean isDeleted;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    public Labor() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @Override
    public String toString() {
        return name + (defaultPrice.compareTo(BigDecimal.ZERO) > 0 ? " (" + defaultPrice + " TL)" : "");
    }
}