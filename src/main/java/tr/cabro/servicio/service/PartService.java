package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.PartRepository;
import tr.cabro.servicio.model.Part;
import tr.cabro.servicio.service.exception.ValidationException;
import tr.cabro.servicio.util.Validator;

import java.util.Collections;
import java.util.List;

public class PartService {

    private final PartRepository repository;

    public PartService(PartRepository repository) {
        this.repository = repository;
    }

    public Part save(Part part, boolean update) {
        // --- Validasyon ---
        if (Validator.isEmpty(part.getBarcode())) throw new ValidationException("Barkod boş olamaz.");
        if (Validator.isEmpty(part.getName())) throw new ValidationException("Ürün adı boş olamaz.");
        if (Validator.isEmpty(part.getBrand())) throw new ValidationException("Marka boş olamaz.");

        if (part.getPurchasePrice() < 0) throw new ValidationException("Alış fiyatı negatif olamaz.");
        if (part.getSalePrice() < 0) throw new ValidationException("Satış fiyatı negatif olamaz.");
        if (part.getStock() < 0) throw new ValidationException("Stok miktarı negatif olamaz.");

        // Barkod Çakışma Kontrolü (Sadece yeni kayıtta)
        if (!update && !isBarcodeAvailable(part.getBarcode())) {
            throw new ValidationException("Bu barkod (" + part.getBarcode() + ") zaten sistemde kayıtlı!");
        }

        // --- DB İşlemi ---
        if (!update) {
            repository.insert(part);
        } else {
            repository.update(part);
        }
        return part;
    }

    public void delete(String barcode) {
        repository.delete(barcode);
    }

    public List<Part> getAll() {
        return repository.findAll();
    }

    public Part get(String barcode) {
        return repository.findByBarcode(barcode).orElse(null);
    }

    public List<Part> getPartsBelowMinStock() {
        return repository.findBelowMinStock();
    }

    public boolean isBarcodeAvailable(String barcode) {
        return !repository.existsByBarcode(barcode);
    }

    public void increaseStock(String barcode, int amount) {
        if (amount <= 0) throw new ValidationException("Artırılacak miktar 0'dan büyük olmalıdır.");
        repository.adjustStock(barcode, amount);
    }

    public void decreaseStock(String barcode, int amount) {
        if (amount <= 0) throw new ValidationException("Azaltılacak miktar 0'dan büyük olmalıdır.");

        Part part = get(barcode);
        if (part == null) {
            throw new ValidationException("Ürün bulunamadı: " + barcode);
        }

        if (part.getStock() < amount) {
            throw new ValidationException(String.format(
                    "Yetersiz stok! Barkod: %s. Mevcut: %d, İstenen: %d",
                    barcode, part.getStock(), amount
            ));
        }

        repository.adjustStock(barcode, -amount);
    }

    public List<Part> search(String searchTerm) {
        // İleride arama özelliği eklenirse repository üzerinden çağrılabilir
        return Collections.emptyList();
    }
}