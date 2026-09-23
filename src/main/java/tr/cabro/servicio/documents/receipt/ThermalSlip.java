package tr.cabro.servicio.documents.receipt;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import tr.cabro.servicio.documents.PdfFonts;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.settings.ReceiptPaperWidth;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import java.awt.Color;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Termal rulo yazıcıya göre ölçülmüş tek sütunlu fiş PDF'i.
 * <p>
 * Termal yazıcı kuralları:
 * <ul>
 *   <li>Yalnızca saf siyah. Termal kafa griyi noktalayarak basar, ince gri yazı okunmaz hale gelir.
 *       İkincil bilgi renkle değil punto ve kalınlıkla ayrılır.</li>
 *   <li>Sayfa genişliği kağıt, içerik genişliği basılabilir alan kadardır (bkz.
 *       {@link ReceiptPaperWidth}). Yazıcı sürücüsünde "gerçek boyut" ile basılmalıdır.</li>
 *   <li>Sayfa boyu içeriğe göre hesaplanır: önce tüm fiş tek bir tabloda kurulur, yüksekliği
 *       ölçülür, sayfa o boyda açılır. Sabit boy, kısa fişte metrelerce boş kağıt demekti.</li>
 *   <li>Sonda kesme payı bırakılır; yazıcı kesicisi son satırı kesmez.</li>
 * </ul>
 * Kullanım: {@link #create()} ile başla, bölümleri sırayla ekle, {@link #write(File)} ile yaz.
 */
public class ThermalSlip {

    private static final float MM = 72f / 25.4f;
    private static final float TOP_MM = 3f;
    private static final float TEAR_OFF_MM = 14f;
    private static final Locale TR = new Locale("tr", "TR");

    private final ReceiptPaperWidth paper;
    private final float contentWidth;
    private final PdfPTable body;

    private final Font shopFont;
    private final Font titleFont;
    private final Font bodyFont;
    private final Font boldFont;
    private final Font smallFont;
    private final Font headingFont;
    private final Font totalLabelFont;
    private final Font totalFont;
    private final Font codeFont;

    private ThermalSlip(ReceiptPaperWidth paper) throws IOException, DocumentException {
        this.paper = paper;
        this.contentWidth = paper.getPrintableMm() * MM;

        BaseFont regular = PdfFonts.regular();
        BaseFont bold = PdfFonts.bold();
        float base = paper.getBaseFontSize();
        shopFont = new Font(bold, base + 3.5f, Font.NORMAL, Color.BLACK);
        titleFont = new Font(bold, base + 1.5f, Font.NORMAL, Color.WHITE);
        bodyFont = new Font(regular, base, Font.NORMAL, Color.BLACK);
        boldFont = new Font(bold, base, Font.NORMAL, Color.BLACK);
        smallFont = new Font(regular, base - 1f, Font.NORMAL, Color.BLACK);
        headingFont = new Font(bold, base - 0.5f, Font.NORMAL, Color.BLACK);
        totalLabelFont = new Font(bold, base + 1.5f, Font.NORMAL, Color.BLACK);
        totalFont = new Font(bold, base + 4.5f, Font.NORMAL, Color.BLACK);
        codeFont = new Font(bold, base + 12f, Font.NORMAL, Color.BLACK);

        body = new PdfPTable(1);
        body.setTotalWidth(contentWidth);
        body.setLockedWidth(true);
    }

    /** Ayarlardaki kağıt genişliğiyle başlar. */
    public static ThermalSlip create() throws IOException, DocumentException {
        return create(AppSettings.get().getPrinting().getReceiptPaperWidth());
    }

    public static ThermalSlip create(ReceiptPaperWidth paper) throws IOException, DocumentException {
        return new ThermalSlip(paper != null ? paper : ReceiptPaperWidth.MM_80);
    }

    // -------------------------------------------------------------------------
    // Bölümler
    // -------------------------------------------------------------------------

    /** İşletme adı büyük ve ortalı, altında adres ve telefon. */
    public ThermalSlip shopHeader(String name, String phone, String address) {
        if (notBlank(name)) add(centered(name, shopFont, 0f, 2f));
        if (notBlank(address)) add(centered(address, smallFont, 0f, 0.5f));
        if (notBlank(phone)) add(centered(PhoneHelper.formatForDisplay(phone), boldFont, 0.5f, 0f));
        return space(4f);
    }

    public ThermalSlip shopHeader(User shop) {
        if (shop == null) return this;
        return shopHeader(shop.getBusinessName(), shop.getPhoneNumber(), shop.getAddress());
    }

    /** Siyah zemin üstüne beyaz belge adı — fişin en belirgin satırı. */
    public ThermalSlip titleBand(String title) {
        // Harf aralığı başlığı band içinde okunur kılar; dar 58mm kağıtta satıra sığsın diye azalır.
        Chunk chunk = new Chunk(title.toUpperCase(TR), titleFont);
        chunk.setCharacterSpacing(paper == ReceiptPaperWidth.MM_58 ? 0.4f : 1.2f);
        PdfPCell cell = new PdfPCell(new Phrase(chunk));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setBackgroundColor(Color.BLACK);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPaddingTop(2.5f);
        cell.setPaddingBottom(4.5f);
        add(cell);
        return space(3f);
    }

    /** Belge no solda kalın, tarih sağda. */
    public ThermalSlip documentLine(String number, LocalDateTime date) {
        PdfPTable t = row(new float[]{1f, 1.7f});
        t.addCell(plain(number, boldFont, Element.ALIGN_LEFT));
        t.addCell(plain(date != null ? date.format(DateFormats.dateTime()) : "", bodyFont, Element.ALIGN_RIGHT));
        add(nested(t, 0.6f));
        return space(2f);
    }

    /** Küçük büyük harfli ara başlık. */
    public ThermalSlip heading(String text) {
        space(3f);
        add(left(text.toUpperCase(TR), headingFont, 0f, 1.5f));
        return this;
    }

    /**
     * Etiket/değer satırı: etiket solda, değer sağında sola hizalı ve gerekirse alt satıra kayar
     * (uzun cihaz adı, müşteri adı). Tutar için {@link #amount} kullanılır.
     */
    public ThermalSlip field(String label, String value) {
        if (!notBlank(value)) return this;
        if (paper == ReceiptPaperWidth.MM_58) {
            // 48mm'de yan yana dizilince ad ve cihaz adı kelime kelime kırılıyordu: etiket üstte.
            add(left(label, smallFont, 1f, 0f));
            add(left(value, boldFont, 0f, 0.5f));
            return this;
        }
        PdfPTable t = row(new float[]{0.32f, 0.68f});
        t.addCell(plain(label, bodyFont, Element.ALIGN_LEFT));
        t.addCell(plain(value, boldFont, Element.ALIGN_LEFT));
        add(nested(t, 0.6f));
        return this;
    }

    /** Tutar satırı: etiket solda, tutar sağa hizalı. */
    public ThermalSlip amount(String label, BigDecimal value) {
        add(pair(label, bodyFont, money(value), bodyFont));
        return this;
    }

    /** Ödenen/kalan gibi vurgulu tutar satırı. */
    public ThermalSlip strongAmount(String label, BigDecimal value) {
        add(pair(label, boldFont, money(value), boldFont));
        return this;
    }

    /** Genel toplam: üstünde kalın çizgi, büyük punto. */
    public ThermalSlip grandTotal(String label, BigDecimal value) {
        rule(1.4f);
        PdfPTable t = row(new float[]{1f, 1.6f});
        PdfPCell l = plain(label.toUpperCase(TR), totalLabelFont, Element.ALIGN_LEFT);
        l.setVerticalAlignment(Element.ALIGN_BOTTOM);
        l.setPaddingBottom(2.5f);
        t.addCell(l);
        t.addCell(plain(money(value), totalFont, Element.ALIGN_RIGHT));
        add(nested(t, 1f));
        return space(1f);
    }

    /** Satış kalemi: ad tam genişlikte, altında "adet × birim" solda, tutar sağda. */
    public ThermalSlip item(String name, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {
        add(left(notBlank(name) ? name : "-", boldFont, 2f, 0.5f));
        add(pair("  " + quantity + " × " + money(unitPrice), bodyFont, money(lineTotal), bodyFont));
        return this;
    }

    /** Serbest metin (şikayet, koşullar). */
    public ThermalSlip text(String text) {
        add(left(text, smallFont, 0f, 1f));
        return this;
    }

    /** Ortalı küçük metin (teşekkür, not). */
    public ThermalSlip note(String text) {
        add(centered(text, smallFont, 1f, 1f));
        return this;
    }

    /**
     * Müşterinin cihazını alırken göstereceği takip numarası: çok büyük, ortalı, çerçeve içinde.
     * Tezgâhta fişe bakıp kaydı bulmak için okunur.
     */
    public ThermalSlip bigCode(String caption, String code) {
        space(3f);
        PdfPTable t = row(new float[]{1f});
        PdfPCell captionCell = plain(caption.toUpperCase(TR), headingFont, Element.ALIGN_CENTER);
        captionCell.setPaddingTop(3.5f);
        t.addCell(captionCell);
        Paragraph p = new Paragraph();
        Chunk chunk = new Chunk(code, codeFont);
        chunk.setCharacterSpacing(1f);
        p.add(chunk);
        PdfPCell codeCell = new PdfPCell(p);
        codeCell.setBorder(Rectangle.NO_BORDER);
        codeCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        codeCell.setPaddingTop(-1f);
        codeCell.setPaddingBottom(7f);
        t.addCell(codeCell);
        PdfPCell box = new PdfPCell(t);
        box.setBorder(Rectangle.BOX);
        box.setBorderWidth(1.4f);
        box.setBorderColor(Color.BLACK);
        box.setPadding(0f);
        add(box);
        return space(3f);
    }

    /** İmza alanı: boşluk, düz çizgi, altında etiket ve (varsa) ad. */
    public ThermalSlip signature(String label, String name) {
        space(22f);
        PdfPCell line = plain(notBlank(name) ? name : " ", boldFont, Element.ALIGN_LEFT);
        line.setBorder(Rectangle.TOP);
        line.setBorderWidthTop(0.9f);
        line.setBorderColorTop(Color.BLACK);
        line.setPaddingTop(3f);
        add(line);
        add(left(label, smallFont, 0.5f, 2f));
        return this;
    }

    /** Kesikli ayırıcı çizgi — bölümler arası. */
    public ThermalSlip dashed() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(9f);
        cell.setCellEvent(new DashEvent());
        add(cell);
        return this;
    }

    /** Düz ayırıcı çizgi. */
    public ThermalSlip rule(float width) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidthBottom(width);
        cell.setBorderColorBottom(Color.BLACK);
        cell.setFixedHeight(4f);
        add(cell);
        return space(2.5f);
    }

    public ThermalSlip space(float points) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setFixedHeight(points);
        add(cell);
        return this;
    }

    // -------------------------------------------------------------------------
    // Yazma
    // -------------------------------------------------------------------------

    /** Fişi ölçer, içerik boyunda bir sayfaya yazar. */
    public File write(File outFile) throws IOException, DocumentException {
        float width = paper.getPaperMm() * MM;
        float marginX = (width - contentWidth) / 2f;
        float bodyHeight = body.calculateHeights(true);
        float height = bodyHeight + (TOP_MM + TEAR_OFF_MM) * MM;

        Document document = new Document(new Rectangle(width, height), 0, 0, 0, 0);
        try (FileOutputStream out = new FileOutputStream(outFile)) {
            PdfWriter writer = PdfWriter.getInstance(document, out);
            document.open();
            body.writeSelectedRows(0, -1, marginX, height - TOP_MM * MM, writer.getDirectContent());
            document.close();
        }
        return outFile;
    }

    // -------------------------------------------------------------------------
    // Yardımcılar
    // -------------------------------------------------------------------------

    private void add(PdfPCell cell) {
        body.addCell(cell);
    }

    private PdfPCell centered(String text, Font font, float padTop, float padBottom) {
        PdfPCell cell = plain(text, font, Element.ALIGN_CENTER);
        cell.setPaddingTop(padTop);
        cell.setPaddingBottom(padBottom);
        return cell;
    }

    private PdfPCell left(String text, Font font, float padTop, float padBottom) {
        PdfPCell cell = plain(text, font, Element.ALIGN_LEFT);
        cell.setPaddingTop(padTop);
        cell.setPaddingBottom(padBottom);
        return cell;
    }

    private PdfPCell pair(String left, Font leftFont, String right, Font rightFont) {
        PdfPTable t = row(new float[]{1.35f, 1f});
        t.addCell(plain(left, leftFont, Element.ALIGN_LEFT));
        t.addCell(plain(right, rightFont, Element.ALIGN_RIGHT));
        return nested(t, 0.6f);
    }

    private static PdfPTable row(float[] widths) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        return t;
    }

    private static PdfPCell nested(PdfPTable table, float padding) {
        PdfPCell cell = new PdfPCell(table);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.setPaddingTop(padding);
        cell.setPaddingBottom(padding);
        return cell;
    }

    private static PdfPCell plain(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        cell.setPaddingBottom(1.5f);
        cell.setHorizontalAlignment(align);
        cell.setUseAscender(true);
        cell.setUseDescender(true);
        // Varsayılan satır aralığı termal baskıda satırları birbirine yapıştırıyordu.
        cell.setLeading(0f, 1.22f);
        return cell;
    }

    private static String money(BigDecimal value) {
        return Format.formatPrice(value != null ? value : BigDecimal.ZERO);
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Hücrenin ortasından geçen kesikli çizgi. */
    private static class DashEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
            float y = position.getBottom() + position.getHeight() / 2f;
            cb.saveState();
            cb.setLineWidth(0.8f);
            cb.setColorStroke(Color.BLACK);
            cb.setLineDash(2.5f, 2f, 0f);
            cb.moveTo(position.getLeft(), y);
            cb.lineTo(position.getRight(), y);
            cb.stroke();
            cb.restoreState();
        }
    }
}
