package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;

import java.sql.*;
import java.time.LocalDateTime;

public class CustomerDao extends BaseDao<Customer, Integer> {

    @Override
    protected String getTableName() { return "customers"; }
    @Override
    protected String getPrimaryKeyColumn() { return "id"; }

    @Override
    protected String getInsertSQL() {
        return "INSERT INTO customers (business_name, name, surname, phone_number_1, phone_number_2, id_no, address, email, status, note, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }

    @Override
    protected String getUpdateSQL() {
        return "UPDATE customers SET business_name=?, name=?, surname=?, phone_number_1=?, phone_number_2=?, id_no=?, address=?, email=?, status=?, note=?, created_at=? WHERE id=?";
    }

    @Override
    protected void fillStatement(PreparedStatement stmt, Customer c, boolean isUpdate) throws SQLException {
        int i = 1;
        stmt.setString(i++, c.getBusinessName());
        stmt.setString(i++, c.getName());
        stmt.setString(i++, c.getSurname());
        stmt.setString(i++, c.getPhoneNumber1());
        stmt.setString(i++, c.getPhoneNumber2());
        stmt.setString(i++, c.getIdNo());
        stmt.setString(i++, c.getAddress());
        stmt.setString(i++, c.getEmail());
        stmt.setString(i++, c.getType().getDisplayName());
        stmt.setString(i++, c.getNote());
        stmt.setString(i++, c.getCreatedAt() != null ? c.getCreatedAt().toString() : null);

        if (isUpdate) {
            stmt.setInt(i++, c.getId());
        }
    }

    @Override
    protected Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer(rs.getInt("id"), rs.getString("name"), rs.getString("surname"));
        c.setBusinessName(rs.getString("business_name"));
        c.setPhoneNumber1(rs.getString("phone_number_1"));
        c.setPhoneNumber2(rs.getString("phone_number_2"));
        c.setIdNo(rs.getString("id_no"));
        c.setAddress(rs.getString("address"));
        c.setEmail(rs.getString("email"));
        c.setType(CustomerType.of(rs.getString("status")));
        c.setNote(rs.getString("note"));
        if (rs.getString("created_at") != null) {
            c.setCreatedAt(LocalDateTime.parse(rs.getString("created_at")));
        }
        return c;
    }

    @Override
    protected void setGeneratedId(Customer entity, int id) {
        entity.setId(id);
    }
}