package utils.graphicsUtilities;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.time.Instant;

public class DummyTimer {
    private long startTime, elapsedTime;
    private boolean started = true;
    private JPanel panel;
    private JLabel visualTimer;

    public DummyTimer() {
        panel = new JPanel(new GridLayout(0, 2));
        visualTimer = new JLabel("00:00");
        panel.add(visualTimer);

    }

    public JPanel getGUI() {
        return panel;
    }

    public void start() {
        started = true;
        startTime = Instant.now().getEpochSecond();
    }

    public void updateGUI() {
        elapsedTime = getElapsedTimeSeconds();
        long minutes = elapsedTime / (60);
        long seconds = (elapsedTime) % 60;
        String str = String.format("%d:%02d", minutes, seconds);
        visualTimer.setText(str);
    }

    public long getElapsedTimeSeconds() {
        if (!started) throw new RuntimeException("Start the timer first");
        return Instant.now().getEpochSecond() - startTime;
    }
}



