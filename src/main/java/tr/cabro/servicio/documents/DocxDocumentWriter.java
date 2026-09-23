package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Belgeyi Word (.docx) olarak yazar. Apache POI (~15 MB) eklemek yerine en küçük geçerli
 * OOXML paketi elle üretilir: {@code [Content_Types].xml}, {@code _rels/.rels},
 * {@code word/document.xml}. Word ve LibreOffice ikisi de açar.
 * <p>
 * Düzen PDF'teki sırayı izler; font Arial (her iki platformda da bulunur ya da eşlenir).
 * Ölçüler twip (1/20 pt), yazı boyu yarım punto, çizgi kalınlığı 1/8 pt cinsindendir.
 */
public class DocxDocumentWriter implements DocumentWriter {

    private static final int CONTENT_WIDTH = 11906 - 2 * 1000;
    private static final String MUTED = "555555";
    private static final String RULE = "AFAFAF";
    private static final Locale TR = new Locale("tr", "TR");

    private final File outFile;
    private final StringBuilder body = new StringBuilder();

    DocxDocumentWriter(File outFile, User shop, String title, String documentNumber, String date) {
        this.outFile = outFile;
        writeMasthead(shop, title, documentNumber, date);
    }

    // -------------------------------------------------------------------------
    // Künye
    // -------------------------------------------------------------------------

    private void writeMasthead(User shop, String title, String documentNumber, String date) {
        StringBuilder left = new StringBuilder();
        left.append(p(run(shop != null && notBlank(shop.getBusinessName()) ? shop.getBusinessName() : " ", 25, true, null), null, 0, 40));
        if (shop != null && notBlank(shop.getAddress())) left.append(p(run(shop.getAddress(), 15, false, MUTED), null, 0, 0));
        String contact = contactLine(shop);
        if (!contact.isEmpty()) left.append(p(run(contact, 15, false, MUTED), null, 0, 0));

        String right = p(run(title, 32, true, null), "right", 0, 120)
                + p(run("BELGE NO   ", 14, false, MUTED) + run(documentNumber, 19, true, null), "right", 0, 20)
                + p(run("TARİH   ", 14, false, MUTED) + run(date, 19, false, null), "right", 0, 0);

        int leftWidth = CONTENT_WIDTH * 47 / 100;
        body.append(table(new int[]{leftWidth, CONTENT_WIDTH - leftWidth},
                row(cell(leftWidth, left.toString(), null), cell(CONTENT_WIDTH - leftWidth, right, null))));
        body.append(p("", null, 0, 120, bottomBorder(12, "000000")));
    }

