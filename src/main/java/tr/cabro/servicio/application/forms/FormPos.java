package tr.cabro.servicio.application.forms;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.ModalDialog;
import raven.modal.Toast;
import raven.modal.component.SimpleModalBorder;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.component.ActionButton;
import tr.cabro.servicio.application.component.CurrencyField;
import tr.cabro.servicio.application.component.table.DynamicActionColumnSupport;
import tr.cabro.servicio.application.component.table.TableStatePanel;
import tr.cabro.servicio.application.renderer.CurrencyTableCellRenderer;
import tr.cabro.servicio.application.system.AppModal;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.themes.SemanticColor;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.application.utils.SystemForm;
import tr.cabro.servicio.documents.receipt.ReceiptContent;
import tr.cabro.servicio.documents.receipt.ReceiptPdfRenderer;
import tr.cabro.servicio.i18n.AppLocale;
import tr.cabro.servicio.i18n.Messages;
import org.jdesktop.swingx.autocomplete.AutoCompleteDecorator;
import org.jdesktop.swingx.autocomplete.ObjectToStringConverter;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Payment;
import tr.cabro.servicio.model.Product;
import tr.cabro.servicio.model.Sale;
import tr.cabro.servicio.model.SaleItem;
import tr.cabro.servicio.model.enums.DiscountType;
import tr.cabro.servicio.model.enums.PaymentType;
import tr.cabro.servicio.service.CustomerService;
import tr.cabro.servicio.service.ProductService;
import tr.cabro.servicio.service.SaleService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.DesktopHelper;
import tr.cabro.servicio.util.DialogHelper;
import tr.cabro.servicio.util.Format;
import tr.cabro.servicio.util.SoundPlayer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

/**
 * POS satış ekranı — barkod okut veya ürün arama penceresinden seç, sepete ekle, iskonto
 * uygula, ödeme yöntemine bas. Tek transaction'da yazan {@link SaleService#checkout}
 * kullanılır.
 * <p>
 * Tezgâhta aynı anda birden fazla müşteriye hizmet verilebilsin diye her sepet ayrı bir
 * sekmededir ({@link Cart}). Sekme kendi kalemlerini, kalem iskontolarını, müşterisini,
 * fiş indirimini ve "Ödenen" tutarını taşır; sağ sütundaki denetimler seçili sekmenin
 * durumunu gösterir ve ona yazar. Sekme başlığı müşteri seçilince müşterinin adını alır.
 * <p>
 * Stok yetersizliği satışı ENGELLEMEZ (market akışı) — sepette ürün adının yanında
 * "stok yetersiz" etiketiyle işaretlenir ve satışı tamamlarken onay sorulur.
 * <p>
 * Satış ödeme yöntemi düğmeleriyle doğrudan kapanır, ara pencere açılmaz:
 * <ul>
 *   <li>Nakit (F1): tutarın tamamı nakit alınır. "Ödenen" alanı yalnızca para üstü
 *       hesabı içindir; boşsa tam para kabul edilir, tutardan azsa satış yazılmaz.</li>
 *   <li>POS (F2): tutarın tamamı kartla alınır.</li>
 *   <li>Açık Hesap (F3): tutarın tamamı seçili müşterinin cari hesabına yazılır.</li>
 *   <li>Parçalı (F4): tek pencere açan yöntem — tutar nakit, kart ve açık hesap
 *       arasında paylaştırılır.</li>
 * </ul>
 */
@SystemForm(name = "Satış (POS)", description = "Tezgah üstü satış — barkod okut, sepete ekle, ödeme al")
public class FormPos extends Form {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");
    private static final String PRODUCT_SEARCH_MODAL_ID = "PosProductSearch";
    private static final String LINE_DISCOUNT_MODAL_ID = "PosLineDiscount";
    private static final String SPLIT_PAYMENT_MODAL_ID = "PosSplitPayment";
    /** İskonto penceresindeki "Temizle" düğmesinin eylem kodu — pencereyi kapatmadan alanları sıfırlar. */
    private static final int CLEAR_OPTION = 100;

    /** Hızlı para düğmeleri — her basış "Ödenen" tutarına eklenir. */
    private static final int[] QUICK_CASH = {20, 50, 100, 200};

    private static final String CARD_DATA = "data";
    private static final String CARD_STATE = "state";

    // Sepet tablosu sütun sırası — renderer/editor ve setValueAt bu indekslere bağlı.
    private static final int COL_BARCODE = 0;
    private static final int COL_NAME = 1;
    private static final int COL_QTY = 2;
    private static final int COL_PRICE = 3;
    private static final int COL_DISCOUNT = 4;
    private static final int COL_TOTAL = 5;
    private static final int COL_REMOVE = 6;

    private final ProductService productService;
    private final CustomerService customerService;
    private final SaleService saleService;

    private JTextField barcodeField;

    /** Satışa açık ürünler — arama penceresi bu listeden süzer, her açılışta sorgu atılmaz. */
    private List<Product> saleProducts = new ArrayList<>();
    private boolean productsLoaded;

    /**
     * Ürün id → ürün. {@link SaleItem} barkod ve stok taşımaz; sepetin Barkod sütunu ve stok
     * uyarısı buradan okunur. Ürün listesi yüklenirken ve barkodla sepete eklerken doldurulur,
     * böylece ayrı sorgu atılmaz.
     */
    private final Map<Long, Product> productById = new HashMap<>();

    private JTabbedPane cartTabs;
    /** Sekmelerle aynı sırada tutulur: {@code carts.get(i)} ↔ {@code cartTabs} i. sekme. */
    private final List<Cart> carts = new ArrayList<>();
    /**
     * Seçili sepetin durumu sağ sütundaki denetimlere yüklenirken true — denetimlerin
     * dinleyicileri bu sırada sepete geri yazmaz (aksi halde sekme değişimi, yeni sekmenin
     * müşterisini/indirimini bir önceki değerle ezer).
     */
    private boolean syncingControls;

    private JComboBox<Customer> customerCombo;
    private JComboBox<DiscountType> discountTypeCombo;
    private CurrencyField discountValueField;
    /** İndirim alanının para birimi düğmesi — yüzde modunda sökülüp tutar modunda geri takılır. */
    private Object discountCurrencyButton;

    /** Ödeme yöntemi düğmeleri — satış yazılırken çift gönderimi önlemek için topluca kilitlenir. */
    private final List<JButton> paymentButtons = new ArrayList<>();
    private boolean submitting;

    private CurrencyField tenderedField;
    private JLabel lblTotal;
    private JLabel lblRemaining;
    private JLabel lblRemainingCaption;
    private JLabel lblSubtotal;

    public FormPos() {
        this.productService = ServiceManager.getProductService();
        this.customerService = ServiceManager.getCustomerService();
        this.saleService = ServiceManager.getSaleService();
    }

    @Override
    public void formInit() {
        buildUI();
        loadProducts();
        loadCustomers();
    }

    @Override
    public void formOpen() {
        loadProducts();
        // Başka ekranda eklenen müşteri de görünsün. formInit AllForms'ta invokeLater ile
        // koşabildiği için ilk açılışta combo henüz kurulmamış olabilir; o yükü formInit yapar.
        if (customerCombo != null) loadCustomers();
        SwingUtilities.invokeLater(() -> barcodeField.requestFocusInWindow());
    }

    // =========================================================================
    // SEPET (SEKME) DURUMU
    // =========================================================================

    /**
     * Kalem iskontosu: iki kademe, ikincisi birincinin düşürdüğü tutara uygulanır
     * (ör. %10 + 5 ₺). {@link SaleItem} tek iskonto alanı taşıdığı için satışa bileşik
     * tutar olarak ({@link DiscountType#AMOUNT}) yazılır; kademeler yalnızca ekranda
     * tutulur ki pencere yeniden açılınca aynı değerlerle gelsin ve adet değişince
     * yüzde kademesi yeni brüt tutara göre yeniden hesaplansın.
     */
    private record LineDiscount(DiscountType type1, BigDecimal value1, DiscountType type2, BigDecimal value2) {

        boolean isEmpty() {
            return value1.signum() <= 0 && value2.signum() <= 0;
        }

        BigDecimal amount(BigDecimal gross) {
            BigDecimal first = step(gross, type1, value1);
            return first.add(step(gross.subtract(first), type2, value2));
        }

        /** Tek kademe; servisteki computeDiscount ile aynı kural (%100 ve taban tavanı). */
        private static BigDecimal step(BigDecimal base, DiscountType type, BigDecimal value) {
            if (value == null || value.signum() <= 0 || base.signum() <= 0) return BigDecimal.ZERO;
            if (type == DiscountType.PERCENT) {
                return base.multiply(value.min(HUNDRED)).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            }
            return value.min(base);
        }

        String describe() {
            List<String> parts = new ArrayList<>();
            if (value1.signum() > 0) parts.add(describeStep(type1, value1));
            if (value2.signum() > 0) parts.add(describeStep(type2, value2));
            return parts.isEmpty() ? "Yok" : String.join(" + ", parts);
        }

        private static String describeStep(DiscountType type, BigDecimal value) {
            return type == DiscountType.PERCENT
                    ? "%" + value.stripTrailingZeros().toPlainString()
                    : Format.formatPrice(value);
        }
    }

    /**
     * Bir sekmenin tüm satış durumu. Kalemler, kalem iskontoları ve "Ödenen" tutarı sekmeye
     * aittir; bir sekmede yazılan tutar başka sekmenin kalanını etkilemez.
     */
    private final class Cart {
        /** Varsayılan sekme adındaki sıra ("Müşteri 2"); kapanan sekmenin numarası yeniden kullanılır. */
        final int number;
        final List<SaleItem> items = new ArrayList<>();
        /** Kalem → iskonto kademeleri; aynı ürünün iki satırı karışmasın diye kimlik tabanlı. */
        final Map<SaleItem, LineDiscount> lineDiscounts = new IdentityHashMap<>();
        Customer customer;
        DiscountType discountType = DiscountType.values()[0];
        BigDecimal discountValue = BigDecimal.ZERO;
        /** Kasiyerin "Ödenen" alanına yazdığı tutar — yalnızca nakitte para üstü hesabı için. */
        BigDecimal tendered = BigDecimal.ZERO;

        final JPanel panel;
        final JTable table;
        final GenericTableModel<SaleItem> model;
        final CardLayout cards = new CardLayout();
        final JPanel area = new JPanel(cards);
        final TableStatePanel state = new TableStatePanel(true);

