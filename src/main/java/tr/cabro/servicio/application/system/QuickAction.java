package tr.cabro.servicio.application.system;

import lombok.Getter;
import tr.cabro.servicio.application.forms.FormAccounts;
import tr.cabro.servicio.application.forms.FormCashReport;
import tr.cabro.servicio.application.forms.FormCustomers;
import tr.cabro.servicio.application.forms.FormParts;
import tr.cabro.servicio.application.forms.FormPos;
import tr.cabro.servicio.application.forms.FormWorkOrders;
import tr.cabro.servicio.application.forms.base.AbstractTableForm;

import javax.swing.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

/**
 * Uygulamanın her yerinden erişilen sık işlemler: ana sayfadaki kısayol şeridi, başlıktaki
 * "Yeni" menüsü, komut paleti (Ctrl+K) ve Alt+harf klavye kısayolları hepsi bu tek listeden beslenir.
 * <p>
 * Kısayollar Alt+harf seçildi: F1–F8 ve Ctrl+T/W/I POS ekranına, Ctrl+1..6 müşteri detayına ait.
 */
@Getter
public enum QuickAction {

    NEW_SERVICE("Yeni Servis", "Cihaz kabul et, servis kaydı aç", "icons/wrench.svg", KeyEvent.VK_N,
            "servis kayıt cihaz kabul iş emri tamir"),
    QUICK_SALE("Hızlı Satış", "POS ekranını aç", "icons/shopping-bag.svg", KeyEvent.VK_S,
            "pos satış kasa ürün sepet"),
    COLLECT("Tahsilat Al", "Cari hesaptan müşteri seç, ödeme al", "icons/hand-coins.svg", KeyEvent.VK_T,
            "tahsilat ödeme borç cari hesap para"),
    NEW_CUSTOMER("Yeni Müşteri", "Müşteri kaydı ekle", "icons/user-plus.svg", KeyEvent.VK_M,
            "müşteri kişi firma ekle"),
    NEW_PART("Yeni Parça", "Stoğa parça ekle", "icons/package-plus.svg", KeyEvent.VK_P,
            "parça stok ürün ekle"),
    CASH_REPORT("Kasa Raporu", "Günlük kasa ve ödeme dökümü", "icons/banknote.svg", KeyEvent.VK_R,
            "kasa rapor gün sonu nakit kart");

    private final String label;
    private final String description;
    private final String iconPath;
    private final int keyCode;
    private final String keywords;

    QuickAction(String label, String description, String iconPath, int keyCode, String keywords) {
        this.label = label;
        this.description = description;
        this.iconPath = iconPath;
        this.keyCode = keyCode;
        this.keywords = keywords;
    }

    public KeyStroke getKeyStroke() {
        return KeyStroke.getKeyStroke(keyCode, InputEvent.ALT_DOWN_MASK);
    }

    /** Kullanıcıya gösterilen kısayol metni, ör. "Alt+N". */
    public String getShortcutText() {
        return "Alt+" + KeyEvent.getKeyText(keyCode);
    }

    public boolean matches(String query) {
        String q = query.toLowerCase(java.util.Locale.ROOT);
        return label.toLowerCase(java.util.Locale.ROOT).contains(q)
                || description.toLowerCase(java.util.Locale.ROOT).contains(q)
                || keywords.contains(q);
    }

    public void run() {
        switch (this) {
            case NEW_SERVICE:
                startNew(FormWorkOrders.class);
                break;
            case QUICK_SALE:
                FormManager.showForm(AllForms.getForm(FormPos.class));
                break;
            case COLLECT:
                // Tahsilat penceresi müşteri seçiciyle açılır; hangi ekranda olunursa olunsun iş bölünmez.
                tr.cabro.servicio.application.panels.CollectionPanel.open(FormManager.getMainForm(), null, FormManager::refresh);
                break;
            case NEW_CUSTOMER:
                startNew(FormCustomers.class);
                break;
            case NEW_PART:
                startNew(FormParts.class);
                break;
            case CASH_REPORT:
                FormManager.showForm(AllForms.getForm(FormCashReport.class));
                break;
        }
    }

    private static void startNew(Class<? extends AbstractTableForm> formClass) {
        AbstractTableForm form = (AbstractTableForm) AllForms.getForm(formClass);
        FormManager.showForm(form);
        form.startNew();
    }

    /**
     * Alt+harf kısayollarını pencere geneline bağlar. Açık bir modal varken (ör. servis kaydı
     * doldurulurken) tetiklenmez; yarım kalan işin üstüne ikinci bir akış açılmasın.
     */
    public static void installKeyMap(JComponent component) {
        for (QuickAction action : values()) {
            component.registerKeyboardAction(e -> {
                if (AppModal.hasOpenModal()) return;
                action.run();
            }, action.getKeyStroke(), JComponent.WHEN_IN_FOCUSED_WINDOW);
        }
    }
}
