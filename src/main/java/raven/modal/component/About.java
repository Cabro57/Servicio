package raven.modal.component;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.util.LoggingFacade;
import net.miginfocom.swing.MigLayout;
import tr.cabro.servicio.Servicio;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.text.DefaultCaret;
import java.awt.*;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

public class About extends JPanel {

    public About() {
        init();
    }

    private void init() {
        setLayout(new MigLayout("fillx,wrap,insets 5 30 5 30,width 400", "[fill,330::]", ""));

        JTextPane title = createText("Servisio Projesi");
        title.putClientProperty(FlatClientProperties.STYLE, "" +
                "font:bold +5");

        JTextPane description = createText("");
        description.setContentType("text/html");
        description.setText(getDescriptionText());
        description.addHyperlinkListener(e -> {
            if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                showUrl(e.getURL());
            }
        });

        add(title);
        add(description);
        add(createSystemInformation());
    }

    private JTextPane createText(String text) {
        JTextPane textPane = new JTextPane();
        textPane.setBorder(BorderFactory.createEmptyBorder());
        textPane.setText(text);
        textPane.setEditable(false);
        textPane.setCaret(new DefaultCaret() {
            @Override
            public void paint(Graphics g) {
            }
        });
        return textPane;
    }

    private String getDescriptionText() {
        String text = "This is a demo project for the Modal Dialog library, " +
                "built using FlatLaf Look and Feel and MigLayout library.<br>" +
                "For source code, visit the <a href=\"https://github.com/DJ-Raven/swing-modal-dialog/\">GitHub Project.</a>";

        text =  "    <p>\n" +
                "      Servicio, teknik servis s&#252;re&#231;lerini daha h&#305;zl&#305;, d&#252;zenli ve takip \n" +
                "      edilebilir hale getirmek i&#231;in geli&#351;tirilen bir y&#246;netim uygulamas&#305;d&#305;r.\n" +
                "    </p>\n" +
                "    <p>\n" +
                "      Bu beta s&#252;r&#252;m&#252;, par&#231;a y&#246;netimi, m&#252;&#351;teri takibi ve servis s&#252;re&#231;lerini \n" +
                "      kolayla&#351;t&#305;rmaya odaklanarak geli&#351;tirilmi&#351;tir.\n" +
                "    </p>\n" +
                "    <p style=\"margin-top: 10px\">\n" +
                "      <b>Not:</b><br>Bu uygulama halen geli&#351;tirme a&#351;amas&#305;ndad&#305;r. G&#246;r&#252;&#351; ve geri \n" +
                "      bildirimlerinizle daha iyi bir Servicio deneyimi olu&#351;turulmas&#305;na katk&#305;da \n" +
                "      bulunabilirsiniz.\n" +
                "    </p>\n";

        return text;
    }

    private String getSystemInformationText() {
        String text = "<b>Demo Version: </b>%s<br/>" +
                "<b>Java: </b>%s<br/>" +
                "<b>System: </b>%s<br/>";

        return text;
    }

    private JComponent createSystemInformation() {
        JPanel panel = new JPanel(new MigLayout("wrap"));
        panel.setBorder(new TitledBorder("System Information"));
        JTextPane textPane = createText("");
        textPane.setContentType("text/html");
        String version = Servicio.getInstance().getAppVersion();
        String java = System.getProperty("java.vendor") + " - v" + System.getProperty("java.version");
        String system = System.getProperty("os.name") + " " + System.getProperty("os.arch") + " - v" + System.getProperty("os.version");
        String text = String.format(getSystemInformationText(),
                version,
                java,
                system);
        textPane.setText(text);
        panel.add(textPane);
        return panel;
    }

    private void showUrl(URL url) {
        if (Desktop.isDesktopSupported()) {
            Desktop desktop = Desktop.getDesktop();
            if (desktop.isSupported(Desktop.Action.BROWSE)) {
                try {
                    desktop.browse(url.toURI());
                } catch (IOException | URISyntaxException e) {
                    LoggingFacade.INSTANCE.logSevere("Error browse url", e);
                }
            }
        }
    }
}