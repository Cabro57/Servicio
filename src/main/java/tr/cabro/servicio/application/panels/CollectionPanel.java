package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.CustomerSelectBox;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.NewCustomerModal;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.PaymentAllocation;
import tr.cabro.servicio.model.dto.OpenDocumentDto;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.awt.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tahsilat penceresi.
 * <pre>
 *  Müşteri  [ Barış Çetin  +90 551…     x ]                         Borç 8.479,40 ₺
 *  Tutar    [      8.479,40 ₺ ]  [Tamamı] [Yarısı]
 *  Yöntem   [Nakit][Kart][Havale][Diğer]
 *  Açık belgeler                                              [En eskiden dağıt]
 *   SAT-2  · 03 Ağu 2026       kalan 4.216,20 ₺      → 4.216,20 ₺  kapanır
 *   SRV-5  · 19 Ağu 2026       kalan 2.200,00 ₺      → 1.000,00 ₺  kısmi
 *  Belgelere 5.216,20 ₺ · avans 0,00 ₺ · sonra kalan borç 3.263,20 ₺   [8.479,40 ₺ Tahsil Et]
 * </pre>
 * Tutar, müşterinin açık belgelerine en eskiden başlayarak dağıtılır; bir belgeye tıklayıp tutarı
 * elle değiştirmek o belgeyi SABİTLER, kalan tutar diğerlerine dağıtılmaya devam eder. Dağıtılmayan
 * fark {@link PaymentService#recordCollection} tarafından avans olarak bırakılır.
 * Müşteri verilmeden açılırsa (Alt+T) önce müşteri seçilir.
 */
public class CollectionPanel extends JPanel {

    private static class Row {
        final OpenDocumentDto doc;
        BigDecimal allocated = BigDecimal.ZERO;
        boolean manual = false;

        Row(OpenDocumentDto doc) {
            this.doc = doc;
        }
    }

    private Customer customer;
    private final String modalId;
    private final Runnable onSuccess;
    private final PaymentService paymentService;

    private final List<Row> rows = new ArrayList<>();
    private BigDecimal balance = BigDecimal.ZERO;

    private CustomerSelectBox customerBox;
    private JLabel lblBalanceCaption, lblBalance;
    private CurrencyField amountField;
    private JButton btnFull, btnHalf;
    private SegmentedButtons<PaymentType> methods;
    private JPanel docList;
    private JLabel lblSummary;
    private JButton btnSave;

    /** Müşteri kartından, iş emrinden, cari listesinden ve Alt+T'den çağrılan ortak açılış noktası. */
    public static void open(Component parent, Customer customer, Runnable onSuccess) {
        String modalId = "Collection-" + (customer != null ? customer.getId() : "pick");
        CollectionPanel panel = new CollectionPanel(customer, modalId, onSuccess);
        SimpleModalBorder.Option[] options = {new SimpleModalBorder.Option("Kapat", SimpleModalBorder.CANCEL_OPTION)};
        AppModal.showModal(parent, new SimpleModalBorder(panel, "Tahsilat Al", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) panel.focusFirst();
        }), modalId);
    }

    public CollectionPanel(Customer customer, String modalId, Runnable onSuccess) {
        this.customer = customer;
        this.modalId = modalId;
        this.onSuccess = onSuccess;
        this.paymentService = ServiceManager.getPaymentService();
        init();
        loadCustomers();
        if (customer != null) customerBox.setSelectedItem(customer);
        else showNoCustomer();
    }

    private void init() {
        setLayout(new MigLayout("fillx, insets 4 20 16 20, gap 12 10, hidemode 3", "[pref!][grow, fill]", "[][]10[]16[]6[grow, fill]14[]"));
        setPreferredSize(new Dimension(660, 560));

        // --- Müşteri + borç ---
        customerBox = new CustomerSelectBox(e -> NewCustomerModal.push(modalId, c -> customerBox.appendCustomer(c)));
        customerBox.setOnSelectionChanged(c -> {
            if (c == null) {
                customer = null;
                showNoCustomer();
            } else if (customer == null || !c.getId().equals(customer.getId()) || rows.isEmpty()) {
                customer = c;
                loadOpenDocuments();
            }
        });
        customerBox.setAfterUserChoice(this::focusAmount);

        lblBalanceCaption = new JLabel("Borç");
        lblBalanceCaption.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        lblBalance = new JLabel("—");
        lblBalance.putClientProperty(FlatClientProperties.STYLE, "font: bold +4");
        JPanel balanceBox = new JPanel(new MigLayout("insets 0, gap 0, wrap", "[right]", "[]0[]"));
        balanceBox.setOpaque(false);
        balanceBox.add(lblBalanceCaption);
        balanceBox.add(lblBalance);

        add(caption("Müşteri"));
        JPanel customerRow = new JPanel(new MigLayout("insets 0, fillx, gap 16", "[grow, fill][]", "[center]"));
        customerRow.setOpaque(false);
        customerRow.add(customerBox, "wmin 0");
        customerRow.add(balanceBox);
        add(customerRow, "wrap");

        // --- Tutar ---
        amountField = new CurrencyField();
        amountField.setAvailableCurrencies(List.of("TRY"));
        amountField.putClientProperty(FlatClientProperties.STYLE, "font: bold +6; margin: 6,10,6,10; arc: 10");
        amountField.addPropertyChangeListener("value", e -> redistribute());
        btnFull = chip("Tamamı", () -> setAmount(balance));
        btnHalf = chip("Yarısı", () -> setAmount(balance.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP)));
        add(caption("Tutar"), "aligny center");
        JPanel amountRow = new JPanel(new MigLayout("insets 0, gap 8", "[240!][][]", "[center]"));
        amountRow.setOpaque(false);
        amountRow.add(amountField, "growx");
        amountRow.add(btnFull);
        amountRow.add(btnHalf);
        add(amountRow, "wrap");

        // --- Yöntem ---
        methods = new SegmentedButtons<>();
        for (PaymentType type : PaymentType.values()) methods.add(type, type.getDisplayName(), type.getIconPath());
        methods.setSelected(PaymentType.CASH);
        add(caption("Yöntem"), "aligny center");
        add(methods, "growx 0, wrap");

        // --- Açık belgeler ---
        JLabel docsTitle = new JLabel("Açık belgeler");
        docsTitle.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");
        JButton btnFifo = new JButton("En eskiden dağıt");
        btnFifo.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnFifo.putClientProperty(FlatClientProperties.STYLE, "foreground: $Component.accentColor; margin: 2,6,2,6");
        btnFifo.setToolTipText("Elle yapılan değişiklikleri sıfırlar, en eski belgeden başlayarak yeniden dağıtır.");
        btnFifo.addActionListener(e -> {
            for (Row row : rows) row.manual = false;
            redistribute();
        });
        JPanel docsHeader = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "[center]"));
        docsHeader.setOpaque(false);
        docsHeader.add(docsTitle);
        docsHeader.add(btnFifo);
        add(docsHeader, "span 2, growx, wrap");

        docList = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 0", "[grow, fill]", ""));
        docList.setOpaque(false);
        JScrollPane scroll = new JScrollPane(docList);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(false);
        scroll.setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        JPanel docsCard = new JPanel(new MigLayout("insets 4 6 4 6, fill", "[grow, fill]", "[grow, fill]"));
        docsCard.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        docsCard.add(scroll);
        add(docsCard, "span 2, grow, hmin 120, wrap");

        // --- Özet + kaydet ---
        lblSummary = new JLabel(" ");
        lblSummary.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        btnSave = new JButton("Tahsil Et", new Ikon("icons/hand-coins.svg", 16, "Servicio.onAccentForeground"));
        btnSave.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 8,16,8,16; iconTextGap: 8; font: bold;"
                + " borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                + " background: $Component.accentColor; foreground: $Servicio.onAccentForeground;"
                + " hoverBackground: darken($Component.accentColor,6%); pressedBackground: darken($Component.accentColor,12%)");
        btnSave.addActionListener(e -> save());
        JPanel footer = new JPanel(new MigLayout("insets 0, fillx, gap 12", "[grow][]", "[center]"));
        footer.setOpaque(false);
        footer.add(lblSummary, "wmin 0");
        footer.add(btnSave);
        add(footer, "span 2, growx");

        // Ctrl+Enter kaydeder (kayıt formlarıyla aynı kısayol).
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("ctrl ENTER"), "save");
        getActionMap().put("save", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (btnSave.isEnabled()) save();
            }
        });
    }

    private static JLabel caption(String text) {
        JLabel l = new JLabel(text);
        l.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        return l;
    }

    private static JButton chip(String text, Runnable action) {
        JButton b = new JButton(text);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 999; margin: 4,12,4,12");
        b.addActionListener(e -> action.run());
        return b;
    }

    private void focusFirst() {
        if (customer == null) customerBox.requestFocusInWindow();
        else focusAmount();
    }

    private void focusAmount() {
        SwingUtilities.invokeLater(() -> {
            amountField.requestFocusInWindow();
            amountField.selectAll();
        });
    }

    private void setAmount(BigDecimal value) {
        amountField.setValue(value.max(BigDecimal.ZERO));
        focusAmount();
    }

    // -------------------------------------------------------------------------
    // Veri
    // -------------------------------------------------------------------------

    private void loadCustomers() {
        ServiceManager.getCustomerService().getAll().thenAccept(list -> SwingUtilities.invokeLater(() -> customerBox.setCustomers(list)))
                .exceptionally(ex -> ErrorHandler.handle(this, "Müşteriler yüklenemedi", ex));
    }

    private void showNoCustomer() {
        rows.clear();
        balance = BigDecimal.ZERO;
        lblBalance.setText("—");
        lblBalance.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: $Label.disabledForeground");
        btnFull.setEnabled(false);
        btnHalf.setEnabled(false);
        docList.removeAll();
        docList.add(emptyRow("Önce müşteri seçin", "Ad, telefon ya da TC ile arayın; açık belgeleri burada listelenir."));
        docList.revalidate();
        docList.repaint();
        updateSummary(BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private void loadOpenDocuments() {
        if (customer == null) return;
        Long id = customer.getId();
        paymentService.getOpenDocuments(id).thenCombine(paymentService.getCustomerBalance(id), (docs, bal) -> {
            SwingUtilities.invokeLater(() -> {
                if (customer == null || !id.equals(customer.getId())) return;
                rows.clear();
                for (OpenDocumentDto doc : docs) rows.add(new Row(doc));
                balance = bal.map(b -> b.getBalance()).orElse(BigDecimal.ZERO);
                applyBalance();
                // Açılışta tutar borcun tamamıyla dolu gelir; en sık yapılan iş tam tahsilat.
                if (toBigDecimal(amountField.getValue()).signum() == 0 && balance.signum() > 0) amountField.setValue(balance);
                else redistribute();
            });
            return null;
        }).exceptionally(ex -> ErrorHandler.handle(this, "Açık belgeler yüklenemedi", ex));
    }

    private void applyBalance() {
        boolean owes = balance.signum() > 0;
        lblBalanceCaption.setText(balance.signum() < 0 ? "Alacaklı" : "Borç");
        lblBalance.setText(Format.formatPrice(balance.abs()));
        lblBalance.putClientProperty(FlatClientProperties.STYLE, "font: bold +4; foreground: "
                + (owes ? "$Servicio.warningColor" : balance.signum() < 0 ? "$Servicio.dangerColor" : "$Label.disabledForeground"));
        btnFull.setEnabled(owes);
        btnHalf.setEnabled(owes);
        btnFull.setText(owes ? "Tamamı " + Format.formatPrice(balance) : "Tamamı");
    }

    // -------------------------------------------------------------------------
    // Dağıtım
    // -------------------------------------------------------------------------

    private void redistribute() {
        BigDecimal amount = toBigDecimal(amountField.getValue());
        BigDecimal remaining = amount;

        for (Row row : rows) {
            if (row.manual) {
                remaining = remaining.subtract(row.allocated);
                continue;
            }
            BigDecimal available = remaining.max(BigDecimal.ZERO);
            BigDecimal alloc = row.doc.getRemainingAmount().min(available);
            row.allocated = alloc.max(BigDecimal.ZERO);
            remaining = remaining.subtract(row.allocated);
        }
        renderRows();
        BigDecimal allocated = rows.stream().map(r -> r.allocated).reduce(BigDecimal.ZERO, BigDecimal::add);
        updateSummary(amount, allocated);
    }

    private void renderRows() {
        if (customer == null) return;
        docList.removeAll();
        if (rows.isEmpty()) {
            docList.add(emptyRow("Açık belge yok",
                    "Alınan tutar müşterinin hesabına avans olarak yazılır, sonraki belgeden düşülür."));
        }
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) docList.add(new JSeparator(), "growx");
            docList.add(docRow(rows.get(i)));
        }
        docList.revalidate();
        docList.repaint();
    }

    private JComponent docRow(Row row) {
        JButton b = new JButton();
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,10,6,10");
        b.setLayout(new MigLayout("insets 0, fillx, gap 12 0", "[grow][right][110!, right][70!, right]", "[]0[]"));
        b.setToolTipText("Bu belgeye düşülecek tutarı elle değiştirmek için tıklayın");
        b.getAccessibleContext().setAccessibleName(row.doc.getDocumentLabel() + ", kalan " + Format.formatPrice(row.doc.getRemainingAmount()));

        JLabel label = new JLabel(row.doc.getDocumentLabel());
        label.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        JLabel date = new JLabel(row.doc.getDocumentDate() != null ? row.doc.getDocumentDate().format(DateFormats.shortDate()) : "");
        date.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        JLabel remaining = new JLabel(Format.formatPrice(row.doc.getRemainingAmount()));
        JLabel remainingCap = new JLabel("kalan");
        remainingCap.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");

        boolean closes = row.allocated.compareTo(row.doc.getRemainingAmount()) >= 0 && row.allocated.signum() > 0;
        boolean partial = row.allocated.signum() > 0 && !closes;
        JLabel alloc = new JLabel(row.allocated.signum() > 0 ? Format.formatPrice(row.allocated) : "—");
        alloc.putClientProperty(FlatClientProperties.STYLE, row.allocated.signum() > 0
                ? "font: bold; foreground: $Servicio.successColor" : "foreground: $Label.disabledForeground");
        JLabel state = new JLabel(closes ? "kapanır" : partial ? "kısmi" : "");
        state.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: "
                + (closes ? "$Servicio.successColor" : "$Servicio.warningColor"));
        JLabel manual = new JLabel(row.manual ? "düşülecek · elle" : "düşülecek");
        manual.putClientProperty(FlatClientProperties.STYLE, "font: -2; foreground: $Label.disabledForeground");

        b.add(label, "wmin 0");
        b.add(remaining);
        b.add(alloc);
        b.add(state, "wrap");
        b.add(date);
        b.add(remainingCap);
        b.add(manual, "right");
        b.addActionListener(e -> editRow(row));
        return b;
    }

    private static JComponent emptyRow(String title, String hint) {
        JPanel p = new JPanel(new MigLayout("insets 18 10 18 10, wrap, fillx", "[center]", "[]4[]"));
        p.setOpaque(false);
        JLabel t = new JLabel(title);
        t.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        JLabel h = new JLabel(hint);
        h.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        p.add(t);
        p.add(h);
        return p;
    }

    private void updateSummary(BigDecimal amount, BigDecimal allocated) {
        BigDecimal advance = amount.subtract(allocated).max(BigDecimal.ZERO);
        List<String> parts = new ArrayList<>();
        if (customer != null) {
            parts.add("Belgelere " + Format.formatPrice(allocated));
            if (advance.signum() > 0) parts.add("avans " + Format.formatPrice(advance));
            BigDecimal after = balance.subtract(amount);
            parts.add(after.signum() > 0 ? "sonra kalan borç " + Format.formatPrice(after)
                    : after.signum() < 0 ? "müşteri " + Format.formatPrice(after.negate()) + " alacaklı olur" : "borç kapanır");
        }
        lblSummary.setText(parts.isEmpty() ? " " : String.join("   ·   ", parts));
        btnSave.setText(amount.signum() > 0 ? Format.formatPrice(amount) + " Tahsil Et" : "Tahsil Et");
        btnSave.setEnabled(customer != null && amount.signum() > 0);
    }

    private void editRow(Row row) {
        DialogHelper.prompt(this, "prompt.collection.override.title", "prompt.collection.override.label",
                row.allocated.toPlainString(), text -> {
                    BigDecimal value;
                    try {
                        value = new BigDecimal(text.trim().replace(",", "."));
                    } catch (Exception ex) {
                        Toast.show(this, Toast.Type.WARNING, Messages.get("toast.collection.invalidAmount"));
                        return;
                    }
                    if (value.signum() < 0) value = BigDecimal.ZERO;
                    if (value.compareTo(row.doc.getRemainingAmount()) > 0) value = row.doc.getRemainingAmount();

                    row.allocated = value;
                    row.manual = true;
                    redistribute();
                }, row.doc.getDocumentLabel(), Format.formatPrice(row.doc.getRemainingAmount()));
    }

    private void save() {
        if (customer == null) {
            customerBox.setError(true);
            customerBox.requestFocusInWindow();
            return;
        }
        BigDecimal amount = toBigDecimal(amountField.getValue());
        if (amount.signum() <= 0) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.collection.invalidAmount"));
            return;
        }

        List<PaymentAllocation> allocations = new ArrayList<>();
        BigDecimal allocatedTotal = BigDecimal.ZERO;
        for (Row row : rows) {
            if (row.allocated.signum() <= 0) continue;
            allocatedTotal = allocatedTotal.add(row.allocated);
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setTargetType(AllocationTargetType.valueOf(row.doc.getDocumentType().name()));
            allocation.setTargetId(row.doc.getDocumentId());
            allocation.setAmount(row.allocated);
            allocations.add(allocation);
        }
        if (allocatedTotal.compareTo(amount) > 0) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.collection.overAllocated"));
            return;
        }

        btnSave.setEnabled(false);
        try {
            paymentService.recordCollection(customer.getId(), amount, methods.getSelected(),
                            null, LocalDateTime.now(), allocations)
                    .thenAccept(payment -> SwingUtilities.invokeLater(() -> {
                        Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.collection.completed"));
                        AppModal.closeModal(modalId);
                        if (onSuccess != null) onSuccess.run();
                    })).exceptionally(ex -> {
                        SwingUtilities.invokeLater(() -> btnSave.setEnabled(true));
                        return ErrorHandler.handle(this, "Tahsilat kaydedilemedi", ex);
                    });
        } catch (Exception ex) {
            btnSave.setEnabled(true);
            ErrorHandler.handle(this, "Tahsilat kaydedilemedi", ex);
        }
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return BigDecimal.ZERO;
    }
}
