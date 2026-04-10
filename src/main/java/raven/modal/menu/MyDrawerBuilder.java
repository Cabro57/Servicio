package raven.modal.menu;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import raven.extras.AvatarIcon;
import raven.modal.drawer.DrawerPanel;
import raven.modal.drawer.item.Item;
import raven.modal.drawer.item.MenuItem;
import raven.modal.drawer.menu.MenuOption;
import raven.modal.drawer.menu.MenuStyle;
import raven.modal.drawer.renderer.DrawerNoneLineStyle;
import raven.modal.drawer.simple.SimpleDrawerBuilder;
import raven.modal.drawer.simple.footer.LightDarkButtonFooter;
import raven.modal.drawer.simple.footer.SimpleFooterData;
import raven.modal.drawer.simple.header.SimpleHeader;
import raven.modal.drawer.simple.header.SimpleHeaderData;
import raven.modal.option.Option;
import raven.modal.system.AllForms;
import raven.modal.system.Form;
import raven.modal.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.*;
import tr.cabro.servicio.application.util.Ikon;
import tr.cabro.servicio.model.User;

import javax.swing.*;
import java.awt.*;
import java.io.File;

public class MyDrawerBuilder extends SimpleDrawerBuilder {

    private static MyDrawerBuilder instance;
    @Getter
    private User user;

    public static MyDrawerBuilder getInstance() {
        if (instance == null) {
            instance = new MyDrawerBuilder();
        }
        return instance;
    }

    public void setUser(User user) {
        this.user = user;

        // set user to menu validation
        MyMenuValidation.setUser(user);

        // setup drawer header
        SimpleHeader header = (SimpleHeader) getHeader();
        SimpleHeaderData data = header.getSimpleHeaderData();
        AvatarIcon icon = (AvatarIcon) data.getIcon();

        String photoName = user.getProfilePicture();
        boolean loaded = false;

        if (photoName != null && !photoName.trim().isEmpty()) {
            // Resmin fiziksel olarak bulunduğu klasör yolu
            File profileDir = new File(Servicio.getInstance().getDataFolder(), "profiles");
            File photoFile = new File(profileDir, photoName);

            // Eğer dosya gerçekten diskte varsa
            if (photoFile.exists()) {
                try {
                    // Resmi diskten oku
                    ImageIcon originalIcon = new ImageIcon(photoFile.getAbsolutePath());

                    // Arayüzü kasmamak ve UI'ı bozmamak için resmi 100x100 boyutuna pürüzsüz (SMOOTH) ölçekle
                    Image scaledImage = originalIcon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
                    icon.setIcon(new ImageIcon(scaledImage));
                    loaded = true;
                } catch (Exception e) {
                    Servicio.getLogger().error("Profil resmi yüklenemedi: " + photoFile.getAbsolutePath(), e);
                }
            }
        }

        // Eğer resim veritabanında yoksa, diskten silinmişse veya yüklenirken hata olduysa varsayılanı (SVG) göster
        if (!loaded) {
            icon.setIcon(new FlatSVGIcon("drawer/image/avatar_male.svg", 100, 100));
        }

        data.setTitle(user.getBusinessName());
        data.setDescription(user.getEmail());
        header.setSimpleHeaderData(data);

        rebuildMenu();
    }

    private final int SHADOW_SIZE = 12;

    private MyDrawerBuilder() {
        super(createSimpleMenuOption());
        LightDarkButtonFooter lightDarkButtonFooter = (LightDarkButtonFooter) getFooter();
        lightDarkButtonFooter.addModeChangeListener(isDarkMode -> {
            // event for light dark mode changed
        });
    }

    @Override
    public SimpleHeaderData getSimpleHeaderData() {
        AvatarIcon icon = new AvatarIcon(new FlatSVGIcon("icons/user.svg", 100, 100), 50, 50, 3.5f);
        icon.setType(AvatarIcon.Type.MASK_SQUIRCLE);
        icon.setBorder(2, 2);

        changeAvatarIconBorderColor(icon);

        UIManager.addPropertyChangeListener(evt -> {
            if (evt.getPropertyName().equals("lookAndFeel")) {
                changeAvatarIconBorderColor(icon);
            }
        });

        return new SimpleHeaderData()
                .setIcon(icon)
                .setTitle("Cabro")
                .setDescription("cabro@gmail.com");
    }

