package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.application.component.InfoBox;
import tr.cabro.servicio.application.renderer.CustomerTableRenderer;
import tr.cabro.servicio.application.renderer.ServiceStatusTableRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.ServiceListTableModel;
import tr.cabro.servicio.application.util.SVGIconUIColor;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;


@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormServices extends Form {

    private final RepairService service;
    private TableRowSorter<ServiceListTableModel> sorter;

    public FormServices() {
        this.service = ServiceManager.getRepairService();

        init();
    }

    @Override
    public void formRefresh() {
        refreshTable();
    }

    private void init() {
        initComponent();

        allDeviceButton.setIcon(new SVGIconUIColor("icons/all-service.svg", 0.03f, "MenuItem.foreground"));
        repairButton.setIcon(new FlatSVGIcon("icons/under_repair.svg", 24, 24));
        readyButton.setIcon(new FlatSVGIcon("icons/ready.svg", 0.03f));
        otherServiceButton.setIcon(new FlatSVGIcon("icons/another_service.svg", 0.03f));
        deliveryButton.setIcon(new FlatSVGIcon("icons/delivered.svg", 0.03f));
        returnButton.setIcon(new FlatSVGIcon("icons/return.svg", 0.03f));
        partWaitButton.setIcon(new FlatSVGIcon("icons/waiting_for_part.svg", 0.06f));

        tablePanel.putClientProperty(FlatClientProperties.STYLE,
                "arc:18;" +
                        "background:$Table.background");

        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:30;"
                        + "hoverBackground:null;"
                        + "pressedBackground:null;"
                        + "separatorColor:$TableHeader.background;"
                        + "font:bold;");

        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:50;"
                        + "showHorizontalLines:true;"
                        + "intercellSpacing:0,1;"
                        + "cellFocusColor:$TableHeader.hoverBackground;"
                        + "selectionBackground:$TableHeader.hoverBackground;"
                        + "selectionForeground:$Table.foreground;");

        tableScroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE,
                "trackArc:999;"
                        + "trackInsets:3,3,3,3;"
                        + "thumbInsets:3,3,3,3;"
                        + "background:$Table.background;");

        refreshTable();
        loadInfoBox();

    }

    private void loadInfoBox() {
        List<Service> services = service.getAll();
        int total = services.size();
        if (total == 0) total = 1; // % hesaplamada 0 bölme hatasını önlemek için

        // Her durumun sayısını çekiyoruz
        int tamirde = service.getAll("Tamirde").size();
        int hazir = service.getAll("Hazır").size();
        int baskaServiste = service.getAll("Başka Serviste").size();
        int teslimEdilen = service.getAll("Teslim edildi").size();
        int iade = service.getAll("İade").size();
        int parcaBekleyen = service.getAll("Parça Bekliyor").size();
        int borcuOlanlar = calculateRemainingAmount(services);

        // Her InfoBox’a Content (metin şablonu) ve Value (maksimum ve mevcut değer) veriyoruz
        repairBox.setContent(
                new InfoBox.Content("Tamirde", "{AMOUNT} Adet", "{PERCENT}% tamiri devam etmekte"),
                new InfoBox.Value(total, tamirde)
        );

        readyBox.setContent(
                new InfoBox.Content("Tamir Edilenler", "{AMOUNT} Adet", "{PERCENT}% tamir edildi"),
                new InfoBox.Value(total, hazir)
        );

        otherServiceBox.setContent(
                new InfoBox.Content("Başka Serviste", "{AMOUNT} Adet", "{PERCENT}% başka serviste"),
                new InfoBox.Value(total, baskaServiste)
        );

        deliveryBox.setContent(
                new InfoBox.Content("Teslim Edilen", "{AMOUNT} Adet", "{PERCENT}% teslim edildi"),
                new InfoBox.Value(total, teslimEdilen)
        );

        returnBox.setContent(
                new InfoBox.Content("İptal İade", "{AMOUNT} Adet", "{PERCENT}% iptal/iade edildi"),
                new InfoBox.Value(total, iade)
        );

        partWaitBox.setContent(
                new InfoBox.Content("Parça Bekleyen", "{AMOUNT} Adet", "{PERCENT}% parça bekleniyor"),
                new InfoBox.Value(total, parcaBekleyen)
        );

        debtBox.setContent(
                new InfoBox.Content("Borcu Olanlar", "{AMOUNT} Adet", "{PERCENT}% borcu var"),
                new InfoBox.Value(total, borcuOlanlar)
        );
    }

    public void refreshTable() {
        ServiceListTableModel model = new ServiceListTableModel(service.getAll());
        table.setModel(model);

        initFilters();

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.TRAILING,
                SwingConstants.LEADING,
                SwingConstants.LEADING,
                SwingConstants.LEADING
        };

        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));
        table.getColumnModel().getColumn(1).setCellRenderer(new CustomerTableRenderer());

        table.getColumnModel().getColumn(7).setCellRenderer(new DefaultTableCellRenderer() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("tr", "TR"));
            @Override
            protected void setValue(Object value) {
                if (value instanceof LocalDateTime) {
                    setText(((LocalDateTime) value).format(formatter));
                } else {
                    setText("");
                }
            }
        });

        table.getColumnModel().getColumn(8).setCellRenderer(new DefaultTableCellRenderer() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("tr", "TR"));
            @Override
            protected void setValue(Object value) {
                if (value instanceof LocalDateTime) {
                    setText(((LocalDateTime) value).format(formatter));
                } else {
                    setText("Teslim Edilmedi");
                }
            }
        });

        table.getColumnModel().getColumn(9).setCellRenderer(new ServiceStatusTableRenderer());

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(100);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(80);
        table.getColumnModel().getColumn(8).setPreferredWidth(80);
        table.getColumnModel().getColumn(9).setPreferredWidth(80);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) { // Çift tıklama kontrolü
                    int viewRow = table.getSelectedRow();
                    int modelRow = table.convertRowIndexToModel(viewRow);

                    ServiceListTableModel tableModel = (ServiceListTableModel) table.getModel();

                    Service selected = tableModel.getService(modelRow);
                    FormCreateService form = new FormCreateService();
                    form.setService(selected);
                    FormManager.showForm(form);
                    Drawer.setSelectedItemClass(form.getClass());
                }
            }
        });
    }

    private int calculateRemainingAmount(List<Service> services) {
        int result = 0;
        for (Service service : services) {
            double labor = service.getLabor_cost();
            double parts = this.service.getTotalPartsCostForService(service.getId());
            double paid = service.getPaid();
            double total = (labor + parts) - paid;

            if (total > 0) {
                result+=1;
            }
        }
        return result;
    }

    private void initFilters() {
        sorter = new TableRowSorter<>((ServiceListTableModel) table.getModel());
        table.setRowSorter(sorter);

        // created_at (7. sütun) DESC sıralama
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(7, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);

        // Durum filtreleri
        Map<JButton, String> statusFilters = new HashMap<>();
        statusFilters.put(allDeviceButton, null); // Tümü
        statusFilters.put(repairButton, "Tamirde");
        statusFilters.put(readyButton, "Hazır");
        statusFilters.put(otherServiceButton, "Başka Serviste");
        statusFilters.put(deliveryButton, "Teslim edildi");
        statusFilters.put(returnButton, "İade");
        statusFilters.put(partWaitButton, "Parça Bekliyor");

        final String[] currentStatus = {null};

        for (Map.Entry<JButton, String> entry : statusFilters.entrySet()) {
            final JButton button = entry.getKey();
            final String status = entry.getValue();

            button.addActionListener(e -> {
                currentStatus[0] = status;
                applyFilters(currentStatus[0], searchField.getText());
            });
        }

        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters(currentStatus[0], searchField.getText());
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters(currentStatus[0], searchField.getText());
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters(currentStatus[0], searchField.getText());
            }
        });
    }

    private void applyFilters(String status, String searchText) {
        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();

        if (status != null && !status.isEmpty()) {
            filters.add(RowFilter.regexFilter("^" + status + "$", 9)); // 9 = durum sütunu
        }

        if (searchText != null && !searchText.trim().isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText)));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }

        // filtre sonrası da created_at DESC kalsın
        sorter.sort();
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 0, gap 0, wrap", "[grow]", "[pref!][grow]"));

        // Üstteki InfoBox paneli
        JPanel informationPanel = new JPanel(new MigLayout("fill, wrap 4, insets 0, gap 5"));
        repairBox = new InfoBox("icons/repair.svg", new Color(0, 166, 90));
        readyBox = new InfoBox("icons/ready.svg", new Color(243, 156, 18));
        otherServiceBox = new InfoBox("icons/another_service.svg", new Color(0, 31, 63));
        deliveryBox = new InfoBox("icons/delivered.svg", new Color(221, 75, 57));
        returnBox = new InfoBox("icons/return.svg", new Color(216, 27, 96));
        partWaitBox = new InfoBox("icons/waiting_for_part.svg", new Color(0, 192, 239));
        debtBox = new InfoBox("icons/cash.svg", new Color(0, 115, 183));

        informationPanel.add(repairBox, "grow");
        informationPanel.add(readyBox, "grow");
        informationPanel.add(otherServiceBox, "grow");
        informationPanel.add(deliveryBox, "grow");
        informationPanel.add(returnBox, "grow");
        informationPanel.add(partWaitBox, "grow");
        informationPanel.add(debtBox, "grow, wrap");

        add(informationPanel, "growx");

        // Alt kısım (butonlar, arama, tablo)
        tablePanel = new JPanel(new MigLayout("fill, wrap 2, insets 10, gap 10", "[grow][grow]", "[][][grow]"));

        JPanel buttonsPanel = new JPanel(new MigLayout("fill, insets 0, gap 10"));

        allDeviceButton = new JButton("Tüm Servisler");
        repairButton = new JButton("Tamirde");
        readyButton = new JButton("Hazır");
        otherServiceButton = new JButton("Başka Serviste");
        deliveryButton = new JButton("Teslim Edildi");
        returnButton = new JButton("İade");
        partWaitButton = new JButton("Parça Bekliyor");

        buttonsPanel.add(allDeviceButton, "growx");
        buttonsPanel.add(repairButton, "growx");
        buttonsPanel.add(readyButton, "growx");
        buttonsPanel.add(otherServiceButton, "growx");
        buttonsPanel.add(deliveryButton, "growx");
        buttonsPanel.add(returnButton, "growx");
        buttonsPanel.add(partWaitButton, "growx");

        tablePanel.add(buttonsPanel, "span, growx");

        searchField = new JTextField();
        tablePanel.add(searchField, "growx, span");

        table = new JTable();
        tableScroll = new JScrollPane(table);
        tablePanel.add(tableScroll, "span, grow");

        add(tablePanel, "grow");
    }

    private JPanel tablePanel;
    private JScrollPane tableScroll;
    private JTable table;
    private JTextField searchField;

    private JButton allDeviceButton;
    private JButton repairButton;
    private JButton readyButton;
    private JButton otherServiceButton;
    private JButton deliveryButton;
    private JButton returnButton;
    private JButton partWaitButton;

    private InfoBox repairBox;
    private InfoBox readyBox;
    private InfoBox otherServiceBox;
    private InfoBox deliveryBox;
    private InfoBox returnBox;
    private InfoBox partWaitBox;
    private InfoBox debtBox;

}
