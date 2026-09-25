package tr.cabro.servicio.application.menu;

import com.formdev.flatlaf.FlatClientProperties;
import com.formdev.flatlaf.FlatLaf;
import com.formdev.flatlaf.util.ColorFunctions;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import lombok.Getter;
import net.miginfocom.swing.MigLayout;
import raven.extras.AvatarIcon;
import raven.modal.drawer.DrawerPanel;
import raven.modal.drawer.item.Item;
import raven.modal.drawer.item.MenuItem;
import raven.modal.drawer.menu.AbstractMenuElement;
import raven.modal.drawer.menu.MenuItemLayoutOption;
import raven.modal.drawer.menu.MenuOption;
import raven.modal.drawer.menu.MenuStyle;
import raven.modal.drawer.renderer.DrawerNoneLineStyle;
import raven.modal.drawer.simple.SimpleDrawerBuilder;
import raven.modal.drawer.simple.header.SimpleHeader;
import raven.modal.drawer.simple.footer.LightDarkButtonFooter;
import raven.modal.drawer.simple.footer.SimpleFooterData;
import raven.modal.drawer.simple.header.SimpleHeader;
import raven.modal.drawer.simple.header.SimpleHeaderData;
import raven.modal.option.Option;
import tr.cabro.servicio.application.system.AllForms;
import tr.cabro.servicio.application.system.Form;
import tr.cabro.servicio.application.system.FormManager;
import tr.cabro.servicio.Servicio;
import tr.cabro.servicio.application.forms.*;
import tr.cabro.servicio.model.User;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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

    /**
     * Oturum kullanıcısını menüye işler ve menüyü yeniden kurar. EDT dışından çağrılırsa
     * (ör. {@code UserService.authenticate} kilit ekranında arka plan thread'inden çağırıyor)
     * iş EDT'ye aktarılır: {@link #rebuildMenu()} önce tüm öğeleri söküp sonra yeniden
     * ekliyor; bu EDT'deki çizim/yerleşimle yarışınca menü öğeleri ve başlık yazıları çift
     * görünebiliyordu.
     */
    public void setUser(User user) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> setUser(user));
            return;
        }
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

    /** Menü bileşen ağacını değiştirdiği için yalnızca EDT'de çalışır (bkz. {@link #setUser}). */
    @Override
    public void rebuildMenu() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::rebuildMenu);
            return;
        }
        super.rebuildMenu();
    }

    private final int SHADOW_SIZE = 12;

    private MyDrawerBuilder() {
        super(createSimpleMenuOption());
        LightDarkButtonFooter lightDarkButtonFooter = (LightDarkButtonFooter) getFooter();
        lightDarkButtonFooter.addModeChangeListener(isDarkMode -> {
            // event for light dark mode changed
        });
    }

    /**
     * Menü başlığı: avatar + işletme adı (tık: profil) ve sağda ekranı kilitleme
     * düğmesi. Dar (ikon) modda yalnızca avatar kalır.
     */
    @Override
    public AbstractMenuElement createHeader() {
        return new SimpleHeader(getSimpleHeaderData()) {
            private JPanel sessionActions;

            {
                setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                setToolTipText("Profil Ayarları");
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        tr.cabro.servicio.application.system.FormManager.showProfile();
                    }
                });
            }

            @Override
            protected void initComponent() {
                super.initComponent();
                layout.setLayoutConstraints("hidemode 3,insets 15 15 5 10,gap 10");
                layout.setColumnConstraints("[][grow,fill][]");
                layout.setComponentConstraints(panel, "wmin 0");

                sessionActions = new JPanel(new MigLayout("insets 0,gap 0", "[]", "[center]"));
                sessionActions.setOpaque(false);
                sessionActions.add(sessionButton("icons/lock.svg", "Ekranı kilitle",
                        "Açık pencereler ve yazdıklarınız kilit açılınca aynen geri gelir",
                        tr.cabro.servicio.application.system.FormManager::lockForInactivity));
                add(sessionActions, "aligny center");
            }

            @Override
            protected void layoutOptionChanged(MenuOption.MenuOpenMode menuOpenMode) {
                super.layoutOptionChanged(menuOpenMode);
                boolean full = menuOpenMode == MenuOption.MenuOpenMode.FULL;
                if (full) layout.setColumnConstraints("[][grow,fill][]");
                if (sessionActions != null) sessionActions.setVisible(full);
            }
        };
    }

    private static JButton sessionButton(String icon, String name, String tooltip, Runnable action) {
        JButton b = new JButton(new tr.cabro.servicio.application.utils.Ikon(icon, 16, "Label.disabledForeground"));
        b.setRolloverIcon(new tr.cabro.servicio.application.utils.Ikon(icon, 16, "Label.foreground"));
        b.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        b.putClientProperty(FlatClientProperties.STYLE, "arc:10;margin:6,6,6,6;toolbar.hoverBackground:fade($Label.foreground,6%)");
        b.setToolTipText(name + " — " + tooltip);
        b.getAccessibleContext().setAccessibleName(name);
        b.setFocusable(false);
        b.addActionListener(e -> action.run());
        return b;
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
                .setTitle("User")
                .setDescription("user@example.com");
    }

    private void changeAvatarIconBorderColor(AvatarIcon icon) {
        icon.setBorderColor(new AvatarIcon.BorderColor(UIManager.getColor("Component.accentColor"), 0.7f));
    }

    @Override
    public SimpleFooterData getSimpleFooterData() {
        return new SimpleFooterData()
                .setTitle("Servicio")
                .setDescription("Version " + Servicio.getInstance().getAppVersion());// + Servicio.getInstance().getAppVersion());
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
            {
                // Dar (ikon) modda setter yok; alan protected.
                compactMenuItemLayoutOption = new MenuItemLayoutOption()
                        .setMenuHorizontalMargin(new Point(16, 16))
                        .setLabelMargin(new Insets(14, 4, 4, 4))
                        .setSeparatorMargin(new Insets(2, 20, 2, 20));
            }

            @Override
            public Icon buildMenuIcon(String path, float scale) {
                // İkon, çizildiği butonun durumuna göre boyanır: seçiliyken yazıyla aynı vurgu rengi.
                FlatSVGIcon icon = new FlatSVGIcon(path, scale);
                FlatSVGIcon.ColorFilter filter = new FlatSVGIcon.ColorFilter();
                filter.setMapperEx((component, color) -> menuIconColor(component));
                icon.setColorFilter(filter);
                return icon;
            }
        };

        // Tezgâh akışına göre gruplu menü: en sık açılan iki ekran başlıksız en üstte, sonra
        // operatörün "şapkalarına" göre gruplar. Grup başlıkları (Item.Label) menü sırasını
        // kaydırmaz; görünürlük kararı için bkz. MyMenuValidation.resolveItemName.
        MenuItem[] items = new MenuItem[]{
                new Item("Ana Sayfa", "layout-dashboard.svg", FormDashboard.class),
                new Item("Müşteriler", "user-search.svg", FormCustomers.class),

                new Item.Label("SERVİS"),
                new Item("Servis Kayıtları", "wrench.svg", FormWorkOrders.class),
                new Item("Cihazlar", "tablet-smartphone.svg", FormDevices.class),

                new Item.Label("SATIŞ"),
                new Item("Satış (POS)", "credit-card.svg", FormPos.class),
                new Item("Satışlar", "file-text.svg", FormSales.class),
                new Item("2.el Alım-Satım", "tag.svg", FormSecondHandStock.class),

                new Item.Label("FİNANS"),
                new Item("Cari Hesaplar", "hand-coins.svg", FormAccounts.class),
                new Item("Kasa Raporu", "banknote.svg", FormCashReport.class),

                new Item.Label("STOK"),
                new Item("Parçalar", "circuit-board.svg", FormParts.class),
                new Item("Ürünler", "shopping-bag.svg", FormProducts.class),
                new Item("Tedarikçiler", "store.svg", FormSuppliers.class),

                new Item.Separator(),
                // Ayarlar ve Hakkında bir Form açmaz, modal olarak gösterilir (aşağıdaki menü olayına bkz.)
                new Item("Ayarlar", "settings.svg"),
                new Item("Hakkında", "info.svg")
        };

        // MyMenuValidation.getInstance() ÜZERİNDEN DEĞİL doğrudan bu dizi üzerinden çalışır —
        // bkz. MyMenuValidation'daki sonsuz özyineleme notu (constructor içinde çağrılıyor, henüz singleton yok).
        MyMenuValidation.setMenuItems(items);

        simpleMenuOption.setMenuStyle(new MenuStyle() {

            @Override
            public void styleMenuItem(JButton menu, int[] index, boolean isMainItem) {
                // Satırlar biraz sıkı: grup başlıklarıyla birlikte 768px yükseklikte kaydırmadan sığsın.
                menu.putClientProperty(FlatClientProperties.STYLE, ITEM_STYLE);
                keepSelectedStyle(menu);
            }

            @Override
            public void styleLabel(JLabel label) {
                label.putClientProperty(FlatClientProperties.STYLE,
                        "font:-2 bold;foreground:$Label.disabledForeground");
            }

            @Override
            public void styleSeparator(JSeparator separator) {
                separator.putClientProperty(FlatClientProperties.STYLE, "height:17;stripeIndent:8");
            }

            @Override
            public void styleMenu(JComponent component) {
                component.putClientProperty(FlatClientProperties.STYLE, getDrawerBackgroundStyle());
            }
        });

        simpleMenuOption.getMenuStyle().setDrawerLineStyleRenderer(new DrawerNoneLineStyle());
        simpleMenuOption.setMenuValidation(new MyMenuValidation());

        simpleMenuOption.addMenuEvent((action, index) -> {
            // FormManager seçimi eşitliyorsa form zaten açık: yalnızca işaretlensin, tekrar açılmasın.
            if (FormManager.isSyncingDrawer()) return;
            // Ayarlar/Hakkında bir Form açmaz — index pozisyonuna göre değil isme göre yakalanır,
            // aksi halde menüye yeni öğe eklendiğinde (ör. Ürünler) pozisyon kayar ve yanlış öğe tetiklenir.
            String itemName = action.getItem().getName();
            if ("Ayarlar".equals(itemName)) {
                action.consume();
                FormManager.showSettings();
                return;
            }
            if ("Hakkında".equals(itemName)) {
                action.consume();
                FormManager.showAbout();
                return;
            }

            Class<?> itemClass = action.getItem().getItemClass();
            if (itemClass == null || !Form.class.isAssignableFrom(itemClass)) {
                action.consume();
                return;
            }
            Class<? extends Form> formClass = (Class<? extends Form>) itemClass;
            FormManager.showForm(AllForms.getForm(formClass));
        });

        simpleMenuOption.setMenus(items)
                .setBaseIconPath("icons")
                .setIconScale(0.8f);

        // Grup başlıkları öğelerle aynı hizada başlar; başlıktan önce geniş, sonra dar boşluk.
        simpleMenuOption.setMenuItemLayoutOption(new MenuItemLayoutOption()
                .setMenuHorizontalMargin(new Point(12, 12))
                .setLabelMargin(new Insets(16, 22, 4, 20))
                .setSeparatorMargin(new Insets(2, 20, 2, 20)));


        return simpleMenuOption;
    }

    /**
     * Menü satırının temel görünümü. margin, raven'ın 7px'lik dikey boşluğuna EKLENİR (-2 = 5px).
     * DİKKAT: raven stili ";" ile bölüp anahtar-değer olarak birleştiriyor; ";" sonrası boşluk
     * anahtarın parçası sayılıyor (" margin" ile "margin" ayrı anahtar olur ve eski değer kalır).
     * Bu yüzden stil dizeleri boşluksuz yazılır.
     */
    private static final String ITEM_STYLE = "arc:12;margin:-2,0,-2,0;"
            + "hoverBackground:fade($Label.foreground,5%);pressedBackground:fade($Label.foreground,9%)";

    /**
     * Seçili öğe: vurgu renginde yumuşak dolgu + vurgu renginde yazı. Eskiden yalnızca yazı rengi
     * değişiyordu ve seçili ekran menüde gözden kaçıyordu.
     */
    private static final String SELECTED_STYLE = ";selectedBackground:fade($Component.accentColor,14%)"
            + ";[light]selectedForeground:$Component.accentColor"
            + ";[dark]selectedForeground:lighten($Component.accentColor,20%)";

    /**
     * Menü ikonunun rengi; {@link #SELECTED_STYLE}'daki yazı rengiyle aynı kural. Koyu temada vurgu
     * rengi açılır, aksi halde koyu zemin üstünde 4.5:1 kontrastın altında kalıyordu.
     */
    private static Color menuIconColor(Component component) {
        if (component instanceof AbstractButton button && button.isSelected()) {
            Color accent = UIManager.getColor("Component.accentColor");
            if (accent != null) {
                return FlatLaf.isLafDark() ? ColorFunctions.lighten(accent, 0.2f) : accent;
            }
        }
        if (component != null && !component.isEnabled()) {
            return UIManager.getColor("Label.disabledForeground");
        }
        return UIManager.getColor("Label.foreground");
    }

    /**
     * raven her mod değişiminde seçili stili kendi değerleriyle ({@code selectedBackground:null})
     * sona ekliyor ve bizimkini eziyor. Stil her değiştiğinde bizimki yeniden en sona eklenir;
     * zaten sondaysa dokunulmaz (sonsuz döngü olmaz).
     */
    private static void keepSelectedStyle(JButton menu) {
        menu.addPropertyChangeListener(FlatClientProperties.STYLE, e -> {
            Object value = e.getNewValue();
            String style = value instanceof String ? (String) value : "";
            if (!style.endsWith(SELECTED_STYLE)) {
                menu.putClientProperty(FlatClientProperties.STYLE, style + SELECTED_STYLE);
            }
        });
        Object current = menu.getClientProperty(FlatClientProperties.STYLE);
        menu.putClientProperty(FlatClientProperties.STYLE, (current != null ? current : "") + SELECTED_STYLE);
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
