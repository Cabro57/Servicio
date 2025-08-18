package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.FlatClientProperties;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.application.component.InfoBox;
import tr.cabro.servicio.application.component.table.TableHeaderAlignment;
import tr.cabro.servicio.application.renderer.CustomerTableRenderer;
import tr.cabro.servicio.application.renderer.ServiceStatusTableRenderer;
import tr.cabro.servicio.application.tablemodal.ServiceListTableModel;
import tr.cabro.servicio.icons.SVGIconUIColor;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.service.PartService;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;

public class ServiceListUI extends JDialog {
    private JPanel main_panel;
    private JTable table;
    private JTextField search_field;
    private JButton all_device_button;
    private JButton repair_button;
    private JButton ready_button;
    private JButton other_service_button;
    private JButton delivery_button;
    private JButton return_button;
    private JButton part_wait_button;
    private JPanel information_panel;
    private JPanel table_panel;
    private JScrollPane table_scroll;
    private JPanel buttons_panel;
    private InfoBox repair_box;
    private InfoBox ready_box;
    private InfoBox other_service_box;
    private InfoBox delivery_box;
    private InfoBox return_box;
    private InfoBox part_wait_box;
    private InfoBox debt_box;

    private final RepairService repairService;
    private final PartService partService;
    private TableRowSorter<ServiceListTableModel> sorter;

    public ServiceListUI() {
        super((Frame) null, "Servis Kayıtları", true);

        this.repairService = ServiceManager.getRepairService();
        this.partService = ServiceManager.getPartService();

        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.8);
        int height = (int) (screen_size.height * 0.8);
        setSize(width, height);
//        setMinimumSize(new Dimension(width, height));
        setLocationRelativeTo(null);

        init();

