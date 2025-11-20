package tr.cabro.servicio.service;

import tr.cabro.servicio.database.dao.SupplierDao;
import tr.cabro.servicio.model.Supplier;

import java.util.List;
import java.util.Optional;

public class SupplierService {

    private final SupplierDao supplierDao;

    public SupplierService(SupplierDao supplierDao) {
        this.supplierDao = supplierDao;
    }

    public Supplier save(Supplier supplier, boolean update) {
        if (!update) {
            return supplierDao.create(supplier);
        } else {
            return supplierDao.update(supplier);
        }
    }

    public void delete(int id) {
        supplierDao.delete(id);
    }

    public Optional<Supplier> get(int id) {
        return supplierDao.getByKey(id);
    }

    public List<Supplier> getAll() {
        return supplierDao.getAll();
    }
}