package tr.cabro.servicio.application.component.table;

import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Font;
import tr.cabro.servicio.model.Customer;

public class TableCellProfile extends javax.swing.JPanel {

    public TableCellProfile(Customer data, Font font) {
        lbName.setFont(font);
        lbLocation.setFont(font);
        lbName.setText(data.getName());
        lbLocation.putClientProperty(FlatClientProperties.STYLE, ""
                + "foreground:$Label.disabledForeground");
    }


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lbLocation;
    private javax.swing.JLabel lbName;
    // End of variables declaration//GEN-END:variables
}