    private void changeAvatarIconBorderColor(AvatarIcon icon) {
        icon.setBorderColor(new AvatarIcon.BorderColor(UIManager.getColor("Component.accentColor"), 0.7f));
    }

    @Override
    public SimpleFooterData getSimpleFooterData() {
        return new SimpleFooterData()
                .setTitle("Servicio")
                .setDescription("Version " + Servicio.getInstance().getAppVersion());
    }

    @Override
    public Option createOption() {
        Option option = super.createOption();
        option.setOpacity(0.3f);
//        option.getBorderOption()
//                .setShadowSize(new Insets(0, 0, 0, SHADOW_SIZE));
        return option;
    }

    public static MenuOption createSimpleMenuOption() {

        // create simple menu option
        MenuOption simpleMenuOption = new MenuOption() {
            @Override
            public Icon buildMenuIcon(String path, float scale) {
                return new Ikon(path, scale);
            }
        };

        MenuItem[] items = new MenuItem[]{
                new Item("Ana Sayfa", "layout-dashboard.svg", FormDashboard.class),
                new Item("Servis Kayıtları", "wrench.svg", FormServices.class),
                new Item("Müşteriler", "user-search.svg", FormCustomers.class),
                new Item("Parçalar", "circuit-board.svg", FormParts.class),
                new Item("Tedarikçiler", "store.svg", FormSuppliers.class),
                new Item("Ayarlar", "settings.svg", FormSettings.class),
                new Item("Hakkında", "info.svg")
        };

        simpleMenuOption.setMenuStyle(new MenuStyle() {

            @Override
            public void styleMenuItem(JButton menu, int[] index, boolean isMainItem) {
                menu.getFont().deriveFont(Font.BOLD);
                boolean isTopLevel = index.length == 1;
                if (isTopLevel) {
                    // adjust item menu at the top level because it's contain icon
                    menu.putClientProperty(FlatClientProperties.STYLE, "margin:-1,0,-1,0;");
                }
            }

            @Override
            public void styleMenu(JComponent component) {
                component.putClientProperty(FlatClientProperties.STYLE, getDrawerBackgroundStyle());
            }
        });

        simpleMenuOption.getMenuStyle().setDrawerLineStyleRenderer(new DrawerNoneLineStyle());
        simpleMenuOption.setMenuValidation(new MyMenuValidation());

        simpleMenuOption.addMenuEvent((action, index) -> {
//                System.out.println("Drawer menu selected " + Arrays.toString(index));
            Class<?> itemClass = action.getItem().getItemClass();
            int i = index[0];
            if (i == 6) {
                action.consume();
                FormManager.showAbout();
                return;
            }

            if (itemClass == null || !Form.class.isAssignableFrom(itemClass)) {
                action.consume();
                return;
            }
            Class<? extends Form> formClass = (Class<? extends Form>) itemClass;
            FormManager.showForm(AllForms.getForm(formClass));
        });

        simpleMenuOption.setMenus(items)
                .setBaseIconPath("icons")
                .setIconScale(1f);

        return simpleMenuOption;
    }

    @Override
    public int getDrawerWidth() {
        return 270 + SHADOW_SIZE;
    }

    @Override
    public int getDrawerCompactWidth() {
        return 80 + SHADOW_SIZE;
    }

    @Override
    public int getOpenDrawerAt() {
        return 1000;
    }

    @Override
    public boolean openDrawerAtScale() {
        return false;
    }

    @Override
    public void build(DrawerPanel drawerPanel) {
        drawerPanel.putClientProperty(FlatClientProperties.STYLE, getDrawerBackgroundStyle());
    }

    private static String getDrawerBackgroundStyle() {
        return "[light]background:tint($Panel.background,20%);" +
                "[dark]background:tint($Panel.background,5%);";
    }
}
