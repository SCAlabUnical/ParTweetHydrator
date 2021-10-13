package hydrator;


import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class Callbacks {
    interface MyCallbacks {
        void call(String s);
    }

    static class setIDsPath implements MyCallbacks {

        @Override
        public void call(String path) {
            try {
                Hydrator.INSTANCE.setFileList(path);
                GraphicModule.INSTANCE.totalWorkToDo.setIndeterminate(false);
                GraphicModule.INSTANCE.totalWorkToDo.setMaximum(Hydrator.INSTANCE.getFileListSize());
                GraphicModule.INSTANCE.total.setText("/" + Hydrator.INSTANCE.getFileListSize());
                GraphicModule.INSTANCE.totalWorkToDo.setValue(0);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    static class setLogPath implements MyCallbacks {

        @Override
        public void call(String path) {
            Hydrator.INSTANCE.setLogPath(path);
        }
    }

    static class setSavePath implements MyCallbacks {
        @Override
        public void call(String path) {
            Hydrator.INSTANCE.setSavePath(path);
        }
    }


    static class setAuthTokens implements MyCallbacks {

        static class KeysPanel extends JFrame {
            private JProgressBar progressBar;
            private JPanel holder;

            public KeysPanel() {
                super();
                holder = new JPanel();
                progressBar = new JProgressBar();
                progressBar.setIndeterminate(true);
                holder.add(new JLabel("Validating keys,please wait"));
                holder.add(progressBar);
                add(holder);
                setSize(new Dimension(200, 100));
                setVisible(true);
            }

            void done() {
                progressBar.setIndeterminate(false);
                progressBar.setMaximum(1);
                progressBar.setValue(1);
                holder.add(new JLabel("Done,you can start the hydrator"));
                revalidate();
            }
        }

        @Override
        public void call(String path) {
            Executors.newSingleThreadExecutor().execute(() -> {
                KeysPanel p = new KeysPanel();
                boolean err = false;
                Hydrator.INSTANCE.setTokens(path);
                p.done();
                try {
                    TimeUnit.SECONDS.sleep(3);
                } catch (InterruptedException e) {
                 }
                p.dispose();
                NumberFormat nf = NumberFormat.getNumberInstance(Locale.ITALIAN);
                DecimalFormat df = (DecimalFormat) nf;
                GraphicModule.INSTANCE.workRate.setText("Current work rate : " + df.format(Hydrator.INSTANCE.getCurrentWorkRate())
                        + " tweets/15 minutes");
            });
        }
    }
}
