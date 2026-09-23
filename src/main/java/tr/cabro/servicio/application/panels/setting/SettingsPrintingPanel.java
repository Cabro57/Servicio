package tr.cabro.servicio.application.panels.setting;

import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.Servicio;
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
 * Ayarlar &gt; Uygulama &gt; Yazdırma: termal fiş yazıcısının kağıt genişliği ve deneme fişi.
 * <p>
 * Ayar makineye bağlıdır ({@code config.json}); aynı veritabanını kullanan iki bilgisayarın
 * yazıcıları farklı olabilir.
 */
public class SettingsPrintingPanel extends JPanel {

    private JComboBox<ReceiptPaperWidth> paperCombo;

    public SettingsPrintingPanel() {
        initComponent();
        paperCombo.setSelectedItem(AppSettings.get().getPrinting().getReceiptPaperWidth());
        paperCombo.addActionListener(e -> {
            AppSettings.get().getPrinting().setReceiptPaperWidth((ReceiptPaperWidth) paperCombo.getSelectedItem());
            AppSettings.save();
        });
    }

    private void initComponent() {
        setLayout(new MigLayout("fillx, insets 10, gapy 15", "[grow]", "[][grow]"));

        JPanel panel = new JPanel(new MigLayout("fill, insets 10", "[][grow]", "[]10[]5[]15[]"));
        panel.setBorder(BorderFactory.createTitledBorder("Termal Fiş Yazıcısı"));

        panel.add(new JLabel("Kağıt Genişliği:"));
        paperCombo = new JComboBox<>(ReceiptPaperWidth.values());
        paperCombo.setToolTipText("Satış, iade ve tahsilat fişleriyle cihaz kabul/teslim fişleri bu genişlikte basılır.");
        panel.add(paperCombo, "w 160!, wrap");

        JLabel hint = new JLabel("<html>80 mm rulo yazıcılar satırda 48, 58 mm olanlar 32 karakter basar. "
                + "Yazdırırken ölçeklendirmeyi kapatın (\"Gerçek boyut\" / %100), aksi halde fiş küçülür.</html>");
        hint.putClientProperty("FlatLaf.styleClass", "small");
        panel.add(hint, "span 2, wmin 0, wrap");

        JButton testButton = new JButton("Deneme Fişi Oluştur");
        testButton.setToolTipText("Seçili genişlikte örnek bir satış fişi açar; yazıcıdan basıp hizalamayı kontrol edin.");
        testButton.addActionListener(e -> openTestSlip());
        panel.add(testButton, "span 2");

        add(panel, "growx, wrap");
        add(new JLabel(), "pushy, growy, wrap");
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
