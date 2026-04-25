package tr.cabro.servicio.service;

import tr.cabro.servicio.database.repository.DeviceDictionaryRepository;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;

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
        return CompletableFuture.supplyAsync(() -> repository.insertType(name));
    }

    public CompletableFuture<Void> deleteType(Long id) {
        return CompletableFuture.runAsync(() -> repository.deleteType(id));
    }

    // --- MARKALAR (Brands) ---
    public CompletableFuture<List<DeviceBrand>> getBrandsByTypeId(Long typeId) {
        return CompletableFuture.supplyAsync(() -> repository.findBrandsByTypeId(typeId));
    }

    public CompletableFuture<Integer> addBrand(String name) {
        return CompletableFuture.supplyAsync(() -> repository.insertBrand(name));
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