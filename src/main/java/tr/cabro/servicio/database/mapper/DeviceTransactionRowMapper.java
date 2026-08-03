package tr.cabro.servicio.database.mapper;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceTransaction;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.model.enums.DeviceTransactionType;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** DeviceTransactionRepository'nin devices+customers JOIN'li sorgularında kullanılan row mapper. */
public class DeviceTransactionRowMapper implements RowMapper<DeviceTransaction> {

    @Override
    public DeviceTransaction map(ResultSet rs, StatementContext ctx) throws SQLException {
        DeviceTransaction transaction = new DeviceTransaction();
        transaction.setId(rs.getLong("id"));
        transaction.setDeviceId(rs.getLong("device_id"));
        transaction.setType(DeviceTransactionType.valueOf(rs.getString("type")));
        transaction.setCustomerId(rs.getLong("customer_id"));
        transaction.setTransactionDate(parseDateTime(rs.getString("transaction_date")));
        transaction.setPrice(rs.getBigDecimal("price"));
        transaction.setExpertiseNotes(rs.getString("expertise_notes"));
        int warrantyMonths = rs.getInt("warranty_months");
        transaction.setWarrantyMonths(rs.wasNull() ? null : warrantyMonths);
        transaction.setNote(rs.getString("note"));
        transaction.setDeleted(rs.getBoolean("tr_is_deleted"));
        transaction.setCreatedAt(parseDateTime(rs.getString("tr_created_at")));
        transaction.setUpdatedAt(parseDateTime(rs.getString("tr_updated_at")));

        Device device = new Device();
        device.setId(rs.getLong("device_id"));
        device.setModel(rs.getString("d_model"));
        device.setSerialNo(rs.getString("d_serial_no"));
        device.setAccessory(rs.getString("d_accessory"));

        DeviceType deviceType = new DeviceType();
        deviceType.setId(rs.getLong("dt_id"));
        deviceType.setName(rs.getString("dt_name"));
        device.setDeviceType(deviceType);

        DeviceBrand brand = new DeviceBrand();
        brand.setId(rs.getLong("db_id"));
        brand.setName(rs.getString("db_name"));
        device.setBrand(brand);
        transaction.setDevice(device);

        Customer customer = new Customer();
        customer.setId(rs.getLong("customer_id"));
        String customerType = rs.getString("c_type");
        if (customerType != null) {
            customer.setType(CustomerType.valueOf(customerType));
        }
        customer.setBusinessName(rs.getString("c_business_name"));
        customer.setFirstName(rs.getString("c_first_name"));
        customer.setLastName(rs.getString("c_last_name"));
        customer.setPhoneNumber1(rs.getString("c_phone_number_1"));
        transaction.setCustomer(customer);

        return transaction;
    }

    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
