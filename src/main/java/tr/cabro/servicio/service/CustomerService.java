package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.CustomerDao;
import tr.cabro.servicio.model.Customer;

import java.util.ArrayList;
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
}
