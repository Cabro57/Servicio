package tr.cabro.servicio.util.searchableresult;

// Bu, hem statik formları hem de DB'den gelen müşterileri/ürünleri temsil edecek
public interface ISearchableResult {

    String getDisplayName();  // Ana başlık (Örn: "Müşteri Ayarları" veya "Ahmet Yılmaz")
    String getDescription();  // Alt başlık (Örn: "Yetkileri değiştir" veya "Müşteri: 123")

    String getUniqueId();

    // Tıklandığında veya Enter'a basıldığında çalışacak eylem
    void executeAction();
}
