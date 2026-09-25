package tr.cabro.servicio.application.system;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import raven.modal.Drawer;
import tr.cabro.servicio.application.component.FormSearchButton;
import tr.cabro.servicio.application.component.RefreshLine;
import tr.cabro.servicio.application.component.StatusBar;
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

    // ─── Header ───────────────────────────────────────────────────────────────

    private JPanel createHeader() {
        JPanel panel = new JPanel(new MigLayout("insets 3", "[]push[]push", "[fill]"));
        JToolBar toolBar = new JToolBar();

        JButton buttonDrawer = new JButton(new Ikon("icons/menu.svg", 1f));
        buttonUndo    = new JButton(new Ikon("icons/arrow-left.svg", 1f));
        buttonRedo    = new JButton(new Ikon("icons/arrow-right.svg", 1f));
        buttonRefresh = new JButton(new Ikon("icons/refresh-cw.svg", 1f));

        buttonDrawer.putClientProperty(FlatClientProperties.STYLE, "arc:10;");
        buttonUndo.putClientProperty(FlatClientProperties.STYLE,   "arc:10;");
        buttonRedo.putClientProperty(FlatClientProperties.STYLE,   "arc:10;");
        buttonRefresh.putClientProperty(FlatClientProperties.STYLE,"arc:10;");

        buttonDrawer.addActionListener(e -> {
            if (Drawer.isOpen()) Drawer.showDrawer();
            else Drawer.toggleMenuOpenMode();
        });
        buttonUndo.addActionListener(e    -> FormManager.undo());
        buttonRedo.addActionListener(e    -> FormManager.redo());
        buttonRefresh.addActionListener(e -> FormManager.refresh());

        toolBar.add(buttonDrawer);
        toolBar.add(buttonUndo);
        toolBar.add(buttonRedo);
        toolBar.add(buttonRefresh);

        panel.add(toolBar);
        panel.add(createSearchBox(), "gapx n 135");
        return panel;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────

    private JPanel createFooter() {
        JPanel panel = new JPanel(new MigLayout("insets 0 6 0 6, height 30!", "[grow, fill]", "[fill]"));
        panel.putClientProperty(FlatClientProperties.STYLE,
                "[light]background:tint($Panel.background,20%);" +
                        "[dark]background:tint($Panel.background,5%);");

        // Solda iş nabzı, sağda sistem durumu (güncelleme, yedek, sürüm). Bkz. StatusBar.
        statusBar = new StatusBar();
        panel.add(statusBar);
        return panel;
    }
    // ─── Form Yönetimi ────────────────────────────────────────────────────────

    private JPanel createSearchBox() {
        JPanel panel = new JPanel(new MigLayout("fill", "[fill,center,240:320:]", "[fill]"));
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

        if (statusBar != null) statusBar.refresh();

        buttonUndo.setEnabled(FormManager.FORMS.isUndoAble());
        buttonRedo.setEnabled(FormManager.FORMS.isRedoAble());

        if (mainPanel.getComponentOrientation().isLeftToRight()
                != form.getComponentOrientation().isLeftToRight()) {
            applyComponentOrientation(mainPanel.getComponentOrientation());
        }
    }

    public void refresh() { refreshLine.refresh(); }

    public void refreshStatus() {
        if (statusBar != null) statusBar.refresh();
    }

    // ─── Alanlar ──────────────────────────────────────────────────────────────

    private JPanel      mainPanel;
    private RefreshLine refreshLine;
    private JButton     buttonUndo;
    private JButton     buttonRedo;
    private JButton     buttonRefresh;
    private StatusBar   statusBar;
}