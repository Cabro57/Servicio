package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.CustomerDao;
import tr.cabro.servicio.model.Customer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class CustomerService {

    private final CustomerDao customerDao;

    public CustomerService() {
        this.customerDao = new CustomerDao();
    }

    public boolean save(Customer customer, boolean update) {
        try {
            if (!update) {
                return customerDao.create(customer);
            } else {
                return customerDao.update(customer);
            }
        } catch (Exception ex) {
            Servicio.getLogger().error("CUSTOMER ERROR [SAVE]: {}", ex.getMessage());
            return false;
        }
    }

    public boolean delete(int id) {
        try {
            return customerDao.delete(id);
        } catch (Exception ex) {
            Servicio.getLogger().error("CUSTOMER ERROR [DELETE]: {}", ex.getMessage());
            return false;
        }
    }

    public Optional<Customer> get(int id) {
        try {
            return customerDao.getByKey(id);
        } catch (Exception ex) {
            Servicio.getLogger().error("CUSTOMER ERROR [GET]: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public List<Customer> getAll() {
        try {
            return customerDao.getAll();
        } catch (Exception ex) {
            Servicio.getLogger().error("CUSTOMER ERROR [GET ALL]: {}", ex.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Customer> search(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return getAll();
        }

        // Aranacak alanlar (Service tablosundaki sütun isimleri varsayılmıştır)
        // Bu alanlar 'Service' tablosunda aranacaktır.
        String[] searchableColumns = {"id_no", "name", "surname", "phone_number_1", "phone_number_2", "email", "business_name"};

        try {
            // NOT: ServiceDao sınıfında bu metotun (search) implementasyonu gereklidir.
            return customerDao.search(searchTerm.trim(), searchableColumns);
        } catch (Exception ex) {
            Servicio.getLogger().error("SERVICE ERROR [SEARCH]: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }
}
