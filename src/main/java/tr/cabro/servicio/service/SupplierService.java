package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.SupplierDao;
import tr.cabro.servicio.model.Supplier;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class SupplierService {

    private final SupplierDao supplierDao;

    public SupplierService() {
        this.supplierDao = new SupplierDao();
    }

    public boolean save(Supplier supplier, boolean update) {
        try {
            if (update) {
                return supplierDao.update(supplier);
            } else {
                return supplierDao.create(supplier);
            }
        } catch (Exception e) {
            Servicio.getLogger().error("SUPPLIER ERROR [SAVE]: {}", e.getMessage());
            return false;
        }
    }

    public boolean delete(int id) {
        try {
            return supplierDao.delete(id);
        } catch (Exception e) {
            Servicio.getLogger().error("SUPPLIER ERROR [DELETE]: {}", e.getMessage());
            return false;
        }
    }

    public Optional<Supplier> get(int id) {
        try {
            return supplierDao.getByKey(id);
        } catch (Exception e) {
            Servicio.getLogger().error("SUPPLIER ERROR [GET]: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public List<Supplier> getAll() {
        try {
            return supplierDao.getAll();
        } catch (Exception e) {
            Servicio.getLogger().error("SUPPLIER ERROR [GET ALL]: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
