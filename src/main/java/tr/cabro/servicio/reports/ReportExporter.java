package tr.cabro.servicio.reports;

import tr.cabro.servicio.documents.PdfDocumentBuilder;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.reports.Report.Col;
import tr.cabro.servicio.reports.Report.Figure;
import tr.cabro.servicio.reports.Report.Kind;
import tr.cabro.servicio.reports.Report.Table;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Raporu dışa aktarır. İki çıktı da ekrandakiyle aynı {@link Report} nesnesinden üretilir.
 * <ul>
 *   <li><b>PDF</b>: A4 siyah-beyaz belge düzeni (künye, rakamlar, döküm tabloları); yazdırmaya uygun.</li>
 *   <li><b>CSV</b>: Excel'de doğrudan açılır. Türkçe Excel için ayraç noktalı virgül, dosya UTF-8 BOM'lu;
 *       sayılar para simgesi ve binlik ayırıcı olmadan, arayüz dilinin ondalık işaretiyle yazılır,
 *       böylece Excel'de hücreler sayı olarak toplanabilir.</li>
 * </ul>
 */
public final class ReportExporter {

    private ReportExporter() {}

    private static String generatedAt() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", AppLocale.uiLocale()));
    }

    /** Önerilen dosya adı: "rapor-finans-ve-kar-2026-09-01_2026-09-27". */
    public static String fileBaseName(Report report) {
        String slug = report.kind().title().toLowerCase(AppLocale.uiLocale())
                .replace('ı', 'i').replace('ğ', 'g').replace('ü', 'u').replace('ş', 's').replace('ö', 'o').replace('ç', 'c')
                .replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return "rapor-" + slug + "-" + report.from() + (report.from().equals(report.to()) ? "" : "_" + report.to());
    }

    // ── PDF ─────────────────────────────────────────────────────────────────

    public static File pdf(Report report, User shop, File out) throws Exception {
        PdfDocumentBuilder pdf = PdfDocumentBuilder.createReport(out, shop, report.kind().title(),
                report.periodText(), generatedAt());
        try {
            pdf.section("Özet");
            List<String[]> figures = new ArrayList<>();
            for (Figure f : report.figures()) {
                boolean strong = f.tone() == Report.Tone.SUCCESS || f.tone() == Report.Tone.DANGER || f.tone() == Report.Tone.WARNING;
                figures.add(new String[]{f.label(), f.value(), f.hint(), strong ? "1" : "0"});
            }
            pdf.figures(figures);

            for (Table t : report.tables()) {
                pdf.section(t.title());
                if (t.note() != null) pdf.note(t.note());
                int n = t.cols().size();
                String[] headers = new String[n];
                float[] widths = new float[n];
                boolean[] right = new boolean[n];
                for (int i = 0; i < n; i++) {
                    Col c = t.cols().get(i);
                    headers[i] = c.name();
                    right[i] = c.kind().numeric();
                    widths[i] = i == 0 ? 2.6f : c.kind() == Kind.PERCENT || c.kind() == Kind.COUNT ? 0.8f : 1.3f;
                }
                List<String[]> rows = new ArrayList<>();
                for (Object[] row : t.rows()) rows.add(texts(t, row));
                pdf.table(headers, widths, right, rows, t.totals() != null ? texts(t, t.totals()) : null, t.emptyText());
            }
        } finally {
            pdf.close();
        }
        return out;
    }

    private static String[] texts(Table t, Object[] row) {
        String[] out = new String[row.length];
        for (int i = 0; i < row.length; i++) {
            out[i] = row[i] == null ? "" : t.cols().get(i).kind().format(row[i]);
        }
        return out;
    }

    // ── CSV ─────────────────────────────────────────────────────────────────

    public static File csv(Report report, File out) throws IOException {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(AppLocale.uiLocale());
        DecimalFormat money = new DecimalFormat("0.00", symbols);
        DecimalFormat number = new DecimalFormat("0.#", symbols);
        char decimal = symbols.getDecimalSeparator();
        // Ondalık işareti virgülse (Türkçe) Excel'in beklediği ayraç noktalı virgüldür.
        char sep = decimal == ',' ? ';' : ',';

        try (OutputStream os = Files.newOutputStream(out.toPath());
             Writer w = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            w.write('﻿');
            line(w, sep, report.kind().title());
            line(w, sep, "Dönem", report.periodText());
            line(w, sep, "Oluşturma", generatedAt());
            w.write("\r\n");

            line(w, sep, "Özet");
            for (Figure f : report.figures()) line(w, sep, f.label(), f.value(), f.hint());

            for (Table t : report.tables()) {
                w.write("\r\n");
                line(w, sep, t.title() + (t.note() != null ? " (" + t.note() + ")" : ""));
                List<String> headers = new ArrayList<>();
                for (Col c : t.cols()) {
                    String unit = c.kind() == Kind.PERCENT ? " (%)" : c.kind() == Kind.DAYS ? " (gün)" : "";
                    headers.add(c.name() + unit);
                }
                line(w, sep, headers.toArray(new String[0]));
                for (Object[] row : t.rows()) line(w, sep, csvCells(t, row, money, number));
                if (t.totals() != null) line(w, sep, csvCells(t, t.totals(), money, number));
            }
        }
        return out;
    }

    private static String[] csvCells(Table t, Object[] row, DecimalFormat money, DecimalFormat number) {
        String[] out = new String[row.length];
        for (int i = 0; i < row.length; i++) {
            Object v = row[i];
            Kind kind = t.cols().get(i).kind();
            if (v == null) {
                out[i] = "";
                continue;
            }
            switch (kind) {
                case MONEY:
                case MONEY_SIGN:
                case MONEY_NEGATIVE:
                case MONEY_OWED:
                    out[i] = money.format(((Number) v).doubleValue());
                    break;
                case COUNT:
                    out[i] = number.format(Math.round(((Number) v).doubleValue()));
                    break;
                case PERCENT:
                    out[i] = number.format(Math.round(((Number) v).doubleValue() * 1000) / 10.0);
                    break;
                case DAYS:
                    out[i] = number.format(Math.round(((Number) v).doubleValue() * 10) / 10.0);
                    break;
                case DATE:
                    out[i] = v.toString().length() >= 10 ? v.toString().substring(0, 10) : v.toString();
                    break;
                default:
                    out[i] = v.toString();
            }
        }
        return out;
    }

    private static void line(Writer w, char sep, String... cells) throws IOException {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) w.write(sep);
            w.write(quote(cells[i], sep));
        }
        w.write("\r\n");
    }

    private static String quote(String s, char sep) {
        if (s == null) return "";
        // Excel'in formül olarak çalıştırmaması için =, + ya da @ ile başlayan metin tek tırnakla korunur
        // (eksi işaretli sayılar sayı kalsın diye - hariç).
        boolean formulaLike = !s.isEmpty() && "=+@".indexOf(s.charAt(0)) >= 0;
        String v = formulaLike ? "'" + s : s;
        if (v.indexOf(sep) >= 0 || v.indexOf('"') >= 0 || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0) {
            return '"' + v.replace("\"", "\"\"") + '"';
        }
        return v;
    }
}
