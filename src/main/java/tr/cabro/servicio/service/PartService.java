package tr.cabro.servicio.service;

import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.database.dao.PartDao;
import tr.cabro.servicio.model.Part;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class PartService {

    private final PartDao partDao;

    public PartService(PartDao partDao) {
        this.partDao = partDao;
    }

    // --- PART işlemleri ---

    public boolean save(Part part, boolean update) {
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

    public boolean delete(Part part) {
        return partDao.delete(part.getBarcode());
    }

    public boolean delete(String barcode) {
        try {
            partDao.delete(barcode);
            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DELETE PART] {}", String.valueOf(e));
            return false;
        }
    }

    public List<Part> getAll() {
        try {
            return partDao.getAll();
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [GET ALL PARTS] {}", String.valueOf(e));
            return Collections.emptyList();
        }
    }

    public Part get(String barcode) {
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

    public boolean increaseStock(String barcode, int amount) {
        if (amount <= 0) return false;
        return partDao.adjustStock(barcode, amount);
    }

    public boolean decreaseStock(String barcode, int amount) {
        if (amount <= 0) return false;

        try {
            Part part = get(barcode);
            if (part == null) {
                Servicio.getLogger().warn("PART NOT FOUND [DECREASE STOCK] Barcode: {}", barcode);
                return false;
            }

            if (part.getStock() < amount) {
                Servicio.getLogger().warn("INSUFFICIENT STOCK [Barcode: {}, Current: {}, Requested: {}]",
                        barcode, part.getStock(), amount);
                // Buradan false dönebilirsiniz isterseniz işlemi iptal etmek için
            }

            boolean success = partDao.adjustStock(barcode, -amount);

            if (!success) return false;

            int newStock = part.getStock() - amount;

            if (newStock < 0) {
                Servicio.getLogger().error("STOCK NEGATIVE! [Barcode: {}, NewStock: {}]", barcode, newStock);
            }

            if (part.getMinStock() > 0 && newStock <= part.getMinStock()) {
                Servicio.getLogger().warn("STOCK CRITICAL! [Barcode: {}, Current: {}, Min: {}]",
                        barcode, newStock, part.getMinStock());
            }

            return true;
        } catch (Exception e) {
            Servicio.getLogger().error("SERVICE ERROR [DECREASE STOCK] {}", e.getMessage());
            return false;
        }
    }

    public List<Part> search(String searchTerm) {

        return Collections.emptyList();
    }

}
