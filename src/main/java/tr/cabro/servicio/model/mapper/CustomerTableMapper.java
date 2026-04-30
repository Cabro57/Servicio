package tr.cabro.servicio.model.mapper;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.dto.CustomersTableDto;
import tr.cabro.servicio.model.enums.CustomerType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class CustomerTableMapper implements RowMapper<Customer> {
    @Override
    public Customer map(ResultSet rs, StatementContext ctx) throws SQLException {
        Customer c = new Customer();
        c.setId(rs.getLong("id"));
        c.setType(CustomerType.of(rs.getString("type")));
        c.setFirstName(rs.getString("first_name"));
        c.setLastName(rs.getString("last_name"));
        c.setBusinessName(rs.getString("business_name"));
        c.setPhoneNumber1(rs.getString("phone_number_1"));
        c.setPhoneNumber2(rs.getString("phone_number_2"));
        c.setEmail(rs.getString("email"));
        c.setAddress(rs.getString("address"));
        c.setNote(rs.getString("note"));
        c.setIdentityNo(rs.getString("identity_no"));
        c.setTaxNumber(rs.getString("tax_number"));
        c.setTaxOffice(rs.getString("tax_office"));
        c.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        c.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        // Aggregate alanlar
        c.setDeviceCount(rs.getInt("device_count"));
        c.setSpent(rs.getBigDecimal("spent"));
        return c;
    }
}
