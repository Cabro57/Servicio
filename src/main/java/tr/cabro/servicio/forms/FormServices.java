package tr.cabro.servicio.forms;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.component.ToolBarSelection;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import raven.modal.utils.SystemForm;
import tr.cabro.servicio.application.renderer.CustomerTableCellRenderer;
import tr.cabro.servicio.application.renderer.ServiceStatusTableCellRenderer;
import tr.cabro.servicio.application.renderer.TableHeaderAlignment;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.application.tablemodal.ServiceListTableModel;
import tr.cabro.servicio.application.util.SVGIconUIColor;
import tr.cabro.servicio.model.Customer;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.ServiceStatus;
import tr.cabro.servicio.service.RepairService;
import tr.cabro.servicio.service.ServiceManager;
import tr.cabro.servicio.util.Format;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.regex.Pattern;


@SystemForm(name = "Servis Kayıtları", description = "Tüm servis kayıtlarını oluşturmak için kullanılabilir")
public class FormServices extends Form {

    private final RepairService service;
    private TableRowSorter<GenericTableModel<Service>> sorter;

    public FormServices() {
        this.service = ServiceManager.getRepairService();

        init();
    }

    @Override
    public void formRefresh() {
        refreshTable();
    }

    @Override
    public void formOpen() {
        refreshTable();
    }

    private void init() {
        initComponent();

        setupTable();

        configureTableRenderers();

        initFilters();

        refreshTable();

    }

    private void setupTable() {
        List<ColumnDef<Service>> columns = Arrays.asList(
                new ColumnDef<>("#", Integer.class, Service::getId),
                new ColumnDef<>("Müşteri", Customer.class, s -> ServiceManager.getCustomerService().get(s.getCustomerId()).orElse(null)),
                new ColumnDef<>("Cihaz Türü", String.class, Service::getDeviceType),
                new ColumnDef<>("Marka", String.class, Service::getDeviceBrand),
                new ColumnDef<>("Model", String.class, Service::getDeviceModel),
                new ColumnDef<>("Seri No./IMEI", String.class, Service::getDeviceSerial),
                new ColumnDef<>("Ücret", String.class, s -> Format.formatPrice(calculateRemainingAmount(s))),
                new ColumnDef<>("Kayıt Tarihi", LocalDateTime.class, Service::getCreatedAt),
                new ColumnDef<>("Teslim Tarihi", LocalDateTime.class, Service::getDeliveryAt),
                new ColumnDef<>("Durum", ServiceStatus.class, Service::getServiceStatus)
        );

        serviceTableModel = new GenericTableModel<>(columns);
        table.setModel(serviceTableModel);
    }

    public void refreshTable() {
        if (serviceTableModel != null) {
            List<Service> allServices = service.getAll();
            serviceTableModel.setData(allServices); // Sihirli dokunuş burası!
        }
    }

    // Tablonun görselliği (Genişlik, Tarih formatları, Hizalamalar)
    private void configureTableRenderers() {
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:30; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold;");

        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:50; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:$TableHeader.hoverBackground; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground;");

        Integer[] columnAlignments = {
                SwingConstants.CENTER, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING,
                SwingConstants.TRAILING, SwingConstants.LEADING, SwingConstants.LEADING, SwingConstants.LEADING
        };
        table.getTableHeader().setDefaultRenderer(new TableHeaderAlignment(table, columnAlignments));

        // Özel Hücre Çiziciler (Renderers)
        table.getColumnModel().getColumn(1).setCellRenderer(new CustomerTableCellRenderer());

        DefaultTableCellRenderer dateRenderer = new DefaultTableCellRenderer() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM yyyy", new Locale("tr", "TR"));
            @Override
            protected void setValue(Object value) {
                if (value instanceof LocalDateTime) {
                    setText(((LocalDateTime) value).format(formatter));
                } else {
                    setText(getText().equals("Teslim Tarihi") ? "Teslim Edilmedi" : "");
                }
            }
        };

        table.getColumnModel().getColumn(7).setCellRenderer(dateRenderer);
        table.getColumnModel().getColumn(8).setCellRenderer(dateRenderer);
        table.getColumnModel().getColumn(9).setCellRenderer(new ServiceStatusTableCellRenderer());

