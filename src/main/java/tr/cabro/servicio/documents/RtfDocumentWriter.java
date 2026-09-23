package tr.cabro.servicio.documents;

import tr.cabro.servicio.model.User;
import tr.cabro.servicio.model.WorkOrderItem;
import tr.cabro.servicio.util.PhoneHelper;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/**
 * Belgeyi RTF olarak yazar; Word, LibreOffice ve WordPad açar. Düzen {@link DocxDocumentWriter}
 * ile aynıdır. RTF 7-bit ASCII olduğu için Türkçe karakterler {@code \\uN?} kaçışıyla yazılır.
 * Ölçüler twip, yazı boyu yarım punto cinsindendir.
 */
public class RtfDocumentWriter implements DocumentWriter {

    private static final int CONTENT_WIDTH = 11906 - 2 * 1000;
    private static final Locale TR = new Locale("tr", "TR");

    // Renk tablosu sırası: 1 siyah, 2 soluk metin, 3 ince çizgi.
    private static final String INK = "\\cf1";
    private static final String MUTED = "\\cf2";

    private final File outFile;
    private final StringBuilder body = new StringBuilder();

    RtfDocumentWriter(File outFile, User shop, String title, String documentNumber, String date) {
        this.outFile = outFile;
        writeMasthead(shop, title, documentNumber, date);
    }

    /** Tek paragraf: {@code props} pard ayarları (hizalama, boşluk, kenarlık), {@code runs} biçimli metin. */
    private record Para(String props, String runs) {
    }

    // -------------------------------------------------------------------------
    // Künye
    // -------------------------------------------------------------------------

    private void writeMasthead(User shop, String title, String documentNumber, String date) {
        int left = CONTENT_WIDTH * 47 / 100;
        Para[] leftParas = {
                new Para("\\sa40", run(shop != null && notBlank(shop.getBusinessName()) ? shop.getBusinessName() : " ", 25, true, INK)),
                new Para("", shop != null && notBlank(shop.getAddress()) ? run(shop.getAddress(), 15, false, MUTED) : ""),
                new Para("", run(contactLine(shop), 15, false, MUTED))
        };
        Para[] rightParas = {
                new Para("\\qr\\sa120", run(title, 32, true, INK)),
                new Para("\\qr\\sa20", run("BELGE NO   ", 14, false, MUTED) + run(documentNumber, 19, true, INK)),
                new Para("\\qr", run("TAR\u0130H   ", 14, false, MUTED) + run(date, 19, false, INK))
        };
        body.append(tableRow(new int[]{left, CONTENT_WIDTH - left}, new String[]{"", ""}, leftParas, rightParas));
        body.append(para("\\sa120\\brdrb\\brdrs\\brdrw30\\brsp80\\brdrcf1", ""));
    }

