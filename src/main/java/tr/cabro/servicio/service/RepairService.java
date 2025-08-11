package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.ServiceDao;
import tr.cabro.servicio.model.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class RepairService {

    private final ServiceDao serviceDao;

    public RepairService() {
        this.serviceDao = new ServiceDao();
    }

    public boolean saveService(Service service, boolean update) {
        try {
            if (!update) {
                serviceDao.create(service);
            } else {
                serviceDao.update(service);
            }
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [SAVE SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean deleteService(int id) {
        try {
            serviceDao.delete(id);
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public Optional<Service> getServiceById(int id) {
        return serviceDao.getByKey(id);
    }

    public List<Service> getServicesByCustomerId(int customerId) {
        try {
            return serviceDao.getByCustomerId(customerId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET BY CUSTOMER ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public List<Service> getAllServices() {
        try {
            return serviceDao.getAll();
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET ALL SERVICES] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public List<Service> getServicesByStatus(String status) {
        return serviceDao.getServicesByStatus(status);
    }

    public List<Service> getDescServices() {
        return serviceDao.getAllDesc();
    }
}