        // Genişlik Ayarları
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

        // Çift Tıklama Olayı (GenericTableModel adaptasyonu)
        table.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRow() != -1) {
                    int viewRow = table.getSelectedRow();
                    int modelRow = table.convertRowIndexToModel(viewRow);

                    // ESKİ KOD: ServiceListTableModel tableModel = (ServiceListTableModel) table.getModel();
                    // YENİ KOD: GenericTableModel'in getItemAt metodunu kullanıyoruz
                    Service selected = serviceTableModel.getItemAt(modelRow);

                    if(selected != null) {
                        FormService form = new FormService(selected);
                        FormManager.showForm(form);
                    }
                }
            }
        });
    }

    private void initFilters() {
        // Sorter artık GenericTableModel'e göre çalışıyor
        sorter = new TableRowSorter<>(serviceTableModel);
        table.setRowSorter(sorter);

        // Kayıt tarihine (7. sütun) göre azalan (DESC) sıralama
        List<RowSorter.SortKey> sortKeys = new ArrayList<>();
        sortKeys.add(new RowSorter.SortKey(7, SortOrder.DESCENDING));
        sorter.setSortKeys(sortKeys);

        // Arama kutusu dinleyicisi
        searchField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            public void insertUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void removeUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
            public void changedUpdate(javax.swing.event.DocumentEvent e) { applyFilters(); }
        });
    }

    private void applyFilters() {
        List<RowFilter<TableModel, Object>> filters = new ArrayList<>();
        String searchText = searchField.getText();

        // 9. Sütun (Durum) filtresi
        if (currentStatusFilter != null && !currentStatusFilter.isEmpty()) {
            filters.add(RowFilter.regexFilter("^" + currentStatusFilter + "$", 9));
        }

        // Genel arama metni filtresi (Büyük/Küçük harf duyarsız)
        if (searchText != null && !searchText.trim().isEmpty()) {
            filters.add(RowFilter.regexFilter("(?i)" + Pattern.quote(searchText)));
        }

        if (filters.isEmpty()) {
            sorter.setRowFilter(null);
        } else {
            sorter.setRowFilter(RowFilter.andFilter(filters));
        }
        sorter.sort();
    }

    private double calculateRemainingAmount(Service service) {
        double labor = service.getLaborCost();
        double parts = ServiceManager.getRepairService().getTotalPartsCostForService(service.getId());
        double paid = service.getPaid();
        return (labor + parts) - paid;
    }

    private void initComponent() {
        setLayout(new MigLayout("fill, insets 0, gap 0, wrap", "[grow]", ""));

        // Alt kısım (butonlar, arama, tablo)
        JPanel tablePanel = new JPanel(new MigLayout("fill, wrap 2, insets 10, gap 10", "[grow][grow]", "[][][grow]"));
        tablePanel.putClientProperty(FlatClientProperties.STYLE,
                "arc:18;" +
                        "background:$Table.background");

        // Modern ToolBarSelection Kullanımı (Eski manuel butonları sildik, kod temizlendi)
        ToolBarSelection<ServiceStatus> toolBarSelection = new ToolBarSelection<>(ServiceStatus.values(), serviceStatus -> {
            // Tümü seçiliyse null gönderiyoruz, değilse durum ismini filtreye atıyoruz
            currentStatusFilter = (serviceStatus == null) ? null : serviceStatus.getDisplayName();
            applyFilters();
        });

        searchField = new JTextField();

        table = new JTable();

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.getVerticalScrollBar().putClientProperty(FlatClientProperties.STYLE,
                "trackArc:999;"
                        + "trackInsets:3,3,3,3;"
                        + "thumbInsets:3,3,3,3;"
                        + "background:$Table.background;");

        tablePanel.add(toolBarSelection, "span, growx");
        tablePanel.add(searchField, "growx, span");
        tablePanel.add(tableScroll, "span, grow");

        add(tablePanel, "grow");
    }

    private JTextField searchField;
    private JTable table;

    private GenericTableModel<Service> serviceTableModel;

    private String currentStatusFilter = null;

}
