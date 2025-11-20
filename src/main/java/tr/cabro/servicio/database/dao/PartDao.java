package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.exception.DataAccessException;
import tr.cabro.servicio.model.Part;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class PartDao extends BaseDao<Part, String> {

    @Override
    protected String getTableName() { return "part"; }
    @Override
    protected String getPrimaryKeyColumn() { return "barcode"; }

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
    protected void fillStatement(PreparedStatement stmt, Part entity, boolean isUpdate) throws SQLException {
        int i = 1;
        // Barcode PK olduğu için insertte başta, update'de sonda olabilir ama
        // burada insert statement'a göre PK başta tanımlanmış.
        // BaseDao'dan gelen yapı gereği Insert SQL'de PK elle veriliyor.
        if (!isUpdate) {
            stmt.setString(i++, entity.getBarcode());
        }

        stmt.setString(i++, entity.getBrand());
        stmt.setInt(i++, entity.getSupplier_id());
        stmt.setString(i++, entity.getName());
        stmt.setString(i++, entity.getDevice_type());
        stmt.setString(i++, entity.getModels());
        stmt.setDouble(i++, entity.getPurchase_price());
        stmt.setDouble(i++, entity.getSale_price());
        stmt.setInt(i++, entity.getStock());
        stmt.setInt(i++, entity.getMinStock());
        stmt.setInt(i++, entity.getWarranty_period());
        stmt.setString(i++, entity.getPurchase_date() != null ? entity.getPurchase_date().toString() : null);
        stmt.setString(i++, entity.getDescription());
        stmt.setString(i++, entity.getCreated_at().toString());

        if (isUpdate) {
            stmt.setString(i++, entity.getBarcode());
        }
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
        // String PK olduğu için burası boş
    }

    // --- Özel Metodlar (Exception Fırlatacak Şekilde Güncellendi) ---

    public boolean isBarcodeExists(String barcode) {
        String sql = "SELECT COUNT(*) FROM " + getTableName() + " WHERE barcode = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, barcode);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Barkod kontrolü hatası: " + barcode, e);
        }
        return false;
    }

    public List<Part> getProductsBelowMinStock() {
        List<Part> list = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName() + " WHERE stock < min_stock";
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Kritik stok listesi alınamadı.", e);
        }
        return list;
    }

    public void adjustStock(String barcode, int amount) {
        String sql = "UPDATE part SET stock = stock + ? WHERE barcode = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, amount);
            stmt.setString(2, barcode);
            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new DataAccessException("Stok güncellenemedi, ürün bulunamadı: " + barcode, null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Stok güncelleme hatası: " + barcode, e);
        }
    }
}