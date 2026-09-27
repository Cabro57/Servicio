package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.UIScale;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.PeriodNavigator;
import tr.cabro.servicio.application.component.chart.ColumnChart;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.ListTabBar;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.application.utils.Toasts;
import tr.cabro.servicio.documents.PdfDocumentBuilder;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.reports.Report;
import tr.cabro.servicio.reports.Report.Col;
import tr.cabro.servicio.reports.Report.Figure;
import tr.cabro.servicio.reports.Report.Kind;
import tr.cabro.servicio.reports.Report.Table;
import tr.cabro.servicio.reports.ReportExporter;
import tr.cabro.servicio.reports.ReportKind;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Raporlar: seçilen dönem için dört hazır rapor (finans, servis, satış ve stok, tahsilat ve
 * müşteriler). Liste sayfalarıyla aynı dil: başlık ve tek satırlık özet, sağda dönem gezgini ve
 * dışa aktarma; altında rapor sekmeleri. Her raporun gövdesi aynı üç katmandan oluşur: öne çıkan
 * rakamlar şeridi, zaman grafiği ve döküm tabloları.
 * <p>
 * Kullanıcı rapor "kurmaz": dönemi seçer, sekmeyi değiştirir, gerekirse PDF (yazdır) ya da Excel
 * (CSV) olarak alır. Dışa aktarılan belge ekranda görülenle aynı {@link Report} nesnesinden
 * üretilir. Ctrl+←/→ dönemi kaydırır, Ctrl+P PDF açar, Ctrl+E Excel'e aktarır.
 */
@SystemForm(name = "Raporlar", description = "Finans, servis, satış-stok ve tahsilat raporları; PDF ve Excel çıktısı",
        tags = {"rapor", "analiz", "kâr", "ciro", "grafik", "excel", "pdf", "istatistik"})
public class FormReports extends Form {

    /** Son kaydedilen klasör; oturum boyunca hatırlanır. */
    private static File lastDirectory;

    private ReportKind kind = ReportKind.FINANCE;
    private LocalDate from = LocalDate.now().withDayOfMonth(1);
    private LocalDate to = LocalDate.now();

    private ListSummary summary;
    private PeriodNavigator navigator;
    private ViewTabs tabs;
    private JPanel body;
    private JButton pdfButton;
    private JButton csvButton;

    /** Aynı dönemde sekmeler arasında gidip gelirken rapor yeniden sorgulanmasın. */
    private final Map<ReportKind, Report> cache = new EnumMap<>(ReportKind.class);
    private Report current;
    private int request;
    private boolean wide = true;

    public FormReports() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 14 18 16 18, gap 0", "[grow, fill]", "[pref]10[pref]8[grow, fill]"));

        // --- Başlık şeridi ---
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 8 2", "[grow, fill]", "[][]"));
        header.setOpaque(false);
        JLabel title = new JLabel("Raporlar");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        summary = new ListSummary();
        header.add(title);
        header.add(buildActions(), "gapleft push, spany 2, aligny center, wrap");
        header.add(summary, "wmin 0");
        add(header, "wrap");

        // --- Rapor sekmeleri ---
        tabs = new ViewTabs();
        for (ReportKind k : ReportKind.values()) tabs.addView(k.name(), k.title());
        tabs.setOnChange(key -> {
            kind = ReportKind.valueOf(key);
            load(false);
        });
        ListTabBar bar = new ListTabBar(tabs);
        bar.setOpaque(false);
        add(bar, "wrap, growx");

        // --- Gövde ---
        body = new JPanel(new MigLayout("insets 0 0 4 0, fillx, wrap, gap 12", "[grow, fill]", ""));
        body.setOpaque(false);
        add(DetailKit.scroll(body));
        body.add(DetailKit.muted("Yükleniyor…"), "al center, gaptop 60");

