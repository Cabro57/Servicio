package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.Badge;
import tr.cabro.servicio.application.component.detail.DetailHeader;
import tr.cabro.servicio.application.component.detail.DetailKit;
import tr.cabro.servicio.application.component.detail.DetailListSection;
import tr.cabro.servicio.application.panels.CollectionPanel;
import tr.cabro.servicio.application.panels.ReturnPanel;
import tr.cabro.servicio.application.renderer.MoneyCellRenderer;
import tr.cabro.servicio.application.renderer.MultiLineTableCellRenderer;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.application.system.QuickAction;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.documents.receipt.ReceiptContent;
import tr.cabro.servicio.documents.receipt.ReceiptPdfRenderer;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.contract.Visualizable;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.BadgeColor;
import tr.cabro.servicio.model.enums.DiscountType;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.model.enums.SaleType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Satış / iade fişi detayı. Kimlik şeridinde fişin tutarı, ödenen ve kalan; birincil işlem duruma
 * göre değişir (kalan varsa Tahsilat Al, yoksa Fiş Yazdır). Solda kalemler (kalem iskontosu kalem
 * satırında), sağda ödeme özeti, ödemeler ve bu satışa bağlı iadeler.
 * <p>İadede tutarlar eksi saklanır; ekranda yön etiketten okunur, tutarlar pozitif gösterilir.
 */
public class FormSale extends Form {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");

    private final Sale sale;
    private final SaleService saleService;
    private Sale loadedSale;

    private DetailHeader header;
    private final Badge statusBadge = new Badge(PaymentStatus.values()[0]).setShowIcon(true);
    private final Badge returnBadge = new Badge(simple("İade fişi", BadgeColor.RED));
    private JButton btnCustomer, btnReturn, btnPrint, btnPrimary;
    private DetailListSection<SaleItem> itemsSection;

    private JLabel sumSubtotal, sumLineDiscount, sumSaleDiscount, sumTotal, sumPaid, sumRemaining;
    private JLabel capTotal, capPaid, capRemaining;
    private JPanel paymentsList, returnsCard, returnsList;

    public FormSale(Sale sale) {
        this.sale = sale;
        this.saleService = ServiceManager.getSaleService();
        init();
    }

    private static Visualizable simple(String name, BadgeColor color) {
        return new Visualizable() {
            @Override public String getDisplayName() { return name; }
            @Override public String getIconPath() { return "icons/undo-2.svg"; }
            @Override public BadgeColor getBadgeColor() { return color; }
        };
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 16", "[grow, fill][360!, fill]", "[pref][grow, fill]"));

        header = new DetailHeader();
        header.addBadge(statusBadge);
        header.addBadge(returnBadge);
        header.addStat("total", "Toplam");
        header.addStat("paid", "Ödenen");
        header.addStat("remaining", "Kalan");
        btnCustomer = header.addAction("Müşteri", "icons/user.svg", () -> {
            if (loadedSale != null && loadedSale.getCustomer() != null) FormManager.showForm(new FormCustomer(loadedSale.getCustomer()));
        });
        btnReturn = header.addAction("İade Al", "icons/undo-2.svg", () -> ReturnPanel.open(this, sale, this::loadDetails));
        btnPrint = header.addAction("Fiş Yazdır", "icons/printer.svg", this::printReceipt);
        btnPrimary = header.setPrimary("Tahsilat Al", "icons/hand-coins.svg", this::collect, QuickAction.COLLECT);
        add(header, "span 2, growx, wmin 0, wrap");

        itemsSection = new DetailListSection<>("Kalemler", Arrays.asList(
                new ColumnDef<SaleItem>("Kalem", SaleItem.class, i -> i).alignment(SwingConstants.LEADING),
                new ColumnDef<SaleItem>("Adet", Integer.class, i -> Math.abs(i.getQuantity())).alignment(SwingConstants.CENTER),
                new ColumnDef<SaleItem>("Birim fiyat", BigDecimal.class, SaleItem::getUnitPrice).alignment(SwingConstants.TRAILING),
                new ColumnDef<SaleItem>("Tutar", BigDecimal.class, i -> i.getLineTotal() != null ? i.getLineTotal().abs() : null)
                        .alignment(SwingConstants.TRAILING)
        ), "Kalem yok", "Bu fişte kalem bulunmuyor.", false);
        JTable t = itemsSection.getTable();
        t.getColumnModel().getColumn(0).setCellRenderer(new MultiLineTableCellRenderer<SaleItem>(
                SaleItem::getItemName, FormSale::itemSubtitle));
        t.getColumnModel().getColumn(2).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        t.getColumnModel().getColumn(3).setCellRenderer(new MoneyCellRenderer(MoneyCellRenderer.Mode.NEUTRAL));
        t.getColumnModel().getColumn(0).setPreferredWidth(360);
        t.getColumnModel().getColumn(1).setMaxWidth(80);
        itemsSection.setOnOpen(item -> {
            if (item.getProductId() == null) return;
            ServiceManager.getProductService().getById(item.getProductId()).thenAccept(opt ->
                    SwingUtilities.invokeLater(() -> opt.ifPresent(p -> FormManager.showForm(new FormProduct(p)))));
        });
        add(itemsSection, "grow, wmin 0, hmin 0");

        add(DetailKit.scroll(buildSideColumn()), "grow, hmin 0");
        loadDetails();
    }

