package tr.cabro.servicio.database.mapper;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.DeviceLoan;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.model.enums.LoanDirection;
import tr.cabro.servicio.model.enums.PaymentType;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/** {@code DeviceLoanRepository} sorgularının (devices + customers JOIN'li) satır eşleyicisi. */
public class DeviceLoanRowMapper implements RowMapper<DeviceLoan> {

    @Override
    public DeviceLoan map(ResultSet rs, StatementContext ctx) throws SQLException {
        DeviceLoan loan = new DeviceLoan();
        loan.setId(rs.getLong("id"));
        loan.setDeviceId(rs.getLong("device_id"));
        loan.setCustomerId(rs.getLong("customer_id"));
        long workOrderId = rs.getLong("work_order_id");
        loan.setWorkOrderId(rs.wasNull() ? null : workOrderId);
        loan.setDirection(LoanDirection.valueOf(rs.getString("direction")));
        loan.setGivenAt(parse(rs.getString("given_at")));
        loan.setDueAt(parse(rs.getString("due_at")));
        loan.setDeposit(money(rs, "deposit"));
        String paymentType = rs.getString("deposit_payment_type");
        loan.setDepositPaymentType(paymentType != null ? PaymentType.of(paymentType) : null);
        loan.setConditionOut(rs.getString("condition_out"));
        loan.setAccessories(rs.getString("accessories"));
        loan.setNote(rs.getString("note"));
        loan.setReturnedAt(parse(rs.getString("returned_at")));
        loan.setConditionIn(rs.getString("condition_in"));
        loan.setDamageCharge(money(rs, "damage_charge"));
        loan.setDepositRefund(money(rs, "deposit_refund"));
        loan.setCreatedAt(parse(rs.getString("l_created_at")));
        loan.setUpdatedAt(parse(rs.getString("l_updated_at")));

        Device device = new Device();
        device.setId(loan.getDeviceId());
        device.setModel(rs.getString("d_model"));
        device.setSerialNo(rs.getString("d_serial_no"));
        device.setAccessory(rs.getString("d_accessory"));
        device.setLoaner(rs.getBoolean("d_is_loaner"));
        DeviceType type = new DeviceType();
        type.setId(rs.getLong("dt_id"));
        type.setName(rs.getString("dt_name"));
        device.setDeviceType(type);
        DeviceBrand brand = new DeviceBrand();
        brand.setId(rs.getLong("db_id"));
        brand.setName(rs.getString("db_name"));
        device.setBrand(brand);
        loan.setDevice(device);

        Customer customer = new Customer();
        customer.setId(loan.getCustomerId());
        String customerType = rs.getString("c_type");
        if (customerType != null) customer.setType(CustomerType.valueOf(customerType));
        customer.setBusinessName(rs.getString("c_business_name"));
        customer.setFirstName(rs.getString("c_first_name"));
        customer.setLastName(rs.getString("c_last_name"));
        customer.setPhoneNumber1(rs.getString("c_phone_number_1"));
        loan.setCustomer(customer);
        return loan;
    }

    /** sqlite-jdbc NULL DECIMAL'i 0 okur ama wasNull'ı yanlış bildirebiliyor; metinden okunur. */
    private static BigDecimal money(ResultSet rs, String column) throws SQLException {
        String value = rs.getString(column);
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static LocalDateTime parse(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception e) {
            return null;
        }
    }
}
