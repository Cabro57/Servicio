package tr.cabro.servicio.model.dto;

import lombok.Getter;
import lombok.Setter;
import tr.cabro.servicio.model.Customer;

import java.math.BigDecimal;

@Getter @Setter
public class CustomersTableDto extends Customer {
    private Integer deviceCount;
    private BigDecimal spent;
}
