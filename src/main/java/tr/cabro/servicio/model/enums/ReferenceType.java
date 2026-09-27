package tr.cabro.servicio.model.enums;

/**
 * Stok hareketinin kaynağı — stoğun nereden geldiğini / nereye gittiğini anlatır.
 * Adlar veritabanında saklanır; yeniden adlandırılmaz (bkz. V25 migration).
 */
public enum ReferenceType {
    /** Servis kaleminde kullanılan parça (çıkış). reference_id = work_orders.id */
    WORK_ORDER("Servis"),
    /** Servisten stoğa dönen parça: kalem silindi/azaldı ya da servis "İade" oldu (giriş). */
    WORK_ORDER_CANCEL("Servisten Dönüş"),
    /** Tedarikçiden/dışarıdan alış (giriş). */
    PURCHASE("Alış"),
    /** POS satışı (çıkış). reference_id = sales.id */
    SALE("Satış"),
    /** Elle yapılan düzeltme. */
    ADJUSTMENT("Düzeltme"),
    /** POS satış iadesi (giriş). reference_id = iade sales.id */
    RETURN("Satış İadesi"),
    /** Kart ilk açılırken girilen stok. */
    OPENING("Açılış Stoğu"),
    /** Sayım farkı. */
    COUNT("Sayım"),
    /** Fire / kayıp / hasar (çıkış). */
    LOSS("Fire / Kayıp"),
    /** Depolar arası aktarım (bir çıkış + bir giriş). reference_id = karşı taraftaki depo. */
    TRANSFER("Depo Transferi");

    private final String displayName;

    ReferenceType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
