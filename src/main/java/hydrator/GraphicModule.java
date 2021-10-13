package hydrator;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;


public enum GraphicModule {
    INSTANCE;
    private final JFrame mainFrame;
    JProgressBar totalWorkToDo, currentFileProgress;
    private final JLabel currentFile;
    //overkill
    StatusPanel statusPanel;
    Semaphore waitForSetup = new Semaphore(0);
    JLabel completed = new JLabel(""), total = new JLabel(""), workRate,currentTweets = new JLabel("0"),requestsSent = new JLabel("0");

    private static class ChooserPanel extends JPanel {
        JLabel mid;

        public ChooserPanel(String topLabel, Callbacks.MyCallbacks s) {
            super(new GridLayout(3, 0));
            JFileChooser fileChooser = new JFileChooser();
            JLabel top = new JLabel(topLabel, SwingConstants.CENTER);
            mid = new JLabel("Currently selected : " + fileChooser.getSelectedFile(), SwingConstants.CENTER);
            fileChooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
            JButton toggleChooser = new JButton("Press to select");
            JPanel chooserHolder = new JPanel();
            chooserHolder.add(toggleChooser);
            toggleChooser.addActionListener(action -> {
                int res = fileChooser.showOpenDialog(this);
                if (res == JFileChooser.APPROVE_OPTION) {
                    mid.setText("Selected : " + fileChooser.getSelectedFile());
                    s.call(fileChooser.getSelectedFile() + "");
                }
            });
            add(top);
            add(mid);
            add(chooserHolder);
            setBorder(BorderFactory.createRaisedSoftBevelBorder());
        }

        void updateMidLabel(String up) {
            mid.setText(up);
        }
    }

    void updateCurrentFile(int index, int val, int requiredAcks) {
        currentFile.setText("Current file : " + Hydrator.INSTANCE.getOutputFile(index).getName());
        currentFileProgress.setMaximum(requiredAcks);
        currentFileProgress.setValue(val);
    }


    void fileDone() {
        completed.setText(Hydrator.INSTANCE.getCompletedFiles() + "");
        totalWorkToDo.setValue(Hydrator.INSTANCE.getCompletedFiles());
    }

