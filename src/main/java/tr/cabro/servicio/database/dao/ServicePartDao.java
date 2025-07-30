package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.model.AddedPart;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ServicePartDao extends BaseDao<AddedPart, Integer> {

    @Override
    protected String getTableName() {
        return "added_part";
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO added_part (service_id, barcode, amount, price) VALUES (?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE added_part SET service_id=?, barcode=?, amount=?, price=? WHERE id=?";
    }

    @Override
    protected void fillInsertStatement(PreparedStatement stmt, AddedPart entity) throws SQLException {
        stmt.setInt(1, entity.getServiceId());
        stmt.setString(2, entity.getBarcode());
        stmt.setInt(3, entity.getAmount());
        stmt.setDouble(4, entity.getSellingPrice());
    }

    @Override
    protected void fillUpdateStatement(PreparedStatement stmt, AddedPart entity) throws SQLException {
        stmt.setInt(1, entity.getServiceId());
        stmt.setString(2, entity.getBarcode());
        stmt.setInt(3, entity.getAmount());
        stmt.setDouble(4, entity.getSellingPrice());
        stmt.setInt(5, entity.getId());
    }

    @Override
    protected AddedPart mapRow(ResultSet rs) throws SQLException {
        AddedPart part = new AddedPart();
        part.setId(rs.getInt("id"));
        part.setServiceId(rs.getInt("service_id"));
        part.setBarcode(rs.getString("barcode"));
        part.setAmount(rs.getInt("amount"));
        part.setSellingPrice(rs.getDouble("price"));
        return part;
    }

    @Override
    protected void setGeneratedId(AddedPart entity, int id) {
        entity.setId(id);
    }

    @Override
    protected void setKey(PreparedStatement stmt, int index, Integer key) throws SQLException {
        stmt.setInt(index, key);
    }

    public List<AddedPart> getByServiceId(int serviceId) {
        List<AddedPart> parts = new ArrayList<>();
        String sql = "SELECT * FROM added_part WHERE service_id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serviceId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                parts.add(mapRow(rs));
            }
        } catch (SQLException e) {
            Servicio.getInstance().getLogger().severe("DB ERROR [GET PARTS BY SERVICE ID] " + e.toString());
        }
        return parts;
    }

    public boolean deleteByServiceId(int serviceId) {
        String sql = "DELETE FROM " + getTableName() + " WHERE service_id = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, serviceId);
            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            Servicio.getInstance().getLogger().severe("DB ERROR [DELETE BY SERVICE ID] " + e.toString());
            return false;
        }
    }
}