        // Geniş ekranda rakamlar tek satır, tablolar iki sütun; dar ekranda yığılır.
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                boolean now = getWidth() >= UIScale.scale(1100);
                if (now != wide) {
                    wide = now;
                    if (current != null) render(current);
                }
            }
        });

        bindKey(KeyEvent.VK_LEFT, "servicio.reports.prev", () -> navigator.shift(-1));
        bindKey(KeyEvent.VK_RIGHT, "servicio.reports.next", () -> navigator.shift(1));
        bindKey(KeyEvent.VK_P, "servicio.reports.pdf", this::openPdf);
        bindKey(KeyEvent.VK_E, "servicio.reports.csv", this::saveCsv);
    }

    private JComponent buildActions() {
        JPanel panel = new JPanel(new MigLayout("insets 0, gap 10", "", "[center]"));
        panel.setOpaque(false);
        navigator = new PeriodNavigator((f, t) -> {
            from = f;
            to = t;
            // Kurucudaki ilk set() çağrısında gövde henüz yok; ilk yükleme formInit'te.
            if (body != null) load(true);
        });
        navigator.set(from, to);
        csvButton = DetailKit.secondaryButton("Excel'e aktar", "icons/file-spreadsheet.svg", this::saveCsv);
        csvButton.setToolTipText("Raporu Excel'de açılan CSV dosyası olarak kaydet (Ctrl+E)");
        pdfButton = DetailKit.primaryButton("PDF / Yazdır", "icons/printer.svg", this::openPdf);
        pdfButton.setToolTipText("Raporu A4 PDF olarak aç; yazdır ya da kaydet (Ctrl+P)");
        panel.add(navigator, "growy");
        panel.add(csvButton, "growy");
        panel.add(pdfButton, "growy");
        return panel;
    }

    private void bindKey(int key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isShowing()) action.run();
            }
        });
    }

    @Override
    public void formInit() {
        load(true);
    }

    @Override
    public void formOpen() {
        // Başka ekranda kayıt girilmiş olabilir; geri dönüşte güncel rakamlar gelsin.
        if (current != null) load(true);
    }

    @Override
    public void formRefresh() {
        load(true);
    }

    // ── Veri ────────────────────────────────────────────────────────────────

    private void load(boolean invalidate) {
        if (invalidate) cache.clear();
        navigator.render();
        Report cached = cache.get(kind);
        if (cached != null) {
            render(cached);
            return;
        }
        final int id = ++request;
        final ReportKind k = kind;
        final LocalDate f = from;
        final LocalDate t = to;
        summary.showLoading();
        setExportEnabled(false);
        ServiceManager.getReportManager().analytics(repo -> k.build(repo, f, t))
                .thenAccept(report -> SwingUtilities.invokeLater(() -> {
                    if (id != request) return;
                    cache.put(k, report);
                    render(report);
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        if (id != request) return;
                        body.removeAll();
                        JPanel error = new JPanel(new MigLayout("insets 40 0 0 0, wrap, al center", "[center]"));
                        error.setOpaque(false);
                        JLabel h = new JLabel("Rapor hazırlanamadı");
                        h.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                        error.add(h);
                        error.add(DetailKit.small("Veritabanı okunurken bir hata oluştu."));
                        error.add(DetailKit.link("Tekrar dene", () -> load(true)));
                        body.add(error);
                        body.revalidate();
                        body.repaint();
                        summary.set(ListSummary.Part.of(k.title()));
                    });
                    return ErrorHandler.handle(this, "Rapor hazırlanamadı", ex);
                });
    }

    private void setExportEnabled(boolean enabled) {
        pdfButton.setEnabled(enabled);
        csvButton.setEnabled(enabled);
    }

    // ── Çizim ───────────────────────────────────────────────────────────────

    private void render(Report report) {
        current = report;
        setExportEnabled(true);
        long days = ChronoUnit.DAYS.between(report.from(), report.to()) + 1;
        // Dönemin tarihi sağdaki gezginde yazıyor; özet yalnızca uzunluğu ve kırılımı söyler.
        summary.set(ListSummary.Part.strong(report.kind().title()),
                ListSummary.Part.of(days == 1 ? "tek gün" : days + " gün"),
                ListSummary.Part.of(report.bucket().adjective() + " kırılım"));

        body.removeAll();
        body.add(DetailKit.small(report.kind().description()), "gapleft 2");
        body.add(new FigureStrip(report.figures(), wide ? 6 : 3));
        body.add(chartCard(report));
        addTables(report.tables());
        body.revalidate();
        body.repaint();
    }

    private JComponent chartCard(Report report) {
        Report.Chart spec = report.chart();
        JPanel card = DetailKit.card(spec.title(), DetailKit.small(capitalize(report.bucket().adjective())));
        ColumnChart chart = new ColumnChart();
        if (spec.money()) {
            chart.setValueFormat(Report::money);
        } else {
            chart.setValueFormat(v -> Kind.COUNT.format(v));
            chart.setAxisFormat(v -> v == Math.rint(v) ? String.valueOf((long) v) : "");
        }
        chart.setEmptyText("Bu dönemde hareket yok");
        List<String> axis = new ArrayList<>();
        List<String> tips = new ArrayList<>();
        for (String key : spec.keys()) {
            axis.add(report.bucket().shortLabel(key, AppLocale.uiLocale()));
            tips.add(report.bucket().longLabel(key, AppLocale.uiLocale()));
        }
        chart.setData(axis, tips, spec.bars(), spec.line(), spec.stacked());
        card.add(chart, "h 220:260:300, wmin 0");
        return card;
    }

    /**
     * Tablolar: sütunu az olan ("dar") iki tablo yan yana, geniş tablo tam satır. Yalnız kalan dar
     * tablo da tam satır olur (yarım genişlikte tek kart boşluk bırakıyordu).
     */
    private void addTables(List<Table> tables) {
        int i = 0;
        while (i < tables.size()) {
            Table t = tables.get(i);
            boolean narrow = t.cols().size() <= 4;
            Table next = i + 1 < tables.size() ? tables.get(i + 1) : null;
            if (wide && narrow && next != null && next.cols().size() <= 4) {
                JPanel pair = new JPanel(new MigLayout("insets 0, fillx, gap 12", "[fill, grow, sg t, 0:pref][fill, grow, sg t, 0:pref]", "[top]"));
                pair.setOpaque(false);
                pair.add(tableCard(t), "wmin 0, growy");
                pair.add(tableCard(next), "wmin 0, growy");
                body.add(pair);
                i += 2;
            } else {
                body.add(tableCard(t));
                i++;
            }
        }
    }

    private JComponent tableCard(Table t) {
        // Not başlığın hemen yanında, soluk: sağa yaslı notlar dar kartta kırpılıyordu.
        JPanel card = new JPanel(new MigLayout("insets 14 16 14 16, fillx, wrap", "[grow, fill]", "[]10[]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JPanel header = new JPanel(new MigLayout("insets 0, gap 10", "[][]", "[baseline]"));
        header.setOpaque(false);
        header.add(DetailKit.title(t.title()));
        if (t.note() != null) {
            JLabel note = DetailKit.small(t.note());
            note.setToolTipText(t.note());
            header.add(note, "wmin 0");
        }
        card.add(header, "wmin 0");
        if (t.rows().isEmpty()) {
            JPanel empty = new JPanel(new MigLayout("insets 14 0 14 0, wrap, al center", "[center]"));
            empty.setOpaque(false);
            JLabel h = new JLabel(t.emptyText());
            h.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            empty.add(h);
            empty.add(DetailKit.small("Başka bir dönem seçmeyi deneyin"));
            card.add(empty);
            return card;
        }
        ReportTable table = new ReportTable(t);
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.add(table.getTableHeader(), BorderLayout.NORTH);
        holder.add(table, BorderLayout.CENTER);
        card.add(holder, "wmin 0");
        return card;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(AppLocale.uiLocale()) + s.substring(1);
    }

    // ── Dışa aktarma ────────────────────────────────────────────────────────

    private void openPdf() {
        Report report = current;
        if (report == null || !pdfButton.isEnabled()) return;
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            User shop = shopOpt.orElse(null);
            try {
                File pdf = ReportExporter.pdf(report, shop, PdfDocumentBuilder.tempFile(ReportExporter.fileBaseName(report)));
                SwingUtilities.invokeLater(() -> {
                    if (!DesktopHelper.openFile(pdf)) {
                        Toasts.show(this, Toast.Type.WARNING, Messages.get("toast.document.created.openFailed", pdf.getAbsolutePath()));
                    }
                });
            } catch (Exception ex) {
                Servicio.getLogger().error("Rapor PDF'i oluşturulamadı", ex);
                SwingUtilities.invokeLater(() -> Toasts.show(this, Toast.Type.ERROR,
                        Messages.get("toast.document.failed", String.valueOf(ex.getMessage()))));
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "İşletme bilgisi okunamadı", ex));
    }

    private void saveCsv() {
        Report report = current;
        if (report == null || !csvButton.isEnabled()) return;
        JFileChooser chooser = new JFileChooser(lastDirectory != null ? lastDirectory
                : FileSystemView.getFileSystemView().getDefaultDirectory());
        chooser.setDialogTitle(report.kind().title() + " — Excel'e aktar");
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setFileFilter(new FileNameExtensionFilter("Excel (CSV) (*.csv)", "csv"));
        chooser.setSelectedFile(new File(ReportExporter.fileBaseName(report) + ".csv"));
        if (chooser.showSaveDialog(SwingUtilities.getWindowAncestor(this)) != JFileChooser.APPROVE_OPTION) return;
        File chosen = chooser.getSelectedFile();
        File file = chosen.getName().toLowerCase().endsWith(".csv") ? chosen : new File(chosen.getParentFile(), chosen.getName() + ".csv");
        lastDirectory = file.getParentFile();
        if (file.exists()) {
            DialogHelper.confirm(this, "confirm.overwrite.title", "confirm.overwrite.message",
                    () -> writeCsv(report, file), file.getName());
        } else {
            writeCsv(report, file);
        }
    }

    private void writeCsv(Report report, File file) {
        CompletableFuture.runAsync(() -> {
            try {
                ReportExporter.csv(report, file);
                SwingUtilities.invokeLater(() -> Toasts.show(this, Toast.Type.SUCCESS,
                        Messages.get("toast.document.saved", file.getAbsolutePath())));
            } catch (Exception ex) {
                Servicio.getLogger().error("Rapor CSV'si yazılamadı", ex);
                SwingUtilities.invokeLater(() -> Toasts.show(this, Toast.Type.ERROR,
                        Messages.get("toast.report.exportFailed", String.valueOf(ex.getMessage()))));
            }
        });
    }

    // ── Rakam şeridi ────────────────────────────────────────────────────────

    /**
     * Öne çıkan rakamlar tek kartta, aralarında ince çizgiler: soluk etiket, kalın değer, soluk
     * açıklama. Değer yalnızca anlam taşıyorsa renklenir (kâr/zarar, iade, açık alacak).
     */
    private static final class FigureStrip extends JPanel {
        private final int columns;
        private final int count;

        FigureStrip(List<Figure> figures, int columns) {
            this.columns = Math.min(columns, figures.size());
            this.count = figures.size();
            setLayout(new GridLayout(0, this.columns, 0, 0));
            putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
            for (Figure f : figures) add(cell(f));
        }

        private static JComponent cell(Figure f) {
            JPanel p = new JPanel(new MigLayout("insets 14 18 14 18, wrap, gap 0 3", "[grow, fill]", ""));
            p.setOpaque(false);
            JLabel label = new JLabel(f.label());
            label.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            JLabel value = new JLabel(f.value());
            value.putClientProperty(FlatClientProperties.STYLE, "font: bold +5; foreground: " + toneKey(f.tone()));
            JLabel hint = new JLabel(f.hint() != null ? f.hint() : " ");
            hint.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
            hint.setToolTipText(f.hint());
            p.add(label, "wmin 0");
            p.add(value, "wmin 0");
            p.add(hint, "wmin 0");
            return p;
        }

        private static String toneKey(Report.Tone tone) {
            switch (tone) {
                case SUCCESS: return "$Servicio.successColor";
                case DANGER: return "$Servicio.dangerColor";
                case WARNING: return "$Servicio.warningColor";
                case MUTED: return "$Label.disabledForeground";
                default: return "$Label.foreground";
            }
        }

        @Override
        protected void paintChildren(Graphics g) {
            super.paintChildren(g);
            // Hücreler arası ince çizgiler: dikey ayraçlar ve (iki satırsa) yatay ayraç.
            Graphics2D g2 = (Graphics2D) g.create();
            Color line = UIManager.getColor("Component.borderColor");
            g2.setColor(line != null ? line : Color.LIGHT_GRAY);
            int rows = (count + columns - 1) / columns;
            int inset = UIScale.scale(14);
            for (int i = 0; i < getComponentCount(); i++) {
                Component c = getComponent(i);
                int col = i % columns;
                if (col > 0) g2.fillRect(c.getX(), c.getY() + inset, UIScale.scale(1), c.getHeight() - inset * 2);
                if (rows > 1 && i >= columns) g2.fillRect(c.getX() + (col == 0 ? inset : 0), c.getY(),
                        c.getWidth() - (col == 0 ? inset : 0) - (col == columns - 1 ? inset : 0), UIScale.scale(1));
            }
            g2.dispose();
        }
    }

    // ── Döküm tablosu ───────────────────────────────────────────────────────

    /**
     * Kaydırmasız, tüm satırları gösteren tablo. Toplam satırı en altta, üstünde koyu bir çizgiyle.
     * Müşteri tablolarında satır tıklanınca müşteri sayfası açılır.
     */
    private static final class ReportTable extends JTable {
        private final Table spec;
        private final int totalsRow;
        private int hoverRow = -1;

        ReportTable(Table spec) {
            this.spec = spec;
            List<Object[]> rows = new ArrayList<>(spec.rows());
            if (spec.totals() != null) rows.add(spec.totals());
            this.totalsRow = spec.totals() != null ? rows.size() - 1 : -1;
            setModel(new AbstractTableModel() {
                @Override public int getRowCount() { return rows.size(); }
                @Override public int getColumnCount() { return spec.cols().size(); }
                @Override public String getColumnName(int c) { return spec.cols().get(c).name(); }
                @Override public Object getValueAt(int r, int c) { return rows.get(r)[c]; }
            });

            getTableHeader().setReorderingAllowed(false);
            getTableHeader().setResizingAllowed(false);
            getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                    "height:32; hoverBackground:null; pressedBackground:null; separatorColor:$Table.background;"
                            + " bottomSeparatorColor:$Component.borderColor; background:$Table.background;"
                            + " foreground:$Label.disabledForeground; font:-1");
            putClientProperty(FlatClientProperties.STYLE,
                    "rowHeight:34; showHorizontalLines:true; showVerticalLines:false; intercellSpacing:0,1;"
                            + " gridColor:$Component.borderColor; cellFocusColor:null;"
                            + " selectionBackground:$Table.background; selectionForeground:$Table.foreground;"
                            + " selectionInactiveBackground:$Table.background; selectionInactiveForeground:$Table.foreground");
            setRowSelectionAllowed(false);
            setFocusable(false);
            setFillsViewportHeight(false);

            CellRenderer renderer = new CellRenderer();
            for (int c = 0; c < spec.cols().size(); c++) {
                TableColumn column = getColumnModel().getColumn(c);
                column.setCellRenderer(renderer);
                column.setHeaderRenderer(new HeaderRenderer(spec.cols().get(c).kind().numeric(), getTableHeader().getDefaultRenderer()));
                Kind kind = spec.cols().get(c).kind();
                int weight = c == 0 ? 260 : kind == Kind.COUNT || kind == Kind.PERCENT ? 80 : kind == Kind.TEXT ? 150 : 130;
                column.setPreferredWidth(UIScale.scale(weight));
            }

            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int r = rowAtPoint(e.getPoint());
                    if (r == totalsRow) r = -1;
                    if (r != hoverRow) {
                        hoverRow = r;
                        setCursor(r >= 0 && canOpen(r) ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hoverRow = -1;
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int r = rowAtPoint(e.getPoint());
                    if (r >= 0 && canOpen(r) && SwingUtilities.isLeftMouseButton(e)) openCustomer(spec.customerIds()[r]);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        private boolean canOpen(int row) {
            return spec.customerIds() != null && row < spec.customerIds().length;
        }

        @Override
        public String getToolTipText(MouseEvent e) {
            int r = rowAtPoint(e.getPoint());
            return r >= 0 && canOpen(r) ? "Müşteri sayfasını aç" : super.getToolTipText(e);
        }

        private static void openCustomer(long id) {
            ServiceManager.getCustomerService().get(id).thenAccept(opt -> SwingUtilities.invokeLater(() ->
                    opt.ifPresent(c -> FormManager.showForm(new FormCustomer(c)))));
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        /** Hücre: sütun türüne göre hizalama, biçim ve anlam rengi; toplam satırı kalın. */
        private final class CellRenderer extends DefaultTableCellRenderer {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                           boolean hasFocus, int row, int column) {
                super.getTableCellRendererComponent(table, value, false, false, row, column);
                Col col = spec.cols().get(column);
                Kind kind = col.kind();
                boolean total = row == totalsRow;
                Font base = table.getFont();

                setText(value == null ? (column == 0 || total ? "" : "—") : kind.format(value));
                setHorizontalAlignment(kind.numeric() ? SwingConstants.TRAILING : SwingConstants.LEADING);

                boolean money = kind == Kind.MONEY || kind == Kind.MONEY_SIGN || kind == Kind.MONEY_NEGATIVE || kind == Kind.MONEY_OWED;
                boolean bold = total || column == 0 || money;
                setFont(bold ? base.deriveFont(Font.BOLD) : base);

                Color fg = table.getForeground();
                Color muted = UIManager.getColor("Label.disabledForeground");
                double n = value instanceof Number num ? num.doubleValue() : 0;
                if (value == null) {
                    fg = muted;
                } else if (kind == Kind.MONEY_SIGN) {
                    if (n > 0) fg = UIManager.getColor("Servicio.successColor");
                    else if (n < 0) fg = UIManager.getColor("Servicio.dangerColor");
                } else if (kind == Kind.MONEY_NEGATIVE) {
                    if (n < 0) fg = UIManager.getColor("Servicio.dangerColor");
                } else if (kind == Kind.MONEY_OWED) {
                    fg = n > 0 ? UIManager.getColor("Servicio.warningColor") : muted;
                } else if ((kind == Kind.COUNT || kind == Kind.MONEY) && n == 0) {
                    fg = muted;
                } else if (kind == Kind.DAYS || kind == Kind.DATE || kind == Kind.PERCENT) {
                    fg = total ? fg : UIManager.getColor("Label.foreground");
                }
                setForeground(fg);

                Color bg = row == hoverRow && canOpen(row) ? UIManager.getColor("Servicio.rowHoverBackground") : table.getBackground();
                setBackground(bg);
                setOpaque(true);

                int left = column == 0 ? 4 : 10;
                int right = column == table.getColumnCount() - 1 ? 4 : 10;
                Border pad = BorderFactory.createEmptyBorder(0, UIScale.scale(left), 0, UIScale.scale(right));
                if (total) {
                    Color line = UIManager.getColor("Label.disabledForeground");
                    pad = BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, line), pad);
                }
                setBorder(pad);
                if (column == 0 && value != null) setToolTipText(value.toString());
                else setToolTipText(null);
                return this;
            }
        }
    }

    /** Başlık hücresi: sayı sütunlarında sağa hizalı, soldaki/sağdaki boşluk hücrelerle aynı. */
    private static final class HeaderRenderer implements javax.swing.table.TableCellRenderer {
        private final boolean right;
        private final javax.swing.table.TableCellRenderer delegate;

        HeaderRenderer(boolean right, javax.swing.table.TableCellRenderer delegate) {
            this.right = right;
            this.delegate = delegate;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = delegate.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (c instanceof JLabel l) {
                l.setHorizontalAlignment(right ? SwingConstants.TRAILING : SwingConstants.LEADING);
                int left = column == 0 ? 4 : 10;
                int rightPad = column == table.getColumnCount() - 1 ? 4 : 10;
                l.setBorder(BorderFactory.createEmptyBorder(0, UIScale.scale(left), 0, UIScale.scale(rightPad)));
            }
            return c;
        }
    }
}
