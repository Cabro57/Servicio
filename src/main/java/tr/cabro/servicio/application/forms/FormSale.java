package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.themes.BadgePalette;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.documents.receipt.ReceiptContent;
import tr.cabro.servicio.documents.receipt.ReceiptPdfRenderer;
import tr.cabro.servicio.i18n.DateFormats;
import tr.cabro.servicio.application.panels.ReturnPanel;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.enums.AllocationTargetType;
import tr.cabro.servicio.model.enums.PaymentStatus;
import tr.cabro.servicio.model.enums.SaleType;
import tr.cabro.servicio.service.PaymentService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/** Satış detayı — salt okunur, tek aksiyonu iade. */
public class FormSale extends Form {

    private final Sale sale;
    private final SaleService saleService;

    private JLabel lblTitle;
    private JLabel valDate, valCustomer, valStatus;
    private JLabel lblSubtotal, lblTotal, lblPaid, lblRemaining;
    private JLabel capTotal, capPaid, capRemaining;
    private GenericTableModel<SaleItem> itemsTableModel;
    private JButton btnReturn;
    private JButton btnPrint;
    private Sale loadedSale;

    public FormSale(Sale sale) {
        this.sale = sale;
        this.saleService = ServiceManager.getSaleService();
        init();
    }

    private void init() {
        setLayout(new MigLayout("fill, insets 20, gap 15", "[grow]", "[pref][pref][grow][pref!]"));

        lblTitle = new JLabel("Satış Detayı — SAT-" + sale.getId());
        lblTitle.putClientProperty(FlatClientProperties.STYLE, "font: bold $h1.font");
        add(lblTitle, "wrap");

        add(buildInfoRow(), "growx, wrap");
        add(buildItemsTable(), "grow, wrap");
        add(buildTotalsBox(), "growx");

        loadDetails();
    }

    private JPanel buildInfoRow() {
        JPanel panel = new JPanel(new MigLayout("insets 15, fillx", "[][grow][][grow][][grow]", "[]"));
        panel.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 12;");

        panel.add(new JLabel("Tarih:"));
        valDate = new JLabel("-");
        panel.add(valDate);

        panel.add(new JLabel("Müşteri:"));
        valCustomer = new JLabel(sale.getCustomer() != null ? sale.getCustomer().getFullName() : "Perakende");
        panel.add(valCustomer);

        panel.add(new JLabel("Durum:"));
        valStatus = new JLabel("-");
        panel.add(valStatus);

        return panel;
    }

    private JScrollPane buildItemsTable() {
        List<ColumnDef<SaleItem>> columns = Arrays.asList(
                new ColumnDef<>("Kalem", String.class, SaleItem::getItemName),
                new ColumnDef<>("Adet", Integer.class, SaleItem::getQuantity),
                new ColumnDef<>("Birim Fiyat", BigDecimal.class, SaleItem::getUnitPrice),
                new ColumnDef<>("Tutar", BigDecimal.class, SaleItem::getLineTotal)
        );
        itemsTableModel = new GenericTableModel<>(columns);
        JTable table = new JTable(itemsTableModel);
        table.getColumnModel().getColumn(2).setCellRenderer(new tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer());
        table.getColumnModel().getColumn(3).setCellRenderer(new tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer());

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.putClientProperty(FlatClientProperties.STYLE, "arc: 12;");
        return scrollPane;
    }

    private JPanel buildTotalsBox() {
        JPanel box = new JPanel(new MigLayout("fillx, insets 15", "[grow][pref!][grow][pref!]", "[]"));
        box.putClientProperty(FlatClientProperties.STYLE, "background: darken($Panel.background, 2%); arc: 15;");

        box.add(new JLabel("Ara Toplam:"));
        lblSubtotal = new JLabel("-");
        box.add(lblSubtotal, "align right");

        capTotal = new JLabel("Toplam:");
        box.add(capTotal);
        lblTotal = new JLabel("-");
        lblTotal.putClientProperty(FlatClientProperties.STYLE, "font: bold");
        box.add(lblTotal, "align right, wrap");

        capPaid = new JLabel("Alınan Ödeme:");
        box.add(capPaid);
        lblPaid = new JLabel("-");
        box.add(lblPaid, "align right");

        capRemaining = new JLabel("Kalan:");
        box.add(capRemaining);
        lblRemaining = new JLabel("-");
        box.add(lblRemaining, "align right");

        btnPrint = new JButton("Fiş Yazdır");
        btnPrint.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,12,6,12");
        btnPrint.addActionListener(e -> printReceipt());
        box.add(btnPrint, "align right, split 2");

        btnReturn = new JButton("İade Al");
        btnReturn.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,12,6,12");
        btnReturn.setVisible(false);
        btnReturn.addActionListener(e -> ReturnPanel.open(this, sale, this::loadDetails));
        box.add(btnReturn, "wrap");

        return box;
    }

    private void loadDetails() {
        saleService.getById(sale.getId()).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) return;
            Sale full = opt.get();
            loadedSale = full;

            valDate.setText(full.getSaleDate() != null ? full.getSaleDate().format(DateFormats.dateTime()) : "-");

            PaymentStatus status = PaymentService.resolveStatus(full.getTotalAmount(), full.getTotalPaid());
            valStatus.setText(status.getDisplayName());
            valStatus.putClientProperty(FlatClientProperties.STYLE,
                    "background: " + status.getBadgeColor().getBackgroundHex() + "; foreground: " + status.getBadgeColor().getForegroundHex()
                            + "; arc: 10; border: 3,8,3,8;");
            valStatus.setOpaque(true);

            itemsTableModel.setData(full.getItems());

            lblSubtotal.setText(Format.formatPrice(full.getSubtotal()));
            if (full.getType() == SaleType.RETURN) {
                // İadede tutarlar eksi saklanıyor; ekranda "Alınan Ödeme: -150" okunmuyordu.
                // Yön etiketten okunur, tutarlar pozitif gösterilir.
                BigDecimal paid = full.getTotalPaid() != null ? full.getTotalPaid() : BigDecimal.ZERO;
                lblTitle.setText("İade Detayı — İADE-" + full.getId() + " (SAT-" + full.getParentSaleId() + ")");
                capTotal.setText("İade Tutarı:");
                capPaid.setText("Müşteriye Ödenen:");
                capRemaining.setText("Hesaptan Düşülen:");
                lblTotal.setText(Format.formatPrice(full.getTotalAmount().negate()));
                lblPaid.setText(Format.formatPrice(paid.negate()));
                lblRemaining.setText(Format.formatPrice(full.getRemainingAmount().negate()));
            } else {
                lblTotal.setText(Format.formatPrice(full.getTotalAmount()));
                lblPaid.setText(Format.formatPrice(full.getTotalPaid()));
                lblRemaining.setText(Format.formatPrice(full.getRemainingAmount()));
            }

            btnReturn.setVisible(full.getType() == SaleType.SALE);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Satış detayı yüklenemedi", ex));
    }

    /** Termal yazıcı henüz yok — 80mm PDF üretilip varsayılan PDF görüntüleyicide açılır (bkz. FormPos.printReceipt). */
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
                        SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.WARNING, "Fiş oluşturulamadı."));
                    }
                })).exceptionally(ex -> ErrorHandler.handle(this, "Fiş oluşturulamadı", ex));
    }
}
