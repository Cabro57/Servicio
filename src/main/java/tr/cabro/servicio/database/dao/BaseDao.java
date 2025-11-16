package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public abstract class BaseDao<T, K> {

    protected abstract String getTableName();
    protected abstract String getPrimaryKeyColumn();  // Ör: "id" veya "barcode"

    protected abstract String getInsertSQL();
    protected abstract String getUpdateSQL();
    protected abstract void fillInsertStatement(PreparedStatement stmt, T entity) throws SQLException;
    protected abstract void fillUpdateStatement(PreparedStatement stmt, T entity) throws SQLException;
    protected abstract T mapRow(ResultSet rs) throws SQLException;
    protected abstract void setGeneratedId(T entity, int id);
    protected abstract void setKey(PreparedStatement stmt, int index, K key) throws SQLException;

    public boolean create(T entity) {
        try (PreparedStatement stmt = DatabaseManager.getConnection()
                .prepareStatement(getInsertSQL(), Statement.RETURN_GENERATED_KEYS)) {

            fillInsertStatement(stmt, entity);

            int affected = stmt.executeUpdate();
            if (affected == 0) {
                Servicio.getLogger().error("Kayıt eklenemedi: " + getTableName());
                return false;
            }

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    setGeneratedId(entity, keys.getInt(1));
                }
            }

            return true;

        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [CREATE] {}", e.toString());
            return false;
        }
    }

    public boolean update(T entity) {
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(getUpdateSQL())) {
            fillUpdateStatement(stmt, entity);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [UPDATE] {}", e.toString());
            return false;
        }
    }

    public boolean delete(K key) {
        String sql = "DELETE FROM " + getTableName() + " WHERE " + getPrimaryKeyColumn() + " = ?";
        try (PreparedStatement stmt = DatabaseManager.getConnection().prepareStatement(sql)) {
            setKey(stmt, 1, key);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [DELETE] {}", e.toString());
            return false;
        }
    }

    public Optional<T> getByKey(K key) {
        String sql = "SELECT * FROM " + getTableName() + " WHERE " + getPrimaryKeyColumn() + " = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            setKey(stmt, 1, key);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [GET BY KEY] {}", e.toString());
        }
        return Optional.empty();
    }

    public List<T> getAll() {
        List<T> list = new ArrayList<>();
        String sql = "SELECT * FROM " + getTableName();
        try (Statement stmt = DatabaseManager.getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                list.add(mapRow(rs));
            }
        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [GET ALL] {}", e.toString());
        }
        return list;
    }

    /**
     * Belirtilen sütunlarda verilen arama terimini (LIKE operatoru ile) arar.
     *
     * @param searchTerm Arama yapılacak terim (örn: "ahmet")
     * @param columns Aranacak veritabanı sütun adları (örn: {"name", "surname"})
     * @return Bulunan varlıkların listesi
     */
    public List<T> search(String searchTerm, String[] columns) {
        if (columns == null || columns.length == 0 || searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }

        List<T> list = new ArrayList<>();
        StringBuilder sqlBuilder = new StringBuilder("SELECT * FROM ")
                .append(getTableName())
                .append(" WHERE ");

        // WHERE şartlarını oluştur (Örn: name LIKE ? OR surname LIKE ?)
        for (int i = 0; i < columns.length; i++) {
            sqlBuilder.append(columns[i]).append(" LIKE ?");
            if (i < columns.length - 1) {
                sqlBuilder.append(" OR ");
            }
        }

        String sql = sqlBuilder.toString();
        String likePattern = "%" + searchTerm.trim() + "%";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            // PreparedStatement'ı doldur
            for (int i = 0; i < columns.length; i++) {
                // SQL indexleri 1'den başladığı için i + 1 kullanılır.
                stmt.setString(i + 1, likePattern);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            Servicio.getLogger().error("DB ERROR [SEARCH] in {} table: {}", getTableName(), e.toString());
        }
        return list;
    }
}
