package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Font;
import tr.cabro.servicio.model.Customer;

import javax.swing.*;

public class TableCellProfile extends JPanel {

    public TableCellProfile(Customer data, Font font) {
        lbName.setFont(font);
        lbLocation.setFont(font);
        lbName.setText(data.getFullName());
        lbLocation.putClientProperty(FlatClientProperties.STYLE, ""
                + "foreground:$Label.disabledForeground");
    }

    private javax.swing.JLabel lbLocation;
    private javax.swing.JLabel lbName;
}