    private void collect() {
        if (loadedSale != null && loadedSale.getCustomer() != null) {
            CollectionPanel.open(this, loadedSale.getCustomer(), this::loadDetails);
        }
    }

    /** "Kalem iskontosu %10 · kataloğa bağlı değil" gibi ikinci satır. */
    private static String itemSubtitle(SaleItem i) {
        String discount = null;
        if (i.getLineDiscountType() != null && i.getLineDiscountValue() != null && i.getLineDiscountValue().signum() > 0) {
            discount = i.getLineDiscountType() == DiscountType.PERCENT
                    ? "%" + i.getLineDiscountValue().stripTrailingZeros().toPlainString() + " iskonto"
                    : Format.formatPrice(i.getLineDiscountValue()) + " iskonto";
        }
        String origin = i.getProductId() == null ? "Manuel kalem" : null;
        if (discount != null && origin != null) return discount + "  ·  " + origin;
        return discount != null ? discount : (origin != null ? origin : "");
    }

    private JPanel buildSideColumn() {
        JPanel column = new JPanel(new MigLayout("insets 0, wrap, fillx, gapy 16", "[grow, fill]", ""));
        column.setOpaque(false);

        JPanel summary = DetailKit.card("Ödeme özeti", null);
        JPanel facts = DetailKit.facts();
        sumSubtotal = DetailKit.fact(facts, "Ara toplam");
        sumLineDiscount = DetailKit.fact(facts, "Kalem iskontoları");
        sumSaleDiscount = DetailKit.fact(facts, "Fiş iskontosu");
        facts.add(new JSeparator(), "span 2, growx, wrap");
        capTotal = DetailKit.muted("Toplam");
        sumTotal = new JLabel("—");
        sumTotal.putClientProperty(FlatClientProperties.STYLE, "font: bold +2");
        facts.add(capTotal);
        facts.add(sumTotal, "wrap");
        capPaid = DetailKit.muted("Ödenen");
        sumPaid = new JLabel("—");
        sumPaid.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        facts.add(capPaid);
        facts.add(sumPaid, "wrap");
        capRemaining = DetailKit.muted("Kalan");
        sumRemaining = new JLabel("—");
        facts.add(capRemaining);
        facts.add(sumRemaining, "wrap");
        summary.add(facts);
        column.add(summary);

        JPanel payments = DetailKit.card("Ödemeler", null);
        paymentsList = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 8", "[grow][right]", ""));
        paymentsList.setOpaque(false);
        payments.add(paymentsList);
        column.add(payments);

        returnsCard = DetailKit.card("İadeler", null);
        returnsList = new JPanel(new MigLayout("insets 0, fillx, wrap, gap 0 2", "[grow, fill]", ""));
        returnsList.setOpaque(false);
        returnsCard.add(returnsList);
        column.add(returnsCard);
        return column;
    }

    private void loadDetails() {
        saleService.getById(sale.getId()).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) return;
            Sale full = opt.get();
            loadedSale = full;
            boolean isReturn = full.getType() == SaleType.RETURN;

