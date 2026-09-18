package tr.cabro.servicio.documents.receipt;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * {@link ReceiptContent}'i 80mm genişlikte, termal fiş görünümlü bir PDF'e basar — henüz
 * fiziksel yazıcı bağlanmadığı için önizleme/test amaçlı. Yazıcı bağlandığında aynı
 * {@link ReceiptContent} modeli ESC/POS'a çeviren ayrı bir renderer'a da verilecek;
 * ikisi arasında kod paylaşımı YOK (biri sayfa/font tabanlı PDF, diğeri ham byte akışı —
 * ortak soyutlamaya zorlamak yapay olurdu).
 * <p>
 * {@code PdfDocumentBuilder}'dan bilinçli olarak AYRI: o A4 + antet/logo düzeni için, bu
 * dar tek-sütun düzen için. Font gömme mantığı (DejaVu Sans — Türkçe karakterler için)
 * aynı kaynaklardan tekrar yükleniyor; iki builder'ı ortak bir taban sınıfa zorlamak
 * bu noktada yapay bir soyutlama olurdu.
 */
public class ReceiptPdfRenderer {

    private static final String FONT_REGULAR = "/fonts/DejaVuSans-Regular.ttf";
    private static final String FONT_BOLD = "/fonts/DejaVuSans-Bold.ttf";
    private static final float MM_TO_PT = 2.834646f;
    private static final float WIDTH_MM = 80f;
    private static final float HEIGHT_MM = 200f;   // İçerik kısa kalırsa altta boşluk kalır — sorun değil, önizleme amaçlı.
    private static final float MARGIN_MM = 4f;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", new Locale("tr", "TR"));

    public File render(ReceiptContent content, File outFile) throws IOException, DocumentException {
        BaseFont regular = loadFont(FONT_REGULAR);
        BaseFont bold = loadFont(FONT_BOLD);

        Font titleFont = new Font(bold, 11, Font.BOLD);
        Font headerFont = new Font(bold, 8, Font.BOLD);
        Font normalFont = new Font(regular, 8, Font.NORMAL);
        Font mutedFont = new Font(regular, 7, Font.NORMAL, new Color(110, 110, 110));

        float widthPt = WIDTH_MM * MM_TO_PT;
        float heightPt = HEIGHT_MM * MM_TO_PT;
        float marginPt = MARGIN_MM * MM_TO_PT;
        float contentWidthPt = widthPt - 2 * marginPt;

        Document document = new Document(new Rectangle(widthPt, heightPt), marginPt, marginPt, marginPt, marginPt);
        PdfWriter.getInstance(document, new FileOutputStream(outFile));
        document.open();

        if (notBlank(content.getShopName())) addCentered(document, content.getShopName(), headerFont);
        if (notBlank(content.getShopPhone())) addCentered(document, PhoneHelper.formatForDisplay(content.getShopPhone()), mutedFont);
        if (notBlank(content.getShopAddress())) addCentered(document, content.getShopAddress(), mutedFont);
        addSeparator(document, normalFont);

        addCentered(document, content.getDocumentTitle(), titleFont);
        addCentered(document, content.getDocumentNumber(), normalFont);
        if (content.getDate() != null) addCentered(document, content.getDate().format(DATE_FORMATTER), mutedFont);
        addCentered(document, notBlank(content.getCustomerName()) ? content.getCustomerName() : "Perakende", mutedFont);
        addSeparator(document, normalFont);

        for (ReceiptContent.Line line : content.getLines()) {
            document.add(new Paragraph(line.name(), normalFont));
            String qtyPrice = line.quantity() + " x " + Format.formatPrice(line.unitPrice());
            document.add(twoColumnLine(qtyPrice, Format.formatPrice(line.lineTotal()), mutedFont, normalFont, contentWidthPt));
        }
        addSeparator(document, normalFont);

        document.add(twoColumnLine("Ara Toplam", Format.formatPrice(content.getSubtotal()), normalFont, normalFont, contentWidthPt));
        if (content.getDiscount() != null && content.getDiscount().signum() > 0) {
            document.add(twoColumnLine("İndirim", "-" + Format.formatPrice(content.getDiscount()), normalFont, normalFont, contentWidthPt));
        }
        document.add(twoColumnLine("TOPLAM", Format.formatPrice(content.getTotal()), headerFont, headerFont, contentWidthPt));
        addSeparator(document, normalFont);

        for (ReceiptContent.PaymentLine payment : content.getPayments()) {
            document.add(twoColumnLine(payment.methodLabel(), Format.formatPrice(payment.amount()), normalFont, normalFont, contentWidthPt));
        }
        if (content.getChangeGiven() != null && content.getChangeGiven().signum() > 0) {
            document.add(twoColumnLine("Para Üstü", Format.formatPrice(content.getChangeGiven()), normalFont, normalFont, contentWidthPt));
        }

        addSeparator(document, normalFont);
        addCentered(document, "Teşekkür ederiz", mutedFont);

        document.close();
        return outFile;
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private void addCentered(Document document, String text, Font font) throws DocumentException {
        Paragraph p = new Paragraph(text, font);
        p.setAlignment(Element.ALIGN_CENTER);
        document.add(p);
    }

    private void addSeparator(Document document, Font font) throws DocumentException {
        document.add(new Paragraph("--------------------------------", font));
    }

    private PdfPTable twoColumnLine(String left, String right, Font leftFont, Font rightFont, float widthPt) {
        PdfPTable table = new PdfPTable(2);
        table.setWidths(new float[]{2f, 1f});
        try {
            table.setTotalWidth(widthPt);
            table.setLockedWidth(true);
        } catch (DocumentException ignored) {
            // setTotalWidth'in genişlik <= 0 durumunda attığı istisna burada göz ardı edilebilir;
            // pratikte contentWidthPt her zaman pozitif (80mm sayfa - kenar boşlukları).
        }

        PdfPCell leftCell = new PdfPCell(new Paragraph(left, leftFont));
        leftCell.setBorder(0);
        leftCell.setPadding(1);
        table.addCell(leftCell);

        PdfPCell rightCell = new PdfPCell(new Paragraph(right, rightFont));
        rightCell.setBorder(0);
        rightCell.setPadding(1);
        rightCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(rightCell);

        return table;
    }

    private BaseFont loadFont(String classpathPath) throws IOException, DocumentException {
        try (InputStream is = ReceiptPdfRenderer.class.getResourceAsStream(classpathPath)) {
            if (is == null) {
                throw new IOException("Font kaynağı bulunamadı: " + classpathPath);
            }
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont(classpathPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, fontBytes, null);
        }
    }
}
