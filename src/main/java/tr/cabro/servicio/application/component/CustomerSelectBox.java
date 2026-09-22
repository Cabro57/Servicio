package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.enums.CustomerType;
import tr.cabro.servicio.util.PhoneHelper;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Aranabilir müşteri seçici: tek bir metin alanı + altında açılan sonuç listesi + "yeni müşteri" düğmesi.
 * <p>
 * Alan iki iş görür:
 * <ul>
 *   <li><b>Odak dışındayken</b> seçili müşteriyi gösterir: metinde ad (kurumsalda firma adı),
 *       sağında soluk renkte telefon ve müşteri numarası.</li>
 *   <li><b>Odaklanınca</b> arama kutusuna dönüşür: metin seçili hale gelir (yazmak değiştirir),
 *       altında sonuç listesi açılır. Yazdıkça liste süzülür; oklar/PageUp/PageDown gezdirir,
 *       Enter veya tıklama seçer, Esc vazgeçer.</li>
 * </ul>
 * Seçimi kaldırmak için: alandaki temizle (x) düğmesi, ya da metni tamamen silip alandan çıkmak /
 * Enter'a basmak. Metin yazılıp seçim yapılmadan çıkılırsa eski seçim geri gelir.
 * <p>
 * Arama ad, firma adı, iki telefon (0532…, 532…, +90532… yazımlarının hepsi eşleşir), TC kimlik no
 * ve "C-12" müşteri numarası üzerinde çalışır. Liste penceresi odak almaz; klavye hep alanda kalır.
 */
public class CustomerSelectBox extends JPanel {

    private static final int MAX_RESULTS = 200;
    private static final int ROW_HEIGHT_ESTIMATE = 48;

    private Customer selectedCustomer;
    private final List<Customer> allCustomers = new ArrayList<>();
    private Consumer<Customer> onSelectionChanged;
    private Runnable afterUserChoice;

    private JTextField field;
    private JLabel detailLabel;
    private JButton btnNewCustomer;

    private JPopupMenu popupMenu;
    private DefaultListModel<Customer> listModel;
    private JList<Customer> list;
    private JScrollPane listScroll;
    private JLabel emptyLabel;

    /** Metni programatik olarak yazarken arama tetiklenmesin. */
    private boolean updatingText;

    public CustomerSelectBox(ActionListener onNewCustomerAction) {
        initComponent();
        btnNewCustomer.addActionListener(onNewCustomerAction);
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 0", "[grow, fill]", "[fill]"));
        setOpaque(false);

        field = new JTextField();
        field.getAccessibleContext().setAccessibleName("Müşteri ara ve seç");
        field.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Müşteri ara: ad, firma, telefon, TC veya C-no");
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/user-search.svg", 0.85f));
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        // Temizle (x): seçimi kaldırır ve aramaya hazır bırakır.
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_CLEAR_CALLBACK, (Runnable) () -> {
            setSelectedItem(null);
            field.requestFocusInWindow();
            if (field.isFocusOwner()) openPopup();
        });
        field.putClientProperty(FlatClientProperties.STYLE, "margin: 5,6,5,6");

