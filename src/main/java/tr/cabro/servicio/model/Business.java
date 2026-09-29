package tr.cabro.servicio.model;

import lombok.Getter;
import lombok.Setter;

/**
 * İşletmenin kendisi: belgelerin antedinde, termal fişte ve WhatsApp şablonlarında basılan
 * bilgiler. Tek satırlık {@code business_profile} tablosunda durur (id her zaman 1); uygulamayı
 * kullanan kişiden ({@link User}) ayrıdır.
 * <p>
 * Alan adları eski {@link User} alanlarıyla aynı tutuldu (businessName, phoneNumber, address,
 * logoPath, email), belge kodu yalnızca tür değiştirerek taşındı.
 */
@Getter @Setter
public class Business {

    private Long id = 1L;

    private String businessName;
    private String phoneNumber;
    private String phoneNumber2;
    private String email;
    private String website;
    private String address;

    private String taxOffice;
    private String taxNumber;

    private String iban;
    private String bankName;
    private String accountHolder;

    private String workingHours;

    /** "logos/" alt klasöründeki dosya adı (isteğe bağlı). */
    private String logoPath;

    /** "TR330006100519786457841326" → "TR33 0006 1005 1978 6457 8413 26". */
    public static String formatIban(String iban) {
        if (iban == null) return "";
        String n = iban.replaceAll("\\s+", "").toUpperCase(java.util.Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append(' ');
            sb.append(n.charAt(i));
        }
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * Vergi satırı: "Kadıköy V.D. · VKN 1234567890" (11 haneliyse TCKN); ikisi de boşsa null.
     * Numaranın türü yazılır ki okuyan neye baktığını bilsin.
     */
    public String getTaxLine() {
        boolean office = notBlank(taxOffice);
        boolean number = notBlank(taxNumber);
        if (!office && !number) return null;
        String no = number ? (taxNumber.trim().length() == 11 ? "TCKN " : "VKN ") + taxNumber.trim() : null;
        if (office && number) return taxOffice.trim() + " V.D. · " + no;
        return office ? taxOffice.trim() + " V.D." : no;
    }

    /** Havale kutusu basılabilir mi: IBAN yazılmışsa. */
    public boolean hasBankAccount() {
        return notBlank(iban);
    }
}