        Cart(int number) {
            this.number = number;
            this.model = createCartModel(this);
            this.table = new JTable(model);
            setupCartTable(this);

            area.setOpaque(false);
            area.add(new JScrollPane(table), CARD_DATA);
            area.add(state, CARD_STATE);
            state.showMessage("icons/barcode.svg", "Sepet boş",
                    "Barkod okutun (F5) veya ürün arayın (F6).");

            panel = new JPanel(new MigLayout("fill, insets 8", "[grow]", "[grow]"));
            panel.setOpaque(false);
            panel.add(area, "grow");
            updateState();
        }

        /** Sepet boşken ızgara iskeleti yerine sıradaki adımı söyleyen mesaj gösterilir. */
        void updateState() {
            cards.show(area, items.isEmpty() ? CARD_STATE : CARD_DATA);
        }

        String title() {
            String name = customer != null ? customer.getFullName() : "Müşteri " + number;
            return name + " (" + Format.formatPrice(computeTotal(this)) + ")";
        }

        void reset() {
            items.clear();
            lineDiscounts.clear();
            customer = null;
            discountType = DiscountType.values()[0];
            discountValue = BigDecimal.ZERO;
            tendered = BigDecimal.ZERO;
            model.setData(items);
            updateState();
        }
    }

    /** Seçili sekmenin sepeti. Kurulum sırasında sekme yoksa null. */
    private Cart cart() {
        int index = cartTabs != null ? cartTabs.getSelectedIndex() : -1;
        return index >= 0 && index < carts.size() ? carts.get(index) : null;
    }

    /** Yeni sepet sekmesi açar ve seçer. Numara, kullanımda olmayan en küçük sayıdır. */
    private void addCart() {
        int number = 1;
        while (hasCartNumber(number)) number++;
        Cart cart = new Cart(number);
        carts.add(cart);
        cartTabs.addTab(cart.title(), cart.panel);
        cartTabs.setSelectedIndex(carts.size() - 1);
        barcodeField.requestFocusInWindow();
    }

    private boolean hasCartNumber(int number) {
        for (Cart cart : carts) {
            if (cart.number == number) return true;
        }
        return false;
    }

    /**
     * Sekmeyi kapatır. İçinde kalem varsa önce onay sorulur — satış yazılmadığı için
     * kapatınca kaybolurlar. Tek sekme kalmışsa kapatmak onu boşaltmak demektir; tezgâhta
     * her zaman bir sepet açıktır.
     */
    private void closeCart(int index) {
        if (submitting || index < 0 || index >= carts.size()) return;
        Cart cart = carts.get(index);
        Runnable close = () -> {
            int current = carts.indexOf(cart);
            if (current < 0) return;
            if (carts.size() == 1) {
                cart.reset();
                refreshTabTitle(cart);
                onCartSwitched();
                return;
            }
            carts.remove(current);
            cartTabs.removeTabAt(current);
            barcodeField.requestFocusInWindow();
        };
        if (cart.items.isEmpty()) {
            close.run();
        } else {
            DialogHelper.confirm(this, "confirm.sale.closeCart.title", "confirm.sale.closeCart",
                    close, cart.title(), cart.items.size());
        }
    }

    private void refreshTabTitle(Cart cart) {
        int index = carts.indexOf(cart);
        if (index >= 0) cartTabs.setTitleAt(index, cart.title());
    }

    /** Sekme değişince sağ sütundaki denetimler yeni sepetin durumunu gösterir. */
    private void onCartSwitched() {
        Cart cart = cart();
        if (cart == null || customerCombo == null) return;
        syncingControls = true;
        try {
            customerCombo.setSelectedItem(findCustomerInCombo(cart.customer));
            discountTypeCombo.setSelectedItem(cart.discountType);
            discountValueField.setValue(cart.discountValue.doubleValue());
            tenderedField.setValue(cart.tendered.doubleValue());
            updateDiscountFieldMode();
        } finally {
            syncingControls = false;
        }
        recalcTotals();
    }

    /** Sepetteki müşteri nesnesi eski bir yüklemeden kalmış olabilir; combo'daki eşi id ile bulunur. */
    private Customer findCustomerInCombo(Customer customer) {
        if (customer == null || customer.getId() == null) return null;
        for (int i = 0; i < customerCombo.getItemCount(); i++) {
            Customer item = customerCombo.getItemAt(i);
            if (item != null && customer.getId().equals(item.getId())) return item;
        }
        return customer;
    }

    /** "Ödenen" tutarını sepete ve alana yazar; alanın dinleyicisi tekrar tetiklenmez. */
    private void setTendered(Cart cart, BigDecimal value) {
        cart.tendered = value.max(BigDecimal.ZERO);
        if (cart != cart()) return;
        syncingControls = true;
        try {
            tenderedField.setValue(cart.tendered.doubleValue());
        } finally {
            syncingControls = false;
        }
        recalcTotals();
    }

    // =========================================================================
    // UI KURULUMU
    // =========================================================================

    private void buildUI() {
        // Sağ sütun oransal: dar ekranda 400px'in altına inmez, geniş ekranda 540px'te durur.
        // Ödenen/Tutar/Kalan şeridi üç kutuyu yan yana taşıdığı için alt sınır 400px.
        setLayout(new MigLayout("fill, insets 15, gap 15", "[grow][400:36%:540]", "[grow]"));

        // Sağ sütundaki denetimler (müşteri/indirim/toplamlar) sepet sekmeleri kurulmadan önce
        // hazır olmalı: ilk sekme eklenince onCartSwitched bu denetimlere yazar.
        JPanel right = buildRightPanel();
        add(buildLeftPanel(), "grow");
        add(right, "grow");

        installShortcuts();
        addCart();
    }

