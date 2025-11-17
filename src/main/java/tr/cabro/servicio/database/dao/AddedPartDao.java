package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.model.AddedPart;

import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class AddedPartDao extends BaseDao<AddedPart, Integer> {

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
        return "INSERT INTO added_part (" +
                "service_id, series_no, brand, name, supplier_id, device_type, model, amount, " +
                "purchase_price, sale_price, warranty_period, purchase_date, description, created_at" +
                ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE added_part SET " +
                "service_id=?, series_no=?, brand=?, name=?, supplier_id=?, device_type=?, model=?, " +
                "amount=?, purchase_price=?, sale_price=?, warranty_period=?, purchase_date=?, description=?, created_at=? " +
                "WHERE id=?";
    }

    @Override
    protected void fillInsertStatement(PreparedStatement stmt, AddedPart entity) throws SQLException {
        stmt.setInt(1, entity.getServiceId());
        stmt.setString(2, entity.getSerial_no());
        stmt.setString(3, entity.getBrand());
        stmt.setString(4, entity.getName());
        stmt.setObject(5, entity.getSupplier_id(), Types.INTEGER);
        stmt.setString(6, entity.getDevice_type());
        stmt.setString(7, entity.getModels());
        stmt.setInt(8, entity.getAmount());
        stmt.setDouble(9, entity.getPurchasePrice());
        stmt.setDouble(10, entity.getSellingPrice());
        stmt.setObject(11, entity.getWarranty_period(), Types.INTEGER);
        stmt.setString(12, entity.getPurchase_date() != null ? entity.getPurchase_date().toString() : null);
        stmt.setString(13, entity.getDescription());
        stmt.setString(14, dateToStr(entity.getCreated_at()));
    }

    @Override
    protected void fillUpdateStatement(PreparedStatement stmt, AddedPart entity) throws SQLException {
        fillInsertStatement(stmt, entity);
        stmt.setInt(15, entity.getId());
    }

    @Override
    protected AddedPart mapRow(ResultSet rs) throws SQLException {
        AddedPart ap = new AddedPart();
        ap.setId(rs.getInt("id"));
        ap.setServiceId(rs.getInt("service_id"));
        ap.setSerial_no(rs.getString("series_no"));
        ap.setBrand(rs.getString("brand"));
        ap.setName(rs.getString("name"));
        ap.setSupplier_id(rs.getInt("supplier_id"));
        ap.setDevice_type(rs.getString("device_type"));
        ap.setModels(rs.getString("model"));
        ap.setAmount(rs.getInt("amount"));
        ap.setPurchasePrice(rs.getDouble("purchase_price"));
        ap.setSellingPrice(rs.getDouble("sale_price"));
        ap.setWarranty_period(rs.getInt("warranty_period"));

        String dateStr = rs.getString("purchase_date");
        if (dateStr != null && !dateStr.isEmpty()) {
            ap.setPurchase_date(LocalDate.parse(dateStr));
        }
        ap.setDescription(rs.getString("description"));

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null && !createdAtStr.isEmpty()) {
            ap.setCreated_at(LocalDateTime.parse(createdAtStr));
        }

        return ap;
    }

    @Override
    protected void setGeneratedId(AddedPart entity, int id) {
        entity.setId(id);
    }

    @Override
    protected void setKey(PreparedStatement stmt, int index, Integer key) throws SQLException {
        stmt.setInt(index, key);
    }

    public boolean create(List<AddedPart> entities) {
        if (entities == null || entities.isEmpty()) {
            return true;
        }

        String sql = getInsertSQL();
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

            for (AddedPart entity : entities) {
                fillInsertStatement(stmt, entity);

                stmt.addBatch();
            }

            int[] updateCounts = stmt.executeBatch();

            int totalInserted = 0;
            for (int count : updateCounts) {
                if (count == Statement.SUCCESS_NO_INFO || count >= 1) {
                    totalInserted++;
                }
            }

            conn.commit();

            return totalInserted == entities.size();

        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [CREATE ADDED PART BATCH]: {}", e.toString());
            // Hata durumunda rollback yapıyoruz.
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    Servicio.getLogger().error("DB ERROR [ROLLBACK FAILED]: {}", ex.toString());
                }
            }
            return false;
        } finally {
            // Kaynakları kapatıp otomatik commit'i tekrar açıyoruz.
            try {
                if (stmt != null) stmt.close();
                if (conn != null) conn.setAutoCommit(true);
                if (conn != null) conn.close();
            } catch (SQLException e) {
                Servicio.getLogger().error("DB ERROR [RESOURCE CLOSE FAILED]: {}", e.toString());
            }
        }
    }

    public List<AddedPart> getByServiceId(int serviceId) {
        List<AddedPart> parts = new ArrayList<>();
        String sql = "SELECT * FROM added_part WHERE service_id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, serviceId);

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                parts.add(mapRow(rs));
            }
            rs.close();
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [GET PARTS BY SERVICE ID] {}", e.toString());
        }
        return parts;
    }

    public boolean deleteByServiceId(int serviceId) {
        String sql = "DELETE FROM " + getTableName() + " WHERE service_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, serviceId);

            int affectedRows = stmt.executeUpdate();
            return affectedRows > 0;
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [DELETE BY SERVICE ID] {}", e.toString());
            return false;
        }
    }

    private String dateToStr(LocalDateTime date) {
        return date != null ? date.toString() : null;
    }

    private LocalDateTime strToDate(String dateStr) {
        return (dateStr != null) ? LocalDateTime.parse(dateStr) : null;
    }
}
