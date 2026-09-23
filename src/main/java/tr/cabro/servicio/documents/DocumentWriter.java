package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrderItem;

import java.io.File;
import java.math.BigDecimal;
import java.util.List;

/**
 * A4 belgelerin içerik dili. Generator'lar içeriği bir kez bu arayüze yazar; aynı içerik
 * PDF ({@link PdfDocumentBuilder}), Word ({@link DocxDocumentWriter}) ya da RTF
 * ({@link RtfDocumentWriter}) olarak çıkar. Word/RTF çıktısı müşteriye göndermeden önce
 * elle düzeltmek içindir; düzen PDF ile aynı sırada, benzer vurgularla kurulur.
 */
public interface DocumentWriter {

    /** Belgeyi açar ve künyeyi (işletme, belge adı, belge no, tarih) basar. */
    static DocumentWriter open(DocumentFormat format, File outFile, User shop, String title,
                               String documentNumber, String date) throws Exception {
        return switch (format) {
            case PDF -> PdfDocumentBuilder.create(outFile, shop, title, documentNumber, date);
            case DOCX -> new DocxDocumentWriter(outFile, shop, title, documentNumber, date);
            case RTF -> new RtfDocumentWriter(outFile, shop, title, documentNumber, date);
        };
    }

    void section(String title) throws Exception;

    /** Etiket/değer çiftleri, iki sütunlu form alanları olarak. */
    void fields(String[][] labelValuePairs) throws Exception;

    /** Tam genişlikte tek form alanı. */
    void wideField(String label, String value) throws Exception;

    /** Serbest metin; boşsa {@code emptyText} soluk yazılır. */
    void paragraph(String text, String emptyText) throws Exception;

    /** Koşul/beyan metni. */
    void terms(String text) throws Exception;

    /** Kalem tablosu ve genel toplam. */
    void items(List<WorkOrderItem> items, BigDecimal total) throws Exception;

    /** Elle işaretlenecek seçenekler. */
    void choices(String... options) throws Exception;

    /** İki taraflı imza alanı. */
    void signatures(String leftLabel, String leftName, String rightLabel, String rightName) throws Exception;

    /** Belgeyi bitirip dosyayı yazar. */
    void close() throws Exception;
}
