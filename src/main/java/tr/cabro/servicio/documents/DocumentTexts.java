package tr.cabro.servicio.documents;

import tr.cabro.servicio.service.AppSettingService;
import tr.cabro.servicio.service.ServiceManager;

/**
 * {@link DocumentText} metinlerinin kayıtlı değerlerine erişim. Metinler işletmeye ait olduğu için
 * {@code app_settings} tablosunda tutulur (yedeğe girer). Servis henüz yoksa (ör. test) varsayılan döner.
 */
public final class DocumentTexts {

    private static final String WARRANTY_DAYS_KEY = "document.delivery_warranty_days";

    private DocumentTexts() {
    }

    public static String get(DocumentText text) {
        AppSettingService service = service();
        if (service == null) return text.getDefaultText();
        String value = service.getString(text.settingKey(), text.getDefaultText());
        return value == null || value.isBlank() ? text.getDefaultText() : value;
    }

    /** Varsayılanla aynı ya da boş metin kaydedilmez; böylece varsayılan ileride güncellenirse ona uyar. */
    public static void set(DocumentText text, String value) {
        AppSettingService service = service();
        if (service == null) return;
        boolean isDefault = value == null || value.isBlank() || value.strip().equals(text.getDefaultText());
        service.setString(text.settingKey(), isDefault ? "" : value.strip());
    }

    public static int getWarrantyDays() {
        AppSettingService service = service();
        return service == null ? DocumentText.DEFAULT_WARRANTY_DAYS
                : Math.max(0, service.getInt(WARRANTY_DAYS_KEY, DocumentText.DEFAULT_WARRANTY_DAYS));
    }

    public static void setWarrantyDays(int days) {
        AppSettingService service = service();
        if (service != null) service.setInt(WARRANTY_DAYS_KEY, Math.max(0, days));
    }

    /**
     * Teslim belgesinin garanti notu: gün sayısı sıfırsa "garanti verilmedi" metni,
     * değilse {@code {gün}} yer tutucusu doldurulmuş garanti notu.
     */
    public static String warrantyNote(int days, String warrantyTemplate, String noWarrantyText) {
        return days > 0 ? warrantyTemplate.replace("{gün}", String.valueOf(days)) : noWarrantyText;
    }

    private static AppSettingService service() {
        try {
            return ServiceManager.getAppSettingService();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