    private static String contactLine(User shop) {
        if (shop == null) return "";
        StringBuilder sb = new StringBuilder();
        if (notBlank(shop.getPhoneNumber())) sb.append(PhoneHelper.formatForDisplay(shop.getPhoneNumber()));
        if (notBlank(shop.getEmail())) {
            if (sb.length() > 0) sb.append("   ·   ");
            sb.append(shop.getEmail());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Bölümler
    // -------------------------------------------------------------------------

    @Override
    public void section(String title) {
        body.append(p(run(title.toUpperCase(TR), 17, true, null), null, 320, 140, bottomBorder(4, "000000")));
    }

    @Override
    public void fields(String[][] labelValuePairs) {
        int half = CONTENT_WIDTH / 2;
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            String first = fieldCell(half, labelValuePairs[i][0], labelValuePairs[i][1]);
            String second = i + 1 < labelValuePairs.length
                    ? fieldCell(half, labelValuePairs[i + 1][0], labelValuePairs[i + 1][1])
                    : cell(half, p("", null, 0, 0), null);
            rows.append(row(first, second));
        }
        body.append(table(new int[]{half, half}, rows.toString()));
    }

    @Override
    public void wideField(String label, String value) {
        body.append(table(new int[]{CONTENT_WIDTH}, row(fieldCell(CONTENT_WIDTH, label, value))));
    }

    private String fieldCell(int width, String label, String value) {
        String content = p(run(label.toUpperCase(TR), 14, false, MUTED), null, 100, 20)
                + p(run(notBlank(value) ? value : "—", 20, false, null), null, 0, 100);
        return cell(width, content, "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"" + RULE + "\"/>");
    }

    @Override
    public void paragraph(String text, String emptyText) {
        boolean empty = !notBlank(text);
        body.append(p(run(empty ? emptyText : text, empty ? 16 : 19, false, empty ? MUTED : null), null, 0, 80));
    }

    @Override
    public void terms(String text) {
        body.append(p(run(text, 17, false, null), "both", 0, 80));
    }

    @Override
    public void items(List<WorkOrderItem> items, BigDecimal total) {
        int[] w = {
                CONTENT_WIDTH * 5 / 100, CONTENT_WIDTH * 51 / 100, CONTENT_WIDTH * 10 / 100,
                CONTENT_WIDTH * 16 / 100, 0};
        w[4] = CONTENT_WIDTH - w[0] - w[1] - w[2] - w[3];
        String headerBorder = "<w:bottom w:val=\"single\" w:sz=\"8\" w:color=\"000000\"/>";
        String rowBorder = "<w:bottom w:val=\"single\" w:sz=\"4\" w:color=\"" + RULE + "\"/>";

        StringBuilder rows = new StringBuilder();
        String[] headers = {"#", "KALEM", "ADET", "BİRİM FİYAT", "TUTAR"};
        String[] headerCells = new String[5];
        for (int i = 0; i < 5; i++) {
            headerCells[i] = cell(w[i], p(run(headers[i], 14, false, MUTED), i >= 2 ? "right" : null, 40, 80), headerBorder);
        }
        rows.append(row(headerCells));

        if (items == null || items.isEmpty()) {
            rows.append("<w:tr>" + spanCell(CONTENT_WIDTH, 5, p(run("Kalem eklenmemiş.", 16, false, MUTED), null, 80, 80), rowBorder) + "</w:tr>");
        } else {
            int index = 1;
            for (WorkOrderItem item : items) {
                rows.append(row(
                        cell(w[0], p(run(String.valueOf(index++), 16, false, MUTED), null, 80, 80), rowBorder),
                        cell(w[1], p(run(item.getItemName(), 19, false, null), null, 80, 80), rowBorder),
                        cell(w[2], p(run(String.valueOf(item.getQuantity()), 19, false, null), "right", 80, 80), rowBorder),
                        cell(w[3], p(run(PdfDocumentBuilder.money(item.getUnitPrice()), 19, false, null), "right", 80, 80), rowBorder),
                        cell(w[4], p(run(PdfDocumentBuilder.money(item.getTotalPrice()), 19, false, null), "right", 80, 80), rowBorder)));
            }
        }
        body.append(table(w, rows.toString()));

        int totalsWidth = CONTENT_WIDTH * 42 / 100;
        String topBorder = "<w:top w:val=\"single\" w:sz=\"12\" w:color=\"000000\"/>";
        String totals = row(
                cell(totalsWidth / 2, p(run("GENEL TOPLAM", 17, true, null), null, 120, 0), topBorder),
                cell(totalsWidth - totalsWidth / 2, p(run(PdfDocumentBuilder.money(total), 24, true, null), "right", 100, 0), topBorder));
        body.append(p("", null, 0, 0));
        body.append(table(new int[]{totalsWidth / 2, totalsWidth - totalsWidth / 2}, totals, "right"));
    }

    @Override
    public void choices(String... options) {
        StringBuilder runs = new StringBuilder();
        for (String option : options) {
            runs.append(run("☐ ", 22, false, null)).append(run(option + "          ", 19, true, null));
        }
        body.append(p(runs.toString(), null, 200, 80));
    }

    @Override
    public void signatures(String leftLabel, String leftName, String rightLabel, String rightName) {
        int side = CONTENT_WIDTH * 44 / 100;
        int gap = CONTENT_WIDTH - 2 * side;
        body.append(p("", null, 400, 0));
        body.append(table(new int[]{side, gap, side},
                row(signatureCell(side, leftLabel, leftName), cell(gap, p("", null, 0, 0), null), signatureCell(side, rightLabel, rightName))));
    }

    private String signatureCell(int width, String label, String name) {
        String content = p(run(label.toUpperCase(TR), 14, false, MUTED), null, 0, 0)
                + p("", null, 0, 0) + p("", null, 0, 0) + p("", null, 0, 0)
                + p(run(notBlank(name) ? name : " ", 19, true, null), null, 60, 0, "<w:pBdr><w:top w:val=\"single\" w:sz=\"6\" w:space=\"4\" w:color=\"000000\"/></w:pBdr>")
                + p(run("Ad Soyad  ·  İmza", 15, false, MUTED), null, 0, 0);
        return cell(width, content, null);
    }

    @Override
    public void close() throws IOException {
        String document = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + body
                + "<w:p/>"
                + "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>"
                + "<w:pgMar w:top=\"920\" w:right=\"1000\" w:bottom=\"1000\" w:left=\"1000\" w:header=\"500\" w:footer=\"500\" w:gutter=\"0\"/>"
                + "</w:sectPr></w:body></w:document>";

        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outFile))) {
            entry(zip, "[Content_Types].xml", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
                    + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                    + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                    + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
                    + "</Types>");
            entry(zip, "_rels/.rels", "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                    + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
                    + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
                    + "</Relationships>");
            entry(zip, "word/document.xml", document);
        }
    }

    private static void entry(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    // -------------------------------------------------------------------------
    // OOXML parçaları
    // -------------------------------------------------------------------------

    /** Metin parçası. {@code halfPoints}: yazı boyu × 2. Satır sonları {@code w:br} olur. */
    private static String run(String text, int halfPoints, boolean bold, String color) {
        StringBuilder rPr = new StringBuilder("<w:rPr><w:rFonts w:ascii=\"Arial\" w:hAnsi=\"Arial\" w:cs=\"Arial\"/>");
        if (bold) rPr.append("<w:b/>");
        if (color != null) rPr.append("<w:color w:val=\"").append(color).append("\"/>");
        rPr.append("<w:sz w:val=\"").append(halfPoints).append("\"/><w:szCs w:val=\"").append(halfPoints).append("\"/></w:rPr>");

        StringBuilder sb = new StringBuilder();
        String[] lines = (text != null ? text : "").split("\n", -1);
        sb.append("<w:r>").append(rPr);
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append("<w:br/>");
            sb.append("<w:t xml:space=\"preserve\">").append(escape(lines[i])).append("</w:t>");
        }
        sb.append("</w:r>");
        return sb.toString();
    }

    private static String p(String runs, String align, int before, int after) {
        return p(runs, align, before, after, "");
    }

    /** Paragraf. {@code before}/{@code after} twip; {@code extra} pPr içine eklenir (ör. kenarlık). */
    private static String p(String runs, String align, int before, int after, String extra) {
        return "<w:p><w:pPr>" + (extra != null ? extra : "")
                + "<w:spacing w:before=\"" + before + "\" w:after=\"" + after + "\"/>"
                + (align != null ? "<w:jc w:val=\"" + align + "\"/>" : "")
                + "</w:pPr>" + runs + "</w:p>";
    }

    private static String bottomBorder(int eighths, String color) {
        return "<w:pBdr><w:bottom w:val=\"single\" w:sz=\"" + eighths + "\" w:space=\"4\" w:color=\"" + color + "\"/></w:pBdr>";
    }

    private static String table(int[] widths, String rows) {
        return table(widths, rows, null);
    }

    private static String table(int[] widths, String rows, String align) {
        int total = 0;
        StringBuilder grid = new StringBuilder("<w:tblGrid>");
        for (int w : widths) {
            total += w;
            grid.append("<w:gridCol w:w=\"").append(w).append("\"/>");
        }
        grid.append("</w:tblGrid>");
        return "<w:tbl><w:tblPr><w:tblW w:w=\"" + total + "\" w:type=\"dxa\"/>"
                + (align != null ? "<w:jc w:val=\"" + align + "\"/>" : "")
                + "<w:tblBorders><w:top w:val=\"nil\"/><w:left w:val=\"nil\"/><w:bottom w:val=\"nil\"/>"
                + "<w:right w:val=\"nil\"/><w:insideH w:val=\"nil\"/><w:insideV w:val=\"nil\"/></w:tblBorders>"
                + "<w:tblLayout w:type=\"fixed\"/>"
                + "<w:tblCellMar><w:left w:w=\"0\" w:type=\"dxa\"/><w:right w:w=\"120\" w:type=\"dxa\"/></w:tblCellMar>"
                + "</w:tblPr>" + grid + rows + "</w:tbl>";
    }

    private static String row(String... cells) {
        return "<w:tr><w:trPr><w:cantSplit/></w:trPr>" + String.join("", cells) + "</w:tr>";
    }

    private static String cell(int width, String content, String borders) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/>"
                + (borders != null ? "<w:tcBorders>" + borders + "</w:tcBorders>" : "")
                + "</w:tcPr>" + content + "</w:tc>";
    }

    private static String spanCell(int width, int span, String content, String borders) {
        return "<w:tc><w:tcPr><w:tcW w:w=\"" + width + "\" w:type=\"dxa\"/><w:gridSpan w:val=\"" + span + "\"/>"
                + (borders != null ? "<w:tcBorders>" + borders + "</w:tcBorders>" : "")
                + "</w:tcPr>" + content + "</w:tc>";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> {
                    // XML 1.0'da geçersiz kontrol karakterleri atlanır (Word dosyayı bozuk sayar).
                    if (c >= 0x20 || c == '\t') sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
