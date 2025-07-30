package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.model.Supplier;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class SupplierDao extends BaseDao<Supplier, Integer> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    protected String getTableName() {
        return "suppliers";
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO suppliers (name, business_name, id_no, tax_no, tax_office, email, phone, address, note, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE suppliers SET name=?, business_name=?, id_no=?, tax_no=?, tax_office=?, email=?, phone=?, address=?, note=?, created_at=? " +
                "WHERE id=?";
    }

    @Override
    protected void fillInsertStatement(PreparedStatement stmt, Supplier s) throws SQLException {
        stmt.setString(1, s.getName());
        stmt.setString(2, s.getBusiness_name());
        stmt.setString(3, s.getId_no());
        stmt.setString(4, s.getTax_no());
        stmt.setString(5, s.getTax_office());
        stmt.setString(6, s.getEmail());
        stmt.setString(7, s.getPhone());
        stmt.setString(8, s.getAddress());
        stmt.setString(9, s.getNotes());
        stmt.setString(10, s.getCreated_at() != null ? s.getCreated_at().format(formatter) : null);
    }

    @Override
    protected void fillUpdateStatement(PreparedStatement stmt, Supplier s) throws SQLException {
        stmt.setString(1, s.getName());
        stmt.setString(2, s.getBusiness_name());
        stmt.setString(3, s.getId_no());
        stmt.setString(4, s.getTax_no());
        stmt.setString(5, s.getTax_office());
        stmt.setString(6, s.getEmail());
        stmt.setString(7, s.getPhone());
        stmt.setString(8, s.getAddress());
        stmt.setString(9, s.getNotes());
        stmt.setString(10, s.getCreated_at() != null ? s.getCreated_at().format(formatter) : null);
        stmt.setInt(11, s.getId());
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

        String createdAtStr = rs.getString("created_at");
        if (createdAtStr != null) {
            s.setCreated_at(LocalDateTime.parse(createdAtStr, formatter));
        }
        return s;
    }

    @Override
    protected void setGeneratedId(Supplier s, int id) {
        s.setId(id);
    }

    @Override
    protected void setKey(PreparedStatement stmt, int index, Integer key) throws SQLException {
        stmt.setInt(index, key);
    }
}