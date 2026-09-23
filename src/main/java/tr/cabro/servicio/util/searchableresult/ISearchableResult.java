package tr.cabro.servicio.util.searchableresult;

// Bu, hem statik formları hem de DB'den gelen müşterileri/ürünleri temsil edecek
public interface ISearchableResult {

    String getDisplayName();  // Ana başlık (Örn: "Müşteri Ayarları" veya "Ahmet Yılmaz")
    String getDescription();  // Alt başlık (Örn: "Yetkileri değiştir" veya "Müşteri: 123")

    String getUniqueId();

    // Tıklandığında veya Enter'a basıldığında çalışacak eylem
    void executeAction();

    /** Satırın solunda gösterilecek ikon (icons/...svg); null ise ikon gösterilmez. */
    default String getIconPath() { return null; }

    /** Satırın sağında gösterilecek klavye kısayolu (ör. "Alt+N"); null ise ok işareti gösterilir. */
    default String getShortcutText() { return null; }
}
