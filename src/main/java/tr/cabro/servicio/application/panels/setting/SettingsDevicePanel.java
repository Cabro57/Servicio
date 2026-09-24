package tr.cabro.servicio.application.panels.setting;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Toast;
import tr.cabro.servicio.application.handlers.BrandExportHandler;
import tr.cabro.servicio.application.handlers.TypeImportHandler;
import tr.cabro.servicio.application.renderer.list.BrandListCellRenderer;
import tr.cabro.servicio.application.renderer.list.TypeListCellRenderer;
import tr.cabro.servicio.application.utils.ErrorHandler;
import tr.cabro.servicio.application.utils.Ikon;
import tr.cabro.servicio.i18n.Messages;
import tr.cabro.servicio.model.dictionary.DeviceBrand;
import tr.cabro.servicio.model.dictionary.DeviceType;
import tr.cabro.servicio.service.DeviceDictionaryManager;
import tr.cabro.servicio.service.ServiceManager;

import javax.swing.*;

public class SettingsDevicePanel extends JPanel {

    private final DefaultListModel<DeviceType> typeModel;
    private final DefaultListModel<DeviceBrand> brandModel;

    private final DeviceDictionaryManager deviceDictService;

    public SettingsDevicePanel() {
        this.deviceDictService = ServiceManager.getDeviceDictionaryManager();

        typeModel = new DefaultListModel<>();
        brandModel = new DefaultListModel<>();

        init();
    }

