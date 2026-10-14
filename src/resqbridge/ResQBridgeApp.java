package resqbridge;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class ResQBridgeApp extends JFrame {

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel pages = new JPanel();
    private boolean openingAnimationPlayed;

    public static void launchHeadless(DataStore store, LoginAccount account) {
        System.out.println("\nResQBridge console mode");
        System.out.println("Signed in as " + account.displayName + " (" + account.role + ")");
        System.out.println("System time: " + java.time.LocalDateTime.now().toString());

        switch (account.role) {
            case "Driver":
                System.out.println("Driver overview: active route, manifest status, hazard monitoring, and chat are available.");
                System.out.println("Assigned route: " + store.drivers.stream()
                        .filter(d -> account.linkedDriverName != null && account.linkedDriverName.equals(d.name))
                        .findFirst()
                        .map(d -> d.destination.isEmpty() ? "No active route" : d.destination)
                        .orElse("No active route"));
                break;
            case "Camp Employee":
                System.out.println("Camp overview: deliveries inbound, gate capacity, equipment usage, and discrepancies are available.");
                System.out.println("Camp: " + account.linkedCampName);
                break;
            default:
                System.out.println("Admin overview: dashboard metrics, donor inventory, alerts, and dispatch activity are available.");
                System.out.println("Active camps: " + store.camps.size() + " | Alerts: " + store.alerts.size() + " | Inventory: " + store.inventory.size() + " categories");
                break;
        }

        System.out.println("This lightweight fallback keeps the app usable in headless / non-GUI environments.");
    }

    public ResQBridgeApp(DataStore store, LoginAccount account) {
        super("ResQBridge - Disaster Relief Logistics System (" + account.role + ")");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1440, 900);
        setMinimumSize(new Dimension(1100, 700));
        setLocationRelativeTo(null);

        getContentPane().setBackground(Theme.BG_APP);
        setLayout(new BorderLayout());

        Runnable logout = () -> {
            dispose();
            SwingUtilities.invokeLater(() -> new LoginFrame().setVisible(true));
        };

        HeaderPanel header = new HeaderPanel(account, logout);
        add(header, BorderLayout.NORTH);

        String[][] navItems;
        String initialPage;

        switch (account.role) {
            case "Driver":
                navItems = new String[][]{{"Driver Console", "TRUCK"}};
                initialPage = "Driver Console";
                break;
            case "Camp Employee":
                navItems = new String[][]{{"Camp Console", "CAMP"}};
                initialPage = "Camp Console";
                break;
            default: // Admin
                navItems = new String[][]{
                        {"Dashboard", "DASHBOARD"},
                        {"Relief Camps", "CAMP"},
                        {"Donors", "DONOR"},
                        {"Supply Inventory", "INVENTORY"},
                        {"Dispatch", "DISPATCH"},
                        {"Drivers", "DRIVER"},
                        {"Alerts", "ALERT"},
                        {"Messages", "BELL"},
                        {"Reports", "REPORT"},
                        {"Settings", "SETTINGS"},
                };
                initialPage = "Dashboard";
        }

        SidebarPanel sidebar = new SidebarPanel(navItems, initialPage, this::showPage);
        add(sidebar, BorderLayout.WEST);

        pages.setLayout(cardLayout);
        pages.setBackground(Theme.BG_APP);

        switch (account.role) {
            case "Driver":
                pages.add(wrap(new DriverConsolePanel(store, account, this)), "Driver Console");
                break;
            case "Camp Employee":
                pages.add(wrap(new CampConsolePanel(store, account, this)), "Camp Console");
                break;
            default: // Admin - full access to every feature
                pages.add(wrap(new DashboardPanel(store, this)), "Dashboard");
                pages.add(wrap(new CampsPanel(store)), "Relief Camps");
                pages.add(wrap(centered(new DonorsCardPanel(store, this, 0))), "Donors");
                pages.add(wrap(centered(new InventoryCardPanel(store))), "Supply Inventory");
                pages.add(wrap(centered(new DispatchCardPanel(store, this, 0))), "Dispatch");
                pages.add(wrap(new DriversPanel(store, this)), "Drivers");
                pages.add(wrap(new AlertsPanel(store)), "Alerts");
                pages.add(wrap(new AdminMessagesPanel(store)), "Messages");
                pages.add(wrap(new SimplePagePanel("Reports", "Detailed operational reports are generated at the end of each day. Check back soon.", AppIcon.Kind.REPORT)), "Reports");
                pages.add(wrap(new SettingsPanel(store, this)), "Settings");
        }

        add(pages, BorderLayout.CENTER);
        cardLayout.show(pages, initialPage);
    }

    void playOpeningAnimation() {
        if (openingAnimationPlayed) return;
        openingAnimationPlayed = true;

        JLayeredPane layeredPane = getLayeredPane();
        OpeningOverlay overlay = new OpeningOverlay();
        overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
        layeredPane.add(overlay, JLayeredPane.DRAG_LAYER);
        ComponentAdapter resizeListener = new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                overlay.setBounds(0, 0, layeredPane.getWidth(), layeredPane.getHeight());
            }
        };
        layeredPane.addComponentListener(resizeListener);

        long startedAt = System.nanoTime();
        Timer timer = new Timer(16, null);
        timer.addActionListener(e -> {
            overlay.progress = Math.min(1f, (System.nanoTime() - startedAt) / 700_000_000f);
            overlay.repaint();
            if (overlay.progress >= 1f) {
                timer.stop();
                layeredPane.removeComponentListener(resizeListener);
                layeredPane.remove(overlay);
                layeredPane.repaint();
            }
        });
        timer.start();
    }

    private static final class OpeningOverlay extends JComponent {
        private float progress;

        OpeningOverlay() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            float fade = 1f - progress;
            int alpha = Math.round(225 * fade);
            g.setColor(new Color(Theme.BG_APP.getRed(), Theme.BG_APP.getGreen(), Theme.BG_APP.getBlue(), alpha));
            g.fillRect(0, 0, getWidth(), getHeight());

            float scale = 0.88f + 0.12f * progress;
            int logoSize = 58;
            int centerX = getWidth() / 2;
            int centerY = getHeight() / 2;
            Graphics2D mark = (Graphics2D) g.create();
            mark.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));
            mark.translate(centerX, centerY - 18);
            mark.scale(scale, scale);
            new AppIcon(AppIcon.Kind.LOGO, logoSize, Theme.ACCENT_CYAN)
                    .paintIcon(this, mark, -logoSize / 2, -logoSize / 2);
            mark.dispose();

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fade));
            g.setFont(Theme.FONT_TITLE.deriveFont(22f));
            g.setColor(Theme.TEXT_PRIMARY);
            String title = "ResQBridge";
            FontMetrics metrics = g.getFontMetrics();
            g.drawString(title, centerX - metrics.stringWidth(title) / 2, centerY + 38);

            int barWidth = 150;
            int barX = centerX - barWidth / 2;
            int barY = centerY + 58;
            g.setColor(new Color(Theme.BORDER.getRed(), Theme.BORDER.getGreen(), Theme.BORDER.getBlue(), alpha));
            g.fillRoundRect(barX, barY, barWidth, 4, 4, 4);
            g.setColor(new Color(Theme.ACCENT_CYAN.getRed(), Theme.ACCENT_CYAN.getGreen(),
                    Theme.ACCENT_CYAN.getBlue(), alpha));
            g.fillRoundRect(barX, barY, Math.round(barWidth * progress), 4, 4, 4);
            g.dispose();
        }
    }

    private JPanel wrap(JComponent content) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Theme.BG_APP);
        wrapper.add(content, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel centered(JComponent content) {
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setOpaque(false);
        outer.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.weightx = 1;
        gbc.weighty = 1;
        gbc.fill = GridBagConstraints.BOTH;
        content.setPreferredSize(new Dimension(700, 560));
        outer.add(content, gbc);
        return outer;
    }

    private void showPage(String name) {
        cardLayout.show(pages, name);
    }
}
