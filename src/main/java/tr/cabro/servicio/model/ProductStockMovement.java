package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.enums.ReferenceType;
import tr.cabro.servicio.model.enums.StockType;

import java.time.LocalDateTime;

/** {@link tr.cabro.servicio.model.StockMovement}'ın {@code products} için ayrı kopyası — bkz. V21 migration. */
@Getter @Setter
public class ProductStockMovement {
    private Long id;
    private Long productId;
    private Integer quantity;
    private StockType type;
    private ReferenceType referenceType;
    private Long referenceId;
    private LocalDateTime createdAt;
}
