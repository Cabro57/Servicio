package tr.cabro.servicio.model.contract;

import tr.cabro.servicio.model.enums.BadgeColor;

public interface Visualizable {
    String getDisplayName();
    String getIconPath();
    BadgeColor getBadgeColor();
}