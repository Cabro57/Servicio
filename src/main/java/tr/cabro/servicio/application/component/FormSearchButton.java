package tr.cabro.servicio.application.component;

import com.formdev.flatlaf.FlatClientProperties;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.application.utils.Ikon;

import javax.swing.*;

public class FormSearchButton extends JButton {

    public FormSearchButton() {
        super("Ara veya işlem yap…", new Ikon("icons/search.svg", 0.4f));
        setToolTipText("Müşteri, servis, parça ara ya da Yeni Servis, Tahsilat gibi işlemleri çalıştır (Ctrl+K)");
        init();
    }

    private void init() {
        setLayout(new MigLayout("insets 0,al trailing,filly", "", "[center]"));
        setHorizontalAlignment(JButton.LEADING);
        putClientProperty(FlatClientProperties.STYLE, "" +
                "margin:5,7,5,10;" +
                "arc:10;" +
                "borderWidth:0;" +
                "focusWidth:0;" +
                "innerFocusWidth:0;" +
                "[light]background:shade($Panel.background,10%);" +
                "[dark]background:tint($Panel.background,10%);" +
                "[light]foreground:tint($Button.foreground,40%);" +
                "[dark]foreground:shade($Button.foreground,30%);");
        JLabel label = new JLabel("Ctrl K");
        label.putClientProperty(FlatClientProperties.STYLE, "" +
                "[light]foreground:tint($Button.foreground,40%);" +
                "[dark]foreground:shade($Button.foreground,30%);");
        add(label);
    }
}
