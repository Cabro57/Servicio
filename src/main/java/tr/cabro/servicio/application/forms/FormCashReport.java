package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.component.PeriodNavigator;
import tr.cabro.servicio.application.component.table.ListSummary;
import tr.cabro.servicio.application.component.table.ListTable;
import tr.cabro.servicio.application.component.table.TableColumnConfigurator;
import tr.cabro.servicio.application.component.table.TableStatePanel;
import tr.cabro.servicio.application.component.table.TableStyler;
import tr.cabro.servicio.application.component.table.ViewTabs;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.renderer.StyledLabelCellRenderer;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Günlük kasa raporu. Liste sayfalarıyla aynı dil: başlık altında günün özeti (net kasa, satış ve
 * iade adedi), altta günün her ödeme hareketi — saat, kim, ne için, yöntem, tutar. Sekmeler yönteme
 * göre süzer ve kendi toplamını gösterir; alt satırda yöntem kırılımı ve net toplam durur.
 * Günler arasında ‹ › ile gezilir. Tutarlar {@code payments} tablosundan gelir, iadeler eksidir.
 */
@SystemForm(name = "Kasa Raporu", description = "Günün tahsilat, satış ve iade hareketleri; yöntem kırılımı")
public class FormCashReport extends Form {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    /** Ana sayfadaki tarih satırıyla aynı biçim (arayüz diline göre). */
    private static DateTimeFormatter longDate() {
        return DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.FULL).withLocale(AppLocale.uiLocale());
    }
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("d MMM HH:mm", AppLocale.uiLocale());
    private static final String VIEW_ALL = "all";

    private final SaleService saleService;
    private final PaymentService paymentService;

    private LocalDate from = LocalDate.now();
    private LocalDate to = LocalDate.now();
    private JLabel title;
    private ListSummary summary;
    private ViewTabs views;
    private ListTable table;
    private GenericTableModel<Payment> tableModel;
    private JPanel tableArea;
    private TableStatePanel statePanel;
    private JLabel breakdownLabel;
    private JLabel netLabel;
    private PeriodNavigator navigator;

    private List<Payment> dayPayments = Collections.emptyList();
    private Map<Long, Customer> customers = Collections.emptyMap();

    public FormCashReport() {
        this.saleService = ServiceManager.getSaleService();
        this.paymentService = ServiceManager.getPaymentService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 14 18 16 18, gap 0", "[grow, fill]", "[pref]12[grow, fill]"));

        // --- Başlık şeridi ---
        JPanel header = new JPanel(new MigLayout("insets 0, fillx, gap 8 2", "[grow, fill]", "[][]"));
        header.setOpaque(false);
        title = new JLabel("Kasa Raporu");
        title.putClientProperty(FlatClientProperties.STYLE, "font: bold +5");
        summary = new ListSummary();

        header.add(title);
        header.add(buildDayNavigator(), "gapleft push, spany 2, aligny center, wrap");
        header.add(summary, "wmin 0");
        add(header, "wrap");

        // --- Hareket kartı ---
        JPanel card = new JPanel(new MigLayout("fill, insets 0 8 8 8, gap 0", "[grow, fill]", "[pref]4[grow, fill]6[pref]"));
        card.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");

        views = new ViewTabs();
        views.addView(VIEW_ALL, "Tüm hareketler");
        for (PaymentType type : PaymentType.values()) views.addView(type.name(), type.getDisplayName());
        views.setOnChange(key -> applyView());
        card.add(new tr.cabro.servicio.application.component.table.ListTabBar(views), "wrap, growx");

        List<ColumnDef<Payment>> columns = Arrays.asList(
                new ColumnDef<Payment>("Zaman", String.class, p -> p.getPaymentDate() == null ? ""
                        : p.getPaymentDate().format(isSingleDay() ? TIME : DATE_TIME))
                        .alignment(SwingConstants.LEADING),
                new ColumnDef<Payment>("Hareket", Payment.class, p -> p).alignment(SwingConstants.LEADING),
                ColumnDef.<Payment>badge("Yöntem", PaymentType.class, Payment::getPaymentType),
                new ColumnDef<Payment>("Tutar", BigDecimal.class, Payment::getAmount).alignment(SwingConstants.TRAILING)
        );
        tableModel = new GenericTableModel<>(columns);
        table = new ListTable();
        TableStyler.applyStandardStyle(table);
        table.setModel(tableModel);
        TableColumnConfigurator.applyColumnRenderers(table, columns);
        table.getColumnModel().getColumn(0).setCellRenderer(
                StyledLabelCellRenderer.of(SwingConstants.LEADING, "foreground: $Label.disabledForeground", 12));
        table.getColumnModel().getColumn(1).setCellRenderer(new MultiLineTableCellRenderer<Payment>(
                this::who, this::what));
        table.getColumnModel().getColumn(3).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEGATIVE));
        table.getColumnModel().getColumn(0).setMaxWidth(120);
        table.getColumnModel().getColumn(0).setPreferredWidth(100);
        table.getColumnModel().getColumn(1).setPreferredWidth(520);
        table.getColumnModel().getColumn(2).setPreferredWidth(170);
        table.getColumnModel().getColumn(3).setPreferredWidth(150);
        table.setRowOpener(row -> open(tableModel.getItemAt(table.convertRowIndexToModel(row))), -1);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        statePanel = new TableStatePanel();
        tableArea = new JPanel(new CardLayout());
        tableArea.setOpaque(false);
        tableArea.add(scroll, "data");
        tableArea.add(statePanel, "state");
        card.add(tableArea, "wrap");

        JPanel footer = new JPanel(new MigLayout("insets 0, fillx, gap 8", "[]push[]", "[center]"));
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        breakdownLabel = new JLabel(" ");
        breakdownLabel.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        netLabel = new JLabel(" ");
        footer.add(breakdownLabel, "wmin 0");
        footer.add(netLabel);
        card.add(footer);

        add(card);
        refresh();
    }

    /** Dönem (gün ya da tarih aralığı) gezgini + "Tahsilat Al". Gelecek güne geçilmez; Ctrl+←/→ ile dönem kayar. */
    private JComponent buildDayNavigator() {
        JPanel panel = new JPanel(new MigLayout("insets 0, gap 10", "", "[center]"));
        panel.setOpaque(false);

        navigator = new PeriodNavigator((f, t) -> { from = f; to = t; refresh(); });

        JButton collect = tr.cabro.servicio.application.component.detail.DetailKit.primaryButton(
                "Tahsilat Al", "icons/hand-coins.svg", null, QuickAction.COLLECT);
        collect.setToolTipText("Cari hesaptan müşteri seçip ödeme al (" + QuickAction.COLLECT.getShortcutText() + ")");
        collect.addActionListener(e -> QuickAction.COLLECT.run());

        panel.add(navigator, "growy");
        panel.add(collect, "growy");

        bindDayKey(KeyEvent.VK_LEFT, "servicio.cash.prevDay", () -> navigator.shift(-1));
        bindDayKey(KeyEvent.VK_RIGHT, "servicio.cash.nextDay", () -> navigator.shift(1));
        return panel;
    }

    private void bindDayKey(int key, String name, Runnable action) {
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK), name);
        getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (isShowing()) action.run();
            }
        });
    }

    private boolean isSingleDay() {
        return from.equals(to);
    }

    /** Özet cümlesinin tarih parçası: tek gün "Bugün, 24 Eylül 2026", aralık "1 Eyl – 24 Eyl 2026". */
    private String periodText(LocalDate f, LocalDate t) {
        if (f.equals(t)) return f.equals(LocalDate.now()) ? "Bugün, " + f.format(longDate()) : f.format(longDate());
        DateTimeFormatter d = DateTimeFormatter.ofPattern("d MMM yyyy", AppLocale.uiLocale());
        return f.format(d) + " – " + t.format(d);
    }

    private void refresh() {
        if (navigator != null) navigator.render();
        summary.showLoading();
        statePanel.showLoading();
        ((CardLayout) tableArea.getLayout()).show(tableArea, "state");
        final LocalDate reqFrom = from;
        final LocalDate reqTo = to;

        saleService.getCashReport(reqFrom, reqTo).thenCombine(paymentService.getMovements(reqFrom, reqTo), (report, payments) -> {
            List<Long> ids = payments.stream().map(Payment::getCustomerId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
            Map<Long, Customer> map = ids.isEmpty() ? Collections.emptyMap()
                    : ServiceManager.getCustomerService().getAll(ids).join().stream().collect(Collectors.toMap(Customer::getId, c -> c));
            SwingUtilities.invokeLater(() -> {
                if (!reqFrom.equals(from) || !reqTo.equals(to)) return;
                customers = map;
                dayPayments = payments;
                String dateText = periodText(reqFrom, reqTo);
                BigDecimal total = report.getTotal() != null ? report.getTotal() : BigDecimal.ZERO;
                summary.set(
                        ListSummary.Part.of(dateText),
                        ListSummary.Part.meaning("net kasa " + Format.formatPrice(total),
                                total.signum() < 0 ? "Servicio.dangerColor" : total.signum() > 0 ? "Servicio.successColor" : "Label.foreground"),
                        ListSummary.Part.of(report.getSaleCount() + " satış"),
                        report.getReturnCount() > 0 ? ListSummary.Part.meaning(report.getReturnCount() + " iade", "Servicio.dangerColor") : null,
                        ListSummary.Part.of(payments.size() + " hareket"));
                updateCounts();
                applyView();
            });
            return null;
        }).exceptionally(ex -> ErrorHandler.handle(this, "Kasa raporu yüklenemedi", ex));
    }

    private void updateCounts() {
        views.setCount(VIEW_ALL, (long) dayPayments.size());
        for (PaymentType type : PaymentType.values()) {
            views.setCount(type.name(), dayPayments.stream().filter(p -> p.getPaymentType() == type).count());
        }
    }

    /** Seçili yönteme göre satırları, alttaki kırılımı ve toplamı doldurur (istemci tarafı süzgeç). */
    private void applyView() {
        String key = views.getSelected();
        List<Payment> rows = VIEW_ALL.equals(key) ? dayPayments
                : dayPayments.stream().filter(p -> p.getPaymentType() != null && p.getPaymentType().name().equals(key))
                .collect(Collectors.toList());
        tableModel.setData(new ArrayList<>(rows));

        Map<PaymentType, BigDecimal> byType = new EnumMap<>(PaymentType.class);
        for (Payment p : dayPayments) {
            PaymentType type = p.getPaymentType() != null ? p.getPaymentType() : PaymentType.OTHER;
            byType.merge(type, p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }
        breakdownLabel.setText(byType.isEmpty() ? " " : byType.entrySet().stream()
                .map(e -> e.getKey().getDisplayName() + " " + Format.formatPrice(e.getValue()))
                .collect(Collectors.joining("   ·   ")));

        BigDecimal shown = rows.stream().map(p -> p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        netLabel.setText((VIEW_ALL.equals(key) ? "Net kasa  " : "Bu yöntemde  ") + Format.formatPrice(shown));
        netLabel.putClientProperty(FlatClientProperties.STYLE, "font: bold +1; foreground: "
                + (shown.signum() < 0 ? "$Servicio.dangerColor" : "$Label.foreground"));

        if (rows.isEmpty()) {
            if (dayPayments.isEmpty()) {
                statePanel.showMessage("icons/banknote.svg", isSingleDay() ? (from.equals(LocalDate.now()) ? "Bugün henüz hareket yok" : "Bu gün hareket yok") : "Bu dönemde hareket yok",
                        QuickAction.QUICK_SALE.getShortcutText() + " ile satış, " + QuickAction.COLLECT.getShortcutText()
                                + " ile tahsilat kaydettiğinizde burada görünür.");
            } else {
                statePanel.showMessage("icons/banknote.svg", "Bu yöntemle hareket yok", "Başka bir sekme seçin.");
            }
            ((CardLayout) tableArea.getLayout()).show(tableArea, "state");
        } else {
            ((CardLayout) tableArea.getLayout()).show(tableArea, "data");
        }
    }

    // --- Satır metinleri ---

    private String who(Payment p) {
        Customer c = p.getCustomerId() != null ? customers.get(p.getCustomerId()) : null;
        if (c != null) return c.getFullName();
        return p.getCustomerId() != null ? "Müşteri #" + p.getCustomerId() : "Perakende satış";
    }

    /** Ödemenin neye ait olduğu: "SRV-12 tahsilatı", "SAT-5", "İADE-3", birden çok belge, not. */
    private String what(Payment p) {
        List<String> docs = new ArrayList<>();
        for (PaymentAllocation a : p.getAllocations()) {
            if (a.getTargetType() == AllocationTargetType.WORK_ORDER) docs.add("SRV-" + a.getTargetId());
            else if (a.getTargetType() == AllocationTargetType.SALE) docs.add((a.getAmount() != null && a.getAmount().signum() < 0 ? "İADE · SAT-" : "SAT-") + a.getTargetId());
        }
        String kind;
        boolean negative = p.getAmount() != null && p.getAmount().signum() < 0;
        if (docs.isEmpty()) kind = negative ? "Müşteriye ödeme" : "Cari tahsilat (belgeye bağlanmamış)";
        else kind = String.join(", ", docs) + (negative ? " · iade ödemesi" : "");
        if (p.getNote() != null && !p.getNote().isBlank()) kind += "  ·  " + p.getNote();
        return kind;
    }

    /** Satır: tek belgeye bağlıysa o belgeyi, değilse müşteri kartını açar. */
    private void open(Payment p) {
        if (p == null) return;
        List<PaymentAllocation> allocs = p.getAllocations();
        if (allocs.size() == 1) {
            PaymentAllocation a = allocs.get(0);
            if (a.getTargetType() == AllocationTargetType.WORK_ORDER) {
                ServiceManager.getWorkOrderService().get(a.getTargetId()).thenAccept(opt -> SwingUtilities.invokeLater(() ->
                        opt.ifPresent(wo -> FormManager.showForm(new FormWorkOrder(wo)))));
                return;
            }
            if (a.getTargetType() == AllocationTargetType.SALE) {
                saleService.getById(a.getTargetId()).thenAccept(opt -> SwingUtilities.invokeLater(() ->
                        opt.ifPresent(s -> FormManager.showForm(new FormSale(s)))));
                return;
            }
        }
        Customer c = p.getCustomerId() != null ? customers.get(p.getCustomerId()) : null;
        if (c != null) FormManager.showForm(new FormCustomer(c));
    }

    @Override
    public void formOpen() {
        refresh();
    }

    @Override
    public void formRefresh() {
        refresh();
    }
}
