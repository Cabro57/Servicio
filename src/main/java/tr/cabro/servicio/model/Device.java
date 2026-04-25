package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.reflect.ColumnName;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    private Long id;

    @ColumnName("device_type")
    private String deviceType;

    private String brand;
    private String model;

    @ColumnName("serial_no")
    private String serialNo;

    private String password;
    private String accessory;

    @ColumnName("is_deleted")
    private boolean deleted;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return brand + " " + model + " (" + serialNo + ")";
    }
}