package tr.cabro.servicio.service;

import tr.cabro.servicio.database.dao.CustomerDao;
import tr.cabro.servicio.model.Customer;

import java.util.List;
import java.util.Optional;

public class CustomerService {

    private final CustomerDao customerDao;

    public CustomerService(CustomerDao customerDao) {
        this.customerDao = customerDao;
    }

    public Customer save(Customer customer, boolean update) {
        // Validasyon örneği (İsteğe bağlı aktif edilebilir)
        if (customer.getName() == null || customer.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Müşteri adı boş olamaz.");
        }

        if (!update) {
            return customerDao.create(customer);
        } else {
            return customerDao.update(customer);
        }
    }

    public void delete(int id) {
        customerDao.delete(id);
    }

    public Optional<Customer> get(int id) {
        return customerDao.getByKey(id);
    }

    public List<Customer> getAll() {
        return customerDao.getAll();
    }

    public List<Customer> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return customerDao.getAll();
        }
        String[] searchableColumns = {"id_no", "name", "surname", "phone_number_1", "phone_number_2", "email", "business_name"};
        return customerDao.search(searchTerm.trim(), searchableColumns);
    }
}