package tr.cabro.servicio.documents;

/**
 * Belgelerde basılan, işletmenin değiştirebildiği metinler. Düzen (yerleşim) kodda sabittir,
 * yalnızca bu metinler Ayarlar &gt; Belge Metinleri'nden ve belge oluşturma penceresinden
 * düzenlenir. Kayıtlı değer yoksa buradaki varsayılan kullanılır (bkz. {@link DocumentTexts}).
 * <p>
 * {@code {gün}} yer tutucusu garanti gün sayısıyla değiştirilir.
 */
public enum DocumentText {

    INTAKE_TERMS("Cihaz kabul — koşullar",
            "Cihaz, belirtilen arıza şikayeti ile teslim alınmıştır. Onarım öncesi cihaz üzerinde " +
                    "yedeklenmemiş verilerin kaybolabileceğini, tahmini onarım süresinin arızanın niteliğine göre " +
                    "değişebileceğini müşteri kabul eder."),
    INTAKE_SLIP_NOTE("Cihaz kabul fişi — alt not",
            "Cihazınızı teslim alırken takip numarasını belirtiniz."),
    DELIVERY_WARRANTY_NOTE("Servis teslim — garanti notu",
            "Değiştirilen parçalar ve yapılan işçilik, üretici/tedarikçi garanti koşulları saklı kalmak kaydıyla " +
                    "{gün} gün garantilidir. Fiziksel darbe, sıvı teması veya yetkisiz müdahale garanti dışıdır."),
    DELIVERY_NO_WARRANTY_NOTE("Servis teslim — garanti verilmediğinde",
            "Bu teslimatta yapılan işlemler için garanti verilmemiştir."),
    QUOTE_APPROVAL_NOTE("Teklif onayı — müşteri beyanı",
            "Yukarıda belirtilen tespit ve önerilen işlemler/tutar tarafıma bildirilmiştir. " +
                    "Onarımın bu şartlarla yapılmasını onaylıyorum."),
    PURCHASE_CONTRACT_NOTE("Satın alma sözleşmesi — satıcı beyanı",
            "Yukarıda bilgileri belirtilen cihaz, tarafımdan yukarıdaki bedel karşılığında işletmeye satılmıştır. " +
                    "Cihazın yasal olarak tarafıma ait olduğunu ve üzerinde üçüncü şahıslara ait herhangi bir " +
                    "hak/talep bulunmadığını beyan ederim."),
    SALE_CONTRACT_NOTE("Satış sözleşmesi — alıcı beyanı",
            "Yukarıda bilgileri belirtilen ikinci el cihaz, tarafıma yukarıdaki bedel karşılığında, " +
                    "mevcut/görülen haliyle satılmıştır. Cihazı bu şartlarla teslim aldığımı beyan ederim."),
    WARRANTY_CERTIFICATE_NOTE("Garanti belgesi — kapsam",
            "Bu garanti, cihazın normal kullanım koşullarında ortaya çıkan donanımsal arızalarını kapsar; " +
                    "düşürme, sıvı teması, yetkisiz müdahale ve kullanıcı hatasından kaynaklanan hasarları kapsamaz."),
    SLIP_FOOTER("Fişlerin alt notu",
            "Teşekkür ederiz");

    /** Servis teslim belgelerinde varsayılan garanti süresi (gün). */
    public static final int DEFAULT_WARRANTY_DAYS = 90;

    private final String label;
    private final String defaultText;

    DocumentText(String label, String defaultText) {
        this.label = label;
        this.defaultText = defaultText;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultText() {
        return defaultText;
    }

    /** {@code app_settings} anahtarı. */
    public String settingKey() {
        return "document.text." + name().toLowerCase();
    }

    /** Uzun metin mi (çok satırlı alan)? Fiş alt notları tek satırdır. */
    public boolean isMultiline() {
        return this != SLIP_FOOTER && this != INTAKE_SLIP_NOTE;
    }
}
