package tr.cabro.servicio.application.panels;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import net.miginfocom.swing.MigLayout;
import raven.modal.system.AllForms;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.application.forms.FormService;
import tr.cabro.servicio.application.forms.FormServices;
import tr.cabro.servicio.application.renderer.UniversalVisualizableRenderer;
import tr.cabro.servicio.application.tablemodal.ColumnDef;
import tr.cabro.servicio.application.tablemodal.GenericTableModel;
import tr.cabro.servicio.model.Service;
import tr.cabro.servicio.model.enums.ServiceStatus;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.List;

public class ActiveServiceTable extends JPanel {

    private GenericTableModel<Service> tableModel;
    private JTable table;

    public ActiveServiceTable() {
        init();
    }

    private void init() {
        initComponent();
    }

    public void setData(List<Service> service) {
        if (tableModel != null) {
            tableModel.setData(service);
            revalidate();
            repaint();
        }
    }

    private void initComponent() {
        setLayout(new MigLayout("wrap, fillx, insets 15, gapy 0", "[fill]", "[pref!]15[pref!][pref!]"));
        putClientProperty(FlatClientProperties.STYLE_CLASS, "dashboardBackground");
        //putClientProperty(FlatClientProperties.STYLE, "arc: 16; background: lighten($Panel.background, 3%);");

        // --- 1. BAŞLIK BÖLÜMÜ ---
        JPanel headerPanel = new JPanel(new MigLayout("insets 0, fillx", "[grow][]", "center"));
        headerPanel.setOpaque(false);

        JLabel title = new JLabel("Aktif Servisler");
        title.setIcon(createIcon("icons/activity.svg", new Color(13, 110, 253))); // Şık Mavi
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font; iconTextGap: 8");

        JButton btnSeeAll = new JButton("Tümünü Gör \u2192");
        btnSeeAll.putClientProperty(FlatClientProperties.STYLE, "font: $light.font");
        btnSeeAll.putClientProperty( "JButton.buttonType", "toolBarButton" );
        btnSeeAll.setCursor(new Cursor(Cursor.HAND_CURSOR));
        Form form = AllForms.getForm(FormServices.class);
        btnSeeAll.addActionListener(e -> FormManager.showForm(form));

        headerPanel.add(title);
        headerPanel.add(btnSeeAll);

        // --- 2. TABLO BÖLÜMÜ ---
        List<ColumnDef<Service>> columns = Arrays.asList(
                new ColumnDef<>("Müşteri", String.class, s -> s.getCustomer() != null ? s.getCustomer().getName() + " " + s.getCustomer().getSurname() : "-"),
                new ColumnDef<>("Cihaz", String.class, s -> s.getDeviceBrand() + " " + s.getDeviceModel()),
                new ColumnDef<>("Durum", ServiceStatus.class, Service::getServiceStatus)
        );

        tableModel = new GenericTableModel<>(columns);
        table = new JTable(tableModel);

        table.setFocusable(false);
        table.setCursor(new Cursor(Cursor.HAND_CURSOR));

        applyTableStyles(table);

        // -- ÖZEL RENDERER İŞLEMLERİ --

        // 1. Header (Başlık) Hizalaması: Sola Yaslı
        TableCellRenderer defaultHeaderRenderer = table.getTableHeader().getDefaultRenderer();
        table.getTableHeader().setDefaultRenderer((t, value, isSelected, hasFocus, row, column) -> {
            JLabel label = (JLabel) defaultHeaderRenderer.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            label.setHorizontalAlignment(SwingConstants.LEADING);
            return label;
        });

        // 2. Müşteri Sütunu (Kalın Yazı)
        table.getColumnModel().getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                label.setFont(label.getFont().deriveFont(Font.BOLD));
                return label;
            }
        });

        // 3. Cihaz Sütunu (Pasif Gri Renk)
        table.getColumnModel().getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                JLabel label = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    label.setForeground(UIManager.getColor("Label.disabledForeground"));
                }
                return label;
            }
        });

        // -- TEK TIKLAMA İLE YÖNLENDİRME (Mükemmel UX) --
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int viewRow = table.rowAtPoint(e.getPoint());
                if (viewRow >= 0 && e.getClickCount() == 1) { // Sadece Tek Tık
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    Service selectedService = tableModel.getItemAt(modelRow);
                    if (selectedService != null) {
                        FormManager.showForm(new FormService(selectedService));
                    }
                }
            }
        });

        // Durum Badgeleri için özel Renderer
        table.getColumnModel().getColumn(2).setCellRenderer(new UniversalVisualizableRenderer());

        // Bileşenleri ana panele ekle
        add(headerPanel);
        add(table.getTableHeader());
        add(table);
    }

    private void applyTableStyles(JTable table) {
        table.setRowHeight(40);
        table.setShowVerticalLines(false);
        table.setShowHorizontalLines(true);
        table.setOpaque(false);
        ((JComponent)table.getDefaultRenderer(Object.class)).setOpaque(false);
        table.getTableHeader().putClientProperty(FlatClientProperties.STYLE,
                "height:40; hoverBackground:null; pressedBackground:null; separatorColor:$TableHeader.background; font:bold -1;");
        table.putClientProperty(FlatClientProperties.STYLE,
                "rowHeight:37; showHorizontalLines:true; intercellSpacing:0,1; " +
                        "cellFocusColor:null; selectionBackground:$TableHeader.hoverBackground; " +
                        "selectionForeground:$Table.foreground;");
    }

    private Icon createIcon(String icon, Color color) {
        return new FlatSVGIcon(icon, 1).setColorFilter(new FlatSVGIcon.ColorFilter(color1 -> color));
    }


}
