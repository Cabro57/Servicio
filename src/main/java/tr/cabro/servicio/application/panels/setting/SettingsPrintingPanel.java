package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.SegmentedButtons;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.documents.PdfDocumentBuilder;
import tr.cabro.servicio.documents.receipt.ReceiptContent;
import tr.cabro.servicio.documents.receipt.ReceiptPdfRenderer;
import tr.cabro.servicio.model.User;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.settings.AppSettings;
import tr.cabro.servicio.settings.ReceiptPaperWidth;
import tr.cabro.servicio.util.DesktopHelper;

import javax.swing.*;
import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Ayarlar &gt; İşletme &gt; Yazdırma: termal fiş yazıcısının kağıt genişliği ve deneme fişi.
 * <p>
 * Ayar makineye bağlıdır ({@code config.json}); aynı veritabanını kullanan iki bilgisayarın
 * yazıcıları farklı olabilir.
 */
public class SettingsPrintingPanel extends JPanel {

    private JLabel paperNote;

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
        SettingsKit.section(page, "Fiş yazıcısı",
                "Satış, iade ve tahsilat fişleri ile cihaz kabul/teslim fişleri bu genişlikte basılır.", paperRows);

        // --- Deneme ---
        JButton testButton = new JButton("Deneme fişi oluştur", new Ikon("icons/printer.svg", 16));
        testButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 5,12,5,12; iconTextGap: 6");
        testButton.addActionListener(e -> openTestSlip());

        JPanel test = SettingsKit.stack();
        test.add(testButton, "growx 0");
        test.add(SettingsKit.wrappingNote("Seçili genişlikte örnek bir satış fişi açılır. Yazdırırken ölçeklendirmeyi "
                + "kapatın (\"Gerçek boyut\" ya da %100); aksi halde fiş küçülür ve kenarları boş kalır."), "wmin 0, wmax 420");
        SettingsKit.section(page, "Hizalama denemesi", "Yazıcıyı değiştirdiğinizde bir kez deneyin.", test);

        return page;
    }

    private void updatePaperNote(ReceiptPaperWidth width) {
        int chars = width == ReceiptPaperWidth.MM_80 ? 48 : 32;
        paperNote.setText("Basılabilir alan " + Math.round(width.getPrintableMm()) + " mm · satırda " + chars + " karakter");
    }

    private void openTestSlip() {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            try {
                File pdf = new ReceiptPdfRenderer().render(sampleContent(shopOpt.orElse(null)),
                        PdfDocumentBuilder.tempFile("deneme-fisi"));
                SwingUtilities.invokeLater(() -> {
                    if (!DesktopHelper.openFile(pdf)) {
                        Toast.show(this, Toast.Type.WARNING, "Fiş oluşturuldu ama açılamadı: " + pdf.getAbsolutePath());
                    }
                });
            } catch (Exception ex) {
                Servicio.getLogger().error("Deneme fişi oluşturulamadı", ex);
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.ERROR, "Deneme fişi oluşturulamadı."));
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "Deneme fişi için işletme bilgisi alınamadı", ex));
    }

    private static ReceiptContent sampleContent(User shop) {
        ReceiptContent content = new ReceiptContent();
        content.setDocumentTitle("Deneme Fişi");
        content.setDocumentNumber("SAT-0000");
        content.setDate(LocalDateTime.now());
        content.setCustomerName("Örnek Müşteri");
        if (shop != null) {
            content.setShopName(shop.getBusinessName());
            content.setShopPhone(shop.getPhoneNumber());
            content.setShopAddress(shop.getAddress());
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