        setContentPane(main_panel);
    }

    private void init() {

        buttons_panel.setBackground(null);

        all_device_button.setIcon(new SVGIconUIColor("icon/all-service.svg", 0.03f, "MenuItem.foreground"));
        repair_button.setIcon(new FlatSVGIcon("icon/under_repair.svg", 24, 24));
        ready_button.setIcon(new FlatSVGIcon("icon/ready.svg", 0.03f));
        other_service_button.setIcon(new FlatSVGIcon("icon/another_service.svg", 0.03f));
        delivery_button.setIcon(new FlatSVGIcon("icon/delivered.svg", 0.03f));
        return_button.setIcon(new FlatSVGIcon("icon/return.svg", 0.03f));
        part_wait_button.setIcon(new FlatSVGIcon("icon/waiting_for_part.svg", 0.06f));

        table_panel.putClientProperty(FlatClientProperties.STYLE, ""
                + "arc:18;"
                + "background:$Table.background");

        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE, ""
                + "height:30;"
                + "hoverBackground:null;"
                + "pressedBackground:null;"
                + "separatorColor:$TableHeader.background;"
                + "font:bold;");

        table.putClientProperty(FlatClientProperties.STYLE, ""
                + "rowHeight:50;"
                + "showHorizontalLines:true;"
                + "intercellSpacing:0,1;"
                + "cellFocusColor:$TableHeader.hoverBackground;"
                + "selectionBackground:$TableHeader.hoverBackground;"
                + "selectionForeground:$Table.foreground;");

        table_scroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE, ""
                + "trackArc:999;"
                + "trackInsets:3,3,3,3;"
                + "thumbInsets:3,3,3,3;"
                + "background:$Table.background;");


        refreshTable();
        loadInfoBox();
    }

    private void loadInfoBox() {
        List<Service> services = repairService.getAllServices();
        int total = services.size();
        if (total == 0) total = 1; // % hesaplamada 0 bölme hatasını önlemek için

        // Her durumun sayısını çekiyoruz
        int tamirde = repairService.getServicesByStatus("Tamirde").size();
        int hazir = repairService.getServicesByStatus("Hazır").size();
        int baskaServiste = repairService.getServicesByStatus("Başka Serviste").size();
        int teslimEdilen = repairService.getServicesByStatus("Teslim edildi").size();
        int iade = repairService.getServicesByStatus("İade").size();
        int parcaBekleyen = repairService.getServicesByStatus("Parça Bekliyor").size();
        int borcuOlanlar = calculateRemainingAmount(services);

        // Her InfoBox’a Content (metin şablonu) ve Value (maksimum ve mevcut değer) veriyoruz
        repair_box.setContent(
                new InfoBox.Content("Tamirde", "{AMOUNT} Adet", "{PERCENT}% tamiri devam etmekte"),
                new InfoBox.Value(total, tamirde)
        );

        ready_box.setContent(
                new InfoBox.Content("Tamir Edilenler", "{AMOUNT} Adet", "{PERCENT}% tamir edildi"),
                new InfoBox.Value(total, hazir)
        );

        other_service_box.setContent(
                new InfoBox.Content("Başka Serviste", "{AMOUNT} Adet", "{PERCENT}% başka serviste"),
                new InfoBox.Value(total, baskaServiste)
        );

        delivery_box.setContent(
                new InfoBox.Content("Teslim Edilen", "{AMOUNT} Adet", "{PERCENT}% teslim edildi"),
                new InfoBox.Value(total, teslimEdilen)
        );

        return_box.setContent(
                new InfoBox.Content("İptal İade", "{AMOUNT} Adet", "{PERCENT}% iptal/iade edildi"),
                new InfoBox.Value(total, iade)
        );

        part_wait_box.setContent(
                new InfoBox.Content("Parça Bekleyen", "{AMOUNT} Adet", "{PERCENT}% parça bekleniyor"),
                new InfoBox.Value(total, parcaBekleyen)
        );

        debt_box.setContent(
                new InfoBox.Content("Borcu Olanlar", "{AMOUNT} Adet", "{PERCENT}% borcu var"),
                new InfoBox.Value(total, borcuOlanlar)
        );
    }

    private void refreshTable() {
        ServiceListTableModel model = new ServiceListTableModel(repairService.getDescServices());
        table.setModel(model);

        initFilters();

        Integer[] columnAlignments = {
                SwingConstants.CENTER,
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

        table.getColumnModel().getColumn(8).setCellRenderer(new ServiceStatusTableRenderer());

        table.getColumnModel().getColumn(0).setMaxWidth(50);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(80);
        table.getColumnModel().getColumn(3).setPreferredWidth(80);
        table.getColumnModel().getColumn(4).setPreferredWidth(80);
        table.getColumnModel().getColumn(5).setPreferredWidth(80);
        table.getColumnModel().getColumn(6).setPreferredWidth(80);
        table.getColumnModel().getColumn(7).setPreferredWidth(80);

        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) { // Çift tıklama kontrolü
                    int row = table.getSelectedRow();
                    ServiceListTableModel model = (ServiceListTableModel) table.getModel();
                    Service service = model.getService(row);

                    ServiceEditUI dialog = new ServiceEditUI(service);
                    dialog.setVisible(true);

                    refreshTable();
                }
            }
        });
    }

    private int calculateRemainingAmount(List<Service> services) {
        int result = 0;
        for (Service service : services) {
            double labor = service.getLabor_cost();
            double parts = partService.getTotalPartsCostForService(service.getId());
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

        // ID sütununa göre DESC sıralama
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sorter.setSortKeys(sortKeys);

        // Durum filtreleri
        Map<JButton, String> statusFilters = new HashMap<>();
        statusFilters.put(all_device_button, null); // Tümü
        statusFilters.put(repair_button, "Tamirde");
        statusFilters.put(ready_button, "Hazır");
        statusFilters.put(other_service_button, "Başka Serviste");
        statusFilters.put(delivery_button, "Teslim edildi");
        statusFilters.put(return_button, "İade");
        statusFilters.put(part_wait_button, "Parça Bekliyor");

        final String[] currentStatus = {null};

        // Buton dinleyicileri tek metotta ekle
        for (Map.Entry<JButton, String> entry : statusFilters.entrySet()) {
            final JButton button = entry.getKey();
            final String status = entry.getValue();

            button.addActionListener(e -> {
                currentStatus[0] = status;
                applyFilters(currentStatus[0], search_field.getText());
            });
        }

        // Search field filtreleme
        search_field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters(currentStatus[0], search_field.getText());
            }
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters(currentStatus[0], search_field.getText());
            }
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                applyFilters(currentStatus[0], search_field.getText());
            }
        });

    }

    private void applyFilters(String status, String searchText) {
        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();

        // Durum filtresi
        if (status != null && !status.isEmpty()) {
            filters.add(RowFilter.regexFilter("^" + status + "$", 8)); // 8 = durum sütunu
        }

        // Arama filtresi (tüm sütunlarda arar)
        if (searchText != null && !searchText.trim().isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText)));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }

    }



    private void createUIComponents() {
        repair_box = new InfoBox("icon/repair.svg", new Color(0, 166, 90));
        ready_box = new InfoBox("icon/ready.svg", new Color(243, 156, 18));
        other_service_box = new InfoBox("icon/another_service.svg", new Color(0, 31, 63));
        delivery_box = new InfoBox("icon/delivered.svg", new Color(221, 75, 57));
        return_box = new InfoBox("icon/return.svg", new Color(216, 27, 96));
        part_wait_box = new InfoBox("icon/waiting_for_part.svg", new Color(0, 192, 239));
        debt_box = new InfoBox("icon/cash.svg", new Color(0, 115, 183));
    }
}
