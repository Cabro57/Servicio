package tr.cabro.servicio.documents;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Servis belgeleri (Cihaz Kabul Formu, Servis Teslim Formu vb.) için ortak PDF üretim
 * yardımcıları — antet/logo, başlık, bilgi tablosu, kalem tablosu, imza satırları,
 * sayfa altbilgisi. OpenPDF'in varsayılan Type1 fontları (Helvetica) Türkçe çğıöşü
 * karakterlerini basamadığı için DejaVu Sans gömülü Unicode font olarak kullanılır.
 */
public class PdfDocumentBuilder {

    private static final String FONT_REGULAR = "/fonts/DejaVuSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/DejaVuSans-Bold.ttf";
    private static final Color ACCENT = new Color(41, 98, 255);
    private static final Color MUTED = new Color(120, 120, 120);

    public final Font titleFont;
    public final Font headerFont;
    public final Font sectionFont;
    public final Font normalFont;
    public final Font mutedFont;

    public final Document document;

    public PdfDocumentBuilder(File outFile, String footerLabel) throws IOException, DocumentException {
        BaseFont regular = loadEmbeddedFont(FONT_REGULAR);
        BaseFont bold = loadEmbeddedFont(FONT_BOLD);

        titleFont = new Font(bold, 18, Font.BOLD);
        headerFont = new Font(bold, 12, Font.BOLD);
        sectionFont = new Font(bold, 10, Font.BOLD, ACCENT);
        normalFont = new Font(regular, 10, Font.NORMAL);
        mutedFont = new Font(regular, 9, Font.NORMAL, MUTED);

        document = new Document(PageSize.A4, 40, 40, 40, 50);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outFile));
        writer.setPageEvent(new FooterPageEvent(footerLabel, mutedFont));
        document.open();
    }

    private BaseFont loadEmbeddedFont(String classpathPath) throws IOException, DocumentException {
        try (InputStream is = PdfDocumentBuilder.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Font kaynağı bulunamadı: " + classpathPath);
            }
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont(classpathPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
        }
    }

    /** Üst bilgi: logo varsa görsel + işletme adı/telefon/adres; logo yoksa sadece metin. */
    public void addLetterhead(User shop) throws DocumentException, IOException {
        boolean hasLogo = shop != null && shop.getLogoPath() != null && !shop.getLogoPath().isBlank()
                && resolveLogoFile(shop.getLogoPath()).exists();

        PdfPTable head = new PdfPTable(hasLogo ? new float[]{1f, 3f} : new float[]{1f});
        head.setWidthPercentage(100);

        if (hasLogo) {
            Image logo = Image.getInstance(resolveLogoFile(shop.getLogoPath()).getAbsolutePath());
            logo.scaleToFit(70, 70);
            PdfPCell logoCell = new PdfPCell(logo, false);
            logoCell.setBorder(0);
            head.addCell(logoCell);
        }

        PdfPCell textCell = new PdfPCell();
        textCell.setBorder(0);
        textCell.addElement(new Paragraph(shop != null && shop.getBusinessName() != null ? shop.getBusinessName() : "-", headerFont));
        if (shop != null && shop.getPhoneNumber() != null && !shop.getPhoneNumber().isBlank()) {
            textCell.addElement(new Paragraph(PhoneHelper.formatForDisplay(shop.getPhoneNumber()), mutedFont));
        }
        if (shop != null && shop.getAddress() != null && !shop.getAddress().isBlank()) {
            textCell.addElement(new Paragraph(shop.getAddress(), mutedFont));
        }
        head.addCell(textCell);

        document.add(head);
        document.add(new Paragraph(" "));
    }

    private File resolveLogoFile(String logoPath) {
        return new File(new File(Servicio.getInstance().getDataFolder(), "logos"), logoPath);
    }

    public void addTitle(String text) throws DocumentException {
        document.add(new Paragraph(text, titleFont));
        document.add(new Paragraph(" "));
    }

    public void addParagraph(String text) throws DocumentException {
        document.add(new Paragraph(text, normalFont));
    }

    public void addSpacer() throws DocumentException {
        document.add(new Paragraph(" "));
    }

    /** Etiket/değer çiftlerinden bordürsüz bir bilgi tablosu oluşturur (4 sütun, 2'şerli çift). */
    public PdfPTable buildInfoTable(String[][] labelValuePairs) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.2f, 2f, 1.2f, 2f});

        for (String[] pair : labelValuePairs) {
            addInfoCell(table, pair[0], pair[1]);
        }
        // Tek sayıda çift varsa son satırı tamamlamak için boş hücreler ekle
        if (labelValuePairs.length % 2 != 0) {
            PdfPCell empty1 = new PdfPCell(new Paragraph(""));
            empty1.setBorder(0);
            table.addCell(empty1);
            PdfPCell empty2 = new PdfPCell(new Paragraph(""));
            empty2.setBorder(0);
            table.addCell(empty2);
        }

        return table;
    }

    private void addInfoCell(PdfPTable table, String label, String value) {
        PdfPCell labelCell = new PdfPCell(new Paragraph(label, headerFont));
        labelCell.setBorder(0);
        labelCell.setPadding(4);
        table.addCell(labelCell);

        PdfPCell valueCell = new PdfPCell(new Paragraph(value != null ? value : "-", normalFont));
        valueCell.setBorder(0);
        valueCell.setPadding(4);
        table.addCell(valueCell);
    }

    /** Kalem/adet/birim fiyat/toplam tablosu + genel toplam satırı. */
    public PdfPTable buildItemsTable(List<WorkOrderItem> items, BigDecimal total) {
        PdfPTable table = new PdfPTable(4);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3f, 1f, 1.3f, 1.3f});

        for (String header : new String[]{"Kalem", "Adet", "Birim Fiyat", "Toplam"}) {
            PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
            cell.setPadding(5);
            cell.setBackgroundColor(new Color(240, 240, 240));
            table.addCell(cell);
        }

        for (WorkOrderItem item : items) {
            table.addCell(cell(item.getItemName(), normalFont));
            PdfPCell qty = cell(String.valueOf(item.getQuantity()), normalFont);
            qty.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(qty);
            PdfPCell unit = cell(Format.formatPrice(item.getUnitPrice()), normalFont);
            unit.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(unit);
            PdfPCell itemTotal = cell(Format.formatPrice(item.getTotalPrice()), normalFont);
            itemTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
            table.addCell(itemTotal);
        }

        PdfPCell totalLabel = cell("Genel Toplam", headerFont);
        totalLabel.setColspan(3);
        totalLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalLabel);
        PdfPCell totalValue = cell(Format.formatPrice(total), headerFont);
        totalValue.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(totalValue);

        return table;
    }

    private PdfPCell cell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "-", font));
        cell.setPadding(5);
        return cell;
    }

    /** İki taraflı imza satırı — etiketler parametrik (form türüne göre taraflar değişebilir). */
    public PdfPTable buildSignatureLines(String leftLabel, String rightLabel) {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(40);

        PdfPCell left = new PdfPCell(new Paragraph(leftLabel + "\n\n\n_________________________", normalFont));
        left.setBorder(0);
        table.addCell(left);

        PdfPCell right = new PdfPCell(new Paragraph(rightLabel + "\n\n\n_________________________", normalFont));
        right.setBorder(0);
        table.addCell(right);

        return table;
    }

    public void close() {
        document.close();
    }

    private static class FooterPageEvent extends PdfPageEventHelper {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("tr", "TR"));

        private final String label;
        private final Font font;

        FooterPageEvent(String label, Font font) {
            this.label = label;
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            String text = label + "  —  Oluşturulma: " + LocalDateTime.now().format(FORMATTER) + "  —  Sayfa " + writer.getPageNumber();
            com.lowagie.text.pdf.ColumnText.showTextAligned(
                    writer.getDirectContent(), Element.ALIGN_CENTER, new Paragraph(text, font),
                    (document.right() + document.left()) / 2, document.bottom() - 20, 0);
        }
    }
}
