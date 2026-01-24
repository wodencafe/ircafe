package cafe.woden.ircclient.embed;

import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Creates Swing components for displaying embeds in chat.
 */
@Component
public class EmbedRenderer {

    private final EmbedSettings settings;

    public EmbedRenderer(EmbedSettings settings) {
        this.settings = settings;
    }

    /**
     * Create a component for the given embed result.
     */
    public JComponent createComponent(EmbedResult result, Runnable onPlayVideo) {
        return switch (result) {
            case EmbedResult.ImageEmbed img -> createImagePanel(img);
            case EmbedResult.VideoEmbed vid -> createVideoPanel(vid, onPlayVideo);
            case EmbedResult.LinkPreview link -> createLinkPreviewPanel(link);
            case EmbedResult.Loading loading -> createLoadingPanel(loading.url());
            case EmbedResult.Failed failed -> createErrorPanel(failed);
        };
    }

    /**
     * Create a loading placeholder panel.
     */
    public JComponent createLoadingPanel(String url) {
        JLabel spinner = new JLabel("Loading...");
        spinner.setIcon(UIManager.getIcon("OptionPane.informationIcon"));
        spinner.setForeground(UIManager.getColor("Label.disabledForeground"));
        spinner.setToolTipText("Loading embed for: " + url);
        return spinner;
    }

    private JComponent createImagePanel(EmbedResult.ImageEmbed img) {
        // Use thumbnail for display, original for full-size viewer
        ResizableEmbedPanel panel = new ResizableEmbedPanel(img.thumbnail(), img.original(), img.url(), EmbedType.IMAGE);

        // Set initial size based on settings
        int maxW = settings.maxThumbnailWidth();
        int maxH = settings.maxThumbnailHeight();
        if (img.thumbnail().getWidth() > maxW || img.thumbnail().getHeight() > maxH) {
            panel.setCurrentSize(maxW, maxH);
        }

        return wrapInContainer(panel);
    }

    private JComponent createVideoPanel(EmbedResult.VideoEmbed vid, Runnable onPlayVideo) {
        ResizableEmbedPanel panel = new ResizableEmbedPanel(vid.thumbnail(), vid.url(), EmbedType.VIDEO);
        panel.setVideoTitle(vid.title());
        panel.setOnPlayVideo(onPlayVideo);

        // Set initial size
        int maxW = settings.maxThumbnailWidth();
        int maxH = settings.maxThumbnailHeight();
        panel.setCurrentSize(maxW, maxH);

        return wrapInContainer(panel);
    }

    private JComponent createLinkPreviewPanel(EmbedResult.LinkPreview link) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(UIManager.getColor("Component.borderColor"), 1, true),
            BorderFactory.createEmptyBorder(8, 10, 8, 10)
        ));

        // Horizontal layout for favicon + text
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        headerPanel.setOpaque(false);
        headerPanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

        // Favicon
        if (link.favicon() != null) {
            JLabel faviconLabel = new JLabel(new ImageIcon(link.favicon()));
            headerPanel.add(faviconLabel);
        }

        // Site name
        if (link.siteName() != null && !link.siteName().isBlank()) {
            JLabel siteLabel = new JLabel(link.siteName());
            siteLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            siteLabel.setFont(siteLabel.getFont().deriveFont(Font.PLAIN, 11f));
            headerPanel.add(siteLabel);
        }

        panel.add(headerPanel);

        // Title
        if (link.title() != null && !link.title().isBlank()) {
            JLabel titleLabel = new JLabel("<html><b>" + escapeHtml(truncate(link.title(), 100)) + "</b></html>");
            titleLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
            titleLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            titleLabel.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override
                public void mouseClicked(java.awt.event.MouseEvent e) {
                    openUrl(link.url());
                }
            });
            panel.add(titleLabel);
        }

        // Description
        if (link.description() != null && !link.description().isBlank()) {
            String desc = truncate(link.description(), 200);
            JLabel descLabel = new JLabel("<html><div style='width: " + (settings.maxThumbnailWidth() - 20) + "px'>" +
                escapeHtml(desc) + "</div></html>");
            descLabel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);
            descLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
            descLabel.setFont(descLabel.getFont().deriveFont(Font.PLAIN, 12f));
            panel.add(Box.createVerticalStrut(4));
            panel.add(descLabel);
        }

        // OG Image
        if (link.ogImage() != null) {
            panel.add(Box.createVerticalStrut(8));
            ResizableEmbedPanel imagePanel = new ResizableEmbedPanel(link.ogImage(), link.url(), EmbedType.IMAGE);
            imagePanel.setAlignmentX(java.awt.Component.LEFT_ALIGNMENT);

            int maxW = settings.maxThumbnailWidth() - 20;
            int maxH = settings.maxThumbnailHeight();
            imagePanel.setCurrentSize(maxW, maxH);

            panel.add(imagePanel);
        }

        // Limit width
        panel.setMaximumSize(new Dimension(settings.maxThumbnailWidth(), Integer.MAX_VALUE));
        panel.setPreferredSize(new Dimension(settings.maxThumbnailWidth(),
            panel.getPreferredSize().height));

        return wrapInContainer(panel);
    }

    private JComponent createErrorPanel(EmbedResult.Failed failed) {
        JLabel errorLabel = new JLabel("Could not load embed");
        errorLabel.setIcon(UIManager.getIcon("OptionPane.errorIcon"));
        errorLabel.setForeground(UIManager.getColor("Component.errorForeground"));
        errorLabel.setToolTipText(failed.errorMessage());
        return errorLabel;
    }

    /**
     * Wrap an embed panel in a container with proper alignment.
     */
    private JComponent wrapInContainer(JComponent inner) {
        JPanel wrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        wrapper.setOpaque(false);
        wrapper.add(inner);
        return wrapper;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength - 3) + "...";
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    private void openUrl(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                // Fallback for Linux - try xdg-open
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            System.err.println("Failed to open URL: " + url + " - " + e.getMessage());
        }
    }
}