            header.setTitle((isReturn ? "İADE-" : "SAT-") + full.getId());
            statusBadge.setVisualizable(PaymentService.resolveStatus(full.getTotalAmount(), full.getTotalPaid()));
            returnBadge.setVisible(isReturn);
            int count = full.getItems() != null ? full.getItems().size() : 0;
            header.setMeta(full.getSaleDate() != null ? full.getSaleDate().format(DateFormats.dateTime()) : null,
                    full.getCustomer() != null ? full.getCustomer().getFullName() + "  " + PhoneHelper.formatForDisplay(full.getCustomer().getPhoneNumber1())
                            : "Perakende satış",
                    count + " kalem",
                    isReturn ? "SAT-" + full.getParentSaleId() + " fişinin iadesi" : null);

            BigDecimal total = nz(full.getTotalAmount());
            BigDecimal paid = nz(full.getTotalPaid());
            BigDecimal remaining = nz(full.getRemainingAmount());
            if (isReturn) {
                header.setStat("total", Format.formatPrice(total.negate()), "Servicio.dangerColor");
                header.setStat("paid", Format.formatPrice(paid.negate()), null);
                header.setStat("remaining", Format.formatPrice(remaining.negate()), null);
                capTotal.setText("İade tutarı");
                capPaid.setText("Müşteriye ödenen");
                capRemaining.setText("Hesaptan düşülen");
            } else {
                header.setStat("total", Format.formatPrice(total), null);
                header.setStat("paid", Format.formatPrice(paid), paid.signum() > 0 ? "Servicio.successColor" : null);
                header.setStat("remaining", remaining.signum() > 0 ? Format.formatPrice(remaining) : "Ödendi",
                        remaining.signum() > 0 ? "Servicio.warningColor" : "Label.disabledForeground");
            }

            itemsSection.setData(full.getItems() != null ? full.getItems() : Collections.emptyList());
            fillSummary(full, isReturn, total, paid, remaining);

            // Birincil işlem: kalan tutar ve kayıtlı müşteri varsa tahsilat, yoksa fiş.
            boolean canCollect = !isReturn && remaining.signum() > 0 && full.getCustomer() != null;
            btnPrimary.setVisible(canCollect);
            btnCustomer.setVisible(full.getCustomer() != null);
            btnReturn.setVisible(!isReturn);
            btnPrint.putClientProperty(FlatClientProperties.STYLE, canCollect
                    ? "arc: 10; margin: 7,12,7,12; iconTextGap: 6"
                    : "arc: 10; margin: 7,14,7,14; iconTextGap: 6; font: bold; borderWidth: 0; focusWidth: 0; innerFocusWidth: 1;"
                    + " background: $Component.accentColor; foreground: $Servicio.onAccentForeground;"
                    + " hoverBackground: darken($Component.accentColor,6%); pressedBackground: darken($Component.accentColor,12%)");
            btnPrint.setIcon(new tr.cabro.servicio.application.utils.Ikon("icons/printer.svg", 16,
                    canCollect ? "Label.foreground" : "Servicio.onAccentForeground"));

