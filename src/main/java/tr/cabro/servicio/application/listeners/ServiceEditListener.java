package tr.cabro.servicio.application.listeners;

import tr.cabro.servicio.model.AddedPart;
import tr.cabro.servicio.model.Process;

import java.math.BigDecimal;

public interface ServiceEditListener {
    // Mevcut metotlar
    void onPartChange(BigDecimal totalPartPrice);
    void onProcessAdded(String name, BigDecimal price);
    void onProcessAdded(Process process);
    void onStatusChanged(String status);

    // YENİ: Mimari iletişim metotları
    void onDataChanged(); // Herhangi bir veri değiştiğinde (Güncelle butonunu aktif etmek için)
    void requestRefresh(); // Alt panel ana formun verilerini yeniden çekmesini istediğinde
    void onPartAdded(AddedPart part); // Parça eklendiğinde fiyat ve tablo senkronizasyonu için
}