    private void init() {
        initComponent();
        deviceDictService.getAllTypes().thenAccept(deviceTypes -> SwingUtilities.invokeLater(() -> {
            deviceTypes.forEach(typeModel::addElement);
            // Marka listesi boş açılmasın: ilk tür seçili gelsin.
            if (!typeModel.isEmpty() && typeList.getSelectedIndex() < 0) typeList.setSelectedIndex(0);
        })).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türleri yüklenemedi", ex));

        typeField.addActionListener(e -> onTypeAdd());

        typeAddButton.addActionListener(e -> onTypeAdd());

        // Tür seçildiğinde markaları yükle
        typeList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                DeviceType selectedType = typeList.getSelectedValue();
                loadBrands(selectedType);
            }
        });

        brandField.addActionListener(e -> onBrandAdd());
        brandAddButton.addActionListener(e -> onBrandAdd());

        // --- Drag & Drop ayarları ---
        brandList.setDragEnabled(true);
        brandList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        brandList.setTransferHandler(new BrandExportHandler());

        typeList.setDropMode(DropMode.ON);
        typeList.setTransferHandler(new TypeImportHandler(this));
    }

    private void onTypeAdd() {
        String typeName = typeField.getText().trim();

        deviceDictService.addType(typeName).thenAccept(id -> {
            SwingUtilities.invokeLater(() -> {
                typeModel.addElement(new DeviceType((long) id, typeName, 0));
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.deviceType.added", typeName));
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türü eklenemedi", ex));

        typeField.setText("");
    }

    private void onTypeDel(DeviceType selectedType) {

        if (selectedType == null) {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.deviceType.selectToDelete"));
            return;
        }

        deviceDictService.deleteType(selectedType.getId()).thenAccept(v -> {
            SwingUtilities.invokeLater(() -> {
                typeModel.removeElement(selectedType);
                brandModel.clear();
                Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.deviceType.deleted", selectedType));
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Cihaz türü silinemedi", ex));

    }

    private void onBrandAdd() {
        DeviceType selectedType = typeList.getSelectedValue();
        String brandName = brandField.getText().trim();
        if (selectedType != null) {
            deviceDictService.addBrandToType(selectedType.getId(), brandName).thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    Toast.show(this, Toast.Type.SUCCESS, Messages.get("toast.brand.created", brandName));
                    loadBrands(selectedType);
                });
            }).exceptionally(ex -> ErrorHandler.handle(this, "Marka eklenemedi", ex));
        } else {
            Toast.show(this, Toast.Type.WARNING, Messages.get("toast.deviceType.selectFirst"));
        }

        brandField.setText("");
    }

    private void onBrandDel(DeviceBrand selectedBrand) {
        DeviceType selectedType = typeList.getSelectedValue();
        if (selectedType != null && selectedBrand != null) {
            deviceDictService.unlinkBrandFromType(selectedType.getId(), selectedBrand.getId()).thenAccept(v -> {
                SwingUtilities.invokeLater(() -> {
                    brandModel.removeElement(selectedBrand);
                    Toast.show(this, Toast.Type.INFO, Messages.get("toast.brand.unlinked", selectedType.getName(), selectedBrand.getName()));
                });
            }).exceptionally(ex -> ErrorHandler.handle(this, "Marka bağlantısı kaldırılamadı", ex));
        }
    }

    public void loadBrands(DeviceType deviceType) {
        if (deviceType == null) {
            return;
        }

        deviceDictService.getBrandsByTypeId(deviceType.getId()).thenAccept(deviceBrands -> {
            deviceBrands.stream()
                    .sorted((o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));

            SwingUtilities.invokeLater(() -> {
                brandModel.clear();
                deviceBrands.forEach(brandModel::addElement);
                brandTitle.setText(deviceType.getName() + " markaları");
            });
        }).exceptionally(ex -> ErrorHandler.handle(this, "Markalar yüklenemedi", ex));
    }

    private void initComponent() {
        setLayout(new MigLayout("insets 4 24 20 24, gap 16", "[grow 40, fill, sg col][grow 60, fill, sg col]", "[fill, grow]"));
        setOpaque(false);

        add(getDeviceTypePanel(), "grow");
        add(getBrandPanel(), "grow");
    }

    private JPanel getDeviceTypePanel() {
        JPanel deviceTypePanel = new JPanel(new MigLayout("insets 14 16 14 16, fill, wrap 2", "[grow][pref!]", "[]2[]10[][grow, fill]"));
        deviceTypePanel.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        JLabel title = new JLabel("Türler");
        title.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");

        JLabel subtitle = SettingsKit.note("Arıza kaydında ilk seçilen kategori.");

        typeField = new JTextField();
        typeField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Yeni tür — Enter ile ekle");

        typeAddButton = new JButton(new Ikon("icons/plus.svg", 16));
        typeAddButton.setToolTipText("Türü ekle");
        typeAddButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");

        typeList = new JList<>();
        typeList.putClientProperty(FlatClientProperties.STYLE, "selectionArc: 10");
        typeList.setCellRenderer(new TypeListCellRenderer(typeList, this::onTypeDel));
        typeList.setModel(typeModel);

        deviceTypePanel.add(title, "span 2");
        deviceTypePanel.add(subtitle, "span 2");
        deviceTypePanel.add(typeField, "growx");
        deviceTypePanel.add(typeAddButton);
        JScrollPane typeScroll = SettingsKit.listScroll(typeList);
        typeScroll.setBorder(BorderFactory.createEmptyBorder());
        deviceTypePanel.add(typeScroll, "span 2, grow, hmin 160");

        return deviceTypePanel;
    }

    private JPanel getBrandPanel() {
        JPanel brandPanel = new JPanel(new MigLayout("insets 14 16 14 16, fill, wrap 2", "[grow][pref!]", "[]2[]10[][grow, fill]"));
        brandPanel.putClientProperty(FlatClientProperties.STYLE_CLASS, "listCard");
        brandTitle = new JLabel("Markalar");
        brandTitle.putClientProperty(FlatClientProperties.STYLE, "font: $h3.font");

        JLabel subtitle = SettingsKit.note("Seçili türün markaları. Bir markayı başka türe taşımak için sürükleyip o türün üstüne bırakın.");

        brandField = new JTextField();
        brandField.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "Yeni marka — Enter ile ekle");

        brandAddButton = new JButton(new Ikon("icons/plus.svg", 16));
        brandAddButton.setToolTipText("Markayı seçili türe ekle");
        brandAddButton.putClientProperty(FlatClientProperties.STYLE, "arc: 10; margin: 4,8,4,8");

        brandList = new JList<>();

        brandList.setCellRenderer(new BrandListCellRenderer(brandList, this::onBrandDel));
        brandList.setLayoutOrientation(JList.HORIZONTAL_WRAP);
        brandList.setVisibleRowCount(-1);
        brandList.setModel(brandModel);

        brandPanel.add(brandTitle, "span 2");
        brandPanel.add(subtitle, "span 2, wmin 0");
        brandPanel.add(brandField, "growx");
        brandPanel.add(brandAddButton);
        JScrollPane brandScroll = SettingsKit.listScroll(brandList);
        brandScroll.setBorder(BorderFactory.createEmptyBorder());
        brandPanel.add(brandScroll, "span 2, grow, hmin 160");

        return brandPanel;
    }

    private JLabel brandTitle;
    JTextField typeField;
    JButton typeAddButton;
    public JList<DeviceType> typeList;
    JTextField brandField;
    JButton brandAddButton;
    public JList<DeviceBrand> brandList;

}
