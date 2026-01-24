package cafe.woden.ircclient.embed;

import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.EmbeddedMediaPlayerComponent;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.regex.Pattern;

/**
 * Dialog for playing videos using VLCJ.
 * For YouTube/Vimeo URLs, opens in browser since VLCJ can't play them directly.
 */
public class VideoPlayerDialog extends JDialog {

    private static final Pattern YOUTUBE_PATTERN =
        Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)");
    private static final Pattern VIMEO_PATTERN =
        Pattern.compile("vimeo\\.com/\\d+");

    private EmbeddedMediaPlayerComponent mediaPlayerComponent;
    private final String videoUrl;
    private final String title;

    private JSlider progressSlider;
    private JLabel timeLabel;
    private JButton playPauseButton;
    private JSlider volumeSlider;
    private JLabel volumeLabel;

    private boolean isUpdatingSlider = false;
    private Timer progressTimer;

    public VideoPlayerDialog(Window parent, String videoUrl, String title) {
        super(parent, title != null ? title : "Video Player", ModalityType.MODELESS);
        this.videoUrl = videoUrl;
        this.title = title;

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // Check if this is a streaming service URL that VLCJ can't handle
        if (isStreamingServiceUrl(videoUrl)) {
            initBrowserFallback();
            setSize(350, 180);
        } else {
            initVlcPlayer();
            setSize(800, 500);
        }

        setLocationRelativeTo(parent);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                cleanup();
            }
        });
    }

    /**
     * Check if the URL is a streaming service that can't be played via VLCJ.
     */
    public static boolean isStreamingServiceUrl(String url) {
        if (url == null) return false;
        return YOUTUBE_PATTERN.matcher(url).find() || VIMEO_PATTERN.matcher(url).find();
    }

    /**
     * Open a URL in the system browser.
     */
    public static void openUrlInBrowser(String url) {
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

    private void initBrowserFallback() {
        setLayout(new BorderLayout());

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        String serviceName = "video";
        if (YOUTUBE_PATTERN.matcher(videoUrl).find()) {
            serviceName = "YouTube";
        } else if (VIMEO_PATTERN.matcher(videoUrl).find()) {
            serviceName = "Vimeo";
        }

        JLabel label = new JLabel("<html><center>" +
            "<b>" + serviceName + "</b><br>" +
            "Opening in browser..." +
            "</center></html>");
        label.setHorizontalAlignment(SwingConstants.CENTER);

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8));
        buttonPanel.add(cancelButton);

        panel.add(label, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        add(panel, BorderLayout.CENTER);

        // Auto-open in browser and close dialog
        SwingUtilities.invokeLater(() -> {
            openInBrowser();
            dispose();
        });
    }

    private void initVlcPlayer() {
        setLayout(new BorderLayout());

        // Check if VLC is available
        try {
            mediaPlayerComponent = new EmbeddedMediaPlayerComponent();
        } catch (Exception | UnsatisfiedLinkError e) {
            showVlcError();
            return;
        }

        add(mediaPlayerComponent, BorderLayout.CENTER);

        // Controls panel
        JPanel controlsPanel = createControlsPanel();
        add(controlsPanel, BorderLayout.SOUTH);

        // Set up media player events
        mediaPlayerComponent.mediaPlayer().events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> playPauseButton.setText("Pause"));
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> playPauseButton.setText("Play"));
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    playPauseButton.setText("Play");
                    progressSlider.setValue(0);
                    timeLabel.setText("0:00 / 0:00");
                });
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    playPauseButton.setText("Play");
                    progressSlider.setValue(0);
                });
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                SwingUtilities.invokeLater(() -> {
                    int result = JOptionPane.showConfirmDialog(VideoPlayerDialog.this,
                        "Error playing video. Open in browser instead?",
                        "Playback Error",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.ERROR_MESSAGE);
                    if (result == JOptionPane.YES_OPTION) {
                        openInBrowser();
                    }
                    dispose();
                });
            }
        });

        // Progress update timer
        progressTimer = new Timer(500, e -> updateProgress());
        progressTimer.start();
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Play/Pause button
        playPauseButton = new JButton("Play");
        playPauseButton.setPreferredSize(new Dimension(70, 30));
        playPauseButton.addActionListener(e -> togglePlayPause());

        // Stop button
        JButton stopButton = new JButton("Stop");
        stopButton.setPreferredSize(new Dimension(60, 30));
        stopButton.addActionListener(e -> {
            if (mediaPlayerComponent != null) {
                mediaPlayerComponent.mediaPlayer().controls().stop();
            }
        });

        // Progress slider
        progressSlider = new JSlider(0, 1000, 0);
        progressSlider.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                isUpdatingSlider = true;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (mediaPlayerComponent != null) {
                    float position = progressSlider.getValue() / 1000f;
                    mediaPlayerComponent.mediaPlayer().controls().setPosition(position);
                }
                isUpdatingSlider = false;
            }
        });

        // Time label
        timeLabel = new JLabel("0:00 / 0:00");
        timeLabel.setPreferredSize(new Dimension(100, 20));

        // Volume label showing percentage
        volumeLabel = new JLabel("Vol: 80%");
        volumeLabel.setPreferredSize(new Dimension(60, 20));

        // Volume control
        volumeSlider = new JSlider(0, 100, 80);
        volumeSlider.setPreferredSize(new Dimension(100, 20));
        volumeSlider.addChangeListener(e -> {
            int vol = volumeSlider.getValue();
            volumeLabel.setText("Vol: " + vol + "%");
            if (mediaPlayerComponent != null) {
                mediaPlayerComponent.mediaPlayer().audio().setVolume(vol);
            }
        });

        // Layout
        JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        leftPanel.add(playPauseButton);
        leftPanel.add(stopButton);

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        rightPanel.add(volumeLabel);
        rightPanel.add(volumeSlider);

        JPanel centerPanel = new JPanel(new BorderLayout(8, 0));
        centerPanel.add(progressSlider, BorderLayout.CENTER);
        centerPanel.add(timeLabel, BorderLayout.EAST);

        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(centerPanel, BorderLayout.CENTER);
        panel.add(rightPanel, BorderLayout.EAST);

        return panel;
    }

    private void showVlcError() {
        JPanel errorPanel = new JPanel(new BorderLayout());
        errorPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel errorLabel = new JLabel("<html><center>" +
            "<h2>VLC Not Found</h2>" +
            "<p>VLC media player is required for video playback.</p>" +
            "<p>Please install VLC from <b>https://www.videolan.org</b></p>" +
            "</center></html>");
        errorLabel.setHorizontalAlignment(SwingConstants.CENTER);

        JButton openUrlButton = new JButton("Open in Browser");
        openUrlButton.addActionListener(e -> {
            openInBrowser();
            dispose();
        });

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(openUrlButton);

        errorPanel.add(errorLabel, BorderLayout.CENTER);
        errorPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(errorPanel, BorderLayout.CENTER);
    }

    private void openInBrowser() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new java.net.URI(videoUrl));
            }
        } catch (Exception ignored) {}
    }

    private void togglePlayPause() {
        if (mediaPlayerComponent == null) return;

        MediaPlayer player = mediaPlayerComponent.mediaPlayer();
        if (player.status().isPlaying()) {
            player.controls().pause();
        } else {
            player.controls().play();
        }
    }

    private void updateProgress() {
        if (mediaPlayerComponent == null || isUpdatingSlider) return;

        MediaPlayer player = mediaPlayerComponent.mediaPlayer();
        if (player.status().isPlaying() || player.status().isPlayable()) {
            long time = player.status().time();
            long length = player.status().length();

            if (length > 0) {
                int progress = (int) ((time * 1000) / length);
                progressSlider.setValue(progress);
                timeLabel.setText(formatTime(time) + " / " + formatTime(length));
            }
        }
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;

        if (minutes >= 60) {
            long hours = minutes / 60;
            minutes = minutes % 60;
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        }

        return String.format("%d:%02d", minutes, seconds);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;");
    }

    /**
     * Start playing the video.
     */
    public void play() {
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.mediaPlayer().media().play(videoUrl);
            mediaPlayerComponent.mediaPlayer().audio().setVolume(volumeSlider != null ? volumeSlider.getValue() : 80);
        }
    }

    /**
     * Clean up resources.
     */
    public void cleanup() {
        if (progressTimer != null) {
            progressTimer.stop();
        }
        if (mediaPlayerComponent != null) {
            mediaPlayerComponent.mediaPlayer().controls().stop();
            mediaPlayerComponent.release();
            mediaPlayerComponent = null;
        }
    }

    @Override
    public void dispose() {
        cleanup();
        super.dispose();
    }
}