    private static String contactLine(User shop) {
        if (shop == null) return "";
        StringBuilder sb = new StringBuilder();
        if (notBlank(shop.getPhoneNumber())) sb.append(PhoneHelper.formatForDisplay(shop.getPhoneNumber()));
        if (notBlank(shop.getEmail())) {
            if (sb.length() > 0) sb.append("   \u00b7   ");
            sb.append(shop.getEmail());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Bölümler
    // -------------------------------------------------------------------------

    @Override
    public void section(String title) {
        body.append(para("\\sb320\\sa140\\brdrb\\brdrs\\brdrw10\\brsp60\\brdrcf1", run(title.toUpperCase(TR), 17, true, INK)));
    }

    @Override
    public void fields(String[][] labelValuePairs) {
        int half = CONTENT_WIDTH / 2;
        String border = "\\clbrdrb\\brdrs\\brdrw10\\brdrcf3";
        for (int i = 0; i < labelValuePairs.length; i += 2) {
            Para[] second = i + 1 < labelValuePairs.length
                    ? fieldParas(labelValuePairs[i + 1][0], labelValuePairs[i + 1][1])
                    : new Para[]{new Para("", "")};
            body.append(tableRow(new int[]{half, half}, new String[]{border, i + 1 < labelValuePairs.length ? border : ""},
                    fieldParas(labelValuePairs[i][0], labelValuePairs[i][1]), second));
        }
    }

    @Override
    public void wideField(String label, String value) {
        body.append(tableRow(new int[]{CONTENT_WIDTH}, new String[]{"\\clbrdrb\\brdrs\\brdrw10\\brdrcf3"},
                fieldParas(label, value)));
    }

    private static Para[] fieldParas(String label, String value) {
        return new Para[]{
                new Para("\\sb100\\sa20", run(label.toUpperCase(TR), 14, false, MUTED)),
                new Para("\\sa100", run(notBlank(value) ? value : "\u2014", 20, false, INK))
        };
    }

    @Override
    public void paragraph(String text, String emptyText) {
        boolean empty = !notBlank(text);
        body.append(para("\\sa80", run(empty ? emptyText : text, empty ? 16 : 19, false, empty ? MUTED : INK)));
    }

    @Override
    public void terms(String text) {
        body.append(para("\\qj\\sa80", run(text, 17, false, INK)));
    }

    @Override
    public void items(List<WorkOrderItem> items, BigDecimal total) {
        int[] w = {CONTENT_WIDTH * 5 / 100, CONTENT_WIDTH * 51 / 100, CONTENT_WIDTH * 10 / 100, CONTENT_WIDTH * 16 / 100, 0};
        w[4] = CONTENT_WIDTH - w[0] - w[1] - w[2] - w[3];
        String head = "\\clbrdrb\\brdrs\\brdrw20\\brdrcf1";
        String line = "\\clbrdrb\\brdrs\\brdrw10\\brdrcf3";
        String[] headBorders = {head, head, head, head, head};
        String[] lineBorders = {line, line, line, line, line};

        String[] headers = {"#", "KALEM", "ADET", "B\u0130R\u0130M F\u0130YAT", "TUTAR"};
        Para[][] headerCells = new Para[5][];
        for (int i = 0; i < 5; i++) {
            headerCells[i] = new Para[]{new Para((i >= 2 ? "\\qr" : "") + "\\sb40\\sa80", run(headers[i], 14, false, MUTED))};
        }
        body.append(tableRow(w, headBorders, headerCells));

        if (items == null || items.isEmpty()) {
            body.append(tableRow(new int[]{CONTENT_WIDTH}, new String[]{line},
                    new Para[]{new Para("\\sb80\\sa80", run("Kalem eklenmemi\u015f.", 16, false, MUTED))}));
        } else {
            int index = 1;
            for (WorkOrderItem item : items) {
                body.append(tableRow(w, lineBorders,
                        new Para[]{new Para("\\sb80\\sa80", run(String.valueOf(index++), 16, false, MUTED))},
                        new Para[]{new Para("\\sb80\\sa80", run(item.getItemName(), 19, false, INK))},
                        new Para[]{new Para("\\qr\\sb80\\sa80", run(String.valueOf(item.getQuantity()), 19, false, INK))},
                        new Para[]{new Para("\\qr\\sb80\\sa80", run(PdfDocumentBuilder.money(item.getUnitPrice()), 19, false, INK))},
                        new Para[]{new Para("\\qr\\sb80\\sa80", run(PdfDocumentBuilder.money(item.getTotalPrice()), 19, false, INK))}));
            }
        }

        // Toplam bloğu sağda: soldaki boş hücre tabloyu sağa iter.
        int totalsWidth = CONTENT_WIDTH * 42 / 100;
        int spacer = CONTENT_WIDTH - totalsWidth;
        String top = "\\clbrdrt\\brdrs\\brdrw30\\brdrcf1";
        body.append(para("", ""));
        body.append(tableRow(new int[]{spacer, totalsWidth / 2, totalsWidth - totalsWidth / 2}, new String[]{"", top, top},
                new Para[]{new Para("", "")},
                new Para[]{new Para("\\sb120", run("GENEL TOPLAM", 17, true, INK))},
                new Para[]{new Para("\\qr\\sb100", run(PdfDocumentBuilder.money(total), 24, true, INK))}));
    }

    @Override
    public void choices(String... options) {
        StringBuilder runs = new StringBuilder();
        for (String option : options) {
            runs.append(run("\u2610 ", 22, false, INK)).append(run(option + "          ", 19, true, INK));
        }
        body.append(para("\\sb200\\sa80", runs.toString()));
    }

    @Override
    public void signatures(String leftLabel, String leftName, String rightLabel, String rightName) {
        int side = CONTENT_WIDTH * 44 / 100;
        int gap = CONTENT_WIDTH - 2 * side;
        body.append(para("\\sb400", ""));
        body.append(tableRow(new int[]{side, gap, side}, new String[]{"", "", ""},
                signatureParas(leftLabel, leftName), new Para[]{new Para("", "")}, signatureParas(rightLabel, rightName)));
    }

    private static Para[] signatureParas(String label, String name) {
        return new Para[]{
                new Para("", run(label.toUpperCase(TR), 14, false, MUTED)),
                new Para("", ""), new Para("", ""), new Para("", ""),
                new Para("\\sb60\\brdrt\\brdrs\\brdrw12\\brsp80\\brdrcf1", run(notBlank(name) ? name : " ", 19, true, INK)),
                new Para("", run("Ad Soyad  \u00b7  \u0130mza", 15, false, MUTED))
        };
    }

    @Override
    public void close() throws IOException {
        String rtf = "{\\rtf1\\ansi\\ansicpg1254\\deff0\\uc1"
                + "{\\fonttbl{\\f0\\fswiss\\fcharset162 Arial;}}"
                + "{\\colortbl;\\red0\\green0\\blue0;\\red85\\green85\\blue85;\\red175\\green175\\blue175;}"
                + "\\paperw11906\\paperh16838\\margl1000\\margr1000\\margt920\\margb1000\n"
                + body
                + "}";
        Files.write(outFile.toPath(), rtf.getBytes(StandardCharsets.US_ASCII));
    }

    // -------------------------------------------------------------------------
    // RTF parçaları
    // -------------------------------------------------------------------------

    private static String para(String props, String runs) {
        return "\\pard\\plain\\f0" + props + " " + runs + "\\par\n";
    }

    /** Tablo satırı: her hücrenin kenarlığı ve paragrafları. Son paragraf {@code \cell} ile biter. */
    private static String tableRow(int[] widths, String[] borders, Para[]... cells) {
        StringBuilder sb = new StringBuilder("\\trowd\\trgaph0\\trleft0\\trkeep");
        int x = 0;
        for (int i = 0; i < widths.length; i++) {
            x += widths[i];
            sb.append(borders[i]).append("\\cellx").append(x);
        }
        sb.append("\n");
        for (Para[] cell : cells) {
            for (int j = 0; j < cell.length; j++) {
                sb.append("\\pard\\plain\\intbl\\f0").append(cell[j].props()).append(' ').append(cell[j].runs());
                sb.append(j == cell.length - 1 ? "\\cell\n" : "\\par\n");
            }
        }
        sb.append("\\row\n");
        return sb.toString();
    }

    private static String run(String text, int halfPoints, boolean bold, String color) {
        return "{" + (bold ? "\\b" : "") + color + "\\fs" + halfPoints + " " + escape(text != null ? text : "") + "}";
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            if (c == '\\' || c == '{' || c == '}') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\line ");
            } else if (c == '\r' || (c < 0x20 && c != '\t')) {
                // atla
            } else if (c > 127) {
                sb.append("\\u").append((int) (short) c).append('?');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
