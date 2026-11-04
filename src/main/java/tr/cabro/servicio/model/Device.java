package tr.cabro.servicio.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jdbi.v3.core.mapper.Nested;
import org.jdbi.v3.core.mapper.reflect.ColumnName;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;

import java.time.LocalDateTime;

@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
public class Device {

    private Long id;

    @Nested("deviceType")
    private DeviceType deviceType;

    @Nested("brand")
    private DeviceBrand brand;
    private String model;

    @ColumnName("serial_no")
    private String serialNo;

    private String accessory;

    /** Dükkân cihazı (yedek): müşteriye emanet verilebilir. Yalnızca {@code DeviceService.setLoaner} yazar. */
    @ColumnName("is_loaner")
    private boolean loaner;

    /**
     * DB kolonu değil: cihazın açık emanetinin yönü ve kimde olduğu (liste/detay için,
     * {@code DeviceRepository.BASE_SELECT} alt sorgusundan). Açık emanet yoksa null.
     */
    private tr.cabro.servicio.model.enums.LoanDirection openLoanDirection;
    private String openLoanParty;

    @ColumnName("is_deleted")
    private boolean deleted;

    @ColumnName("updated_at")
    private LocalDateTime updatedAt;

    @ColumnName("created_at")
    private LocalDateTime createdAt;

    public String getDisplayName() {
        if (getBrand() != null) {
            String brandName = getBrand().getName();
            return String.format("%s %s", brandName, getModel());
        }

        return "-";
    }

    @Override
    public String toString() {
        return brand + " " + model + " (" + serialNo + ")";
    }
}