            loadPayments(isReturn);
            loadReturns(isReturn);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Satış detayı yüklenemedi", ex));
    }

    private void fillSummary(Sale full, boolean isReturn, BigDecimal total, BigDecimal paid, BigDecimal remaining) {
        BigDecimal gross = BigDecimal.ZERO;
        if (full.getItems() != null) {
            for (SaleItem i : full.getItems()) {
                gross = gross.add(nz(i.getUnitPrice()).multiply(BigDecimal.valueOf(Math.abs(i.getQuantity()))));
            }
        }
        BigDecimal subtotal = nz(full.getSubtotal()).abs();
        BigDecimal lineDiscount = gross.subtract(subtotal).max(BigDecimal.ZERO);
        BigDecimal saleDiscount = subtotal.subtract(total.abs()).max(BigDecimal.ZERO);
        DetailKit.setFact(sumSubtotal, Format.formatPrice(gross));
        DetailKit.setFact(sumLineDiscount, lineDiscount.signum() > 0 ? "− " + Format.formatPrice(lineDiscount) : "—");
        DetailKit.setFact(sumSaleDiscount, saleDiscount.signum() > 0 ? "− " + Format.formatPrice(saleDiscount) : "—");
        sumTotal.setText(Format.formatPrice(total.abs()));
        sumPaid.setText(Format.formatPrice(paid.abs()));
        if (isReturn) {
            sumRemaining.setText(Format.formatPrice(remaining.abs()));
            sumRemaining.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        } else {
            sumRemaining.setText(remaining.signum() > 0 ? Format.formatPrice(remaining) : "Ödendi");
            sumRemaining.putClientProperty(FlatClientProperties.STYLE, remaining.signum() > 0
                    ? "font: bold; foreground: $Servicio.warningColor" : "font: bold; foreground: $Label.disabledForeground");
        }
    }

    private void loadPayments(boolean isReturn) {
        ServiceManager.getPaymentService().getPaymentsForTarget(AllocationTargetType.SALE, sale.getId())
                .thenAccept(payments -> SwingUtilities.invokeLater(() -> {
                    paymentsList.removeAll();
                    if (payments.isEmpty()) {
                        paymentsList.add(DetailKit.small(isReturn ? "Müşteriye henüz ödeme yapılmadı." : "Henüz ödeme alınmadı."), "span 2");
                    }
                    for (Payment p : payments) {
                        JLabel what = new JLabel((p.getPaymentType() != null ? p.getPaymentType().getDisplayName() : "Ödeme"));
                        what.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                        JLabel when = DetailKit.small(p.getPaymentDate() != null ? p.getPaymentDate().format(TIME) : "");
                        JLabel amount = new JLabel(Format.formatPrice(nz(p.getAmount()).abs()));
                        amount.putClientProperty(FlatClientProperties.STYLE, "font: bold");
                        JPanel left = new JPanel(new MigLayout("insets 0, gap 0, wrap", "[]", "[]0[]"));
                        left.setOpaque(false);
                        left.add(what);
                        left.add(when);
                        paymentsList.add(left);
                        paymentsList.add(amount);
                    }
                    paymentsList.revalidate();
                    paymentsList.repaint();
                })).exceptionally(ex -> ErrorHandler.handle(this, "Ödemeler yüklenemedi", ex));
    }

    private void loadReturns(boolean isReturn) {
        if (isReturn) {
            returnsCard.setVisible(false);
            return;
        }
        saleService.getReturnsOf(sale.getId()).thenAccept(returns -> SwingUtilities.invokeLater(() -> {
            returnsList.removeAll();
            returnsCard.setVisible(!returns.isEmpty());
            for (Sale r : returns) {
                JButton row = new JButton();
                row.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
                row.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");
                row.setLayout(new MigLayout("insets 0, fillx, gap 8", "[grow][right]", "[center]"));
                JLabel name = new JLabel("İADE-" + r.getId() + (r.getSaleDate() != null ? "  ·  " + r.getSaleDate().format(TIME) : ""));
                JLabel amount = new JLabel("− " + Format.formatPrice(nz(r.getTotalAmount()).abs()));
                amount.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: $Servicio.dangerColor");
                row.add(name, "wmin 0");
                row.add(amount);
                row.addActionListener(e -> FormManager.showForm(new FormSale(r)));
                returnsList.add(row);
            }
            returnsList.revalidate();
        })).exceptionally(ex -> ErrorHandler.handle(this, "İadeler yüklenemedi", ex));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    @Override
    public void formRefresh() {
        loadDetails();
    }

    /** Termal fiş PDF'i üretilip varsayılan PDF görüntüleyicide açılır (bkz. FormPos.printReceipt). */
    private void printReceipt() {
        if (loadedSale == null) return;

        ServiceManager.getPaymentService().getPaymentsForTarget(AllocationTargetType.SALE, sale.getId())
                .thenAccept(payments -> ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
                    try {
                        ReceiptContent content = ReceiptContent.fromSale(loadedSale, payments, shopOpt.orElse(null));
                        File outFile = File.createTempFile("servicio-satis-fis-SAT" + sale.getId() + "-", ".pdf");
                        outFile.deleteOnExit();
                        new ReceiptPdfRenderer().render(content, outFile);
                        SwingUtilities.invokeLater(() -> DesktopHelper.openFile(outFile));
                    } catch (Exception ex) {
                        Servicio.getLogger().error("Satış fişi oluşturma hatası", ex);
                        SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.WARNING, Messages.get("toast.receipt.failed", ex.getMessage())));
                    }
                })).exceptionally(ex -> ErrorHandler.handle(this, "Fiş oluşturulamadı", ex));
    }
}
