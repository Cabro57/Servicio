package tr.cabro.servicio.database.dao;

import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.CustomerType;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CustomerDao extends BaseDao<Customer, Integer> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    @Override
    protected String getTableName() {
        return "customers";
    }

    @Override
    protected String getPrimaryKeyColumn() {
        return "id";
    }

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
    protected void fillInsertStatement(PreparedStatement stmt, Customer c) throws SQLException {
        stmt.setString(1, c.getBusiness_name());
        stmt.setString(2, c.getName());
        stmt.setString(3, c.getSurname());
        stmt.setString(4, c.getPhone_number_1());
        stmt.setString(5, c.getPhone_number_2());
        stmt.setString(6, c.getId_no());
        stmt.setString(7, c.getAddress());
        stmt.setString(8, c.getEmail());
        stmt.setString(9, c.getType().getDisplayName());
        stmt.setString(10, c.getNote());
        stmt.setString(11, c.getCreated_at() != null ? c.getCreated_at().format(formatter) : null);
    }

    @Override
    protected void fillUpdateStatement(PreparedStatement stmt, Customer c) throws SQLException {
        stmt.setString(1, c.getBusiness_name());
        stmt.setString(2, c.getName());
        stmt.setString(3, c.getSurname());
        stmt.setString(4, c.getPhone_number_1());
        stmt.setString(5, c.getPhone_number_2());
        stmt.setString(6, c.getId_no());
        stmt.setString(7, c.getAddress());
        stmt.setString(8, c.getEmail());
        stmt.setString(9, c.getType().getDisplayName());
        stmt.setString(10, c.getNote());
        stmt.setString(11, c.getCreated_at() != null ? c.getCreated_at().format(formatter) : null);
        stmt.setInt(12, c.getID());
    }

    @Override
    protected Customer mapRow(ResultSet rs) throws SQLException {
        Customer c = new Customer(rs.getInt("id"), rs.getString("name"), rs.getString("surname"));
        c.setBusiness_name(rs.getString("business_name"));
        c.setPhone_number_1(rs.getString("phone_number_1"));
        c.setPhone_number_2(rs.getString("phone_number_2"));
        c.setId_no(rs.getString("id_no"));
        c.setAddress(rs.getString("address"));
        c.setEmail(rs.getString("email"));
        c.setType(CustomerType.of(rs.getString("status")));
        c.setNote(rs.getString("note"));
        if (rs.getString("created_at") != null) {
            // DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            LocalDateTime date = LocalDateTime.parse(rs.getString("created_at"), formatter);
            c.setCreated_at(date);
        }
        return c;
    }

    @Override
    protected void setGeneratedId(Customer entity, int id) {
        entity.setID(id);
    }

    @Override
    protected void setKey(PreparedStatement stmt, int index, Integer key) throws SQLException {
        stmt.setInt(index, key);
    }
}
