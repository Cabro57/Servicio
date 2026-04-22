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

    private int id;

    // Cihazın kime ait olduğunu tutmak performansı artırır (İsteğe bağlı, doğrudan müşteriyle eşleşmesi için)
    @ColumnName("customer_id")
    private Integer customerId;

    @ColumnName("device_type")
    private String deviceType;

    private String brand;
    private String model;

    @ColumnName("serial_no")
    private String serialNo; // UNIQUE olmalı (IMEI vb.)

    private String password;
    private String accessory;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    // JOIN işlemleri için İlişkisel Nesne (Veritabanına direkt yazılmaz)
    private Customer customer;

    @Override
    public String toString() {
        return brand + " " + model + " (" + serialNo + ")";
    }
}