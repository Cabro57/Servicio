package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.PartRepository;
import tr.cabro.servicio.database.repository.ServiceItemRepository;
import tr.cabro.servicio.model.ServiceItem;
import tr.cabro.servicio.model.enums.ItemType;
import tr.cabro.servicio.model.enums.SourceType;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ServiceItemManager {

    private final ServiceItemRepository itemRepository;
    private final PartRepository partRepository; // Stok işlemleri için

    public ServiceItemManager(ServiceItemRepository itemRepository, PartRepository partRepository) {
        this.itemRepository = itemRepository;
        this.partRepository = partRepository;
    }

    public CompletableFuture<Void> updateItem(ServiceItem item) {
        return CompletableFuture.runAsync(() -> {
            itemRepository.update(item);
        });
    }

    // Bir servise yeni bir kalem eklendiğinde çağrılır (Asenkron)
    public CompletableFuture<ServiceItem> addItemToService(ServiceItem item) {
        // Geriye bir değer döndürdüğü için supplyAsync kullanıyoruz
        return CompletableFuture.supplyAsync(() -> {
            int itemId = itemRepository.insert(item);
            item.setId(itemId);

            // Eğer eklenen şey Stoklu bir parçaysa (PRESET PART), stoğu DÜŞÜR
            if (item.getItemType() == ItemType.PART && item.getSourceType() == SourceType.PRESET && item.getPartId() != null) {
                // Miktarı negatif göndererek stoğu düşürüyoruz
                partRepository.adjustStock(item.getPartId(), -item.getQuantity());
            }
            return item;
        });
    }

    // Servisten kalem silindiğinde çağrılır (Asenkron)
    public CompletableFuture<Void> deleteItem(ServiceItem item) {
        // Geriye bir değer döndürmediği (void olduğu) için runAsync kullanıyoruz
        return CompletableFuture.runAsync(() -> {
            itemRepository.delete(item.getId());

            // Eğer silinen şey Stoklu bir parçaysa (PRESET PART), stoğu GERİ İADE ET
            if (item.getItemType() == ItemType.PART && item.getSourceType() == SourceType.PRESET && item.getPartId() != null) {
                // Miktarı pozitif göndererek stoğu artırıyoruz
                partRepository.adjustStock(item.getPartId(), item.getQuantity());
            }
        });
    }

    // Servise ait kalemleri getirir (Asenkron)
    public CompletableFuture<List<ServiceItem>> getItemsForService(int serviceId) {
        return CompletableFuture.supplyAsync(() -> itemRepository.findByServiceId(serviceId));
    }
}