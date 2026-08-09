package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.renderer.list.ExchangeRateListCellRenderer;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.dictionary.ExchangeRate;
import tr.cabro.servicio.service.ExchangeRateManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import java.math.BigDecimal;

public class SettingsExchangeRatePanel extends JPanel {

    private final ExchangeRateManager exchangeRateManager;
    private final DefaultListModel<ExchangeRate> rateModel;

    private JList<ExchangeRate> rateList;
    private JButton refreshButton;

    public SettingsExchangeRatePanel() {
        this.exchangeRateManager = ServiceManager.getExchangeRateManager();
        this.rateModel = new DefaultListModel<>();
        init();
    }

    private void init() {
        initComponent();
        refreshList();

        refreshButton.addActionListener(e -> onRefreshFromTcmb());
    }

    private void refreshList() {
        exchangeRateManager.getAll().thenAccept(rates -> SwingUtilities.invokeLater(() -> {
            rateModel.clear();
            rates.forEach(rateModel::addElement);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Döviz kurları yüklenemedi", ex));
    }

    private void onRefreshFromTcmb() {
        refreshButton.setEnabled(false);
        exchangeRateManager.refreshFromTcmb().thenAccept(rates -> SwingUtilities.invokeLater(() -> {
            refreshButton.setEnabled(true);
            Toast.show(this, Toast.Type.SUCCESS, "Kurlar TCMB'den güncellendi.");
            rateModel.clear();
            rates.forEach(rateModel::addElement);
        })).exceptionally(ex -> {
            SwingUtilities.invokeLater(() -> refreshButton.setEnabled(true));
            return ErrorHandler.handle(this, "TCMB'den kur güncellenemedi", ex);
        });
    }

    private void onEdit(ExchangeRate rate) {
        String currentValue = rate.getRate() != null && rate.getRate().signum() > 0 ? rate.getRate().toPlainString() : "";
        String input = (String) JOptionPane.showInputDialog(this,
                rate.getCurrencyCode() + " için 1 birim karşılığı TL kuru:",
                "Kur Girişi", JOptionPane.PLAIN_MESSAGE, null, null, currentValue);
        if (input == null || input.trim().isEmpty()) return;

        BigDecimal rateValue;
        try {
            rateValue = new BigDecimal(input.trim().replace(',', '.'));
        } catch (NumberFormatException ex) {
            Toast.show(this, Toast.Type.WARNING, "Geçerli bir sayı girin.");
            return;
        }

        exchangeRateManager.setManualRate(rate.getCurrencyCode(), rateValue).thenAccept(v -> SwingUtilities.invokeLater(() -> {
            Toast.show(this, Toast.Type.SUCCESS, rate.getCurrencyCode() + " kuru güncellendi.");
            refreshList();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Kur güncellenemedi", ex));
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 5, fill, wrap 1", "[grow]", "[]1[]15[][grow]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");

        JLabel title = new JLabel("Döviz Kurları");
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h2.font");

        JLabel subtitle = new JLabel("Parça alış/satış fiyatlarını dövizle girerken kullanılan TL kurları.");
        subtitle.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");

        refreshButton = new JButton("TCMB'den Güncelle", new Ikon("icons/refresh-cw.svg", 1f));

        rateList = new JList<>();
        rateList.putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        rateList.setCellRenderer(new ExchangeRateListCellRenderer(rateList, this::onEdit));
        rateList.setModel(rateModel);

        add(title);
        add(subtitle, "wrap");
        add(refreshButton, "wrap");
        add(rateList, "grow");
    }
}
