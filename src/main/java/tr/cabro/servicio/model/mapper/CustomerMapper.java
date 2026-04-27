package tr.cabro.servicio.model.mapper;

import org.mapstruct.Mapper;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.CustomersTableDto;

@Mapper
public interface CustomerMapper {

    CustomersTableDto toDto(Customer customer);
}
