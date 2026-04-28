package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.DeviceDictionaryRepository;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.exception.AlreadyExistsException;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DeviceDictionaryManager {

    private final DeviceDictionaryRepository repository;

    public DeviceDictionaryManager(DeviceDictionaryRepository repository) {
        this.repository = repository;
    }

    // --- TÜRLER (Types) ---
    public CompletableFuture<List<DeviceType>> getAllTypes() {
        return CompletableFuture.supplyAsync(repository::findAllTypes);
    }

    public CompletableFuture<Integer> addType(String name) {
        return CompletableFuture.supplyAsync(() -> {
            repository.findTypeByName(name.trim()).ifPresent(type -> {
                throw new AlreadyExistsException("Bu cihaz türü zaten kayıtlı: " + name);
            });

            return repository.insertType(name.trim());
        });
    }

    public CompletableFuture<Void> deleteType(Long id) {
        return CompletableFuture.runAsync(() -> repository.deleteType(id));
    }

    // --- MARKALAR (Brands) ---
    public CompletableFuture<List<DeviceBrand>> getBrandsByTypeId(Long typeId) {
        return CompletableFuture.supplyAsync(() -> repository.findBrandsByTypeId(typeId));
    }

    public CompletableFuture<Integer> addBrand(String name) {
        return CompletableFuture.supplyAsync(() -> {
            // 1. Validasyon: Mevcut mu kontrol et
            repository.findBrandByName(name.trim()).ifPresent(brand -> {
                throw new AlreadyExistsException("Bu marka zaten kayıtlı: " + name);
            });

            // 2. Kaydet
            return repository.insertBrand(name.trim());
        });
    }

    public CompletableFuture<Void> deleteBrand(Long id) {
        return CompletableFuture.runAsync(() -> repository.deleteBrand(id));
    }

    // --- İLİŞKİLENDİRME (Bağlantı Tablosu) ---
    public CompletableFuture<Void> linkBrandToType(Long typeId, Long brandId) {
        return CompletableFuture.runAsync(() -> repository.linkTypeAndBrand(typeId, brandId));
    }

    public CompletableFuture<Void> unlinkBrandFromType(Long typeId, Long brandId) {
        return CompletableFuture.runAsync(() -> repository.unlinkTypeAndBrand(typeId, brandId));
    }
}