package tr.cabro.servicio.model.mapper;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.CustomersTableDto;

public interface CustomerMapper {

    CustomersTableDto toDto(Customer customer);
}
