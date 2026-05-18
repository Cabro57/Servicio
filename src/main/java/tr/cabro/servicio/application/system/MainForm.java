package tr.cabro.servicio.application.system;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import raven.modal.Toast;
import raven.modal.toast.ToastPromise;
import raven.modal.toast.option.ToastLocation;
import raven.modal.toast.option.ToastOption;
import tr.cabro.servicio.application.component.FormSearchButton;
import tr.cabro.servicio.application.component.MemoryBar;
import tr.cabro.servicio.application.component.RefreshLine;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;
import java.awt.*;

public class MainForm extends JPanel {

    public MainForm() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fillx,wrap,insets 0,gap 0", "[fill]", "[][][fill,grow][]"));
        add(createHeader());
        add(createRefreshLine(), "height 3!");
        add(createMain());
        add(new JSeparator(), "height 2!");
        add(createFooter());
    }

    private JPanel createHeader() {
        JPanel panel = new JPanel(new MigLayout("insets 3", "[]push[]push", "[fill]"));
        JToolBar toolBar = new JToolBar();
        JButton buttonDrawer = new JButton(new Ikon("icons/menu.svg", 1f));
        buttonUndo = new JButton(new Ikon("icons/arrow-left.svg", 1f));
        buttonRedo = new JButton(new Ikon("icons/arrow-right.svg", 1f));
        buttonRefresh = new JButton(new Ikon("icons/refresh-cw.svg", 1f));

        // style
        buttonDrawer.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc:10;");
        buttonUndo.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc:10;");
        buttonRedo.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc:10;");
        buttonRefresh.putClientProperty(FlatClientProperties.STYLE, "" +
                "arc:10;");

        buttonDrawer.addActionListener(e -> {
            if (Drawer.isOpen()) {
                Drawer.showDrawer();
            } else {
                Drawer.toggleMenuOpenMode();
            }
        });
        buttonUndo.addActionListener(e -> FormManager.undo());
        buttonRedo.addActionListener(e -> FormManager.redo());
        buttonRefresh.addActionListener(e -> FormManager.refresh());

        toolBar.add(buttonDrawer);
        toolBar.add(buttonUndo);
        toolBar.add(buttonRedo);
        toolBar.add(buttonRefresh);
        panel.add(toolBar);
        panel.add(createSearchBox(), "gapx n 135");
        return panel;
    }

    private JPanel createFooter() {
        JPanel panel = new JPanel(new MigLayout("insets 1 n 1 n,al trailing center,gapx 10,height 30!", "[]push[][][]", "fill"));
        panel.putClientProperty(FlatClientProperties.STYLE, "" +
                "[light]background:tint($Panel.background,20%);" +
                "[dark]background:tint($Panel.background,5%);");

        // demo version
        JLabel lbDemoVersion = new JLabel("Sürüm: " + Servicio.getInstance().getAppVersion());
        lbDemoVersion.putClientProperty(FlatClientProperties.STYLE, "" +
                "foreground:$Label.disabledForeground;");
        lbDemoVersion.setIcon(new Ikon("icons/git-merge.svg", 0.7f, "Label.disabledForeground"));
        panel.add(lbDemoVersion);


        // java version
        String javaVendor = System.getProperty("java.vendor");
        if (javaVendor.equals("Oracle Corporation")) {
            javaVendor = "";
        }
        String java = javaVendor + " v" + System.getProperty("java.version").trim();
        String st = "Running on: Java %s";
        JLabel lbJava = new JLabel(String.format(st, java));
        lbJava.putClientProperty(FlatClientProperties.STYLE, "" +
                "foreground:$Label.disabledForeground;");
        lbJava.setIcon(new Ikon("icons/java.svg", 1f, "Label.disabledForeground"));
        panel.add(lbJava);

        panel.add(new JSeparator(JSeparator.VERTICAL));

        // memory
        MemoryBar memoryBar = new MemoryBar();
        panel.add(memoryBar);

        JButton helper = new JButton(new Ikon("icons/circle-alert.svg", 0.7f));
        helper.putClientProperty(FlatClientProperties.BUTTON_TYPE, "toolBarButton");

        JPopupMenu popupMenu = new JPopupMenu();

        // 1. Öğe: Standart Güncelleme Kontrol Linki
        JMenuItem menuCheckUpdate = new JMenuItem("Güncellemeleri Kontrol Et");
        menuCheckUpdate.setIcon(new Ikon("icons/refresh-cw.svg", 0.7f));
        menuCheckUpdate.addActionListener(ae -> {

            ToastOption option = Toast.createOption();
            option.setHtmlEnabled(true)
                    .setCloseOnClick(true)
                    .getLayoutOption()
                    .setLocation(ToastLocation.TOP_TRAILING);

            Toast.showPromise(this, "", option, new ToastPromise() {
                @Override
                public void execute(PromiseCallback callback) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                    Servicio.getInstance().getUpdateChecker().checkNow();
                    callback.done(Toast.Type.SUCCESS, "Tamamlandı");
                }
            });
        });
        popupMenu.add(menuCheckUpdate);

        popupMenu.addSeparator();

        JMenuItem menuUpdateNow = new JMenuItem("Yeni Güncelleme Mevcut! Şimdi Güncelle");
        menuUpdateNow.putClientProperty(FlatClientProperties.STYLE, "foreground:$Component.warningForeground;");
        menuUpdateNow.setIcon(new Ikon("icons/download.svg", 0.7f));

        menuUpdateNow.addActionListener(ae -> {
            JOptionPane.showMessageDialog(this, "Güncelleme paketi indiriliyor...", "Güncelleme Başladı", JOptionPane.INFORMATION_MESSAGE);
        });
        popupMenu.add(menuUpdateNow);

        helper.addActionListener(e -> {
            int popupWidth = popupMenu.getPreferredSize().width;
            int popupHeight = popupMenu.getPreferredSize().height;

            // X Ekseni: Menünün sağ kenarı ile butonun sağ kenarını hizalayarak sağa taşmayı önlüyoruz
            int x = helper.getWidth() - popupWidth;

            // Y Ekseni: Butonun tam üstünde başlaması için eksi değerde kendi yüksekliğini veriyoruz
            int y = -popupHeight;

            // Hesaplanan yeni konumda popup'ı göster
            popupMenu.show(helper, x, y);
        });

        panel.add(helper);

        return panel;
    }

    private JPanel createSearchBox() {
        JPanel panel = new JPanel(new MigLayout("fill", "[fill,center,200:250:]", "[fill]"));
        FormSearchButton button = new FormSearchButton();
        button.addActionListener(e -> FormSearch.getInstance().showSearch());
        panel.add(button);
        return panel;
    }

    private JPanel createRefreshLine() {
        refreshLine = new RefreshLine();
        return refreshLine;
    }

    private Component createMain() {
        mainPanel = new JPanel(new BorderLayout());
        return mainPanel;
    }

    public void setForm(Form form) {
        mainPanel.removeAll();
        mainPanel.add(form);
        mainPanel.repaint();
        mainPanel.revalidate();

        // check button
        buttonUndo.setEnabled(FormManager.FORMS.isUndoAble());
        buttonRedo.setEnabled(FormManager.FORMS.isRedoAble());
        // check component orientation and update
        if (mainPanel.getComponentOrientation().isLeftToRight() != form.getComponentOrientation().isLeftToRight()) {
            applyComponentOrientation(mainPanel.getComponentOrientation());
        }
    }

    public void refresh() {
        refreshLine.refresh();
    }

    private JPanel mainPanel;
    private RefreshLine refreshLine;

    private JButton buttonUndo;
    private JButton buttonRedo;
    private JButton buttonRefresh;
}
