package tr.cabro.servicio.documents;

import java.util.EnumMap;
import java.util.Map;

/**
 * Bir belgenin tek seferlik girdileri: imza isimleri, garanti günü ve bu belgeye özel düzenlenmiş
 * metinler. Düzenlenmemiş metin için kayıtlı varsayılan ({@link DocumentTexts}) kullanılır.
 */
public class DocumentRequest {

    private String leftSignerName;
    private String rightSignerName;
    private Integer warrantyDays;
    private final Map<DocumentText, String> texts = new EnumMap<>(DocumentText.class);

    /** Kayıtlı varsayılanlarla, imza isimsiz bir istek. */
    public static DocumentRequest defaults() {
        return new DocumentRequest();
    }

    public String getLeftSignerName() {
        return leftSignerName;
    }

    public DocumentRequest setLeftSignerName(String leftSignerName) {
        this.leftSignerName = leftSignerName;
        return this;
    }

    public String getRightSignerName() {
        return rightSignerName;
    }

    public DocumentRequest setRightSignerName(String rightSignerName) {
        this.rightSignerName = rightSignerName;
        return this;
    }

    public int getWarrantyDays() {
        return warrantyDays != null ? warrantyDays : DocumentTexts.getWarrantyDays();
    }

    public DocumentRequest setWarrantyDays(int warrantyDays) {
        this.warrantyDays = Math.max(0, warrantyDays);
        return this;
    }

    public DocumentRequest setText(DocumentText text, String value) {
        texts.put(text, value);
        return this;
    }

    /** Bu belgeye özel metin, yoksa kayıtlı varsayılan. */
    public String text(DocumentText text) {
        String value = texts.get(text);
        return value != null && !value.isBlank() ? value : DocumentTexts.get(text);
    }

    /**
     * Teslim belgelerinin garanti notu. Pencerede elle düzenlendiyse ({@link DocumentText#DELIVERY_WARRANTY_NOTE}
     * bu isteğe yazıldıysa) o metin aynen, değilse gün sayısına göre üretilen metin basılır.
     */
    public String deliveryWarrantyNote() {
        String edited = texts.get(DocumentText.DELIVERY_WARRANTY_NOTE);
        if (edited != null && !edited.isBlank()) return edited;
        return DocumentTexts.warrantyNote(getWarrantyDays(),
                text(DocumentText.DELIVERY_WARRANTY_NOTE), text(DocumentText.DELIVERY_NO_WARRANTY_NOTE));
    }
}
