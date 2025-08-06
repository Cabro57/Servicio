package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.PartDao;
import tr.cabro.servicio.database.dao.ServicePartDao;
import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Part;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PartService {

    private final PartDao partDao;
    private final ServicePartDao servicePartDao;

    public PartService() {
        this.partDao = new PartDao();
        this.servicePartDao = new ServicePartDao();
    }

    // --- PART işlemleri ---

    public boolean savePart(Part part, boolean update) {
        try {
            if (!update) {
                if (partDao.isBarcodeExists(part.getBarcode())) {
                    Servicio.getLogger().warn("Barcode already exists: {}", part.getBarcode());
                    return false;
                }
                partDao.create(part);
            } else {
                partDao.update(part);
            }
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [SAVE PART] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean deletePart(Part part) {
        return partDao.delete(part.getBarcode());
    }

    public boolean deletePartByBarcode(String barcode) {
        try {
            partDao.delete(barcode);
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE PART] {}", String.valueOf(e));
            return false;
        }
    }

    public List<Part> getAllParts() {
        try {
            return partDao.getAll();
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET ALL PARTS] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public Part getPartByBarcode(String barcode) {
        try {
            Optional<Part> part = partDao.getByKey(barcode);
            return part.orElse(null); // eğer varsa döner, yoksa null
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET PART BY BARCODE] {}", String.valueOf(e));
            return null;
        }
    }

    public List<Part> getPartsBelowMinStock() {
        try {
            return partDao.getProductsBelowMinStock();
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET BELOW MIN STOCK] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public boolean isBarcodeAvailable(String barcode) {
        return !partDao.isBarcodeExists(barcode);
    }

    // --- ADDED PART (service_parts) işlemleri ---

    public boolean addPartToService(AddedPart addedPart) {
        try {
            servicePartDao.create(addedPart);

            Optional<Part> optionalPart = partDao.getByKey(addedPart.getBarcode());
            optionalPart.ifPresent(part -> {
                int newStock = part.getStock() - addedPart.getAmount();
                part.setStock(Math.max(newStock, 0)); // Negatif stok önlenir
                partDao.updateStock(part.getBarcode(), part.getStock());
            });

            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [ADD PART TO SERVICE] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean updateAddedPart(AddedPart updated) {
        try {
            Optional<AddedPart> optionalOriginal = servicePartDao.getByKey(updated.getId());
            if (!optionalOriginal.isPresent()) return false;

            AddedPart original = optionalOriginal.get();
            int fark = updated.getAmount() - original.getAmount(); // pozitifse stok eksilir

            servicePartDao.update(updated);

            Optional<Part> optionalPart = partDao.getByKey(updated.getBarcode());
            optionalPart.ifPresent(part -> {
                int newStock = part.getStock() - fark;
                part.setStock(Math.max(newStock, 0));
                partDao.updateStock(part.getBarcode(), part.getStock());
            });

            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [UPDATE ADDED PART] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean deleteAddedPart(AddedPart addedPart) {
        try {
            servicePartDao.delete(addedPart.getId());

            Optional<Part> optionalPart = partDao.getByKey(addedPart.getBarcode());
            optionalPart.ifPresent(part -> {
                part.setStock(part.getStock() + addedPart.getAmount());
                partDao.updateStock(part.getBarcode(), part.getStock());
            });

            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE ADDED PART] {}", String.valueOf(e));
            return false;
        }
    }

    public boolean deleteAllPartsFromService(int serviceId) {
        try {
            List<AddedPart> addedParts = servicePartDao.getByServiceId(serviceId);

            for (AddedPart addedPart : addedParts) {
                Optional<Part> optionalPart = partDao.getByKey(addedPart.getBarcode());
                optionalPart.ifPresent(part -> {
                    part.setStock(part.getStock() + addedPart.getAmount());
                    partDao.updateStock(part.getBarcode(), part.getStock());
                });
            }

            return servicePartDao.deleteByServiceId(serviceId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE PARTS BY SERVICE ID] {}", String.valueOf(e));
            return false;
        }
    }

    public double getTotalPartsCostForService(int serviceId) {
        try {
            List<AddedPart> addedParts = servicePartDao.getByServiceId(serviceId);
            double total = 0;
            for (AddedPart ap : addedParts) {
                total += ap.getTotal(); // fiyat * adet
            }
            return total;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET TOTAL PARTS COST] {}", String.valueOf(e));
            return 0.0;
        }
    }

    public List<AddedPart> getPartsByServiceId(int serviceId) {
        try {
            return servicePartDao.getByServiceId(serviceId);
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET PARTS BY SERVICE ID] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }
}
