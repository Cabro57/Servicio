package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.database.DatabaseManager;
import tr.cabro.servicio.database.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseDao<T, K> {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    private static final DateTimeFormatter DB_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    protected abstract String getTableName();
    protected abstract String getPrimaryKeyColumn();
    protected abstract String getInsertSQL();
    protected abstract String getUpdateSQL();

    // Magic Numbers sorununu çözen ve kod tekrarını önleyen soyut metod
    protected abstract void fillStatement(PreparedStatement stmt, T entity, boolean isUpdate) throws SQLException;

    protected abstract T mapRow(ResultSet rs) throws SQLException;
    protected abstract void setGeneratedId(T entity, int id);

    // --- CREATE (Return T) ---
    public T create(T entity) {
        try (Connection conn = DatabaseManager.getConnection()) {
            return create(entity, conn); // Transaction desteği
        } catch (SQLException e) {
            throw new DataAccessException("Kayıt oluşturulamadı: " + getTableName(), e);
        }
    }

    // Transaction içinde kullanım için overload
    public T create(T entity, Connection conn) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement(getInsertSQL(), Statement.RETURN_GENERATED_KEYS)) {

            fillStatement(stmt, entity, false); // Insert modu

            if (stmt.executeUpdate() == 0) {
                throw new SQLException("Kayıt oluşturulamadı, etkilenen satır yok.");
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    setGeneratedId(entity, keys.getInt(1));
                }
            }
            return entity;
        }
    }

    // --- UPDATE (Return T) ---
    public T update(T entity) {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(getUpdateSQL())) {

            fillStatement(stmt, entity, true); // Update modu

            if (stmt.executeUpdate() == 0) {
                logger.warn("DB WARNING [UPDATE]: Güncellenecek kayıt bulunamadı ({}).", entity);
            }
            return entity;
        } catch (SQLException e) {
            throw new DataAccessException("Güncelleme hatası: " + getTableName(), e);
        }
    }

    // --- DELETE (Return void) ---
    public void delete(K key) {
        String sql = "DELETE FROM " + getTableName() + " WHERE " + getPrimaryKeyColumn() + " = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, key);
            stmt.executeUpdate();

        } catch (SQLException e) {
            throw new DataAccessException("Silme hatası ID: " + key, e);
        }
    }

    // --- READ (Return Optional) ---
    public Optional<T> getByKey(K key) {
        String sql = "SELECT * FROM " + getTableName() + " WHERE " + getPrimaryKeyColumn() + " = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, key);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return Optional.of(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Veri getirme hatası ID: " + key, e);
        }
        return Optional.empty();
    }

    // --- READ ALL ---
    public List<T> getAll() {
        List<T> list = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName();

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) list.add(mapRow(rs));

        } catch (SQLException e) {
            throw new DataAccessException("Liste getirme hatası", e);
        }
        return list;
    }

    public List<T> search(String searchTerm, String[] columns) {
        if (columns == null || columns.length == 0 || searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }

        List<T> list = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ")
                .append(getTableName())
                .append(" WHERE ");

        for (int i = 0; i < columns.length; i++) {
            sqlBuilder.append(columns[i]).append(" LIKE ?");
            if (i < columns.length - 1) {
                sqlBuilder.append(" OR ");
            }
        }

        String likePattern = "%" + searchTerm.trim() + "%";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlBuilder.toString())) {

            for (int i = 0; i < columns.length; i++) {
                stmt.setString(i + 1, likePattern);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) list.add(mapRow(rs));
            }

        } catch (SQLException e) {
            throw new DataAccessException("Arama hatası: " + searchTerm, e);
        }
        return list;
    }

    // Helper Methods
    protected String dateToStr(LocalDateTime date) {
        return date != null ? date.format(DB_DATE_FMT) : null;
    }

    protected LocalDateTime strToDate(String dateStr) {
        return (dateStr != null && !dateStr.isEmpty()) ? LocalDateTime.parse(dateStr, DB_DATE_FMT) : null;
    }
}