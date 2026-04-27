package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockType;

import java.time.LocalDateTime;

@Getter @Setter
public class StockMovement {
    private Long id;
    private Long partId;
    private Long warehouseId;
    private Integer quantity;
    private StockType type;
    private ReferenceType referenceType;
    private Long referenceId;
    private LocalDateTime createdAt;

    @Override
    public String toString() {
        return "StockMovement{" +
                "id=" + id +
                ", partId=" + partId +
                ", warehouseId=" + warehouseId +
                ", quantity=" + quantity +
                ", type='" + type + '\'' +
                ", referenceType='" + referenceType + '\'' +
                ", referenceId=" + referenceId +
                ", createdAt=" + createdAt +
                '}';
    }
}