    GraphicModule() {
        mainFrame = new JFrame();
        mainFrame.setSize(new Dimension(550, 870));
        mainFrame.setTitle("Hydrator v" + Hydrator.version);
        ChooserPanel[] panels = {new ChooserPanel("Select the folder containing the tweet IDs", new Callbacks.setIDsPath()),
                new ChooserPanel("Select the folder containing the Accounts.xml file", new Callbacks.setAuthTokens()),
                new ChooserPanel("Select the save path for the rehydrated tweets", new Callbacks.setSavePath()),
                new ChooserPanel("Select the folder to store logs in", new Callbacks.setLogPath())
        };
        panels[3].updateMidLabel("Currently selected : " + System.getProperty("java.io.tmpdir"));
        JLabel currentRate = new JLabel("Select a rate");
        JComboBox<Hydrator.exec_setting> rate = new JComboBox<>(Hydrator.exec_setting.values());
        rate.addActionListener(action -> {
            Hydrator.INSTANCE.setRate((Hydrator.exec_setting) rate.getSelectedItem());
            currentRate.setText("Selected rate is : " + rate.getSelectedItem());
        });
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        JPanel selectionPanel = new JPanel(), bottomPanel = new JPanel();
        bottomPanel.setLayout(new BoxLayout(bottomPanel, BoxLayout.Y_AXIS));
        JPanel ratePanel = new JPanel(new GridLayout(2, 0));
        selectionPanel.setLayout(new BoxLayout(selectionPanel, BoxLayout.Y_AXIS));
        for (ChooserPanel panel : panels)
            selectionPanel.add(panel);
        ratePanel.add(currentRate, SwingConstants.CENTER);
        ratePanel.add(rate);
        selectionPanel.add(ratePanel);
        container.add(selectionPanel);
        JButton hydrate = new JButton("Hydrate");
        totalWorkToDo = new JProgressBar();
        BoxLayout boxLayout = new BoxLayout(container, BoxLayout.Y_AXIS);
        container.setLayout(boxLayout);
        JPanel progressPanel = new JPanel(new GridLayout(7, 1));
        currentFile = new JLabel("Current file");
        currentFileProgress = new JProgressBar();
        totalWorkToDo.setMaximumSize(new Dimension(350, 20));
        currentFileProgress.setMaximumSize(new Dimension(350, 20));
        progressPanel.add(statusPanel = new StatusPanel());
        progressPanel.add(workRate = new JLabel());
        progressPanel.add(new JLabel("Overall progress"));
        progressPanel.add(totalWorkToDo);
        progressPanel.add(currentFile);
        progressPanel.add(currentFileProgress);
        JPanel completedFilesCount = new JPanel(new GridLayout(2, 1));
        JPanel left = new JPanel(new GridLayout(1, 0)), right = new JPanel(new GridLayout(1, 0));
        left.add(new JLabel("Completed files : "));
        left.add(completed);
        left.add(total);
        right.add(new JLabel("Rehydrated Tweets"));
        right.add(currentTweets);
        right.add(requestsSent);
        completedFilesCount.add(left);
        completedFilesCount.add(right);
        progressPanel.add(completedFilesCount);
        progressPanel.setMaximumSize(new Dimension(mainFrame.getWidth() * 70 / 100, mainFrame.getHeight() * 20 / 100));
        container.add(progressPanel);
        hydrate.setPreferredSize(new Dimension(200, 25));
        Runnable task = () -> {
            try {
                waitForSetup.acquire();
                while (true) {
                    currentFileProgress.setValue((int) Hydrator.INSTANCE.ioHandler.getCurrentAcks());
                    currentTweets.setText(ResponseParser.getRehydratedTweets()+"");
                    requestsSent.setText("/" + Hydrator.INSTANCE.executor.getRequests()*100);
                    Thread.sleep(150);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        };
        ExecutorService service = Executors.newFixedThreadPool(2);
        hydrate.addActionListener(action -> {
            if (!Hydrator.INSTANCE.isSetup()) {
                JOptionPane.showMessageDialog(mainFrame, "Select a save folder,an input folder,a file containing the auth tokens and a rate", "Setup Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (Hydrator.INSTANCE.isRunning()) return;
            service.execute(Hydrator.INSTANCE::hydrate);
            service.execute(task);
        });
        JPanel buttonHolder = new JPanel();
        buttonHolder.add(hydrate);
        container.add(buttonHolder);
        mainFrame.add(container);
        mainFrame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        selectionPanel.setBorder(BorderFactory.createEmptyBorder(25, 50, 15, 50));
        mainFrame.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                progressPanel.setMaximumSize(new Dimension(mainFrame.getWidth() * 70 / 100, mainFrame.getHeight() * 20 / 100));
            }
        });
        mainFrame.setVisible(true);
    }


    static class StatusPanel extends JPanel {
        private enum state {STOPPED, HYDRATING, WAITING_FOR_WINDOW_RESET, TIMED_OUT}

        private JButton visualIndicator;
        private state currentState = state.STOPPED;

        private static class RoundedBorder implements Border {

            private int radius;


            RoundedBorder(int radius) {
                this.radius = radius;
            }


            public Insets getBorderInsets(Component c) {
                return new Insets(this.radius + 1, this.radius + 1, this.radius + 2, this.radius);
            }


            public boolean isBorderOpaque() {
                return true;
            }


            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                g.drawRoundRect(x, y, width - 1, height - 1, radius, radius);
            }
        }

        StatusPanel() {
            super(new GridLayout(0, 3));
            add(new JLabel("Current status: "));
            visualIndicator = new JButton(currentState.toString());
            visualIndicator.setBounds(15, 15, 15, 15);
            visualIndicator.setBorder(new RoundedBorder(8)); //10 is the radius
            visualIndicator.setForeground(Color.RED);
            add(visualIndicator);
        }

        void timeOut() {
            currentState = state.TIMED_OUT;
            visualIndicator.setForeground(Color.RED);
            revalidate();
        }

        void stop() {
            currentState = state.STOPPED;
            revalidate();
        }

        void start() {
            currentState = state.HYDRATING;
            visualIndicator.setForeground(Color.BLACK);
            revalidate();
        }

        @Override
        public void revalidate() {
            super.revalidate();
            if (currentState != null)
                visualIndicator.setText(currentState.toString());
        }

        void waitingForKey() {
            currentState = state.WAITING_FOR_WINDOW_RESET;
            visualIndicator.setForeground(Color.BLUE);
            revalidate();
        }
    }
}
