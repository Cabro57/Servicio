package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.config.RegisterColumnMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
import org.jdbi.v3.sqlobject.customizer.BindList;
import org.jdbi.v3.sqlobject.statement.GetGeneratedKeys;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;
import tr.cabro.servicio.model.Customer;

import java.util.List;
import java.util.Optional;

@RegisterBeanMapper(Customer.class)
public interface CustomerRepository {

    @SqlUpdate("INSERT INTO customers (business_name, name, surname, phone_number_1, phone_number_2, id_no, address, email, status, note, created_at) " +
            "VALUES (:businessName, :name, :surname, :phoneNumber1, :phoneNumber2, :idNo, :address, :email, :type, :note, :createdAt)")
    @GetGeneratedKeys
    int insert(@BindBean Customer customer);

    @SqlUpdate("UPDATE customers SET business_name=:businessName, name=:name, surname=:surname, " +
            "phone_number_1=:phoneNumber1, phone_number_2=:phoneNumber2, id_no=:idNo, address=:address, " +
            "email=:email, status=:type, note=:note WHERE id=:id")
    void update(@BindBean Customer customer);

    @SqlUpdate("DELETE FROM customers WHERE id = :id")
    void delete(@Bind("id") int id);

    @SqlUpdate("DELETE FROM customers WHERE id IN (<ids>)")
    void deleteByIds(@BindList("ids") List<Integer> ids);

    @SqlQuery("SELECT id, business_name, name, surname, phone_number_1, phone_number_2, id_no, address, email, status, note, created_at FROM customers WHERE id = :id")
    Optional<Customer> findById(@Bind("id") int id);

    @SqlQuery("SELECT id, business_name, name, surname, phone_number_1, phone_number_2, id_no, address, email, status, note, created_at FROM customers WHERE id IN (<ids>)")
    List<Customer> findByIds(@BindList("ids") List<Integer> ids);

    @SqlQuery("SELECT id, business_name, name, surname, phone_number_1, phone_number_2, id_no, address, email, status, note, created_at FROM customers ORDER BY created_at DESC")
    List<Customer> findAll();

    @SqlQuery("SELECT id, business_name, name, surname, phone_number_1, phone_number_2, id_no, address, email, status, note, created_at FROM customers WHERE " +
            "name LIKE :search OR surname LIKE :search OR business_name LIKE :search OR " +
            "phone_number_1 LIKE :search OR id_no LIKE :search " +
            "ORDER BY created_at DESC")
    List<Customer> search(@Bind("search") String searchTerm);
}