        detailLabel = new JLabel();
        detailLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground");
        detailLabel.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 4));

        // "Yeni müşteri" düğmesi alanın içinde, en sağda: ayrı bir düğme yan tarafta yer kaplıyordu.
        btnNewCustomer = new JButton(new Ikon("icons/user-plus.svg", 0.85f));
        btnNewCustomer.setToolTipText("Yeni müşteri ekle");
        btnNewCustomer.getAccessibleContext().setAccessibleName("Yeni müşteri ekle");
        btnNewCustomer.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        btnNewCustomer.putClientProperty(FlatClientProperties.STYLE, "arc: 8; margin: 2,4,2,4");

        JPanel trailing = new JPanel(new MigLayout("insets 0, gap 2, hidemode 3", "[][]", "[center]"));
        trailing.setOpaque(false);
        trailing.add(detailLabel);
        trailing.add(btnNewCustomer);
        field.putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, trailing);

        add(field, "wmin 0");

        initPopup();
        initFieldEvents();
        showSelection();
    }

    private void initPopup() {
        popupMenu = new JPopupMenu();
        popupMenu.setFocusable(false);
        popupMenu.setLayout(new MigLayout("fill, insets 4, hidemode 3", "[grow, fill]", "[grow, fill][pref]"));

        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setCellRenderer(new Renderer());
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFocusable(false); // Odak metin alanında kalır; oklar listeyi gezdirir.

        listScroll = new JScrollPane(list);
        listScroll.setFocusable(false);
        listScroll.getVerticalScrollBar().setFocusable(false);
        listScroll.getVerticalScrollBar().setUnitIncrement(16);
        listScroll.setBorder(BorderFactory.createEmptyBorder());

        emptyLabel = new JLabel("Eşleşen müşteri yok. Alanın sağındaki düğmeyle yeni müşteri ekleyebilirsiniz.");
        emptyLabel.putClientProperty(FlatClientProperties.STYLE, "foreground: $Label.disabledForeground; font: -1");
        emptyLabel.setHorizontalAlignment(SwingConstants.CENTER);

        popupMenu.add(listScroll, "wrap");
        popupMenu.add(emptyLabel, "gapy 10 10");

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0) choose(listModel.getElementAt(index));
            }
        });
    }

    private void initFieldEvents() {
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(FocusEvent e) {
                if (e.isTemporary()) return;
                // Arama moduna geç: ayrıntı gizlenir, metin seçilir (yazmak eski adı değiştirir).
                detailLabel.setVisible(false);
                field.selectAll();
                SwingUtilities.invokeLater(() -> { if (field.isFocusOwner()) openPopup(); });
            }

            @Override
            public void focusLost(FocusEvent e) {
                if (e.isTemporary()) return;
                popupMenu.setVisible(false);
                if (field.getText().isBlank() && selectedCustomer != null) {
                    // Metni tamamen silip çıkmak = seçimi kaldırmak.
                    setSelectedItem(null);
                } else {
                    // Yazılan arama metni atılır, mevcut seçim yeniden gösterilir.
                    showSelection();
                }
            }
        });

        // Odaktayken tıklamak (ör. Esc ile kapatılmış listeyi) yeniden açar.
        field.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (field.isFocusOwner() && !popupMenu.isVisible()) openPopup();
            }
        });

        field.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { onQueryChanged(); }
            public void removeUpdate(DocumentEvent e) { onQueryChanged(); }
            public void changedUpdate(DocumentEvent e) { }
        });

        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                int size = listModel.getSize();
                int index = list.getSelectedIndex();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_DOWN -> {
                        if (!popupMenu.isVisible()) openPopup();
                        else if (size > 0) select(Math.min(size - 1, index + 1));
                        e.consume();
                    }
                    case KeyEvent.VK_UP -> { if (size > 0) select(Math.max(0, index - 1)); e.consume(); }
                    case KeyEvent.VK_PAGE_DOWN -> { if (size > 0) select(Math.min(size - 1, index + 6)); e.consume(); }
                    case KeyEvent.VK_PAGE_UP -> { if (size > 0) select(Math.max(0, index - 6)); e.consume(); }
                    case KeyEvent.VK_ENTER -> {
                        // Ctrl+Enter modalin kaydet kısayolu olarak kalır; burada yalnızca düz Enter işlenir.
                        if (e.getModifiersEx() != 0) return;
                        if (field.getText().isBlank()) {
                            // Boş alanda Enter = seçimi kaldır.
                            popupMenu.setVisible(false);
                            if (selectedCustomer != null) setSelectedItem(null);
                            e.consume();
                            return;
                        }
                        if (!popupMenu.isVisible()) return;
                        Customer c = list.getSelectedValue();
                        if (c != null) choose(c);
                        e.consume();
                    }
                    case KeyEvent.VK_ESCAPE -> {
                        // Açık liste varsa önce o kapanır; modal ancak ikinci Esc'te kapanır.
                        if (popupMenu.isVisible()) {
                            popupMenu.setVisible(false);
                            showSelectionText();
                            field.selectAll();
                            e.consume();
                        }
                    }
                    default -> { }
                }
            }
        });
    }

    private void onQueryChanged() {
        if (updatingText || !field.isFocusOwner()) return;
        filterList(field.getText());
        if (!popupMenu.isVisible()) openPopup(); else resizePopup();
    }

    private void openPopup() {
        // Alanda seçili müşterinin adı duruyorsa bu bir arama değildir: tüm liste gösterilir.
        String text = field.getText();
        boolean showingSelection = selectedCustomer != null && text.equals(displayName(selectedCustomer));
        filterList(showingSelection ? "" : text);
        if (selectedCustomer != null) {
            int idx = indexOf(selectedCustomer);
            if (idx >= 0) select(idx);
        }
        resizePopup();
        if (isShowing()) popupMenu.show(field, 0, field.getHeight() + 2);
    }

    private void resizePopup() {
        int rows = Math.max(1, Math.min(6, listModel.getSize()));
        int height = listModel.getSize() == 0 ? 60 : rows * ROW_HEIGHT_ESTIMATE + 10;
        popupMenu.setPopupSize(Math.max(field.getWidth(), 360), height);
        popupMenu.revalidate();
    }

    private int indexOf(Customer c) {
        for (int i = 0; i < listModel.getSize(); i++) {
            if (Objects.equals(listModel.get(i).getId(), c.getId())) return i;
        }
        return -1;
    }

    private void select(int index) {
        list.setSelectedIndex(index);
        list.ensureIndexIsVisible(index);
    }

    private void choose(Customer c) {
        popupMenu.setVisible(false);
        setSelectedItem(c);
        field.selectAll();
        if (afterUserChoice != null) SwingUtilities.invokeLater(afterUserChoice);
    }

    // -------------------------------------------------------------------------
    // Arama
    // -------------------------------------------------------------------------

    private void filterList(String text) {
        listModel.clear();
        String query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        String queryDigits = nationalDigits(query);

        for (Customer c : allCustomers) {
            if (c == null) continue;
            if (listModel.getSize() >= MAX_RESULTS) break;
            if (query.isEmpty() || matches(c, query, queryDigits)) listModel.addElement(c);
        }
        if (listModel.getSize() > 0) list.setSelectedIndex(0);
        listScroll.setVisible(listModel.getSize() > 0);
        emptyLabel.setVisible(listModel.getSize() == 0);
    }

    private static boolean matches(Customer c, String query, String queryDigits) {
        if (contains(c.getFullName(), query) || contains(c.getBusinessName(), query)) return true;
        if (("c-" + c.getId()).equals(query) || String.valueOf(c.getId()).equals(query)) return true;
        if (queryDigits.length() >= 3) {
            if (nationalDigits(c.getPhoneNumber1()).contains(queryDigits)) return true;
            if (nationalDigits(c.getPhoneNumber2()).contains(queryDigits)) return true;
            if (c.getIdentityNo() != null && c.getIdentityNo().contains(queryDigits)) return true;
        }
        return false;
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    /**
     * Telefonu ülke kodu ve baştaki sıfır olmadan rakam dizisine indirger: "+90 532…", "0532…" ve
     * "532…" aynı diziye düşer. Rakam içermeyen sorgu boş döner.
     */
    private static String nationalDigits(String value) {
        if (value == null) return "";
        String digits = value.replaceAll("\\D", "");
        if (digits.startsWith("90") && digits.length() > 10) digits = digits.substring(2);
        if (digits.startsWith("0")) digits = digits.substring(1);
        return digits;
    }

    // -------------------------------------------------------------------------
    // Görünüm
    // -------------------------------------------------------------------------

    /** Seçili müşteriyi gösterim moduna alır: ad metinde, telefon ve numara sağda. */
    private void showSelection() {
        showSelectionText();
        Customer c = selectedCustomer;
        detailLabel.setText(c != null ? phoneOf(c) + "  ·  C-" + c.getId() : "");
        detailLabel.setVisible(c != null && !field.isFocusOwner());
        field.setToolTipText(c != null ? displayName(c) + "  ·  " + phoneOf(c) + "  (x ile seçimi kaldırın)" : null);
    }

    private void showSelectionText() {
        updatingText = true;
        try {
            field.setText(selectedCustomer != null ? displayName(selectedCustomer) : "");
            field.setCaretPosition(0);
        } finally {
            updatingText = false;
        }
    }

    static String displayName(Customer c) {
        if (c.getType() == CustomerType.KURUMSAL && c.getBusinessName() != null && !c.getBusinessName().isBlank()) {
            return c.getBusinessName().trim();
        }
        String first = c.getFirstName() != null ? c.getFirstName() : "";
        String last = c.getLastName() != null ? c.getLastName() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? "İsimsiz müşteri" : full;
    }

    private static String phoneOf(Customer c) {
        String p = PhoneHelper.formatForDisplay(c.getPhoneNumber1());
        return p.isEmpty() ? "Telefon yok" : p;
    }

    /** Liste satırı: ad (kalın), altında telefon ve müşteri no; sağda kurumsal/sorunlu işaretleri. */
    private static class Renderer implements ListCellRenderer<Customer> {
        private final JPanel row = new JPanel(new MigLayout("insets 7 10 7 10, gap 0, fillx", "[grow, fill][]", "[]2[]"));
        private final JLabel name = new JLabel();
        private final JLabel detail = new JLabel();
        private final JLabel flag = new JLabel();

        Renderer() {
            name.putClientProperty(FlatClientProperties.STYLE, "font: bold");
            detail.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            flag.putClientProperty(FlatClientProperties.STYLE, "font: -1");
            row.add(name, "wmin 0");
            row.add(flag, "spany 2, aligny center, wrap");
            row.add(detail, "wmin 0");
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Customer> list, Customer c, int index,
                                                      boolean selected, boolean focus) {
            name.setText(displayName(c));
            StringBuilder d = new StringBuilder(phoneOf(c)).append("   ·   C-").append(c.getId());
            if (c.getType() == CustomerType.KURUMSAL && c.getFirstName() != null) {
                d.append("   ·   ").append(Objects.toString(c.getFirstName(), "")).append(' ')
                        .append(Objects.toString(c.getLastName(), ""));
            }
            detail.setText(d.toString());

            if (c.isProblematic()) {
                flag.setText("Sorunlu");
                flag.setIcon(new Ikon("icons/triangle-alert.svg", 0.75f, "Servicio.dangerColor"));
            } else if (c.getType() == CustomerType.KURUMSAL) {
                flag.setText("Kurumsal");
                flag.setIcon(new Ikon("icons/building-2.svg", 0.75f));
            } else {
                flag.setText("");
                flag.setIcon(null);
            }

            Color bg = selected ? UIManager.getColor("List.selectionBackground") : UIManager.getColor("List.background");
            Color fg = selected ? UIManager.getColor("List.selectionForeground") : UIManager.getColor("List.foreground");
            Color muted = selected ? fg : UIManager.getColor("Label.disabledForeground");
            row.setBackground(bg);
            name.setForeground(fg);
            detail.setForeground(muted);
            flag.setForeground(c.isProblematic() && !selected ? UIManager.getColor("Servicio.dangerColor") : muted);
            return row;
        }
    }

    // -------------------------------------------------------------------------
    // Dışa açık API
    // -------------------------------------------------------------------------

    public void setCustomers(List<Customer> customers) {
        this.allCustomers.clear();
        if (customers != null) {
            this.allCustomers.addAll(customers);
        }
        if (popupMenu.isVisible()) openPopup();
    }

    public void appendCustomer(Customer c) {
        // Yeni eklenen müşteri listenin başına: bir sonraki aramada en üstte çıksın.
        this.allCustomers.add(0, c);
        setSelectedItem(c);
    }

    public void setSelectedItem(Customer customer) {
        this.selectedCustomer = customer;
        showSelection();
        setError(false);
        if (onSelectionChanged != null) onSelectionChanged.accept(customer);
    }

    /** Müşteri seçimi değiştiğinde (kullanıcı tıklaması veya programatik çağrı) tetiklenir. */
    public void setOnSelectionChanged(Consumer<Customer> listener) {
        this.onSelectionChanged = listener;
    }

    /**
     * Kullanıcı listeden müşteri seçtikten sonra çalışır (programatik seçimde çalışmaz).
     * Formlar bunu odağı bir sonraki mantıklı alana taşımak için kullanır.
     */
    public void setAfterUserChoice(Runnable afterUserChoice) {
        this.afterUserChoice = afterUserChoice;
    }

    public Customer getSelectedItem() {
        return selectedCustomer;
    }

    /** Doğrulama hatasında alanı hata çerçevesine alır. */
    public void setError(boolean error) {
        field.putClientProperty(FlatClientProperties.OUTLINE, error ? FlatClientProperties.OUTLINE_ERROR : null);
    }

    @Override
    public void grabFocus() {
        field.requestFocusInWindow();
    }

    @Override
    public boolean requestFocusInWindow() {
        return field.requestFocusInWindow();
    }

    @Override
    public void requestFocus() {
        field.requestFocus();
    }
}
