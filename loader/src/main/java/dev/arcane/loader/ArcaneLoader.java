package dev.arcane.loader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.io.InputStream;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

public final class ArcaneLoader {
    static final String LOADER_VERSION = "1.2.7+mc26.3";
    static final String PRIMARY_MANIFEST_URL =
        "https://www.arcaneclient.shop/updates/channels/mc-26.3/loader-release.json";
    static final String FALLBACK_MANIFEST_URL =
        "https://arcane-client-puce.vercel.app/updates/channels/mc-26.3/loader-release.json";
    private static final String MANIFEST_URL_OVERRIDE = System.getProperty("arcane.manifestUrl");

    private final LoaderState state;
    private final Image logo = loadLogo();
    private final JFrame frame = new JFrame("ARCLoader");
    private final JTextField modsDirectory = new JTextField();
    private final JLabel status = new JLabel("Ready to install the single ARCLoader mod");
    private final JButton install = new JButton("Install ARCLoader");

    private ArcaneLoader() throws Exception {
        state = LoaderState.load();
        buildUi();
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--install".equals(args[0])) {
            try {
                Path mods = args.length > 1 ? Path.of(args[1]) : defaultModsDirectory();
                InstallResult result = SingleFileInstaller.installCurrent(mods);
                System.out.println(result.changed()
                    ? "Installed " + result.path()
                    : "Already current: " + result.path());
            } catch (Exception error) {
                System.err.println("Arcane update failed: " + error.getMessage());
                System.exit(1);
            }
            return;
        }

        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new ArcaneLoader().frame.setVisible(true);
            } catch (Exception error) {
                error.printStackTrace();
                javax.swing.JOptionPane.showMessageDialog(
                    null, error.getMessage(), "ARCLoader", javax.swing.JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private void buildUi() {
        Color background = new Color(12, 17, 15);
        Color panelColor = new Color(20, 28, 24);
        Color foreground = new Color(226, 238, 231);
        Color accent = new Color(169, 240, 70);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(560, 265));
        frame.getContentPane().setBackground(background);
        frame.setLocationByPlatform(true);
        if (logo != null) frame.setIconImage(logo);

        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(background);
        header.setBorder(BorderFactory.createEmptyBorder(24, 28, 12, 28));
        JLabel title = new JLabel("ARCLOADER");
        title.setForeground(foreground);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 24f));
        if (logo != null) {
            title.setIcon(new ImageIcon(logo.getScaledInstance(28, 28, Image.SCALE_SMOOTH)));
            title.setIconTextGap(10);
        }
        header.add(title, BorderLayout.WEST);

        JPanel form = new JPanel(new GridBagLayout());
        form.setBackground(panelColor);
        form.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(45, 59, 51)),
            BorderFactory.createEmptyBorder(22, 22, 22, 22)
        ));
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(7, 7, 7, 7);
        constraints.fill = GridBagConstraints.HORIZONTAL;

        modsDirectory.setText(state.get("minecraft.modsDirectory", defaultModsDirectory().toString()));
        JButton browse = new JButton("Browse");
        browse.addActionListener(event -> chooseModsDirectory());
        addRow(form, constraints, 0, "Fabric mods folder", modsDirectory, browse, foreground);

        install.setBackground(accent);
        install.setForeground(new Color(5, 26, 15));
        install.setFocusPainted(false);
        install.addActionListener(event -> runInstall());
        constraints.gridx = 1;
        constraints.gridy = 1;
        constraints.weightx = 1;
        constraints.gridwidth = 2;
        form.add(install, constraints);

        status.setForeground(new Color(168, 188, 176));
        status.setBorder(BorderFactory.createEmptyBorder(12, 28, 22, 28));

        JPanel center = new JPanel(new BorderLayout());
        center.setBackground(background);
        center.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 28));
        center.add(form, BorderLayout.NORTH);
        frame.add(header, BorderLayout.NORTH);
        frame.add(center, BorderLayout.CENTER);
        frame.add(status, BorderLayout.SOUTH);
        frame.pack();
    }

    private static void addRow(JPanel form, GridBagConstraints constraints, int row, String label,
                               JTextField field, JButton button, Color foreground) {
        JLabel text = new JLabel(label);
        text.setForeground(foreground);
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 0;
        constraints.gridwidth = 1;
        form.add(text, constraints);
        constraints.gridx = 1;
        constraints.weightx = 1;
        form.add(field, constraints);
        constraints.gridx = 2;
        constraints.weightx = 0;
        form.add(button, constraints);
    }

    private void chooseModsDirectory() {
        JFileChooser chooser = new JFileChooser(modsDirectory.getText());
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(frame) == JFileChooser.APPROVE_OPTION) {
            modsDirectory.setText(chooser.getSelectedFile().toPath().toString());
        }
    }

    private void runInstall() {
        install.setEnabled(false);
        status.setText("Installing the single ARCLoader JAR…");
        Path mods = Path.of(modsDirectory.getText().trim()).toAbsolutePath().normalize();
        new SwingWorker<InstallResult, Void>() {
            @Override
            protected InstallResult doInBackground() throws Exception {
                InstallResult result = SingleFileInstaller.installCurrent(mods);
                state.put("minecraft.modsDirectory", mods.toString());
                state.save();
                return result;
            }

            @Override
            protected void done() {
                install.setEnabled(true);
                try {
                    InstallResult result = get();
                    status.setText(result.changed()
                        ? "Installed " + result.path().getFileName() + " — this is the only Arcane mod required"
                        : "ARCLoader " + result.version() + " is already installed");
                } catch (Exception error) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    status.setText("Update failed: " + cause.getMessage());
                }
            }
        }.execute();
    }

    static ReleaseManifest fetchRelease() throws Exception {
        if (MANIFEST_URL_OVERRIDE != null && !MANIFEST_URL_OVERRIDE.isBlank()) {
            return new ReleaseManifestClient(MANIFEST_URL_OVERRIDE).latest();
        }
        try {
            return new ReleaseManifestClient(PRIMARY_MANIFEST_URL).latest();
        } catch (Exception primaryFailure) {
            try {
                return new ReleaseManifestClient(FALLBACK_MANIFEST_URL).latest();
            } catch (Exception fallbackFailure) {
                fallbackFailure.addSuppressed(primaryFailure);
                throw fallbackFailure;
            }
        }
    }

    private static Path defaultModsDirectory() {
        String appData = System.getenv("APPDATA");
        if (appData != null && !appData.isBlank()) return Path.of(appData, ".minecraft", "mods");
        return Path.of(System.getProperty("user.home"), ".minecraft", "mods");
    }

    private static Image loadLogo() {
        try (InputStream input = ArcaneLoader.class.getResourceAsStream("/arcane-icon.png")) {
            return input == null ? null : ImageIO.read(input);
        } catch (Exception ignored) {
            return null;
        }
    }
}
