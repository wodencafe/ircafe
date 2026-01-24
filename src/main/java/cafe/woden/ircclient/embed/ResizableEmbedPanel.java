package cafe.woden.ircclient.embed;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/**
 * A panel that displays embedded content (images/videos) with resizable corners.
 * Users can drag the corner to resize while maintaining aspect ratio.
 */
public class ResizableEmbedPanel extends JPanel {

    private static final int RESIZE_HANDLE_SIZE = 12;
    private static final int MIN_SIZE = 50;
    private static final int BORDER_RADIUS = 8;
    private static final int PADDING = 4;

    private BufferedImage image;          // Thumbnail for display
    private BufferedImage originalImage;  // Full-size for viewer
    private final String url;
    private final EmbedType type;
    private final double aspectRatio;

    private int currentWidth;
    private int currentHeight;

    private boolean hovering = false;
    private boolean resizing = false;
    private Point resizeStart;
    private Dimension sizeAtResizeStart;

    private Runnable onPlayVideo;
    private boolean isVideo;
    private String videoTitle;

    /**
     * Constructor for videos or when original is same as thumbnail.
     */
    public ResizableEmbedPanel(BufferedImage image, String url, EmbedType type) {
        this(image, image, url, type);
    }

    /**
     * Constructor with separate thumbnail and original image.
     */
    public ResizableEmbedPanel(BufferedImage thumbnail, BufferedImage original, String url, EmbedType type) {
        this.image = thumbnail;
        this.originalImage = original;
        this.url = url;
        this.type = type;
        this.isVideo = (type == EmbedType.VIDEO);

        if (thumbnail != null) {
            this.aspectRatio = (double) thumbnail.getWidth() / thumbnail.getHeight();
            this.currentWidth = thumbnail.getWidth();
            this.currentHeight = thumbnail.getHeight();
        } else {
            this.aspectRatio = 16.0 / 9.0; // Default for videos without thumbnail
            this.currentWidth = 320;
            this.currentHeight = 180;
        }

        setOpaque(false);
        setCursor(Cursor.getDefaultCursor());
        setToolTipText(url);

        setupMouseListeners();
        updatePreferredSize();
    }

    public void setVideoTitle(String title) {
        this.videoTitle = title;
        repaint();
    }

