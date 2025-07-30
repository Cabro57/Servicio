package tr.cabro.servicio.application.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.settings.Theme;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.Set;

public class SettingsUI extends JDialog {
    private JPanel main_panel;
    private JButton ok_button;
    private JButton cancel_button;
    private JButton apply_button;
    private JLabel theme_label;
    private JComboBox<String> theme_combo;
    private JTabbedPane tabbed_pane;
    private JPanel theme_panel;

    public SettingsUI() {
        setIconImage(new FlatSVGIcon("icon/settings.svg").getImage());
        Setup();
        setContentPane(main_panel);
        getRootPane().setDefaultButton(ok_button);

        ok_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onOK();
            }
        });

        cancel_button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        });

        apply_button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                onApply();
            }
        });

        // call onCancel() when cross is clicked
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                onCancel();
            }
        });

        // call onCancel() on ESCAPE
        main_panel.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                onCancel();
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    }

    private void Setup() {
        Dimension screen_size = Toolkit.getDefaultToolkit().getScreenSize();
        int width = (int) (screen_size.width * 0.6);
        int height = (int) (screen_size.height * 0.6);

        setSize(width, height);
        setLocationRelativeTo(null);

        setTitle("Ayarlar");

        FlatSVGIcon icon = new FlatSVGIcon("icon/settings.svg", 18, 18);
        Color newcolor = UIManager.getColor( "MenuItem.foreground" );
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(color -> newcolor));
        setIconImage(icon.getImage());

        // Diğer ayarların yüklenmesi
        loadThemes();
    }


    private void loadThemes() {
        Set<String> themes =  Servicio.getSettings().getTemplate().getThemes().keySet();

        for (String theme : themes) {
            theme_combo.addItem(theme);
        }

        String selectedTheme = Servicio.getSettings().getTemplate().getSelected_theme();
        if (selectedTheme != null) {
            theme_combo.setSelectedItem(selectedTheme);
        }

        theme_combo.addActionListener(e -> {
            String selected_theme = (String) theme_combo.getSelectedItem();
            if (selected_theme != null && !selected_theme.equals(Servicio.getSettings().getTemplate().getSelected_theme())) {
                Theme.apply(selected_theme);
                Servicio.getSettings().getTemplate().setSelected_theme(selected_theme);
            }
        });
    }


    private void onOK() {
        Servicio.getSettings().save();

        dispose(); // Pencereyi kapat
    }

    private void onCancel() {
        // add your code here if necessary
        Servicio.getSettings().save();
        dispose();
    }

    private void onApply() {
        Servicio.getSettings().save();
    }
}
