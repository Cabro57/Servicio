package tr.cabro.servicio.database.repository;

import org.jdbi.v3.sqlobject.config.RegisterBeanMapper;
import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.customizer.BindBean;
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

    // DÜZELTME: 'status AS type' diyerek DB'deki 'status' kolonunu Java'daki 'type' alanına eşliyoruz.
    @SqlQuery("SELECT *, status AS type FROM customers WHERE id = :id")
    Optional<Customer> findById(@Bind("id") int id);

    // DÜZELTME: 'status AS type' eklendi
    @SqlQuery("SELECT *, status AS type FROM customers ORDER BY created_at DESC")
    List<Customer> findAll();

    // DÜZELTME: 'status AS type' eklendi
    @SqlQuery("SELECT *, status AS type FROM customers WHERE " +
            "name LIKE :search OR surname LIKE :search OR business_name LIKE :search OR " +
            "phone_number_1 LIKE :search OR id_no LIKE :search " +
            "ORDER BY created_at DESC")
    List<Customer> search(@Bind("search") String searchTerm);
}