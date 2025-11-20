package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.model.Supplier;
import java.sql.*;
import java.time.LocalDateTime;

public class SupplierDao extends BaseDao<Supplier, Integer> {

    @Override
    protected String getTableName() { return "suppliers"; }
    @Override
    protected String getPrimaryKeyColumn() { return "id"; }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO suppliers (name, business_name, id_no, tax_no, tax_office, email, phone, address, note, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE suppliers SET name=?, business_name=?, id_no=?, tax_no=?, tax_office=?, email=?, phone=?, address=?, note=?, created_at=? WHERE id=?";
    }

    @Override
    protected void fillStatement(PreparedStatement stmt, Supplier entity, boolean isUpdate) throws SQLException {
        int i = 1;
        stmt.setString(i++, entity.getName());
        stmt.setString(i++, entity.getBusiness_name());
        stmt.setString(i++, entity.getId_no());
        stmt.setString(i++, entity.getTax_no());
        stmt.setString(i++, entity.getTax_office());
        stmt.setString(i++, entity.getEmail());
        stmt.setString(i++, entity.getPhone());
        stmt.setString(i++, entity.getAddress());
        stmt.setString(i++, entity.getNotes());
        stmt.setString(i++, entity.getCreated_at() != null ? entity.getCreated_at().toString() : null);

        if (isUpdate) {
            stmt.setInt(i++, entity.getId());
        }
    }

    @Override
    protected Supplier mapRow(ResultSet rs) throws SQLException {
        Supplier s = new Supplier();
        s.setId(rs.getInt("id"));
        s.setName(rs.getString("name"));
        s.setBusiness_name(rs.getString("business_name"));
        s.setId_no(rs.getString("id_no"));
        s.setTax_no(rs.getString("tax_no"));
        s.setTax_office(rs.getString("tax_office"));
        s.setEmail(rs.getString("email"));
        s.setPhone(rs.getString("phone"));
        s.setAddress(rs.getString("address"));
        s.setNotes(rs.getString("note"));
        if (rs.getString("created_at") != null) {
            s.setCreated_at(LocalDateTime.parse(rs.getString("created_at")));
        }
        return s;
    }

    @Override
    protected void setGeneratedId(Supplier s, int id) {
        s.setId(id);
    }
}