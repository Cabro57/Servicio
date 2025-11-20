package tr.cabro.servicio.service;

import tr.cabro.servicio.database.dao.PartDao;
import tr.cabro.servicio.model.Part;

import java.util.Collections;
import java.util.List;

public class PartService {

    private final PartDao partDao;

    public PartService(PartDao partDao) {
        this.partDao = partDao;
    }

    public Part save(Part part, boolean update) {
        // Insert yaparken Barkod kontrolü
        if (!update && !isBarcodeAvailable(part.getBarcode())) {
            throw new IllegalArgumentException("Bu barkod zaten kullanımda: " + part.getBarcode());
        }

        if (!update) {
            return partDao.create(part);
        } else {
            return partDao.update(part);
        }
    }

    public void delete(String barcode) {
        partDao.delete(barcode);
    }

    public List<Part> getAll() {
        return partDao.getAll();
    }

    public Part get(String barcode) {
        return partDao.getByKey(barcode).orElse(null);
    }

    public List<Part> getPartsBelowMinStock() {
        return partDao.getProductsBelowMinStock();
    }

    public boolean isBarcodeAvailable(String barcode) {
        return !partDao.isBarcodeExists(barcode);
    }

    public void increaseStock(String barcode, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Miktar 0'dan büyük olmalıdır.");
        partDao.adjustStock(barcode, amount);
    }

    public void decreaseStock(String barcode, int amount) {
        if (amount <= 0) throw new IllegalArgumentException("Miktar 0'dan büyük olmalıdır.");

        Part part = get(barcode);
        if (part == null) {
            throw new IllegalArgumentException("Ürün bulunamadı: " + barcode);
        }

        // Yetersiz stok kontrolü (DB işleminden önce)
        if (part.getStock() < amount) {
            throw new IllegalStateException(String.format(
                    "Yetersiz stok! Barkod: %s. Mevcut: %d, İstenen: %d",
                    barcode, part.getStock(), amount
            ));
        }

        // Stok düşme işlemi
        partDao.adjustStock(barcode, -amount);

        // Kritik seviye uyarısı (İşlem sonrası kontrol)
        int newStock = part.getStock() - amount;
        if (part.getMinStock() > 0 && newStock <= part.getMinStock()) {
            System.out.println("UYARI: Stok kritik seviyede! Ürün: " + part.getName());
        }
    }

    public List<Part> search(String searchTerm) {
        // TODO: PartDao içinde search metodu implemente edilirse burası açılabilir.
        return Collections.emptyList();
    }
}