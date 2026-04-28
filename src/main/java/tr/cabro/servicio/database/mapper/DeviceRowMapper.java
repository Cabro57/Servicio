package tr.cabro.servicio.database.mapper;

import org.jdbi.v3.core.mapper.RowMapper;
import org.jdbi.v3.core.statement.StatementContext;
import tr.cabro.servicio.model.Device;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;

import java.sql.ResultSet;
import java.sql.SQLException;

public class DeviceRowMapper implements RowMapper<Device> {

    @Override
    public Device map(ResultSet rs, StatementContext ctx) throws SQLException {
        Device device = new Device();
        device.setId(rs.getLong("id"));
        device.setModel(rs.getString("model"));
        device.setSerialNo(rs.getString("serial_no"));
        device.setPassword(rs.getString("password"));
        device.setAccessory(rs.getString("accessory"));
        device.setDeleted(rs.getBoolean("is_deleted"));

        // Hatalı olan rs.getTimestamp yerine rs.getString kullanıyoruz
        device.setCreatedAt(parseDateTime(rs.getString("created_at")));
        device.setUpdatedAt(parseDateTime(rs.getString("updated_at")));

        // DeviceType mapping
        DeviceType deviceType = new DeviceType();
        deviceType.setId(rs.getLong("device_type_id"));
        deviceType.setName(rs.getString("device_type_name"));
        device.setDeviceType(deviceType);

        // DeviceBrand mapping
        DeviceBrand brand = new DeviceBrand();
        brand.setId(rs.getLong("device_brand_id"));
        brand.setName(rs.getString("device_brand_name"));
        device.setBrand(brand);

        return device;
    }

    // SQLite'daki farklı formatları güvenli şekilde parse etmek için yardımcı metod
    private java.time.LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.isEmpty()) {
            return null;
        }
        try {
            // "T" harfi içeren ISO formatını (2026-04-07T17:17:14) otomatik çözer
            return java.time.LocalDateTime.parse(dateTimeStr.replace(" ", "T"));
        } catch (Exception e) {
            // Eğer format yine de bozuksa loglayıp null dönebilirsiniz
            return null;
        }
    }
}