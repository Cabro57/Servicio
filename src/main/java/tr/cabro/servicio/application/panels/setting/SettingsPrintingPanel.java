package tr.cabro.servicio.application.panels.setting;

import tr.cabro.servicio.application.utils.Toasts;
import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.PrintActions;
import tr.cabro.servicio.documents.PdfDocumentBuilder;
import tr.cabro.servicio.documents.print.PdfPrinter;
import tr.cabro.servicio.documents.receipt.ReceiptContent;
import tr.cabro.servicio.documents.receipt.ReceiptPdfRenderer;
import tr.cabro.servicio.model.Business;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppConfig;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.settings.ReceiptPaperWidth;
import tr.cabro.servicio.util.DesktopHelper;

import javax.swing.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Ayarlar &gt; İşletme &gt; Yazdırma: fiş ve A4 yazıcısı seçimi, kopya sayısı, kağıt genişliği,
 * otomatik yazdırma ve deneme fişi.
 * <p>
 * Ayar makineye bağlıdır ({@code config.json}); aynı veritabanını kullanan iki bilgisayarın
 * yazıcıları farklı olabilir.
 */
public class SettingsPrintingPanel extends JPanel {

    /** Yazıcı listesinin bir satırı; {@code name} boşsa sistem varsayılanı. */
    private record PrinterChoice(String name, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private JLabel paperNote;
    private JComboBox<PrinterChoice> receiptPrinter;
    private JComboBox<PrinterChoice> documentPrinter;
    private boolean loadingPrinters;

    public SettingsPrintingPanel() {
        setLayout(new MigLayout("fill, insets 0", "[grow, fill]", "[top]"));
        setOpaque(false);
        add(createPage());
    }

    private JPanel createPage() {
        JPanel page = SettingsKit.page();

        // --- Kağıt ---
        SegmentedButtons<ReceiptPaperWidth> paper = new SegmentedButtons<>();
        for (ReceiptPaperWidth width : ReceiptPaperWidth.values()) {
            paper.add(width, width.toString(), null);
        }
        ReceiptPaperWidth current = AppSettings.get().getPrinting().getReceiptPaperWidth();
        paper.setSelected(current);
        paperNote = SettingsKit.note("");
        updatePaperNote(current);
        paper.setOnChange(width -> {
            AppSettings.get().getPrinting().setReceiptPaperWidth(width);
            AppSettings.save();
            updatePaperNote(width);
            SettingsKit.saved(this);
        });

        JPanel paperRows = SettingsKit.rows();
        paperRows.add(SettingsKit.label("Kağıt genişliği"));
        paperRows.add(paper, "growx 0");
        paperRows.add(paperNote, "skip");
        AppConfig.Printing printing = AppSettings.get().getPrinting();
        receiptPrinter = printerCombo(printing::setReceiptPrinter);
        paperRows.add(SettingsKit.label("Yazıcı"));
        paperRows.add(receiptPrinter);
        paperRows.add(SettingsKit.label("Kopya"));
        paperRows.add(copiesSpinner(printing.getReceiptCopies(), printing::setReceiptCopies), "growx 0, w 70!");
        SettingsKit.section(page, "Fiş yazıcısı",
                "Satış, iade ve tahsilat fişleri ile cihaz kabul/teslim fişleri bu yazıcıya, bu genişlikte basılır.", paperRows);

        // --- A4 ---
        documentPrinter = printerCombo(printing::setDocumentPrinter);
        JPanel documentRows = SettingsKit.rows();
        documentRows.add(SettingsKit.label("Yazıcı"));
        documentRows.add(documentPrinter);
        documentRows.add(SettingsKit.label("Kopya"));
        documentRows.add(copiesSpinner(printing.getDocumentCopies(), printing::setDocumentCopies), "growx 0, w 70!");
        JButton refresh = new JButton("Yazıcı listesini yenile", new Ikon("icons/refresh-cw.svg", 14));
        refresh.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,10,4,10; iconTextGap: 6");
        refresh.addActionListener(e -> loadPrinters());
        documentRows.add(refresh, "skip, growx 0, gaptop 4");
        SettingsKit.section(page, "A4 yazıcısı",
                "Servis formları ve 2.el belgeleri \"Yazdır\" ile bu yazıcıya gönderilir.", documentRows);

        // --- Otomatik ---
        JCheckBox autoSale = new JCheckBox("POS satış fişini otomatik yazdır", printing.isAutoPrintSaleReceipt());
        autoSale.addActionListener(e -> {
            printing.setAutoPrintSaleReceipt(autoSale.isSelected());
            saveSetting();
        });
        JCheckBox autoIntake = new JCheckBox("Servis kabul fişini otomatik yazdır", printing.isAutoPrintIntakeSlip());
        autoIntake.addActionListener(e -> {
            printing.setAutoPrintIntakeSlip(autoIntake.isSelected());
            saveSetting();
        });
        JPanel auto = SettingsKit.stack();
        auto.add(SettingsKit.check(autoSale, "Satış tamamlanınca fiş ekranda açılmaz, doğrudan fiş yazıcısına basılır."));
        auto.add(SettingsKit.check(autoIntake, "Yeni servis kaydı oluşturulunca cihaz kabul fişi fiş yazıcısına basılır."));
        SettingsKit.section(page, "Otomatik yazdırma", "Kapalıyken satış fişi ekranda açılır.", auto);

        // --- Deneme ---
        JButton printTest = new JButton("Deneme fişi yazdır", new Ikon("icons/printer.svg", 16));
        printTest.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        printTest.addActionListener(e -> printTestSlip());
        JButton openTest = new JButton("PDF olarak aç", new Ikon("icons/file-text.svg", 16));
        openTest.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        openTest.addActionListener(e -> openTestSlip());

        JPanel buttons = new JPanel(new MigLayout("insets 0, gapx 8", "[][]", "[]"));
        buttons.setOpaque(false);
        buttons.add(printTest);
        buttons.add(openTest);

        JPanel test = SettingsKit.stack();
        test.add(buttons, "growx 0");
        test.add(SettingsKit.wrappingNote("Deneme fişi seçili fiş yazıcısına gerçek boyutta (ölçeklemesiz) basılır. "
                + "Kenarlar kesiliyor ya da fiş kayıyorsa kağıt genişliğini ve yazıcı sürücüsündeki kağıt boyutunu denetleyin."),
                "wmin 0, wmax 420");
        SettingsKit.section(page, "Hizalama denemesi", "Yazıcıyı değiştirdiğinizde bir kez deneyin.", test);

        loadPrinters();
        return page;
    }

    private JComboBox<PrinterChoice> printerCombo(Consumer<String> setter) {
        JComboBox<PrinterChoice> combo = new JComboBox<>();
        combo.addActionListener(e -> {
            if (loadingPrinters || !(combo.getSelectedItem() instanceof PrinterChoice choice)) return;
            setter.accept(choice.name());
            saveSetting();
        });
        return combo;
    }

    private JSpinner copiesSpinner(int value, Consumer<Integer> setter) {
        JSpinner spinner = new JSpinner(new SpinnerNumberModel(value, 1, AppConfig.Printing.MAX_COPIES, 1));
        spinner.addChangeListener(e -> {
            setter.accept(((Number) spinner.getValue()).intValue());
            saveSetting();
        });
        return spinner;
    }

    private void saveSetting() {
        AppSettings.save();
        SettingsKit.saved(this);
    }

    /** Kurulu yazıcılar ve varsayılanın adı; tek sorguda toplanır. */
    private record PrinterList(List<String> names, String defaultName) {
    }

    /** Yazıcı sorgusu ağ yazıcılarında yavaş olabilir: arka planda yapılır, listeler sonra dolar. */
    private void loadPrinters() {
        receiptPrinter.setEnabled(false);
        documentPrinter.setEnabled(false);
        CompletableFuture.supplyAsync(() -> new PrinterList(PdfPrinter.installedPrinters(), PdfPrinter.defaultPrinterName()))
                .thenAccept(list -> SwingUtilities.invokeLater(() -> {
                    fillPrinters(receiptPrinter, list, PdfPrinter.configuredPrinter(PdfPrinter.Role.RECEIPT));
                    fillPrinters(documentPrinter, list, PdfPrinter.configuredPrinter(PdfPrinter.Role.DOCUMENT));
                }))
                .exceptionally(ex -> {
                    SwingUtilities.invokeLater(() -> {
                        receiptPrinter.setEnabled(true);
                        documentPrinter.setEnabled(true);
                    });
                    return ErrorHandler.handle(this, "Yazıcı listesi alınamadı", ex);
                });
    }

    private void fillPrinters(JComboBox<PrinterChoice> combo, PrinterList list, String selected) {
        loadingPrinters = true;
        try {
            combo.removeAllItems();
            combo.addItem(new PrinterChoice("", list.defaultName() != null
                    ? "Sistem varsayılanı (" + list.defaultName() + ")" : "Sistem varsayılanı (tanımlı değil)"));
            for (String name : list.names()) {
                combo.addItem(new PrinterChoice(name, name));
            }
            // Kayıtlı yazıcı artık kurulu değilse seçim kaybolmasın; görünür şekilde işaretlenir.
            if (!selected.isBlank() && !list.names().contains(selected)) {
                combo.addItem(new PrinterChoice(selected, selected + " (bulunamadı)"));
            }
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (Objects.equals(combo.getItemAt(i).name(), selected)) {
                    combo.setSelectedIndex(i);
                    break;
                }
            }
            combo.setEnabled(true);
        } finally {
            loadingPrinters = false;
        }
    }