    public void setOnPlayVideo(Runnable onPlayVideo) {
        this.onPlayVideo = onPlayVideo;
    }

    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                hovering = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!resizing) {
                    hovering = false;
                    repaint();
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (isInResizeHandle(e.getPoint())) {
                    resizing = true;
                    resizeStart = e.getPoint();
                    sizeAtResizeStart = new Dimension(currentWidth, currentHeight);
                } else if (isVideo && isInPlayButton(e.getPoint())) {
                    if (onPlayVideo != null) {
                        onPlayVideo.run();
                    }
                } else if (!isVideo && SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2) {
                    // Double-click on image opens full-size viewer
                    openFullSizeViewer();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                resizing = false;
                updateCursor(e.getPoint());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                updateCursor(e.getPoint());
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (resizing && resizeStart != null) {
                    int dx = e.getX() - resizeStart.x;
                    int dy = e.getY() - resizeStart.y;

                    // Use the larger delta to determine new size while maintaining aspect ratio
                    int newWidth = Math.max(MIN_SIZE, sizeAtResizeStart.width + dx);
                    int newHeight = (int) (newWidth / aspectRatio);

                    if (newHeight < MIN_SIZE) {
                        newHeight = MIN_SIZE;
                        newWidth = (int) (newHeight * aspectRatio);
                    }

                    currentWidth = newWidth;
                    currentHeight = newHeight;
                    updatePreferredSize();

                    revalidate();
                    repaint();

                    // Notify parent to revalidate layout
                    Container parent = getParent();
                    if (parent != null) {
                        parent.revalidate();
                        parent.repaint();
                    }
                }
            }
        };

        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);
    }

    private void updateCursor(Point p) {
        if (isInResizeHandle(p)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
        } else if (isVideo && isInPlayButton(p)) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else if (!isVideo) {
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        } else {
            setCursor(Cursor.getDefaultCursor());
        }
    }

    private boolean isInResizeHandle(Point p) {
        int x = currentWidth + PADDING - RESIZE_HANDLE_SIZE;
        int y = currentHeight + PADDING - RESIZE_HANDLE_SIZE;
        return p.x >= x && p.y >= y &&
               p.x <= currentWidth + PADDING &&
               p.y <= currentHeight + PADDING;
    }

    private boolean isInPlayButton(Point p) {
        int centerX = currentWidth / 2 + PADDING;
        int centerY = currentHeight / 2 + PADDING;
        int buttonRadius = 30;

        double distance = Math.sqrt(
            Math.pow(p.x - centerX, 2) + Math.pow(p.y - centerY, 2)
        );
        return distance <= buttonRadius;
    }

    private void updatePreferredSize() {
        Dimension size = new Dimension(currentWidth + PADDING * 2, currentHeight + PADDING * 2);
        setPreferredSize(size);
        setMinimumSize(new Dimension(MIN_SIZE + PADDING * 2, MIN_SIZE + PADDING * 2));
    }

    private void openFullSizeViewer() {
        // Use original image if available, fall back to thumbnail
        BufferedImage viewImage = (originalImage != null) ? originalImage : image;
        if (viewImage == null) return;

        JDialog dialog = new JDialog(
            (Frame) SwingUtilities.getWindowAncestor(this),
            "Image Viewer",
            true
        );

        // Create a panel that scales the image to fit the dialog
        ScalableImagePanel imagePanel = new ScalableImagePanel(viewImage);
        dialog.add(imagePanel);

        // Size to fit image or screen, whichever is smaller
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxW = (int) (screen.width * 0.9);
        int maxH = (int) (screen.height * 0.9);
        int w = Math.min(viewImage.getWidth() + 50, maxW);
        int h = Math.min(viewImage.getHeight() + 50, maxH);

        dialog.setSize(w, h);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    /**
     * Panel that displays an image scaled to fit while maintaining aspect ratio.
     */
    private static class ScalableImagePanel extends JPanel {
        private final BufferedImage image;
        private final double aspectRatio;

        ScalableImagePanel(BufferedImage image) {
            this.image = image;
            this.aspectRatio = (double) image.getWidth() / image.getHeight();
            setBackground(Color.BLACK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (image == null) return;

            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int panelWidth = getWidth();
            int panelHeight = getHeight();

            // Calculate scaled dimensions maintaining aspect ratio
            int drawWidth, drawHeight;
            double panelRatio = (double) panelWidth / panelHeight;

            if (panelRatio > aspectRatio) {
                // Panel is wider than image aspect ratio - fit to height
                drawHeight = panelHeight;
                drawWidth = (int) (drawHeight * aspectRatio);
            } else {
                // Panel is taller than image aspect ratio - fit to width
                drawWidth = panelWidth;
                drawHeight = (int) (drawWidth / aspectRatio);
            }

            // Center the image
            int x = (panelWidth - drawWidth) / 2;
            int y = (panelHeight - drawHeight) / 2;

            g2.drawImage(image, x, y, drawWidth, drawHeight, null);
            g2.dispose();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        int x = PADDING;
        int y = PADDING;

        // Draw rounded clip
        Shape clip = new RoundRectangle2D.Float(x, y, currentWidth, currentHeight, BORDER_RADIUS, BORDER_RADIUS);
        g2.setClip(clip);

        // Draw background
        g2.setColor(UIManager.getColor("Panel.background").darker());
        g2.fillRect(x, y, currentWidth, currentHeight);

        // Draw image if available
        if (image != null) {
            g2.drawImage(image, x, y, currentWidth, currentHeight, null);
        }

        // Reset clip for overlays
        g2.setClip(null);

        // Draw border
        g2.setColor(UIManager.getColor("Component.borderColor"));
        g2.setStroke(new BasicStroke(1));
        g2.draw(clip);

        // Draw play button for videos
        if (isVideo) {
            drawPlayButton(g2);
        }

        // Draw video title if available
        if (isVideo && videoTitle != null && !videoTitle.isBlank()) {
            drawVideoTitle(g2);
        }

        // Draw resize handle when hovering
        if (hovering || resizing) {
            drawResizeHandle(g2);
        }

        g2.dispose();
    }

    private void drawPlayButton(Graphics2D g2) {
        int centerX = currentWidth / 2 + PADDING;
        int centerY = currentHeight / 2 + PADDING;
        int buttonRadius = 30;

        // Semi-transparent background circle
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillOval(centerX - buttonRadius, centerY - buttonRadius,
            buttonRadius * 2, buttonRadius * 2);

        // White border
        g2.setColor(Color.WHITE);
        g2.setStroke(new BasicStroke(2));
        g2.drawOval(centerX - buttonRadius, centerY - buttonRadius,
            buttonRadius * 2, buttonRadius * 2);

        // Play triangle
        int[] xPoints = {centerX - 8, centerX - 8, centerX + 12};
        int[] yPoints = {centerY - 12, centerY + 12, centerY};
        g2.fillPolygon(xPoints, yPoints, 3);
    }

    private void drawVideoTitle(Graphics2D g2) {
        // Draw title bar at bottom
        int barHeight = 30;
        int y = currentHeight + PADDING - barHeight;

        // Semi-transparent background
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(PADDING, y, currentWidth, barHeight,
            BORDER_RADIUS, BORDER_RADIUS);

        // Title text
        g2.setColor(Color.WHITE);
        g2.setFont(g2.getFont().deriveFont(Font.PLAIN, 12f));

        FontMetrics fm = g2.getFontMetrics();
        String displayTitle = videoTitle;
        int maxTextWidth = currentWidth - 20;
        if (fm.stringWidth(displayTitle) > maxTextWidth) {
            while (fm.stringWidth(displayTitle + "...") > maxTextWidth && displayTitle.length() > 0) {
                displayTitle = displayTitle.substring(0, displayTitle.length() - 1);
            }
            displayTitle += "...";
        }

        int textY = y + (barHeight + fm.getAscent() - fm.getDescent()) / 2;
        g2.drawString(displayTitle, PADDING + 10, textY);
    }

    private void drawResizeHandle(Graphics2D g2) {
        int x = currentWidth + PADDING - RESIZE_HANDLE_SIZE;
        int y = currentHeight + PADDING - RESIZE_HANDLE_SIZE;

        // Draw resize grip lines
        g2.setColor(new Color(255, 255, 255, 200));
        g2.setStroke(new BasicStroke(2));

        for (int i = 0; i < 3; i++) {
            int offset = i * 4;
            g2.drawLine(
                x + RESIZE_HANDLE_SIZE - 4 - offset, y + RESIZE_HANDLE_SIZE - 2,
                x + RESIZE_HANDLE_SIZE - 2, y + RESIZE_HANDLE_SIZE - 4 - offset
            );
        }
    }

    public String getUrl() {
        return url;
    }

    public EmbedType getEmbedType() {
        return type;
    }

    public Dimension getCurrentSize() {
        return new Dimension(currentWidth, currentHeight);
    }

    public void setCurrentSize(int width, int height) {
        this.currentWidth = width;
        this.currentHeight = (int) (width / aspectRatio);
        if (this.currentHeight > height) {
            this.currentHeight = height;
            this.currentWidth = (int) (height * aspectRatio);
        }
        updatePreferredSize();
        revalidate();
        repaint();
    }
}
