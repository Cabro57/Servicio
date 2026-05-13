package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

public class CustomerSelectBox extends JPanel {

    private Customer selectedCustomer;
    private final List<Customer> allCustomers = new ArrayList<>();

    private JButton selectButton;
    private JButton btnNewCustomer;

    private JPopupMenu popupMenu;
    private JTextField searchField;
    private DefaultListModel<Customer> listModel;
    private JList<Customer> list;

    public CustomerSelectBox(ActionListener onNewCustomerAction) {
        initComponent();

        btnNewCustomer.addActionListener(onNewCustomerAction);
    }

    private void initComponent() {
        setLayout(new BorderLayout(0, 0));

        // 1. Ana Seçim Butonu
        selectButton = new JButton("Müşteri Seçiniz...");
        selectButton.setHorizontalAlignment(SwingConstants.LEFT);
        selectButton.putClientProperty(FlatClientProperties.STYLE, "arc: 8;");

        // 2. Yeni Müşteri Ekle Butonu
        btnNewCustomer = new JButton(new Ikon("icons/user-plus.svg"));
        btnNewCustomer.setToolTipText("Yeni Müşteri Ekle");
        btnNewCustomer.putClientProperty(FlatClientProperties.STYLE, "arc: 8;");


        add(selectButton, BorderLayout.CENTER);
        add(btnNewCustomer, BorderLayout.EAST);

        initPopup();

        // Butona tıklandığında Popup'ı aç
        selectButton.addActionListener(e -> {
            searchField.setText("");
            filterList("");
            list.setSelectedValue(selectedCustomer, true);
            popupMenu.setPopupSize(this.getWidth(), 250);
            popupMenu.show(this, 0, this.getHeight() + 2);
            // Popup açıldığında imleci anında arama kutusuna odakla
            SwingUtilities.invokeLater(() -> searchField.requestFocusInWindow());
        });
    }

    private void initPopup() {
        popupMenu = new JPopupMenu();
        popupMenu.setLayout(new MigLayout("fill, insets 5", "[grow]", "[pref][grow]"));
//        popupMenu.setFocusable(false);

        // Arama Kutusu
        searchField = new JTextField();
        searchField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "İsim veya Telefon ara...");
        // searchField.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON, new Ikon("icons/search.svg", 0.8f));

        // Liste ve Model
        listModel = new DefaultListModel<>();
        list = new JList<>(listModel);
        list.setCellRenderer(new CustomerCellRenderer()); // Senin özel Renderer'ın!
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        JScrollPane scrollPane = new JScrollPane(list);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        popupMenu.add(searchField, "cell 0 0, growx, wrap");
        popupMenu.add(scrollPane, "cell 0 1, grow");

        // Arama kutusuna yazıldıkça anında filtrele
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filterList(searchField.getText()); }
            public void removeUpdate(DocumentEvent e) { filterList(searchField.getText()); }
            public void changedUpdate(DocumentEvent e) { filterList(searchField.getText()); }
        });

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    // Tıklanan koordinattaki index'i bul (Bu yöntem daha güvenlidir)
                    int index = list.locationToIndex(e.getPoint());
                    if (index >= 0) {
                        Customer clicked = list.getModel().getElementAt(index);

                        // YENİ MANTIK: Eğer tıklanan kişi zaten şu an seçili olan kişiyse, iptal et
                        if (selectedCustomer != null && clicked.getId() == selectedCustomer.getId()) {
                            setSelectedItem(null); // Seçimi sıfırla
                            list.clearSelection(); // Listedeki görsel vurguyu kaldır
                        } else {
                            // Değilse yeni tıklananı seç
                            setSelectedItem(clicked);
                        }

                        popupMenu.setVisible(false); // Seçim/İptal sonrası popup'ı kapat
                    }
                }
            }
        });
    }

    private void filterList(String text) {
        listModel.clear();
        String lowerText = text.toLowerCase();

        for (Customer c : allCustomers) {
            if (c == null) continue;
            String name = c.getFullName() != null ? c.getFullName().toLowerCase() : "";
            String phone = c.getPhoneNumber1() != null ? c.getPhoneNumber1().toLowerCase() : "";

            if (name.contains(lowerText) || phone.contains(lowerText)) {
                listModel.addElement(c);
            }
        }
    }

    public void setCustomers(List<Customer> customers) {
        this.allCustomers.clear();
        if (customers != null) {
            this.allCustomers.addAll(customers);
        }
    }

    public void appendCustomer(Customer c) {
        this.allCustomers.add(c);
        setSelectedItem(c);
    }

    public void setSelectedItem(Customer customer) {
        this.selectedCustomer = customer;
        if (customer != null) {
            // Ekranda seçili kişinin adını ve telefonunu göster
            selectButton.setText("C-" + customer.getId());
        } else {
            selectButton.setText("Müşteri Seçiniz...");
        }
    }

    public Customer getSelectedItem() {
        return selectedCustomer;
    }
}