    /**
     * Tezgâh kısayolları. F1–F4 ödeme yöntemlerine ayrıldığı için gezinme F5–F8'dedir.
     * Kapsam bilinçli olarak {@code WHEN_ANCESTOR_OF_FOCUSED_COMPONENT}: raven modalleri bu
     * panelin alt bileşeni olmadığı için bir pencere açıkken F1–F4 ikinci bir satış başlatmaz.
     */
    private void installShortcuts() {
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F5, 0), "pos.focusBarcode",
                e -> barcodeField.requestFocusInWindow());
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F6, 0), "pos.productSearch", e -> openProductSearch());
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F7, 0), "pos.focusTendered", e -> {
            tenderedField.requestFocusInWindow();
            tenderedField.selectAll();
        });
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0), "pos.focusCart", e -> focusCart());
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK), "pos.newCart", e -> {
            if (!submitting) addCart();
        });
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_DOWN_MASK), "pos.closeCart",
                e -> closeCart(cartTabs.getSelectedIndex()));
    }

    /** Sepete klavyeyle geçiş — seçili satır yoksa ilk satır seçilir ki +/-/Delete hemen çalışsın. */
    private void focusCart() {
        JTable table = cart().table;
        if (table.getRowCount() > 0 && table.getSelectedRow() < 0) {
            table.setRowSelectionInterval(0, 0);
        }
        table.requestFocusInWindow();
    }

    /** Bileşene yalnızca kendisi odaktayken çalışan bir tuş eşlemesi ekler. */
    private static void bindFocused(JComponent component, KeyStroke keyStroke, String actionKey, Runnable handler) {
        component.getInputMap(WHEN_FOCUSED).put(keyStroke, actionKey);
        component.getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.run();
            }
        });
    }

    private void bindKey(KeyStroke keyStroke, String actionKey, java.util.function.Consumer<ActionEvent> handler) {
        getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(keyStroke, actionKey);
        getActionMap().put(actionKey, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                handler.accept(e);
            }
        });
    }

    private JPanel buildLeftPanel() {
        JPanel panel = new JPanel(new MigLayout("fill, insets 0, gap 10", "[grow][pref!]", "[pref!][grow]"));

        barcodeField = new JTextField();
        barcodeField.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 8,12,8,12; font: +3");
        barcodeField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Barkod okutun veya girip Enter'a basın... (F5)");
        barcodeField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/barcode.svg", 1.2f));
        barcodeField.getAccessibleContext().setAccessibleName("Barkod");
        barcodeField.addActionListener(e -> onBarcodeEnter());
        panel.add(barcodeField, "growx, h 50!");

        JButton searchButton = new JButton("Ürün Ara", new Ikon("icons/search.svg", 1f));
        searchButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 0,14,0,14; font: bold");
        searchButton.setToolTipText("Ürün adı veya barkodla ara (F6)");
        searchButton.getAccessibleContext().setAccessibleName("Ürün ara");
        searchButton.setFocusable(false);
        searchButton.addActionListener(e -> openProductSearch());
        panel.add(searchButton, "h 50!, wrap");

        cartTabs = new JTabbedPane();
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE, FlatClientProperties.TABBED_PANE_TAB_TYPE_CARD);
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_SHOW_TAB_SEPARATORS, true);
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_HEIGHT, 36);
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, true);
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSE_TOOLTIPTEXT, "Sepeti kapat (Ctrl+W)");
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
                (BiConsumer<JTabbedPane, Integer>) (tabs, index) -> closeCart(index));
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TABS_POPUP_POLICY,
                FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
                FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
        cartTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

        JButton newCartButton = new JButton(new Ikon("icons/plus.svg", 0.9f));
        newCartButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        newCartButton.setToolTipText("Yeni sepet (Ctrl+T)");
        newCartButton.getAccessibleContext().setAccessibleName("Yeni sepet");
        newCartButton.setFocusable(false);
        newCartButton.addActionListener(e -> {
            if (!submitting) addCart();
        });
        // FlatLaf sondaki bileşeni sekme şeridinin kalan genişliğine yayar; düğme doğrudan
        // verilince tüm boşluğu kaplıyordu. Saydam bir kaba sabit boyutla sola yaslanır.
        JPanel newCartHolder = new JPanel(new MigLayout("insets 0 4 0 0, gap 0", "[32!]push", "[center]"));
        newCartHolder.setOpaque(false);
        newCartHolder.add(newCartButton, "w 32!, h 30!");
        cartTabs.putClientProperty(FlatClientProperties.TABBED_PANE_TRAILING_COMPONENT, newCartHolder);
        cartTabs.addChangeListener(e -> onCartSwitched());
        cartTabs.getAccessibleContext().setAccessibleName("Sepetler");

        panel.add(cartTabs, "span 2, grow");
        return panel;
    }

    /**
     * Sepet modeli. Miktar hücresi düzenlenebilir; editörün döndürdüğü metin burada
     * ayrıştırılıp kaleme yazılır. Yeniden çizim bir EDT turu ertelenir: setValueAt
     * JTable.editingStopped içinden çağrılır ve editör henüz sökülmemişken tüm tabloyu
     * değiştirmek editörü yanlış satıra taşıyabiliyordu.
     */
    private GenericTableModel<SaleItem> createCartModel(Cart cart) {
        List<ColumnDef<SaleItem>> columns = Arrays.asList(
                new ColumnDef<>("Barkod", String.class, this::barcodeOf),
                new ColumnDef<>("Ürün Adı", String.class, SaleItem::getItemName),
                new ColumnDef<SaleItem>("Miktar", Integer.class, SaleItem::getQuantity).editable(true),
                new ColumnDef<>("Fiyat", BigDecimal.class, SaleItem::getUnitPrice),
                new ColumnDef<>("İskonto", BigDecimal.class, SaleItem::getLineDiscountValue),
                new ColumnDef<>("Tutar", BigDecimal.class, SaleItem::getLineTotal),
                new ColumnDef<SaleItem>("Sil", String.class, i -> "").editable(true)
        );
        return new GenericTableModel<>(columns) {
            @Override
            public void setValueAt(Object value, int rowIndex, int columnIndex) {
                SaleItem item = getItemAt(rowIndex);
                if (item == null || value == null || columnIndex != COL_QTY) return;
                Integer quantity = parseQuantity(value.toString());
                if (quantity != null) SwingUtilities.invokeLater(() -> setQuantity(cart, item, quantity));
            }
        };
    }

    private void setupCartTable(Cart cart) {
        JTable table = cart.table;
        table.setRowHeight(32);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        // Hücreye yazmaya başlamak düzenlemeyi başlatmasın: '+'/'-' seçili satırda kısayol
        // olarak kalır. Miktar tıklanarak düzenlenir.
        table.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        TableColumnModel columns = table.getColumnModel();
        columns.getColumn(COL_BARCODE).setCellRenderer(new MutedCellRenderer());
        columns.getColumn(COL_NAME).setCellRenderer(new ItemNameCellRenderer(cart));
        columns.getColumn(COL_QTY).setCellRenderer(new QuantityRenderer());
        columns.getColumn(COL_QTY).setCellEditor(new QuantityEditor(cart));
        columns.getColumn(COL_PRICE).setCellRenderer(new CurrencyTableCellRenderer());
        columns.getColumn(COL_DISCOUNT).setCellRenderer(new DiscountButtonRenderer(cart));
        columns.getColumn(COL_TOTAL).setCellRenderer(new TotalCellRenderer());

        DynamicActionColumnSupport.install(table, COL_REMOVE, cart.model, List.of(
                DynamicActionColumnSupport.<SaleItem>button("icons/trash-2.svg", SemanticColor.danger(),
                        "Sepetten çıkar (Delete)", item -> removeFromCart(cart, item)).iconScale(0.55f)
        ));

        // Esneyen tek sütun Ürün Adı; diğerleri içeriği kadar dar tutulur ki uzun ürün adları
        // kesilmesin.
        setColumnWidth(columns, COL_BARCODE, 80, 100, 130);
        setColumnWidth(columns, COL_QTY, 92, 92, 92);
        setColumnWidth(columns, COL_PRICE, 70, 85, 110);
        setColumnWidth(columns, COL_DISCOUNT, 74, 88, 110);
        setColumnWidth(columns, COL_TOTAL, 80, 95, 120);
        setColumnWidth(columns, COL_REMOVE, 36, 36, 36);

        // İskonto hücresi düğme gibi çizilir; tıklanınca kalem iskontosu penceresi açılır.
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int column = table.columnAtPoint(e.getPoint());
                if (row < 0 || column < 0 || table.convertColumnIndexToModel(column) != COL_DISCOUNT) return;
                SaleItem item = cart.model.getItemAt(table.convertRowIndexToModel(row));
                if (item != null) openLineDiscount(cart, item);
            }
        });

        table.getAccessibleContext().setAccessibleName("Sepet");
        table.getAccessibleContext().setAccessibleDescription(
                "F8 ile odaklanır. Seçili satırda + ve - miktarı değiştirir, Ctrl+I iskonto açar, Delete sepetten çıkarır.");

        // Satır düğmeleri fareyle kullanılır; aynı işlemler seçili satır üzerinde klavyeden
        // de yapılır. '+'/'-' KEY_TYPED olarak bağlanır ki hem ana klavye hem numpad (ve
        // Türkçe Q düzenindeki ayrı '+' tuşu) aynı eşlemeye düşsün.
        bindFocused(table, KeyStroke.getKeyStroke('+'), "pos.cart.increment",
                () -> withSelectedCartItem(cart, item -> changeQuantity(cart, item, 1)));
        bindFocused(table, KeyStroke.getKeyStroke('-'), "pos.cart.decrement",
                () -> withSelectedCartItem(cart, item -> changeQuantity(cart, item, -1)));
        bindFocused(table, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "pos.cart.remove",
                () -> withSelectedCartItem(cart, item -> removeFromCart(cart, item)));
        bindFocused(table, KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), "pos.cart.discount",
                () -> withSelectedCartItem(cart, item -> openLineDiscount(cart, item)));
    }

    private static void setColumnWidth(TableColumnModel columns, int index, int min, int preferred, int max) {
        columns.getColumn(index).setMinWidth(min);
        columns.getColumn(index).setPreferredWidth(preferred);
        columns.getColumn(index).setMaxWidth(max);
    }

    private String barcodeOf(SaleItem item) {
        Product product = item.getProductId() != null ? productById.get(item.getProductId()) : null;
        return product != null && product.getBarcode() != null ? product.getBarcode() : "—";
    }

    /** Satılan adet eldeki stoktan fazla mı? Stok bilinmiyorsa uyarı üretilmez. */
    private boolean isOversold(SaleItem item) {
        Integer stock = stockOf(item);
        return stock != null && item.getQuantity() > stock;
    }

    private Integer stockOf(SaleItem item) {
        Product product = item.getProductId() != null ? productById.get(item.getProductId()) : null;
        return product != null ? product.getStockQuantity() : null;
    }

    // -------------------------------------------------------------------------
    // Sepet hücreleri
    // -------------------------------------------------------------------------

    /** Barkod gibi ikincil sütunlar: seçili değilse sönük yazı rengi. */
    private static class MutedCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            if (!isSelected) setForeground(UIManager.getColor("Label.disabledForeground"));
            return this;
        }
    }

    /**
     * Ürün adı: stok yetersizse adın yanına eldeki adet "stok yetersiz" metniyle yazılır ve
     * tehlike token'ıyla boyanır — uyarı yalnızca renge bırakılmaz, nedeni tooltip'tedir.
     */
    private class ItemNameCellRenderer extends DefaultTableCellRenderer {
        private final Cart cart;

        ItemNameCellRenderer(Cart cart) {
            this.cart = cart;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            SaleItem item = cart.model.getItemAt(table.convertRowIndexToModel(row));
            boolean oversold = item != null && isOversold(item);
            if (oversold) setText(value + "  ·  stok yetersiz (" + stockOf(item) + ")");
            // Seçili satırda tablonun kendi vurgu rengi korunur; else dalı şart, aksi halde
            // DefaultTableCellRenderer son rengi saklar ve sonraki satırlar da kırmızı boyanır.
            if (!isSelected) setForeground(oversold ? SemanticColor.danger() : table.getForeground());
            setToolTipText(oversold
                    ? "Eldeki stok satılan adetten az — satış engellenmez, tamamlarken onay istenir."
                    : null);
            return this;
        }
    }

    /**
     * İskonto hücresi düğme olarak çizilir: iskonto yoksa sönük "İskonto" yazar, varsa düşülen
     * tutarı gösterir. Tıklama tablonun fare dinleyicisinde yakalanır (bkz. setupCartTable).
     */
    private class DiscountButtonRenderer implements TableCellRenderer {
        private final Cart cart;
        private final JButton button = new JButton();
        private final JPanel holder = new JPanel(new MigLayout("insets 0 3 0 3, fill", "[grow, fill]", "[center]"));

        DiscountButtonRenderer(Cart cart) {
            this.cart = cart;
            button.putClientProperty(FlatClientProperties.STYLE, "arc: 6; margin: 0,4,0,4; font: -2");
            button.setFocusable(false);
            holder.add(button, "h 22!");
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            BigDecimal amount = value instanceof BigDecimal bd ? bd : BigDecimal.ZERO;
            boolean none = amount.signum() <= 0;
            button.setText(none ? "İskonto" : "−" + Format.formatPrice(amount));
            button.setForeground(none ? UIManager.getColor("Label.disabledForeground") : SemanticColor.success());
            SaleItem item = cart.model.getItemAt(table.convertRowIndexToModel(row));
            LineDiscount discount = item != null ? cart.lineDiscounts.get(item) : null;
            holder.setToolTipText(discount != null && !discount.isEmpty()
                    ? "İskonto: " + discount.describe() + " — değiştirmek için tıklayın (Ctrl+I)"
                    : "Kalem iskontosu uygula (Ctrl+I)");
            holder.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return holder;
        }
    }

    /** Satır tutarı: sepetin asıl okunan sayısı, kalın. */
    private static class TotalCellRenderer extends CurrencyTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            c.setFont(table.getFont().deriveFont(Font.BOLD));
            return c;
        }
    }

    /** Miktar hücresinin ortak düzeni: [-] [adet] [+]. Renderer ve editör aynı iskeleti kullanır. */
    private static JPanel createQuantityBar(Component minus, Component center, Component plus) {
        JPanel bar = new JPanel(new MigLayout("insets 0 2 0 2, gap 2, fill", "[pref!][grow, fill][pref!]", "[center]"));
        bar.add(minus);
        bar.add(center, "h 22!, wmin 30");
        bar.add(plus);
        return bar;
    }

    private static ActionButton quantityButton(String iconPath, Color hoverColor, String tooltip) {
        ActionButton button = new ActionButton(new Ikon(iconPath, 0.55f), hoverColor);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        return button;
    }

    private static JTextField quantityField() {
        JTextField field = new JTextField();
        field.setHorizontalAlignment(SwingConstants.CENTER);
        field.putClientProperty(FlatClientProperties.STYLE, "arc: 6; margin: 0,1,0,1; font: bold -1");
        return field;
    }

    private static class QuantityRenderer implements TableCellRenderer {
        private final JTextField field = quantityField();
        private final JPanel bar = createQuantityBar(
                quantityButton("icons/minus.svg", SemanticColor.warning(), null),
                field,
                quantityButton("icons/plus.svg", SemanticColor.success(), null));

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            field.setText(value != null ? value.toString() : "");
            bar.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
            return bar;
        }
    }

    /**
     * Miktar editörü: eksi/artı düğmeleri adeti bir azaltır/artırır, ortadaki alana adet
     * doğrudan yazılır (Enter veya odak kaybı onaylar, Esc vazgeçer). 0 yazmak kalemi
     * sepetten çıkarır.
     */
    private class QuantityEditor extends AbstractCellEditor implements TableCellEditor {
        private final JTextField field = quantityField();
        private final JPanel bar;
        private final Cart cart;
        private SaleItem editing;

        QuantityEditor(Cart cart) {
            this.cart = cart;
            ActionButton minus = quantityButton("icons/minus.svg", SemanticColor.warning(), "Miktarı azalt (-)");
            ActionButton plus = quantityButton("icons/plus.svg", SemanticColor.success(), "Miktarı artır (+)");
            minus.addActionListener(e -> step(-1));
            plus.addActionListener(e -> step(1));
            field.getAccessibleContext().setAccessibleName("Miktar");
            field.addActionListener(e -> stopCellEditing());
            field.addFocusListener(new FocusAdapter() {
                @Override
                public void focusGained(FocusEvent e) {
                    SwingUtilities.invokeLater(field::selectAll);
                }
            });
            bar = createQuantityBar(minus, field, plus);
        }

        /** Düğme adımı düzenlemeyi iptal edip doğrudan kaleme uygulanır — alandaki yarım metin yok sayılır. */
        private void step(int delta) {
            SaleItem item = editing;
            cancelCellEditing();
            if (item != null) changeQuantity(cart, item, delta);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            editing = cart.model.getItemAt(table.convertRowIndexToModel(row));
            field.setText(value != null ? value.toString() : "");
            bar.setBackground(table.getSelectionBackground());
            return bar;
        }

        @Override
        public Object getCellEditorValue() {
            return field.getText();
        }
    }

    // -------------------------------------------------------------------------
    // Sağ sütun
    // -------------------------------------------------------------------------

    private JPanel buildRightPanel() {
        JPanel panel = new JPanel(new MigLayout("fillx, insets 0, gap 10", "[grow]", "[pref!]6[pref!]4[pref!]6[pref!][pref!]"));
        panel.add(buildAmountStrip(), "growx, wrap");
        panel.add(buildQuickCashRow(), "growx, wrap");
        panel.add(buildTotalsDetail(), "growx, wrap");
        panel.add(buildPaymentBox(), "growx, wrap");
        panel.add(buildDiscountAndCustomerBox(), "growx");
        return panel;
    }

    /**
     * Ödenen / Tutar / Kalan şeridi — tezgâhta ekrana ilk bakılan üç değer. Ödenen,
     * müşterinin verdiği parayı yazmak için bir girdi alanıdır ve yalnızca para üstü
     * hesabına yarar: Kalan anında düşer, tutarı aşarsa "Para Üstü"ne döner. Enter nakit
     * satışı tamamlar (F1 ile aynı).
     */
    private JPanel buildAmountStrip() {
        JPanel strip = new JPanel(new MigLayout("fillx, insets 0, gap 8", "[grow, sg amt, fill][grow, sg amt, fill][grow, sg amt, fill]", "[fill]"));

        tenderedField = new CurrencyField();
        tenderedField.setAvailableCurrencies(List.of("TRY"));
        tenderedField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, null);
        tenderedField.putClientProperty(FlatClientProperties.STYLE,
                "font: bold $h2.font; arc: 12; margin: 6,10,6,10");
        tenderedField.setHorizontalAlignment(SwingConstants.RIGHT);
        tenderedField.setToolTipText("Müşterinin verdiği tutar (F7) — para üstü hesabı içindir, Enter nakit satışı tamamlar");
        tenderedField.getAccessibleContext().setAccessibleName("Ödenen tutar");
        tenderedField.addPropertyChangeListener("value", e -> {
            if (syncingControls || cart() == null) return;
            cart().tendered = toBigDecimal(tenderedField.getValue());
            recalcTotals();
        });
        tenderedField.addActionListener(e -> {
            try {
                tenderedField.commitEdit();
            } catch (java.text.ParseException ignored) {
                // geçersiz metin — son geçerli değer kullanılır
            }
            payCash();
        });
        strip.add(amountCell("Ödenen", tenderedField, null));

        lblTotal = new JLabel(Format.formatPrice(BigDecimal.ZERO), SwingConstants.RIGHT);
        lblTotal.putClientProperty(FlatClientProperties.STYLE, "font: bold +9");
        strip.add(amountCell("Tutar", lblTotal, "background: darken($Panel.background, 4%)"));

        lblRemaining = new JLabel(Format.formatPrice(BigDecimal.ZERO), SwingConstants.RIGHT);
        JPanel remainingCell = amountCell("Kalan", lblRemaining, null);
        lblRemainingCaption = (JLabel) remainingCell.getComponent(0);
        strip.add(remainingCell);

        return strip;
    }

    /** Şeritteki tek kutu: üstte küçük başlık, altta değer. Başlık, değerin erişilebilir etiketidir. */
    private static JPanel amountCell(String caption, JComponent value, String extraStyle) {
        JPanel cell = new JPanel(new MigLayout("fill, insets 8 10 10 10, gap 0", "[grow, fill]", "[pref!]2[grow, fill]"));
        cell.putClientProperty(FlatClientProperties.STYLE, "arc: 14; background: lighten($Panel.background, 3%)"
                + (extraStyle != null ? "; " + extraStyle : ""));
        JLabel label = new JLabel(caption);
        label.putClientProperty(FlatClientProperties.STYLE, "font: -1; foreground: $Label.disabledForeground");
        label.setLabelFor(value);
        cell.add(label, "wrap");
        cell.add(value, "wmin 0, h 44!");
        return cell;
    }

    /**
     * Hızlı para düğmeleri: her basış banknot tutarını "Ödenen"e ekler (50 + 20 = 70 gibi
     * birden fazla banknot verildiğinde art arda basılır). Sondaki düğme alanı sıfırlar.
     */
    private JPanel buildQuickCashRow() {
        JPanel row = new JPanel(new MigLayout("fillx, insets 0, gap 6",
                "[grow, sg cash, fill][grow, sg cash, fill][grow, sg cash, fill][grow, sg cash, fill][pref!]", "[]"));
        for (int amount : QUICK_CASH) {
            BigDecimal value = BigDecimal.valueOf(amount);
            JButton button = new JButton(Format.formatPrice(value));
            button.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,4,6,4; font: bold");
            button.setToolTipText("Ödenen tutara " + Format.formatPrice(value) + " ekle");
            button.getAccessibleContext().setAccessibleName("Ödenene " + amount + " ekle");
            button.setFocusable(false);
            button.addActionListener(e -> {
                Cart cart = cart();
                if (cart != null && !submitting) setTendered(cart, cart.tendered.add(value));
            });
            row.add(button);
        }
        JButton reset = new JButton(new Ikon("icons/x.svg", 0.8f));
        reset.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        reset.setToolTipText("Ödenen tutarı sıfırla");
        reset.getAccessibleContext().setAccessibleName("Ödenen tutarı sıfırla");
        reset.setFocusable(false);
        reset.addActionListener(e -> {
            Cart cart = cart();
            if (cart != null && !submitting) setTendered(cart, BigDecimal.ZERO);
        });
        row.add(reset);
        return row;
    }

    /** Şeridin altındaki ikincil satır: kalem iskontoları düşülmüş, fiş indirimi öncesi ara toplam. */
    private JPanel buildTotalsDetail() {
        JPanel row = new JPanel(new MigLayout("fillx, insets 0 4 0 4, gap 6", "[][]push", "[]"));
        JLabel subtotalCaption = new JLabel("Ara toplam");
        subtotalCaption.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        row.add(subtotalCaption);
        lblSubtotal = new JLabel(Format.formatPrice(BigDecimal.ZERO));
        subtotalCaption.setLabelFor(lblSubtotal);
        row.add(lblSubtotal);
        return row;
    }

    private JPanel buildDiscountAndCustomerBox() {
        JPanel box = new JPanel(new MigLayout("fillx, insets 10", "[][grow][pref!]", "[][]"));
        box.putClientProperty(FlatClientProperties.STYLE, "background: lighten($Panel.background, 2%); arc: 12;");

        JLabel customerLabel = new JLabel("Müşteri");
        box.add(customerLabel);
        customerCombo = new JComboBox<>();
        customerCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        customerCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                setText(customerDisplayName(value));
                return this;
            }
        });
        // Yazarak arama: yüzlerce müşteride açılır listeyi kaydırmak tezgâhı yavaşlatıyordu.
        // Katı eşleşme (decorate'in varsayılanı) yalnızca listedeki öğelerin seçilmesine izin
        // verir, serbest metin seçili öğe olarak kalmaz.
        AutoCompleteDecorator.decorate(customerCombo, new ObjectToStringConverter() {
            @Override
            public String getPreferredStringForItem(Object item) {
                return customerDisplayName(item);
            }
        });
        // Müşteri seçimi sepete aittir; sekme başlığı müşterinin adını alır.
        customerCombo.addActionListener(e -> {
            Cart cart = cart();
            if (syncingControls || cart == null) return;
            cart.customer = selectedCustomer();
            refreshTabTitle(cart);
        });
        customerLabel.setLabelFor(customerCombo);
        customerCombo.setToolTipText("Yazarak arayın — açık hesap satışı için müşteri seçimi zorunlu");
        box.add(customerCombo, "span 2, growx, wrap");

        JLabel discountLabel = new JLabel("Fiş indirimi");
        box.add(discountLabel);
        discountTypeCombo = new JComboBox<>(DiscountType.values());
        discountTypeCombo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        discountTypeCombo.setRenderer(discountTypeRenderer());
        discountTypeCombo.addActionListener(e -> {
            updateDiscountFieldMode();
            if (syncingControls || cart() == null) return;
            cart().discountType = (DiscountType) discountTypeCombo.getSelectedItem();
            clampPercentDiscount();
            recalcTotals();
        });
        discountLabel.setLabelFor(discountTypeCombo);
        box.add(discountTypeCombo, "growx");

        discountValueField = new CurrencyField();
        discountValueField.setAvailableCurrencies(List.of("TRY"));
        discountValueField.getAccessibleContext().setAccessibleName("Fiş indirimi değeri");
        discountCurrencyButton = discountValueField.getClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT);
        discountValueField.addPropertyChangeListener("value", e -> {
            if (syncingControls || cart() == null) return;
            cart().discountValue = toBigDecimal(discountValueField.getValue());
            clampPercentDiscount();
            recalcTotals();
        });
        box.add(discountValueField, "growx");
        updateDiscountFieldMode();

        return box;
    }

    /** İndirim türü combo'larında "%" / para simgesi gösterilir; enum adı kasiyere anlamsız. */
    private static DefaultListCellRenderer discountTypeRenderer() {
        return new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value == DiscountType.AMOUNT) setText("Tutar (" + AppLocale.currencySymbol() + ")");
                else if (value == DiscountType.PERCENT) setText("Yüzde (%)");
                return this;
            }
        };
    }

    private static String customerDisplayName(Object value) {
        return value instanceof Customer customer ? customer.getFullName() : "Perakende (müşteri yok)";
    }

    /** Combo'da seçili müşteri; perakende (boş seçim) için null. */
    private Customer selectedCustomer() {
        return customerCombo.getSelectedItem() instanceof Customer customer ? customer : null;
    }

    /**
     * Yüzde modunda alanın para birimi düğmesi gizlenir — "%15" girilen alanda ₺ simgesi
     * durması değerin tutar sanılmasına yol açıyordu. Tutar moduna dönünce geri takılır.
     */
    private void updateDiscountFieldMode() {
        if (discountValueField == null) return;
        boolean percent = discountTypeCombo.getSelectedItem() == DiscountType.PERCENT;
        discountValueField.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT,
                percent ? null : discountCurrencyButton);
        discountValueField.setToolTipText(percent ? "Fiş indirimi yüzdesi (en fazla %100)" : "Fiş indirimi tutarı");
    }

    /**
     * Yüzde indiriminde alana 100'den büyük değer yazılırsa 100'e çeker — hesap zaten
     * sınırlıyor ama alanda "150" görünmesi kasiyeri yanıltır. Geri yazılan değer tekrar
     * "value" olayı üretir; ikinci turda koşul sağlanmadığı için döngü oluşmaz.
     */
    private void clampPercentDiscount() {
        if (discountTypeCombo == null || discountValueField == null) return;
        if (discountTypeCombo.getSelectedItem() != DiscountType.PERCENT) return;
        if (toBigDecimal(discountValueField.getValue()).compareTo(HUNDRED) > 0) {
            discountValueField.setValue(100.0);
        }
    }

    /**
     * Ödeme yöntemi düğmeleri satışın tek kapanış noktasıdır ve F1–F4 ile de basılır. Yalnızca
     * Parçalı pencere açar; diğerleri satışı doğrudan yazar.
     */
    private JPanel buildPaymentBox() {
        JPanel box = new JPanel(new MigLayout("fillx, insets 0, wrap 2, gap 8", "[grow, sg pay][grow, sg pay]", "[][]"));
        box.add(createPaymentButton("Nakit", "icons/banknote.svg", KeyEvent.VK_F1, SemanticColor.success(),
                "Tutarın tamamı nakit — Ödenen girildiyse para üstü hesaplanır", this::payCash), "growx");
        box.add(createPaymentButton("POS", "icons/credit-card.svg", KeyEvent.VK_F2, SemanticColor.info(),
                "Tutarın tamamı kredi/banka kartı", this::payByCard), "growx");
        box.add(createPaymentButton("Açık Hesap", "icons/hand-coins.svg", KeyEvent.VK_F3, SemanticColor.warning(),
                "Tutarın tamamı seçili müşterinin cari hesabına", this::payOnAccount), "growx");
        box.add(createPaymentButton("Parçalı", "icons/sigma.svg", KeyEvent.VK_F4, SemanticColor.action(),
                "Tutarı nakit, kart ve açık hesap arasında paylaştır", this::openSplitPayment), "growx");
        return box;
    }

    /**
     * Ödeme düğmesi: yöntem adı ve kısayolu iki satırda; yöntemi renginden de tanınsın diye
     * zemin, yöntemin anlam renginden hafif bir tonla boyanır (metin ön plan rengiyle kalır).
     */
    private JButton createPaymentButton(String label, String iconPath, int keyCode, Color accent,
                                        String description, Runnable action) {
        String key = KeyEvent.getKeyText(keyCode);
        JButton button = new JButton("<html><center><b>" + label + "</b><br><small>" + key + "</small></center></html>",
                new Ikon(iconPath, 1.2f));
        String tint = SemanticColor.hex(accent);
        button.putClientProperty(FlatClientProperties.STYLE, "arc: 12; margin: 12,8,12,8; font: +3;"
                + " background: fade(" + tint + ", 18%); hoverBackground: fade(" + tint + ", 28%);"
                + " pressedBackground: fade(" + tint + ", 38%); borderColor: fade(" + tint + ", 55%)");
        button.setToolTipText(label + " (" + key + ") — " + description);
        button.getAccessibleContext().setAccessibleName(label + " ile ödeme al");
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        bindKey(KeyStroke.getKeyStroke(keyCode, 0), "pos.pay." + key, e -> {
            if (button.isEnabled()) action.run();
        });
        paymentButtons.add(button);
        return button;
    }

    // =========================================================================
    // ÜRÜN ARAMA PENCERESİ
    // =========================================================================

    /**
     * Ürün arama penceresi (F6 / "Ürün Ara"). Arama → liste → sepet akışı fare gerektirmez:
     * aramada ↓ listeye geçer, Enter tek sonucu doğrudan ekler (birden fazlaysa listeye
     * geçer); listede Enter ya da çift tık seçili ürünü ekler. Ekleme pencereyi kapatır ve
     * odağı barkod alanına geri verir — aksi halde sonraki barkod okutması kaybolur.
     */
    private void openProductSearch() {
        if (submitting || ModalDialog.isIdExist(PRODUCT_SEARCH_MODAL_ID)) return;

        List<ColumnDef<Product>> columns = Arrays.asList(
                new ColumnDef<>("Ürün", String.class, Product::getName),
                new ColumnDef<>("Barkod", String.class, Product::getBarcode),
                new ColumnDef<>("Stok", Integer.class, Product::getStockQuantity),
                new ColumnDef<>("Fiyat", BigDecimal.class, Product::getSalePrice)
        );
        GenericTableModel<Product> model = new GenericTableModel<>(columns);
        JTable table = new JTable(model);
        table.setRowHeight(34);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getTableHeader().setReorderingAllowed(false);
        table.getAccessibleContext().setAccessibleName("Ürün listesi");
        table.getColumnModel().getColumn(2).setCellRenderer(new ProductStockCellRenderer(model));
        table.getColumnModel().getColumn(3).setCellRenderer(new CurrencyTableCellRenderer());
        setColumnWidth(table.getColumnModel(), 1, 110, 150, 200);
        setColumnWidth(table.getColumnModel(), 2, 80, 100, 130);
        setColumnWidth(table.getColumnModel(), 3, 90, 110, 150);

        JTextField search = new JTextField();
        search.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Ürün adı veya barkod ara...");
        search.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 6,10,6,10; font: +1");
        search.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 1f));
        search.setToolTipText("↓ ile listeye geç, Enter ile tek sonucu sepete ekle");
        search.getAccessibleContext().setAccessibleName("Ürün ara");

        CardLayout cards = new CardLayout();
        JPanel area = new JPanel(cards);
        area.setOpaque(false);
        TableStatePanel state = new TableStatePanel();
        area.add(new JScrollPane(table), CARD_DATA);
        area.add(state, CARD_STATE);

        Runnable filter = () -> {
            String text = search.getText().trim();
            String needle = normalizeForSearch(text);
            model.setData(text.isEmpty() ? saleProducts : saleProducts.stream()
                    .filter(p -> normalizeForSearch(p.getName()).contains(needle)
                            || normalizeForSearch(p.getBarcode()).contains(needle))
                    .toList());
            if (table.getRowCount() > 0) {
                cards.show(area, CARD_DATA);
                return;
            }
            // Liste boşsa nedenini söyle: yükleniyor mu, arama mı eşleşmedi, ürün mü yok.
            if (!productsLoaded) {
                state.showLoading();
            } else if (!text.isEmpty()) {
                state.showNoResults(text, () -> {
                    search.setText("");
                    search.requestFocusInWindow();
                });
            } else {
                state.showMessage("icons/package-plus.svg", "Satışa açık ürün yok",
                        "Ürünler ekranından ürün ekleyin; barkodla satış yine çalışır.");
            }
            cards.show(area, CARD_STATE);
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter.run(); }
            public void removeUpdate(DocumentEvent e) { filter.run(); }
            public void changedUpdate(DocumentEvent e) { filter.run(); }
        });
        filter.run();

        Runnable addSelected = () -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            Product product = model.getItemAt(table.convertRowIndexToModel(row));
            if (product == null) return;
            AppModal.closeModal(PRODUCT_SEARCH_MODAL_ID);
            addToCart(product);
            barcodeField.requestFocusInWindow();
        };
        Runnable focusList = () -> {
            if (table.getRowCount() == 0) return;
            if (table.getSelectedRow() < 0) table.setRowSelectionInterval(0, 0);
            table.requestFocusInWindow();
        };
        bindFocused(search, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "pos.search.toList", focusList);
        search.addActionListener(e -> {
            if (table.getRowCount() == 1) {
                table.setRowSelectionInterval(0, 0);
                addSelected.run();
            } else {
                focusList.run();
            }
        });
        // JTable'ın varsayılan Enter'ı (sonraki satıra geç) WHEN_FOCUSED eşlemesiyle ezilir.
        bindFocused(table, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "pos.list.add", addSelected);
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) addSelected.run();
            }
        });

        JPanel panel = new JPanel(new MigLayout("fill, insets 4 15 10 15, gapy 10", "[grow]", "[pref!][grow]"));
        panel.add(search, "growx, wrap");
        panel.add(area, "grow");
        panel.setPreferredSize(new Dimension(760, 480));

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Kapat", SimpleModalBorder.CLOSE_OPTION)
        };
        SimpleModalBorder modal = new SimpleModalBorder(panel, "Ürün Ara", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                search.requestFocusInWindow();
            } else if (action == SimpleModalBorder.CLOSE_OPTION || action == SimpleModalBorder.CANCEL_OPTION) {
                SwingUtilities.invokeLater(() -> barcodeField.requestFocusInWindow());
            }
        });
        AppModal.showModal(this, modal, PRODUCT_SEARCH_MODAL_ID);
    }

    /**
     * Ürün listesi stok sütunu: tükenmiş ürün tehlike, kritik seviyedeki uyarı token'ıyla
     * işaretlenir. Durum metne de yazılır ("tükendi"/"az") — yalnızca renge bırakılmaz.
     */
    private static class ProductStockCellRenderer extends DefaultTableCellRenderer {
        private final GenericTableModel<Product> model;

        ProductStockCellRenderer(GenericTableModel<Product> model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
                                                       boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
            setHorizontalAlignment(SwingConstants.CENTER);

            Product product = model.getItemAt(table.convertRowIndexToModel(row));
            Integer stock = product != null ? product.getStockQuantity() : null;
            Integer min = product != null ? product.getMinStockLevel() : null;
            boolean out = stock != null && stock <= 0;
            boolean low = !out && stock != null && min != null && min > 0 && stock <= min;

            if (stock == null) setText("—");
            else if (out) setText(stock + " · tükendi");
            else if (low) setText(stock + " · az");

            // Seçili satırda tablonun vurgu rengi korunur; else dalı önceki satırın rengini sıfırlar.
            if (!isSelected) {
                setForeground(out ? SemanticColor.danger() : low ? SemanticColor.warning() : table.getForeground());
            }
            setFont(getFont().deriveFont(out || low ? Font.BOLD : Font.PLAIN));
            setToolTipText(out ? "Stok yok — satış engellenmez, tamamlarken onay istenir."
                    : low ? "Stok kritik seviyede (en az " + min + ")." : null);
            return this;
        }
    }

    // =========================================================================
    // KALEM İSKONTOSU PENCERESİ
    // =========================================================================

    /**
     * Ürün iskontosu penceresi: brüt tutar, iki kademe iskonto (her biri % veya tutar) ve
     * canlı özet (düşülen tutar, net tutar). Uygula kaleme yazar, Temizle alanları sıfırlar
     * (pencere açık kalır, Uygula ile iskonto kaldırılır), İptal değişiklik yapmaz.
     */
    private void openLineDiscount(Cart cart, SaleItem item) {
        if (submitting || ModalDialog.isIdExist(LINE_DISCOUNT_MODAL_ID)) return;
        if (cart.table.isEditing()) cart.table.getCellEditor().cancelCellEditing();

        BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        LineDiscount current = cart.lineDiscounts.get(item);

        JPanel panel = new JPanel(new MigLayout("insets 6 20 12 20, fillx, wrap 3, w 400!",
                "[pref!]12[130!][grow, fill]", "[]16[]8[]18[]"));

        JLabel grossCaption = new JLabel("Brüt Tutar");
        JLabel grossValue = new JLabel(Format.formatPrice(gross));
        grossValue.putClientProperty(FlatClientProperties.STYLE, "font: bold $h3.font");
        grossCaption.setLabelFor(grossValue);
        JLabel itemName = new JLabel(item.getItemName() + "  ×" + item.getQuantity());
        itemName.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        panel.add(grossCaption);
        panel.add(grossValue, "span 2, split 2");
        panel.add(itemName, "gapleft push, wmin 0");

        JComboBox<DiscountType> type1 = discountTypeCombo(current != null ? current.type1() : DiscountType.PERCENT);
        CurrencyField value1 = plainAmountField(current != null ? current.value1() : BigDecimal.ZERO, "İskonto 1 değeri");
        JComboBox<DiscountType> type2 = discountTypeCombo(current != null ? current.type2() : DiscountType.PERCENT);
        CurrencyField value2 = plainAmountField(current != null ? current.value2() : BigDecimal.ZERO, "İskonto 2 değeri");

        JLabel label1 = new JLabel("İskonto 1");
        label1.setLabelFor(value1);
        panel.add(label1);
        panel.add(type1, "growx");
        panel.add(value1);

        JLabel label2 = new JLabel("İskonto 2");
        label2.setLabelFor(value2);
        panel.add(label2);
        panel.add(type2, "growx");
        panel.add(value2);

        JPanel summary = new JPanel(new MigLayout("fillx, insets 12 14 12 14, gap 4", "[grow]", "[][]"));
        summary.putClientProperty(FlatClientProperties.STYLE, "arc: 12; background: lighten($Panel.background, 4%)");
        JLabel discountLine = new JLabel();
        discountLine.putClientProperty(FlatClientProperties.STYLE,
                "font: bold; foreground: " + SemanticColor.hex(SemanticColor.danger()));
        JLabel netLine = new JLabel();
        netLine.putClientProperty(FlatClientProperties.STYLE,
                "font: bold $h3.font; foreground: " + SemanticColor.hex(SemanticColor.success()));
        summary.add(discountLine, "wmin 0, wrap");
        summary.add(netLine, "wmin 0");
        panel.add(summary, "span 3, growx");

        java.util.function.Supplier<LineDiscount> read = () -> new LineDiscount(
                (DiscountType) type1.getSelectedItem(), clampFor(type1, value1),
                (DiscountType) type2.getSelectedItem(), clampFor(type2, value2));
        Runnable refresh = () -> {
            LineDiscount discount = read.get();
            BigDecimal amount = discount.amount(gross);
            discountLine.setText("İskonto: " + discount.describe() + " = −" + Format.formatPrice(amount));
            netLine.setText("Net Tutar: " + Format.formatPrice(gross.subtract(amount)));
        };
        refresh.run();
        for (JComboBox<DiscountType> combo : List.of(type1, type2)) combo.addActionListener(e -> refresh.run());
        for (CurrencyField field : List.of(value1, value2)) field.addPropertyChangeListener("value", e -> refresh.run());

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("Temizle", CLEAR_OPTION),
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION),
                new SimpleModalBorder.Option("Uygula", SimpleModalBorder.OK_OPTION)
        };
        SimpleModalBorder modal = new SimpleModalBorder(panel, "Ürün İskontosu", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                value1.requestFocusInWindow();
                value1.selectAll();
                return;
            }
            if (action == CLEAR_OPTION) {
                controller.consume();
                value1.setValue(0.0);
                value2.setValue(0.0);
                value1.requestFocusInWindow();
                return;
            }
            if (action == SimpleModalBorder.OK_OPTION) {
                LineDiscount discount = read.get();
                SwingUtilities.invokeLater(() -> setLineDiscount(cart, item, discount));
            }
            SwingUtilities.invokeLater(() -> cart.table.requestFocusInWindow());
        });

        // Değer alanlarında Enter uygular: metin önce değere işlenir.
        for (CurrencyField field : List.of(value1, value2)) {
            field.addActionListener(e -> {
                try {
                    field.commitEdit();
                } catch (java.text.ParseException ignored) {
                    // geçersiz metin — son geçerli değer kullanılır
                }
                modal.doAction(SimpleModalBorder.OK_OPTION);
            });
        }
        AppModal.showModal(this, modal, LINE_DISCOUNT_MODAL_ID);
    }

    private static JComboBox<DiscountType> discountTypeCombo(DiscountType selected) {
        JComboBox<DiscountType> combo = new JComboBox<>(DiscountType.values());
        combo.putClientProperty(FlatClientProperties.STYLE, "arc: 10");
        combo.setRenderer(discountTypeRenderer());
        combo.setSelectedItem(selected != null ? selected : DiscountType.PERCENT);
        return combo;
    }

    /** Para birimi düğmesi olmayan sayı alanı — iskonto ve parçalı ödeme pencerelerinde. */
    private static CurrencyField plainAmountField(BigDecimal value, String accessibleName) {
        CurrencyField field = new CurrencyField();
        field.setAvailableCurrencies(List.of("TRY"));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, null);
        field.setHorizontalAlignment(SwingConstants.RIGHT);
        field.setValue(value.doubleValue());
        field.getAccessibleContext().setAccessibleName(accessibleName);
        return field;
    }

    /** Yüzde kademesinde 100'ün üstü anlamsız; alana geri yazılır ki kasiyer "150" görmesin. */
    private static BigDecimal clampFor(JComboBox<DiscountType> type, CurrencyField field) {
        BigDecimal value = toBigDecimal(field.getValue());
        if (type.getSelectedItem() == DiscountType.PERCENT && value.compareTo(HUNDRED) > 0) {
            SwingUtilities.invokeLater(() -> field.setValue(100.0));
            return HUNDRED;
        }
        return value;
    }

    // =========================================================================
    // VERİ YÜKLEME
    // =========================================================================

    private void loadProducts() {
        productService.getAll().thenAccept(products -> SwingUtilities.invokeLater(() -> {
            saleProducts = products;
            productsLoaded = true;
            for (Product product : products) {
                if (product.getId() != null) productById.put(product.getId(), product);
            }
            // Satış sonrası stok düştüğü için sepetlerin stok uyarısı da tazelenmeli.
            for (Cart cart : carts) cart.table.repaint();
        })).exceptionally(ex -> ErrorHandler.handle(this, "Ürünler yüklenemedi", ex));
    }

    private void loadCustomers() {
        customerService.getAll().thenAccept(customers -> SwingUtilities.invokeLater(() -> {
            // Yeniden doldururken combo'nun ara seçimleri sepete yazılmasın; sonra seçili
            // sepetin müşterisi id ile yeniden seçilir.
            syncingControls = true;
            try {
                customerCombo.removeAllItems();
                customerCombo.addItem(null);
                for (Customer c : customers) customerCombo.addItem(c);
                Cart cart = cart();
                customerCombo.setSelectedItem(cart != null ? findCustomerInCombo(cart.customer) : null);
            } finally {
                syncingControls = false;
            }
        })).exceptionally(ex -> ErrorHandler.handle(this, "Müşteriler yüklenemedi", ex));
    }

    /**
     * Türkçe kurallarla küçültür (İ→i, I→ı), ardından ı'yı i'ye indirir. Böylece hem
     * "İPHONE"/"iphone" hem "IŞIK"/"ışık" eşleşir; salt Türkçe ya da salt kök yerel
     * küçültme bu çiftlerden birini kaçırıyordu.
     */
    private static String normalizeForSearch(String value) {
        if (value == null) return "";
        return value.toLowerCase(TURKISH).replace('ı', 'i');
    }

    // =========================================================================
    // BARKOD / SEPET
    // =========================================================================

    private void onBarcodeEnter() {
        String barcode = barcodeField.getText().trim();
        barcodeField.setText("");
        if (barcode.isEmpty() || submitting) return;

        productService.get(barcode).thenAccept(opt -> SwingUtilities.invokeLater(() -> {
            if (opt.isEmpty()) {
                SoundPlayer.error();
                Toast.show(this, Toast.Type.ERROR, Messages.get("toast.sale.itemNotFound"));
                return;
            }
            addToCart(opt.get());
        })).exceptionally(ex -> ErrorHandler.handle(this, "Barkod aranamadı", ex));
    }

    /** Ürünü seçili sepete ekler; sepette varsa adedi bir artırır. */
    private void addToCart(Product product) {
        Cart cart = cart();
        SoundPlayer.success();

        // Barkodla gelen ürün listede olmayabilir; barkod/stok bilgisini burada da kaydet.
        if (product.getId() != null) productById.put(product.getId(), product);

        for (SaleItem item : cart.items) {
            if (product.getId().equals(item.getProductId())) {
                item.setQuantity(item.getQuantity() + 1);
                recalcCart(cart);
                revealCartItem(cart, item);
                return;
            }
        }

        SaleItem item = new SaleItem();
        item.setProductId(product.getId());
        item.setItemName(product.getName());
        item.setQuantity(1);
        item.setPurchasePrice(product.getPurchasePrice());
        item.setUnitPrice(product.getSalePrice() != null ? product.getSalePrice() : BigDecimal.ZERO);
        item.setSaleCurrency("TRY");
        cart.items.add(item);
        recalcCart(cart);
        revealCartItem(cart, item);
    }

    /**
     * Son okutulan kalemi seçer ve görünür alana kaydırır: uzun sepette yeni satır görünmez
     * kalıyordu ve F8 ile gelinen +/-/Delete ilk satıra uygulanıyordu, son okutulana değil.
     */
    private static void revealCartItem(Cart cart, SaleItem item) {
        int modelRow = cart.items.indexOf(item);
        if (modelRow < 0) return;
        int viewRow = cart.table.convertRowIndexToView(modelRow);
        cart.table.setRowSelectionInterval(viewRow, viewRow);
        cart.table.scrollRectToVisible(cart.table.getCellRect(viewRow, 0, true));
    }

    private void changeQuantity(Cart cart, SaleItem item, int delta) {
        setQuantity(cart, item, item.getQuantity() + delta);
    }

    /** Adedi doğrudan yazar; 0 ve altı kalemi sepetten çıkarır. */
    private void setQuantity(Cart cart, SaleItem item, int quantity) {
        if (!cart.items.contains(item)) return;
        if (quantity <= 0) {
            removeFromCart(cart, item);
            return;
        }
        item.setQuantity(quantity);
        recalcCart(cart);
        revealCartItem(cart, item);
    }

    /** Kalem iskontosunu kaydeder; boş iskonto kaydı siler. */
    private void setLineDiscount(Cart cart, SaleItem item, LineDiscount discount) {
        if (!cart.items.contains(item)) return;
        if (discount.isEmpty()) cart.lineDiscounts.remove(item);
        else cart.lineDiscounts.put(item, discount);
        recalcCart(cart);
        revealCartItem(cart, item);
    }

    private void removeFromCart(Cart cart, SaleItem item) {
        cart.items.remove(item);
        cart.lineDiscounts.remove(item);
        recalcCart(cart);
    }

    /** Adet alanına yazılan metin: yalnızca 0–99999 arası tam sayı; geçersizse null (değişiklik yok). */
    private static Integer parseQuantity(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            return value >= 0 && value <= 99_999 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Seçili sepet satırına klavye işlemi uygular. {@code setData} seçimi sıfırladığı için
     * işlem sonrası aynı konum (satır silindiyse bir üstü) yeniden seçilir — art arda
     * +/+/+ veya Delete/Delete basılabilsin.
     */
    private static void withSelectedCartItem(Cart cart, java.util.function.Consumer<SaleItem> action) {
        JTable table = cart.table;
        int row = table.getSelectedRow();
        if (row < 0) return;
        SaleItem item = cart.model.getItemAt(table.convertRowIndexToModel(row));
        if (item == null) return;
        action.accept(item);
        int count = table.getRowCount();
        if (count > 0) {
            int target = Math.min(row, count - 1);
            table.setRowSelectionInterval(target, target);
        }
    }

    /**
     * Sepeti yeniden çizer. Satır tutarı burada hesaplanır: {@link SaleItem#getLineTotal()}
     * yalnızca checkout'ta servis tarafından doldurulduğu için aksi halde "Tutar" sütunu her
     * satırda 0 gösteriyordu. Kalem iskontosu her seferinde güncel brüt tutardan yeniden
     * hesaplanıp bileşik tutar olarak kaleme yazılır — servis aynı değeri AMOUNT olarak
     * uygular, sonuç birebir tutar.
     */
    private void recalcCart(Cart cart) {
        if (cart.table.isEditing()) cart.table.getCellEditor().cancelCellEditing();
        for (SaleItem item : cart.items) {
            BigDecimal gross = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
            LineDiscount discount = cart.lineDiscounts.get(item);
            BigDecimal amount = discount != null ? discount.amount(gross) : BigDecimal.ZERO;
            item.setLineDiscountType(amount.signum() > 0 ? DiscountType.AMOUNT : null);
            item.setLineDiscountValue(amount);
            item.setLineTotal(gross.subtract(amount));
        }
        cart.model.setData(cart.items);
        cart.updateState();
        refreshTabTitle(cart);
        if (cart == cart()) recalcTotals();
    }

    // =========================================================================
    // ÖDEME / TOPLAM
    // =========================================================================

    /** Ödeme almadan önce ortak ön koşul: sepet boş olamaz ve önceki satış yazılıyor olamaz. */
    private boolean canTakePayment() {
        if (submitting) return false;
        if (cart().items.isEmpty()) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.emptyCart"));
            barcodeField.requestFocusInWindow();
            return false;
        }
        return true;
    }

    /**
     * Nakit (F1): tutarın tamamı nakit alınır. "Ödenen" boşsa tam para kabul edilir; doluysa
     * para üstü hesaplanıp fişe yazılır. Ödenen tutardan azsa satış yazılmaz — kasiyer ya
     * tutarı düzeltir ya da Parçalı ile kalanı başka yönteme verir.
     */
    private void payCash() {
        if (!canTakePayment()) return;
        Cart cart = cart();
        BigDecimal total = computeTotal(cart);
        BigDecimal tendered = cart.tendered;
        if (tendered.signum() > 0 && tendered.compareTo(total) < 0) {
            SoundPlayer.error();
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.tenderedInsufficient",
                    Format.formatPrice(total.subtract(tendered))));
            tenderedField.requestFocusInWindow();
            return;
        }
        BigDecimal change = tendered.signum() > 0 ? tendered.subtract(total) : BigDecimal.ZERO;
        finalizeSale(cart, singlePayment(PaymentType.CASH, total), change);
    }

    /** POS (F2): tutarın tamamı kartla alınır; "Ödenen" alanı dikkate alınmaz. */
    private void payByCard() {
        if (!canTakePayment()) return;
        Cart cart = cart();
        finalizeSale(cart, singlePayment(PaymentType.CREDIT_CARD, computeTotal(cart)), BigDecimal.ZERO);
    }

    /** Açık Hesap (F3): ödeme kaydı oluşmaz, tutarın tamamı seçili müşterinin cari hesabına yazılır. */
    private void payOnAccount() {
        if (!canTakePayment()) return;
        Cart cart = cart();
        if (computeTotal(cart).signum() > 0 && cart.customer == null) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.creditRequiresCustomer"));
            customerCombo.requestFocusInWindow();
            return;
        }
        finalizeSale(cart, List.of(), BigDecimal.ZERO);
    }

    /** Sıfır tutarlı satışta (ör. %100 indirim) ödeme kaydı oluşturulmaz. */
    private static List<Payment> singlePayment(PaymentType type, BigDecimal amount) {
        return amount.signum() > 0 ? List.of(newPayment(type, amount)) : List.of();
    }

    /**
     * Parçalı (F4): tutar nakit, kart ve açık hesap arasında paylaştırılır. Havale/EFT
     * tezgâhta nakitle aynı işlem gördüğü için ayrı satır yok.
     * Her satırdaki "Kalan" düğmesi dağıtılmamış tutarı o satıra ekler. Toplam, satış
     * tutarına tam eşit olmadan onay kabul edilmez; açık hesap payı varsa müşteri zorunludur.
     * Açık hesap payı ayrı bir ödeme kaydı değildir — servis ödenmeyen kısmı cariye yazar.
     */
    private void openSplitPayment() {
        if (!canTakePayment() || ModalDialog.isIdExist(SPLIT_PAYMENT_MODAL_ID)) return;
        Cart cart = cart();
        BigDecimal total = computeTotal(cart);

        JPanel panel = new JPanel(new MigLayout("insets 6 20 12 20, fillx, wrap 3, w 420!",
                "[pref!]12[grow, fill]8[pref!]", "[]14[][][]14[][]"));

        JLabel totalCaption = new JLabel("Satış Tutarı");
        JLabel totalValue = new JLabel(Format.formatPrice(total));
        totalValue.putClientProperty(FlatClientProperties.STYLE, "font: bold $h3.font");
        totalCaption.setLabelFor(totalValue);
        panel.add(totalCaption);
        panel.add(totalValue, "span 2");

        // Satır sırası ekrandaki sıradır; null tür açık hesap payıdır (ödeme kaydı oluşmaz).
        Map<String, PaymentType> rows = new LinkedHashMap<>();
        rows.put("Nakit", PaymentType.CASH);
        rows.put("POS", PaymentType.CREDIT_CARD);
        rows.put("Açık Hesap", null);
        Map<PaymentType, CurrencyField> fields = new LinkedHashMap<>();

        JLabel remainderLine = new JLabel();
        JLabel customerHint = new JLabel(" ");
        customerHint.putClientProperty(FlatClientProperties.STYLE,
                "font: -1; foreground: " + SemanticColor.hex(SemanticColor.warning()));

        java.util.function.Supplier<BigDecimal> assigned = () -> fields.values().stream()
                .map(f -> toBigDecimal(f.getValue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Runnable refresh = () -> {
            if (fields.size() < rows.size()) return;
            BigDecimal diff = total.subtract(assigned.get());
            String color;
            if (diff.signum() > 0) {
                remainderLine.setText("Dağıtılmayan: " + Format.formatPrice(diff));
                color = SemanticColor.hex(SemanticColor.danger());
            } else if (diff.signum() < 0) {
                remainderLine.setText("Fazla dağıtıldı: " + Format.formatPrice(diff.negate()));
                color = SemanticColor.hex(SemanticColor.danger());
            } else {
                remainderLine.setText("Tutar tamamen dağıtıldı — onaylayınca satış tamamlanır.");
                color = SemanticColor.hex(SemanticColor.success());
            }
            remainderLine.putClientProperty(FlatClientProperties.STYLE, "font: bold; foreground: " + color);
            boolean needsCustomer = toBigDecimal(fields.get(null).getValue()).signum() > 0 && cart.customer == null;
            customerHint.setText(needsCustomer ? "Açık hesap payı için önce müşteri seçin." : " ");
        };

        for (Map.Entry<String, PaymentType> row : rows.entrySet()) {
            CurrencyField field = plainAmountField(BigDecimal.ZERO, row.getKey() + " payı");
            field.putClientProperty(FlatClientProperties.STYLE, "font: +2");
            field.addPropertyChangeListener("value", e -> refresh.run());
            fields.put(row.getValue(), field);

            JLabel label = new JLabel(row.getKey());
            label.setLabelFor(field);
            JButton fillRest = new JButton("Kalan");
            fillRest.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,8,2,8");
            fillRest.setToolTipText("Dağıtılmamış tutarı " + row.getKey() + " satırına ekle");
            fillRest.setFocusable(false);
            fillRest.addActionListener(e -> {
                BigDecimal rest = total.subtract(assigned.get());
                if (rest.signum() <= 0) return;
                field.setValue(toBigDecimal(field.getValue()).add(rest).doubleValue());
                field.requestFocusInWindow();
            });
            panel.add(label);
            panel.add(field);
            panel.add(fillRest);
        }
        panel.add(remainderLine, "span 3, wmin 0");
        panel.add(customerHint, "span 3, wmin 0");

        // Kasiyer "Ödenen"e nakit yazdıysa nakit satırı onunla (satış tutarıyla sınırlı) başlar.
        if (cart.tendered.signum() > 0) {
            fields.get(PaymentType.CASH).setValue(cart.tendered.min(total).doubleValue());
        }
        refresh.run();

        SimpleModalBorder.Option[] options = {
                new SimpleModalBorder.Option("İptal", SimpleModalBorder.CANCEL_OPTION),
                new SimpleModalBorder.Option("Satışı Tamamla", SimpleModalBorder.OK_OPTION)
        };
        SimpleModalBorder modal = new SimpleModalBorder(panel, "Parçalı Ödeme", options, (controller, action) -> {
            if (action == SimpleModalBorder.OPENED) {
                CurrencyField first = fields.get(PaymentType.CASH);
                first.requestFocusInWindow();
                first.selectAll();
                return;
            }
            if (action != SimpleModalBorder.OK_OPTION) {
                SwingUtilities.invokeLater(() -> barcodeField.requestFocusInWindow());
                return;
            }
            if (total.subtract(assigned.get()).signum() != 0) {
                SoundPlayer.error();
                Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.splitMismatch"));
                controller.consume();
                return;
            }
            BigDecimal onAccount = toBigDecimal(fields.get(null).getValue());
            if (onAccount.signum() > 0 && cart.customer == null) {
                Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.creditRequiresCustomer"));
                controller.consume();
                return;
            }
            List<Payment> payments = new ArrayList<>();
            fields.forEach((type, field) -> {
                BigDecimal amount = toBigDecimal(field.getValue());
                if (type != null && amount.signum() > 0) payments.add(newPayment(type, amount));
            });
            // Pencere bu geri çağrı dönünce kapanır; stok onayı onun üstüne değil ardından
            // açılsın diye bir EDT turu ertelenir.
            SwingUtilities.invokeLater(() -> finalizeSale(cart, payments, BigDecimal.ZERO));
        });
        for (CurrencyField field : fields.values()) {
            field.addActionListener(e -> {
                try {
                    field.commitEdit();
                } catch (java.text.ParseException ignored) {
                    // geçersiz metin — son geçerli değer kullanılır
                }
                modal.doAction(SimpleModalBorder.OK_OPTION);
            });
        }
        AppModal.showModal(this, modal, SPLIT_PAYMENT_MODAL_ID);
    }

    private static Payment newPayment(PaymentType type, BigDecimal amount) {
        Payment payment = new Payment();
        payment.setPaymentType(type);
        payment.setAmount(amount);
        return payment;
    }

    private static BigDecimal computeSubtotal(Cart cart) {
        BigDecimal subtotal = BigDecimal.ZERO;
        for (SaleItem item : cart.items) {
            subtotal = subtotal.add(item.getLineTotal());
        }
        return subtotal;
    }

    private static BigDecimal computeTotal(Cart cart) {
        BigDecimal subtotal = computeSubtotal(cart);
        BigDecimal value = cart.discountValue;
        BigDecimal discount = BigDecimal.ZERO;
        if (cart.discountType != null && value.signum() > 0) {
            // Yüzde %100 ile sınırlı (SaleService.computeDiscount ile aynı kural) — aksi halde toplam negatife düşer.
            discount = (cart.discountType == DiscountType.PERCENT)
                    ? subtotal.multiply(value.min(HUNDRED)).divide(HUNDRED, 2, RoundingMode.HALF_UP)
                    : value.min(subtotal);
        }
        return subtotal.subtract(discount);
    }

    /**
     * Seçili sepetin toplamlarını yazar. Kalan = Tutar − Ödenen; negatife düşerse etiket
     * "Para Üstü"ne döner. Kalan ile para üstü yalnızca renkle ayrılmaz — her ikisi de
     * adıyla yazılır, renk ikinci bir sinyaldir.
     */
    private void recalcTotals() {
        Cart cart = cart();
        if (cart == null || lblTotal == null) return;

        BigDecimal total = computeTotal(cart);
        BigDecimal diff = total.subtract(cart.tendered);

        lblTotal.setText(Format.formatPrice(total));
        lblSubtotal.setText(Format.formatPrice(computeSubtotal(cart)));

        String successHex = SemanticColor.hex(SemanticColor.success());
        String dangerHex = SemanticColor.hex(SemanticColor.danger());
        String valueStyle = "font: bold $h2.font; foreground: ";
        if (diff.signum() < 0) {
            lblRemainingCaption.setText("Para Üstü");
            lblRemaining.setText(Format.formatPrice(diff.negate()));
            lblRemaining.putClientProperty(FlatClientProperties.STYLE, valueStyle + successHex);
        } else {
            lblRemainingCaption.setText("Kalan");
            lblRemaining.setText(Format.formatPrice(diff));
            lblRemaining.putClientProperty(FlatClientProperties.STYLE, valueStyle + (diff.signum() > 0 ? dangerHex : successHex));
        }
        refreshTabTitle(cart);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal) return (BigDecimal) value;
        if (value instanceof Number) return new BigDecimal(value.toString());
        return BigDecimal.ZERO;
    }

    // =========================================================================
    // SATIŞI TAMAMLA
    // =========================================================================

    /**
     * Satışı kapatır. {@code payments} bu satışta alınan ödemelerdir; toplamı satış
     * tutarından azsa fark açık hesaba yazılır ve müşteri zorunludur. Ödeme düğmeleri
     * doğrudan satış yazar — tek ara onay stok yetersizliğidir (market akışında satış
     * engellenmez ama kasiyer bilerek geçmeli). Sepet parametre olarak taşınır: onay
     * açıkken sekme değişse de satış doğru sepete yazılır.
     */
    private void finalizeSale(Cart cart, List<Payment> payments, BigDecimal cashChange) {
        if (submitting || cart.items.isEmpty()) return;

        BigDecimal paid = payments.stream().map(Payment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (computeTotal(cart).subtract(paid).signum() > 0 && cart.customer == null) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.creditRequiresCustomer"));
            customerCombo.requestFocusInWindow();
            return;
        }

        Runnable submit = () -> submitSale(cart, payments, cashChange);
        List<SaleItem> oversold = cart.items.stream().filter(this::isOversold).toList();
        if (!oversold.isEmpty()) {
            String lines = oversold.stream()
                    .map(item -> "• " + item.getItemName() + " — satılan " + item.getQuantity()
                            + ", eldeki " + stockOf(item))
                    .collect(Collectors.joining("\n"));
            DialogHelper.confirm(this, "confirm.sale.oversell.title", "confirm.sale.oversell", submit, lines);
            return;
        }
        submit.run();
    }

    /** Satışı yazar; başarısızlıkta sepet olduğu gibi kalır, kasiyer yeniden deneyebilir. */
    private void submitSale(Cart cart, List<Payment> payments, BigDecimal cashChange) {
        if (submitting) return;
        Customer customer = cart.customer;

        Sale sale = new Sale();
        sale.setCustomerId(customer != null ? customer.getId() : null);
        sale.setCustomer(customer);
        sale.setItems(new ArrayList<>(cart.items));
        sale.setDiscountType(cart.discountType);
        sale.setDiscountValue(cart.discountValue);

        setSubmitting(true);
        try {
            // SaleService.checkout doğrulamayı transaction'a girmeden ÖNCE senkron yapar
            // (PartService.save ile aynı konvansiyon) — bu yüzden try/catch şart, aksi halde
            // bir ValidationException burada ödeme düğmelerini kalıcı olarak kilitler.
            saleService.checkout(sale, payments).thenAccept(saved -> SwingUtilities.invokeLater(() -> {
                setSubmitting(false);
                SoundPlayer.success();
                Toast.show(this, Toast.Type.SUCCESS, cashChange.signum() > 0
                        ? Messages.get("toast.sale.completedWithChange", Format.formatPrice(cashChange))
                        : Messages.get("toast.sale.completed"));
                printReceipt(saved, payments, cashChange);
                completeCart(cart);
            })).exceptionally(ex -> {
                SwingUtilities.invokeLater(() -> setSubmitting(false));
                SoundPlayer.error();
                return ErrorHandler.handle(this, "Satış tamamlanamadı", ex);
            });
        } catch (Exception ex) {
            setSubmitting(false);
            SoundPlayer.error();
            ErrorHandler.handle(this, "Satış tamamlanamadı", ex);
        }
    }

    private void setSubmitting(boolean value) {
        submitting = value;
        for (JButton button : paymentButtons) button.setEnabled(!value);
    }

    /** Termal fiş PDF'i (genişlik Ayarlar > Yazdırma'dan) üretilip varsayılan PDF görüntüleyicide açılır. */
    private void printReceipt(Sale sale, List<Payment> payments, BigDecimal changeGiven) {
        ServiceManager.getUserService().get(1L).thenAccept(shopOpt -> {
            try {
                ReceiptContent content = ReceiptContent.fromSale(sale, payments, shopOpt.orElse(null));
                content.setChangeGiven(changeGiven);

                File outFile = File.createTempFile("servicio-satis-fis-SAT" + sale.getId() + "-", ".pdf");
                outFile.deleteOnExit();
                new ReceiptPdfRenderer().render(content, outFile);

                SwingUtilities.invokeLater(() -> DesktopHelper.openFile(outFile));
            } catch (Exception ex) {
                Servicio.getLogger().error("Satış fişi oluşturma hatası", ex);
                SwingUtilities.invokeLater(() -> Toast.show(this, Toast.Type.WARNING, Messages.get("toast.sale.receiptFailed")));
            }
        }).exceptionally(ex -> ErrorHandler.handle(this, "Fiş için işletme bilgisi alınamadı", ex));
    }

    /**
     * Satışı yazılan sepeti kapatır: başka sepet açıksa sekme kaldırılır (sıradaki müşteri
     * kendi sekmesinde bekliyor), tek sepetse boşaltılıp yeni satışa hazırlanır.
     */
    private void completeCart(Cart cart) {
        int index = carts.indexOf(cart);
        if (index >= 0 && carts.size() > 1) {
            carts.remove(index);
            cartTabs.removeTabAt(index);
        } else {
            cart.reset();
            refreshTabTitle(cart);
            onCartSwitched();
        }
        loadProducts();
        barcodeField.requestFocusInWindow();
    }
}
