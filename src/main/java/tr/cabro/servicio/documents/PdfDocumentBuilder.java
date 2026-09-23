package tr.cabro.servicio.documents;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPCellEvent;
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
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * A4 servis ve 2.el belgelerinin (kabul, teslim, arıza tespit, teklif onayı, sözleşmeler,
 * garanti, ekspertiz) ortak düzeni.
 * <p>
 * Tasarım siyah-beyaz baskıya göre yapılmıştır: renk yok, gri yalnızca ikincil metinde ve
 * ince çizgilerde. Lazer yazıcıda ve fotokopide okunaklı kalır. Düzen:
 * <ul>
 *   <li>Künye: solda logo ve işletme bilgisi, sağda belge adı ve çerçeveli belge no/tarih kutusu,
 *       altında kalın bir çizgi.</li>
 *   <li>Bilgiler form alanı gibi dizilir: küçük büyük harfli etiket üstte, değer altta,
 *       altında ince çizgi.</li>
 *   <li>Kalem tablosunda dikey çizgi yok; tutarlar sağa hizalı, toplam ayrı bir blokta.</li>
 *   <li>Her sayfanın altında işletme adı, belge no ve sayfa numarası.</li>
 * </ul>
 * Kullanım: {@link #create} ile aç, bölümleri sırayla ekle, {@link #close()} ile kapat.
 */
public class PdfDocumentBuilder implements DocumentWriter {

    private static final Color INK = Color.BLACK;
    private static final Color MUTED = new Color(85, 85, 85);
    private static final Color RULE = new Color(175, 175, 175);

    private static final float MARGIN_X = 50f;
    private static final float MARGIN_TOP = 46f;
    private static final float MARGIN_BOTTOM = 62f;

    private final Font shopFont;
    private final Font titleFont;
    private final Font labelFont;
    private final Font valueFont;
    private final Font bodyFont;
    private final Font boldFont;
    private final Font smallFont;
    private final Font totalFont;
    private final Font sectionFont;

    private final Document document;

    private PdfDocumentBuilder(File outFile, String footerText) throws IOException, DocumentException {
        BaseFont regular = PdfFonts.regular();
        BaseFont bold = PdfFonts.bold();

        shopFont = new Font(bold, 12.5f, Font.NORMAL, INK);
        titleFont = new Font(bold, 16f, Font.NORMAL, INK);
        sectionFont = new Font(bold, 8.5f, Font.NORMAL, INK);
        labelFont = new Font(regular, 7f, Font.NORMAL, MUTED);
        valueFont = new Font(regular, 10f, Font.NORMAL, INK);
        bodyFont = new Font(regular, 9.5f, Font.NORMAL, INK);
        boldFont = new Font(bold, 9.5f, Font.NORMAL, INK);
        smallFont = new Font(regular, 7.5f, Font.NORMAL, MUTED);
        totalFont = new Font(bold, 12f, Font.NORMAL, INK);

        document = new Document(PageSize.A4, MARGIN_X, MARGIN_X, MARGIN_TOP, MARGIN_BOTTOM);
        PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outFile));
        writer.setPageEvent(new FooterEvent(footerText, smallFont));
        document.open();
    }

    /**
     * Belgeyi açar ve künyeyi basar.
     *
     * @param title          belge adı ("Cihaz Kabul Formu")
     * @param documentNumber belge numarası ("SRV-12", "DT-4")
     * @param date           belgenin tarihi, biçimlenmiş
     */
    public static PdfDocumentBuilder create(File outFile, User shop, String title,
                                            String documentNumber, String date) throws IOException, DocumentException {
        String shopName = shop != null && notBlank(shop.getBusinessName()) ? shop.getBusinessName() : "";
        String footer = (shopName.isEmpty() ? "" : shopName + "   ·   ") + title + "   ·   " + documentNumber;
        PdfDocumentBuilder pdf = new PdfDocumentBuilder(outFile, footer);
        pdf.addMasthead(shop, title, documentNumber, date);
        return pdf;
    }

    /** Geçici bir PDF dosyası açar; uygulama kapanınca silinir. */
    public static File tempFile(String prefix) throws IOException {
        File file = File.createTempFile("servicio-" + prefix + "-", ".pdf");
        file.deleteOnExit();
        return file;
    }

    // -------------------------------------------------------------------------
    // Künye
    // -------------------------------------------------------------------------

    private void addMasthead(User shop, String title, String documentNumber, String date) throws DocumentException, IOException {
        PdfPTable head = new PdfPTable(new float[]{1f, 1.15f});
        head.setWidthPercentage(100);

        head.addCell(shopBlock(shop));

        PdfPCell right = bare();
        right.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph titlePara = new Paragraph(title, titleFont);
        titlePara.setAlignment(Element.ALIGN_RIGHT);
        right.addElement(titlePara);

        // Dış çerçeve tek hücrede çizilir; satırlar arasında yalnızca yatay ayraç olur.
        PdfPTable meta = new PdfPTable(new float[]{1f, 1.25f});
        meta.setWidthPercentage(100);
        addMetaRow(meta, "Belge No", documentNumber, true);
        addMetaRow(meta, "Tarih", date, false);
        PdfPCell frame = new PdfPCell(meta);
        frame.setBorder(Rectangle.BOX);
        frame.setBorderWidth(0.75f);
        frame.setBorderColor(INK);
        frame.setPadding(0f);
        PdfPTable metaBox = new PdfPTable(1);
        metaBox.setWidthPercentage(78);
        metaBox.setHorizontalAlignment(Element.ALIGN_RIGHT);
        metaBox.setSpacingBefore(7f);
        metaBox.addCell(frame);
        right.addElement(metaBox);
        head.addCell(right);

        document.add(head);
        document.add(rule(1.5f, INK, 10f, 4f));
    }

    private PdfPCell shopBlock(User shop) throws IOException, DocumentException {
        PdfPCell cell = bare();
        cell.setVerticalAlignment(Element.ALIGN_TOP);

        File logo = shop != null && notBlank(shop.getLogoPath()) ? resolveLogoFile(shop.getLogoPath()) : null;
        PdfPTable inner = new PdfPTable(logo != null && logo.exists() ? new float[]{0.9f, 3f} : new float[]{1f});
        inner.setWidthPercentage(100);

        if (logo != null && logo.exists()) {
            Image image = Image.getInstance(logo.getAbsolutePath());
            image.scaleToFit(62, 62);
            PdfPCell logoCell = new PdfPCell(image, false);
            logoCell.setBorder(Rectangle.NO_BORDER);
            logoCell.setPaddingRight(10f);
            inner.addCell(logoCell);
        }

        PdfPCell text = bare();
        text.addElement(new Paragraph(shop != null && notBlank(shop.getBusinessName()) ? shop.getBusinessName() : " ", shopFont));
        if (shop != null && notBlank(shop.getAddress())) {
            Paragraph address = new Paragraph(shop.getAddress(), smallFont);
            address.setSpacingBefore(3f);
            text.addElement(address);
        }
        StringBuilder contact = new StringBuilder();
        if (shop != null && notBlank(shop.getPhoneNumber())) contact.append(PhoneHelper.formatForDisplay(shop.getPhoneNumber()));
        if (shop != null && notBlank(shop.getEmail())) {
            if (contact.length() > 0) contact.append("   ·   ");
            contact.append(shop.getEmail());
        }
        if (contact.length() > 0) {
            Paragraph c = new Paragraph(contact.toString(), smallFont);
            c.setSpacingBefore(1.5f);
            text.addElement(c);
        }
        inner.addCell(text);

        cell.addElement(inner);
        return cell;
    }

    private void addMetaRow(PdfPTable meta, String label, String value, boolean first) {
        PdfPCell l = new PdfPCell(new Phrase(label.toUpperCase(TR), labelFont));
        PdfPCell v = new PdfPCell(new Phrase(value != null ? value : "-", first ? boldFont : bodyFont));
        v.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[]{l, v}) {
            // İlk satırın altına ince ayraç; dış çerçeveyi saran hücre çizer.
            c.setBorder(first ? Rectangle.BOTTOM : Rectangle.NO_BORDER);
            c.setBorderWidthBottom(0.5f);
            c.setBorderColorBottom(INK);
            c.setPaddingTop(4.5f);
            c.setPaddingBottom(6f);
            c.setPaddingLeft(6f);
            c.setPaddingRight(6f);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
            c.setUseAscender(true);
            c.setUseDescender(true);
        }
        meta.addCell(l);
        meta.addCell(v);
    }

    private File resolveLogoFile(String logoPath) {
        return new File(new File(Servicio.getInstance().getDataFolder(), "logos"), logoPath);
    }

    // -------------------------------------------------------------------------
    // Bölümler
    // -------------------------------------------------------------------------

    /** Bölüm başlığı: küçük büyük harf, altında ince çizgi. Üstte geniş, altta dar boşluk. */
    @Override
    public void section(String title) throws DocumentException {
        Paragraph p = new Paragraph();
        Chunk chunk = new Chunk(title.toUpperCase(TR), sectionFont);
        chunk.setCharacterSpacing(0.6f);
        p.add(chunk);
        p.setSpacingBefore(16f);
        document.add(p);
        document.add(rule(0.5f, INK, 3f, 7f));
    }

    /**
     * Etiket/değer çiftlerini form alanı olarak iki sütunda dizer. Değeri uzun olan alan
     * (açıklama, adres) için {@link #wideField} kullanılır.
     */
    @Override
    public void fields(String[][] labelValuePairs) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4f);
        for (String[] pair : labelValuePairs) {
            table.addCell(fieldCell(pair[0], pair[1]));
        }
        if (labelValuePairs.length % 2 != 0) {
            PdfPCell filler = bare();
            table.addCell(filler);
        }
        document.add(table);
    }

    /** Tam genişlikte tek bir form alanı. */
    @Override
    public void wideField(String label, String value) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.addCell(fieldCell(label, value));
        document.add(table);
    }

    private PdfPCell fieldCell(String label, String value) {
        PdfPCell cell = bare();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColorBottom(RULE);
        cell.setBorderWidthBottom(0.5f);
        cell.setPaddingTop(7f);
        cell.setPaddingBottom(6f);
        cell.setPaddingRight(14f);

        Paragraph l = new Paragraph(label.toUpperCase(TR), labelFont);
        cell.addElement(l);
        Paragraph v = new Paragraph(notBlank(value) ? value : "—", valueFont);
        v.setSpacingBefore(2f);
        cell.addElement(v);
        return cell;
    }

    /** Serbest metin. Boşsa {@code emptyText} soluk yazılır. */
    @Override
    public void paragraph(String text, String emptyText) throws DocumentException {
        boolean empty = !notBlank(text);
        Paragraph p = new Paragraph(empty ? emptyText : text, empty ? smallFont : bodyFont);
        p.setLeading(empty ? 11f : 14f);
        document.add(p);
    }

    /** Koşul/beyan metni: gövdeden küçük, satır aralığı rahat. */
    @Override
    public void terms(String text) throws DocumentException {
        Paragraph p = new Paragraph(text, new Font(bodyFont.getBaseFont(), 8.5f, Font.NORMAL, INK));
        p.setLeading(12.5f);
        p.setAlignment(Element.ALIGN_JUSTIFIED);
        document.add(p);
    }

    /** Sıra no, kalem, adet, birim fiyat ve tutar tablosu; altında sağa hizalı toplam bloğu. */
    @Override
    public void items(List<WorkOrderItem> items, BigDecimal total) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{0.35f, 4f, 0.7f, 1.3f, 1.4f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(2f);
        table.setHeaderRows(1);

        String[] headers = {"#", "Kalem", "Adet", "Birim Fiyat", "Tutar"};
        for (int i = 0; i < headers.length; i++) {
            PdfPCell h = new PdfPCell(new Phrase(headers[i].toUpperCase(TR), labelFont));
            h.setBorder(Rectangle.BOTTOM);
            h.setBorderWidthBottom(1f);
            h.setBorderColorBottom(INK);
            h.setPaddingBottom(6f);
            h.setPaddingTop(2f);
            h.setHorizontalAlignment(i >= 2 ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
            table.addCell(h);
        }

        if (items == null || items.isEmpty()) {
            PdfPCell empty = itemCell("Kalem eklenmemiş.", smallFont, Element.ALIGN_LEFT);
            empty.setColspan(5);
            table.addCell(empty);
        } else {
            int index = 1;
            for (WorkOrderItem item : items) {
                table.addCell(itemCell(String.valueOf(index++), smallFont, Element.ALIGN_LEFT));
                table.addCell(itemCell(item.getItemName(), bodyFont, Element.ALIGN_LEFT));
                table.addCell(itemCell(String.valueOf(item.getQuantity()), bodyFont, Element.ALIGN_RIGHT));
                table.addCell(itemCell(money(item.getUnitPrice()), bodyFont, Element.ALIGN_RIGHT));
                table.addCell(itemCell(money(item.getTotalPrice()), bodyFont, Element.ALIGN_RIGHT));
            }
        }
        document.add(table);

        PdfPTable totals = new PdfPTable(new float[]{1f, 1f});
        totals.setWidthPercentage(42);
        totals.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totals.setSpacingBefore(8f);
        PdfPCell label = new PdfPCell(new Phrase("GENEL TOPLAM", sectionFont));
        PdfPCell value = new PdfPCell(new Phrase(money(total), totalFont));
        value.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell c : new PdfPCell[]{label, value}) {
            c.setBorder(Rectangle.TOP);
            c.setBorderWidthTop(1.5f);
            c.setBorderColorTop(INK);
            c.setPaddingTop(7f);
            c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        }
        totals.addCell(label);
        totals.addCell(value);
        document.add(totals);
    }

    private PdfPCell itemCell(String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(notBlank(text) ? text : "-", font));
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidthBottom(0.5f);
        cell.setBorderColorBottom(RULE);
        cell.setPaddingTop(5f);
        cell.setPaddingBottom(7f);
        cell.setHorizontalAlignment(align);
        return cell;
    }

    /** Elle işaretlenecek seçenekler; her birinin önünde çizilmiş boş bir kare. */
    @Override
    public void choices(String... options) throws DocumentException {
        PdfPTable table = new PdfPTable(options.length * 2);
        float[] widths = new float[options.length * 2];
        for (int i = 0; i < options.length; i++) {
            widths[i * 2] = 0.22f;
            widths[i * 2 + 1] = 2.4f;
        }
        table.setWidths(widths);
        table.setWidthPercentage(Math.min(100, 30f * options.length));
        table.setHorizontalAlignment(Element.ALIGN_LEFT);
        table.setSpacingBefore(12f);
        for (String option : options) {
            PdfPCell box = bare();
            box.setFixedHeight(16f);
            box.setCellEvent(new CheckboxEvent());
            table.addCell(box);
            // Kare hücrenin dikey ortasına çizilir; metin de ortalanır ki ikisi aynı hizada dursun.
            PdfPCell text = new PdfPCell(new Phrase(option, boldFont));
            text.setBorder(Rectangle.NO_BORDER);
            text.setPadding(0f);
            text.setFixedHeight(16f);
            text.setVerticalAlignment(Element.ALIGN_MIDDLE);
            text.setUseAscender(true);
            text.setUseDescender(true);
            table.addCell(text);
        }
        document.add(table);
    }

    /**
     * İki taraflı imza alanı. İsimler "Form" menüsündeki modalda girilen metinlerdir;
     * boşsa elle yazılması için çizgi bırakılır.
     */
    @Override
    public void signatures(String leftLabel, String leftName, String rightLabel, String rightName) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 0.25f, 1f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(34f);
        table.setKeepTogether(true);
        table.addCell(signatureCell(leftLabel, leftName));
        table.addCell(bare());
        table.addCell(signatureCell(rightLabel, rightName));
        document.add(table);
    }

    private PdfPCell signatureCell(String label, String name) {
        PdfPCell cell = bare();
        cell.addElement(new Paragraph(label.toUpperCase(TR), labelFont));

        PdfPTable line = new PdfPTable(1);
        line.setWidthPercentage(100);
        line.setSpacingBefore(40f);
        PdfPCell nameCell = new PdfPCell(new Phrase(notBlank(name) ? name : " ", boldFont));
        nameCell.setBorder(Rectangle.TOP);
        nameCell.setBorderWidthTop(0.75f);
        nameCell.setBorderColorTop(INK);
        nameCell.setPaddingTop(5f);
        line.addCell(nameCell);
        PdfPCell caption = new PdfPCell(new Phrase("Ad Soyad  ·  İmza", smallFont));
        caption.setBorder(Rectangle.NO_BORDER);
        caption.setPaddingTop(1f);
        line.addCell(caption);
        cell.addElement(line);
        return cell;
    }

    @Override
    public void close() {
        document.close();
    }

    // -------------------------------------------------------------------------
    // Yardımcılar
    // -------------------------------------------------------------------------

    private static final Locale TR = new Locale("tr", "TR");

    public static String money(BigDecimal value) {
        return Format.formatPrice(value != null ? value : BigDecimal.ZERO);
    }

    public static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static PdfPCell bare() {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(0f);
        return cell;
    }

    private static PdfPTable rule(float width, Color color, float before, float after) {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(before);
        table.setSpacingAfter(after);
        PdfPCell cell = bare();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderWidthBottom(width);
        cell.setBorderColorBottom(color);
        cell.setFixedHeight(0.1f);
        table.addCell(cell);
        return table;
    }

    /** Hücrenin sol ortasına 9pt'lik boş bir kare çizer. */
    private static class CheckboxEvent implements PdfPCellEvent {
        @Override
        public void cellLayout(PdfPCell cell, Rectangle position, PdfContentByte[] canvases) {
            PdfContentByte cb = canvases[PdfPTable.LINECANVAS];
            float size = 9f;
            float y = position.getBottom() + (position.getHeight() - size) / 2f;
            cb.saveState();
            cb.setLineWidth(0.9f);
            cb.setColorStroke(INK);
            cb.rectangle(position.getLeft(), y, size, size);
            cb.stroke();
            cb.restoreState();
        }
    }

    /** Sayfa altı: ince çizgi, solda işletme · belge · no, sağda sayfa numarası. */
    private static class FooterEvent extends PdfPageEventHelper {
        private final String text;
        private final Font font;

        FooterEvent(String text, Font font) {
            this.text = text;
            this.font = font;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float y = document.bottom() - 26f;
            cb.saveState();
            cb.setLineWidth(0.5f);
            cb.setColorStroke(RULE);
            cb.moveTo(document.left(), y + 11f);
            cb.lineTo(document.right(), y + 11f);
            cb.stroke();
            cb.restoreState();
            ColumnText.showTextAligned(cb, Element.ALIGN_LEFT, new Phrase(text, font), document.left(), y, 0);
            ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Sayfa " + writer.getPageNumber(), font), document.right(), y, 0);
        }
    }
}