    private void updatePaperNote(ReceiptPaperWidth width) {
        int chars = width == ReceiptPaperWidth.MM_80 ? 48 : 32;
        paperNote.setText("Basılabilir alan " + Math.round(width.getPrintableMm()) + " mm · satırda " + chars + " karakter");
    }

    private void printTestSlip() {
        ServiceManager.getBusinessService().get().thenAccept(shopOpt ->
                PrintActions.print(this, PdfPrinter.Role.RECEIPT, "Deneme Fişi",
                        () -> new ReceiptPdfRenderer().render(sampleContent(shopOpt.orElse(null)),
                                PdfDocumentBuilder.tempFile("deneme-fisi")))
        ).exceptionally(ex -> ErrorHandler.handle(this, "Deneme fişi için işletme bilgisi alınamadı", ex));
    }

    private void openTestSlip() {
        ServiceManager.getBusinessService().get().thenAccept(shopOpt -> {
            try {
                File pdf = new ReceiptPdfRenderer().render(sampleContent(shopOpt.orElse(null)),
                        PdfDocumentBuilder.tempFile("deneme-fisi"));
                SwingUtilities.invokeLater(() -> {
                    if (!DesktopHelper.openFile(pdf)) {
                        Toasts.show(this, Toast.Type.WARNING, "Fiş oluşturuldu ama açılamadı: " + pdf.getAbsolutePath());
                    }
                });
            } catch (Exception ex) {
                Servicio.getLogger().error("Deneme fişi oluşturulamadı", ex);
                SwingUtilities.invokeLater(() -> Toasts.show(this, Toast.Type.ERROR, "Deneme fişi oluşturulamadı."));
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "Deneme fişi için işletme bilgisi alınamadı", ex));
    }

    private static ReceiptContent sampleContent(Business shop) {
        ReceiptContent content = new ReceiptContent();
        content.setDocumentTitle("Deneme Fişi");
        content.setDocumentNumber("SAT-0000");
        content.setDate(LocalDateTime.now());
        content.setCustomerName("Örnek Müşteri");
        if (shop != null) {
            content.setShopName(shop.getBusinessName());
            content.setShopPhone(shop.getPhoneNumber());
            content.setShopAddress(shop.getAddress());
            content.setShopTaxLine(shop.getTaxLine());
        }
        content.getLines().add(new ReceiptContent.Line("Temperli Cam Ekran Koruyucu", 2, new BigDecimal("150.00"), new BigDecimal("300.00")));
        content.getLines().add(new ReceiptContent.Line("USB-C Hızlı Şarj Kablosu 1 m", 1, new BigDecimal("249.90"), new BigDecimal("249.90")));
        content.setSubtotal(new BigDecimal("549.90"));
        content.setDiscount(new BigDecimal("49.90"));
        content.setTotal(new BigDecimal("500.00"));
        content.getPayments().add(new ReceiptContent.PaymentLine("Nakit", new BigDecimal("500.00")));
        return content;
    }
}
