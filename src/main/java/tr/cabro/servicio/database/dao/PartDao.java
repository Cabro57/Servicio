package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.model.Part;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PartDao extends BaseDao<Part, String> {

    @Override
    protected String getTableName() {
        return "part";
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "barcode";
    }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO part (barcode, brand, supplier_id, name, device_type, model, purchase_price, sale_price, stock, min_stock, " +
                "warranty_period, purchase_date, description, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE part SET brand = ?, name = ?, supplier_id = ?, device_type = ?, model = ?, purchase_price = ?, sale_price = ?," +
                "stock = ?, min_stock = ?, warranty_period = ?, purchase_date = ?, description = ?, created_at = ? WHERE barcode = ?";
    }

    @Override
    protected void fillInsertStatement(PreparedStatement stmt, Part entity) throws SQLException {
        stmt.setString(1, entity.getBarcode());
        stmt.setString(2, entity.getBrand());
        stmt.setInt(3, entity.getSupplier_id());
        stmt.setString(4, entity.getName());
        stmt.setString(5, entity.getDevice_type());
        stmt.setString(6, entity.getModels());
        stmt.setDouble(7, entity.getPurchase_price());
        stmt.setDouble(8, entity.getSale_price());
        stmt.setInt(9, entity.getStock());
        stmt.setInt(10, entity.getMinStock());
        stmt.setInt(11, entity.getWarranty_period());
        stmt.setString(12, entity.getPurchase_date() != null ? entity.getPurchase_date().toString() : null);
        stmt.setString(13, entity.getDescription());
        stmt.setString(14, entity.getCreated_at().toString());
    }

    @Override
    protected void fillUpdateStatement(PreparedStatement stmt, Part entity) throws SQLException {
        stmt.setString(1, entity.getBrand());
        stmt.setString(2, entity.getName());
        stmt.setInt(3, entity.getSupplier_id());
        stmt.setString(4, entity.getDevice_type());
        stmt.setString(5, entity.getModels());
        stmt.setDouble(6, entity.getPurchase_price());
        stmt.setDouble(7, entity.getSale_price());
        stmt.setInt(8, entity.getStock());
        stmt.setInt(9, entity.getMinStock());
        stmt.setInt(10, entity.getWarranty_period());
        stmt.setString(11, entity.getPurchase_date() != null ? entity.getPurchase_date().toString() : null);
        stmt.setString(12, entity.getDescription());
        stmt.setString(13, entity.getCreated_at().toString());
        stmt.setString(14, entity.getBarcode());
    }

    @Override
    protected Part mapRow(ResultSet rs) throws SQLException {
        Part p = new Part();
        p.setBarcode(rs.getString("barcode"));
        p.setBrand(rs.getString("brand"));
        p.setName(rs.getString("name"));
        p.setSupplier_id(rs.getInt("supplier_id"));
        p.setDevice_type(rs.getString("device_type"));
        p.setModels(rs.getString("model"));
        p.setPurchase_price(rs.getDouble("purchase_price"));
        p.setSale_price(rs.getDouble("sale_price"));
        p.setStock(rs.getInt("stock"));
        p.setMinStock(rs.getInt("min_stock"));
        p.setWarranty_period(rs.getInt("warranty_period"));

        String dateStr = rs.getString("purchase_date");
        if (dateStr != null && !dateStr.isEmpty()) {
            p.setPurchase_date(LocalDate.parse(dateStr));
        }

        p.setDescription(rs.getString("description"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            p.setCreated_at(LocalDateTime.parse(createdAtStr));
        }

        return p;
    }

    @Override
    protected void setGeneratedId(Part entity, int id) {

    }

    @Override
    protected void setKey(PreparedStatement stmt, int index, String key) throws SQLException {
        stmt.setString(index, key);
    }

    public boolean isBarcodeExists(String barcode) {
        String sql = "SELECT COUNT(*) FROM " + getTableName() + " WHERE barcode = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setString(1, barcode);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [CHECK BARCODE EXISTS] {}", String.valueOf(e));
        }
        return false;
    }

    public List<Part> getProductsBelowMinStock() {
        List<Part> list = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName() + " WHERE stock < min_stock";
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [GET BELOW MIN STOCK] {}", String.valueOf(e));
        }
        return list;
    }

    public void updateStock(String barcode, int newStock) {
        // Basit JDBC güncellemesi
        String sql = "UPDATE part SET stock = ? WHERE barcode = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            stmt.setInt(1, newStock);
            stmt.setString(2, barcode);
            stmt.executeUpdate();
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [UPDATE STOCK] {}", String.valueOf(e));
        }
    }

    private String dateToStr(LocalDateTime date) {
        return date != null ? date.toString() : null;
    }

    private LocalDateTime strToDate(String dateStr) {
        return (dateStr != null) ? LocalDateTime.parse(dateStr) : null;
    }

}
