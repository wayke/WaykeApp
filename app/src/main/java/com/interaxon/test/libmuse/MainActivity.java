package com.interaxon.test.libmuse;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.app.NotificationCompat;
import android.os.Bundle;

import java.io.File;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Observable;
import java.util.Observer;
//import java.util.Random;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.os.Environment;
import android.os.Vibrator;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;
import com.androidplot.xy.XYStepMode;
import com.firebase.client.Firebase;
import com.interaxon.libmuse.AnnotationData;
import com.interaxon.libmuse.ConnectionState;
import com.interaxon.libmuse.Eeg;
import com.interaxon.libmuse.LibMuseVersion;
import com.interaxon.libmuse.MessageType;
import com.interaxon.libmuse.Muse;
import com.interaxon.libmuse.MuseArtifactPacket;
import com.interaxon.libmuse.MuseConfiguration;
import com.interaxon.libmuse.MuseConnectionListener;
import com.interaxon.libmuse.MuseConnectionPacket;
import com.interaxon.libmuse.MuseDataListener;
import com.interaxon.libmuse.MuseDataPacket;
import com.interaxon.libmuse.MuseDataPacketType;
import com.interaxon.libmuse.MuseFileFactory;
import com.interaxon.libmuse.MuseFileReader;
import com.interaxon.libmuse.MuseFileWriter;
import com.interaxon.libmuse.MuseManager;
import com.interaxon.libmuse.MusePreset;
import com.interaxon.libmuse.MuseVersion;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class MainActivity extends Activity implements OnClickListener {
    /**
     * Connection listener updates UI with new connection status and logs it.
     */

    class ConnectionListener extends MuseConnectionListener {

        final WeakReference<Activity> activityRef;

        ConnectionListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseConnectionPacket(MuseConnectionPacket p) {
            final ConnectionState current = p.getCurrentConnectionState();
            final String status = p.getPreviousConnectionState().toString() +
                    " -> " + current;
            final String full = "Muse " + p.getSource().getMacAddress() +
                    " " + status;
            Log.i("Muse Headband", full);
            Activity activity = activityRef.get();
            // UI thread is used here only because we need to update
            // TextView values. You don't have to use another thread, unless
            // you want to run disconnect() or connect() from connection packet
            // handler. In this case creating another thread is required.
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView statusText =
                                (TextView) findViewById(R.id.con_status);
                        statusText.setText(status);
                        if (current == ConnectionState.CONNECTED) {
                            MuseVersion museVersion = muse.getMuseVersion();
                            String version = museVersion.getFirmwareType() +
                                    " - " + museVersion.getFirmwareVersion() +
                                    " - " + Integer.toString(
                                    museVersion.getProtocolVersion());
                        }
                    }
                });
            }
        }
    }

    /**
     * Data listener will be registered to listen for: Accelerometer,
     * Eeg and Relative Alpha bandpower packets. In all cases we will
     * update UI with new values.
     * We also will log message if Artifact packets contains "blink" flag.
     * DataListener methods will be called from execution thread. If you are
     * implementing "serious" processing algorithms inside those listeners,
     * consider to create another thread.
     */
    class DataListener extends MuseDataListener {

        final WeakReference<Activity> activityRef;
        private MuseFileWriter fileWriter;

        DataListener(final WeakReference<Activity> activityRef) {
            this.activityRef = activityRef;
        }

        @Override
        public void receiveMuseDataPacket(MuseDataPacket p) {
            switch (p.getPacketType()) {
                case EEG:
                    updateEeg(p.getValues());
                    break;
                case ACCELEROMETER:
//                    updateAccelerometer(p.getValues());
                    break;
                case ALPHA_RELATIVE:
                    updateAlpha(p.getValues());
                    break;
                case BETA_RELATIVE:
                    updateBeta(p.getValues());
                    break;
                case BATTERY:
                    fileWriter.addDataPacket(1, p);
                    // It's library client responsibility to flush the buffer,
                    // otherwise you may get memory overflow. 
                    if (fileWriter.getBufferedMessagesSize() > 8096)
                        fileWriter.flush();
                    break;
                default:
                    break;
            }
        }


        @Override
        public void receiveMuseArtifactPacket(MuseArtifactPacket p) {
            if (p.getHeadbandOn() && p.getBlink()) {
                Log.i("Artifacts", "blink");
            }
        }

        private void updateEeg(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        TextView tp9 = (TextView) findViewById(R.id.eeg_tp9);
                        TextView fp1 = (TextView) findViewById(R.id.eeg_fp1);
                        TextView fp2 = (TextView) findViewById(R.id.eeg_fp2);
                        TextView tp10 = (TextView) findViewById(R.id.eeg_tp10);
                        tp9.setText(String.format(
                                "%6.2f", data.get(Eeg.TP9.ordinal())));
                        fp1.setText(String.format(
                                "%6.2f", data.get(Eeg.FP1.ordinal())));
                        fp2.setText(String.format(
                                "%6.2f", data.get(Eeg.FP2.ordinal())));
                        tp10.setText(String.format(
                                "%6.2f", data.get(Eeg.TP10.ordinal())));
                    }
                });
            }
        }

        private void writeData(String data, String AlphaCsvFile) {
            PrintWriter csvWriter;
            try
            {

                File file = new File(AlphaCsvFile);
                if(!file.exists()){
                    file = new File(AlphaCsvFile);
                }
                csvWriter = new  PrintWriter(new FileWriter(file,true));


                csvWriter.print(data);
                csvWriter.append('\n');

                csvWriter.flush();
                csvWriter.close();


            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        private void updateAlpha(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Long tsLong = System.currentTimeMillis()/1000;
                        String ts = tsLong.toString();

                        File root = Environment.getExternalStorageDirectory();

                        double tp9 = data.get(Eeg.TP9.ordinal());
                        double fp1 = data.get(Eeg.FP1.ordinal());
                        double fp2 = data.get(Eeg.FP2.ordinal());
                        double tp10 = data.get(Eeg.TP10.ordinal());

                        TextView elem1 = (TextView) findViewById(R.id.elem1);
                        TextView elem2 = (TextView) findViewById(R.id.elem2);
                        TextView elem3 = (TextView) findViewById(R.id.elem3);
                        TextView elem4 = (TextView) findViewById(R.id.elem4);
                        elem1.setText(String.format(
                                "%6.2f", tp9));
                        elem2.setText(String.format(
                                "%6.2f", fp1));
                        elem3.setText(String.format(
                                "%6.2f", tp9));
                        elem4.setText(String.format(
                                "%6.2f", tp10));

                        double alphaAvg = (tp9 + fp1 + fp2 + tp10)/4;

                        TextView avg_alpha = (TextView) findViewById(R.id.avg_alpha);
                        avg_alpha.setText(String.format(
                                "%6.2f", alphaAvg));

                        writeData(ts+','+Double.toString(alphaAvg), root+AlphaCsvFile);

//                        String alphaLog = ts + ',' + Double.toString(tp9) + ',' + Double.toString(fp1)
//                                            + ',' + Double.toString(fp2) + ',' + Double.toString(tp10);

//                        writeData(alphaLog, root+AlphaCsvFile);

                    }
                });
            }
        }

        private void updateBeta(final ArrayList<Double> data) {
            Activity activity = activityRef.get();
            if (activity != null) {
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Long tsLong = System.currentTimeMillis()/1000;
                        String ts = tsLong.toString();

                        File root = Environment.getExternalStorageDirectory();

                        double tp9 = data.get(Eeg.TP9.ordinal());
                        double fp1 = data.get(Eeg.FP1.ordinal());
                        double fp2 = data.get(Eeg.FP2.ordinal());
                        double tp10 = data.get(Eeg.TP10.ordinal());

                        double betaAvg = (tp9 + fp1 + fp2 + tp10)/4;

                        TextView avg_beta = (TextView) findViewById(R.id.avg_beta);
                        avg_beta.setText(String.format(
                                "%6.2f", betaAvg));

                        writeData(ts+','+Double.toString(betaAvg), root+BetaCsvFile);

//                        String betaLog = ts + ',' + Double.toString(tp9) + ',' + Double.toString(fp1)
//                                + ',' + Double.toString(fp2) + ',' + Double.toString(tp10);
//
//                        writeData(betaLog, root+BetaCsvFile);

                    }
                });
            }
        }

        public void setFileWriter(MuseFileWriter fileWriter) {
            this.fileWriter  = fileWriter;
        }
    }

    private static void generateCsv(String sFileName)
    {
        try
        {
            File root = Environment.getExternalStorageDirectory();
            File gpxfile = new File(root, sFileName);
            FileWriter writer = new FileWriter(gpxfile);

            writer.append("TimeStamp");
            writer.append(',');
            writer.append("Avg");
            writer.append('\n');

//            writer.append("TimeStamp");
//            writer.append(',');
//            writer.append("EEG-TP9");
//            writer.append(',');
//            writer.append("EEG-TP9");
//            writer.append(',');
//            writer.append("EEG-TP9");
//            writer.append(',');
//            writer.append("EEG-TP9");
//            writer.append('\n');

            //generate whatever data you want

            writer.flush();
            writer.close();
        }
        catch(IOException e)
        {
            e.printStackTrace();
        }
    }

    private Muse muse = null;
    private ConnectionListener connectionListener = null;
    private DataListener dataListener = null;
    private boolean dataTransmission = true;
    private MuseFileWriter fileWriter = null;

    private final static String AlphaCsvFile = "/alphaMuseData.csv";
    private final static String BetaCsvFile = "/betaMuseData.csv";
//    private final static String csvFile = "museData.csv";
//    private final static String csvFile = "museData.csv";

    private static int count = 0;

    public MainActivity() {
        // Create listeners and pass reference to activity to them
        WeakReference<Activity> weakActivity =
                new WeakReference<Activity>(this);

        connectionListener = new ConnectionListener(weakActivity);
        dataListener = new DataListener(weakActivity);
        generateCsv(AlphaCsvFile);
        generateCsv(BetaCsvFile);
    }

    // redraws a plot whenever an update is received:
    private class MyPlotUpdater implements Observer {
        Plot plot;

        public MyPlotUpdater(Plot plot) {
            this.plot = plot;
        }

        @Override
        public void update(Observable o, Object arg) {
            plot.redraw();
        }
    }

    private XYPlot dynamicPlot;
    private MyPlotUpdater plotUpdater;
    //    SampleDynamicXYDatasource data;
    LiveDataSource liveData;
    private Thread myThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button refreshButton = (Button) findViewById(R.id.refresh);
        refreshButton.setOnClickListener(this);
        Button connectButton = (Button) findViewById(R.id.connect);
        connectButton.setOnClickListener(this);
        Button disconnectButton = (Button) findViewById(R.id.disconnect);
        disconnectButton.setOnClickListener(this);
        Button pauseButton = (Button) findViewById(R.id.pause);
        pauseButton.setOnClickListener(this);
        Firebase.setAndroidContext(this);
        // Uncomment to test Muse File Reader

        // file can be big, read it in a separate thread
        Thread thread = new Thread(new Runnable() {
            public void run() {
                playMuseFile("testfile.muse");
            }
        });
        thread.start();

        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        fileWriter = MuseFileFactory.getMuseFileWriter(
                new File(dir, "new_muse_file.muse"));

        Log.i("Muse Headband", "libmuse version=" + LibMuseVersion.SDK_VERSION);
        fileWriter.addAnnotationString(1, "MainActivity onCreate");
        dataListener.setFileWriter(fileWriter);

        // get handles to our View defined in layout.xml:
        dynamicPlot = (XYPlot) findViewById(R.id.dynamicXYPlot);

        plotUpdater = new MyPlotUpdater(dynamicPlot);

        // only display whole numbers in domain labels
        dynamicPlot.getGraphWidget().setDomainValueFormat(new DecimalFormat("0"));

        // getInstance and position datasets:
//        data = new SampleDynamicXYDatasource();
        liveData = new LiveDataSource();

//        SampleDynamicSeries sine1Series = new SampleDynamicSeries(data, 0, "Sine 1");
//        SampleDynamicSeries sine2Series = new SampleDynamicSeries(data, 1, "Sine 2");

        SampleDynamicSeries alphaSeires = new SampleDynamicSeries(liveData, 0, "Alpha");
        SampleDynamicSeries betaSeires = new SampleDynamicSeries(liveData, 1, "Beta");

//        LineAndPointFormatter formatter1 = new LineAndPointFormatter(
//                Color.rgb(0, 0, 0), null, null, null);
//        formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
//        formatter1.getLinePaint().setStrokeWidth(10);
//        dynamicPlot.addSeries(sine1Series,
//                formatter1);
//
//        LineAndPointFormatter formatter2 =
//                new LineAndPointFormatter(Color.rgb(0, 0, 200), null, null, null);
//        formatter2.getLinePaint().setStrokeWidth(10);
//        formatter2.getLinePaint().setStrokeJoin(Paint.Join.ROUND);

        LineAndPointFormatter formatter1 = new LineAndPointFormatter(
                Color.rgb(5, 5, 5), null, null, null);
        formatter1.getLinePaint().setStrokeJoin(Paint.Join.ROUND);
        formatter1.getLinePaint().setStrokeWidth(10);
        dynamicPlot.addSeries(alphaSeires,
                formatter1);

        LineAndPointFormatter formatter2 =
                new LineAndPointFormatter(Color.rgb(0, 20, 180), null, null, null);
        formatter2.getLinePaint().setStrokeWidth(10);
        formatter2.getLinePaint().setStrokeJoin(Paint.Join.ROUND);

        //formatter2.getFillPaint().setAlpha(220);
        dynamicPlot.addSeries(betaSeires, formatter2);

        // hook up the plotUpdater to the data model:
//        data.addObserver(plotUpdater);
        liveData.addObserver(plotUpdater);

        // thin out domain tick labels so they dont overlap each other:
        dynamicPlot.setDomainStepMode(XYStepMode.INCREMENT_BY_VAL);
        dynamicPlot.setDomainStepValue(10);

        dynamicPlot.setRangeStepMode(XYStepMode.INCREMENT_BY_VAL);
        dynamicPlot.setRangeStepValue(1);

        dynamicPlot.setRangeValueFormat(new DecimalFormat("###.#"));

        // uncomment this line to freeze the range boundaries:
        dynamicPlot.setRangeBoundaries(-1.5, 0.7, BoundaryMode.FIXED);

        // create a dash effect for domain and range grid lines:
        DashPathEffect dashFx = new DashPathEffect(
                new float[] {PixelUtils.dpToPix(3), PixelUtils.dpToPix(3)}, 0);
        dynamicPlot.getGraphWidget().getDomainGridLinePaint().setPathEffect(dashFx);
        dynamicPlot.getGraphWidget().getRangeGridLinePaint().setPathEffect(dashFx);
    }
    @Override
    public void onResume() {
        // kick off the data generating thread:
//        myThread = new Thread(data);
        myThread = new Thread(liveData);
        myThread.start();
        super.onResume();
    }

    @Override
    public void onClick(View v) {
        Spinner musesSpinner = (Spinner) findViewById(R.id.muses_spinner);
        if (v.getId() == R.id.refresh) {
            MuseManager.refreshPairedMuses();
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            List<String> spinnerItems = new ArrayList<String>();
            for (Muse m: pairedMuses) {
                String dev_id = m.getName() + "-" + m.getMacAddress();
                Log.i("Muse Headband", dev_id);
                spinnerItems.add(dev_id);
            }
            ArrayAdapter<String> adapterArray = new ArrayAdapter<String> (
                    this, android.R.layout.simple_spinner_item, spinnerItems);
            musesSpinner.setAdapter(adapterArray);
        }
        else if (v.getId() == R.id.connect) {
            List<Muse> pairedMuses = MuseManager.getPairedMuses();
            if (pairedMuses.size() < 1 ||
                    musesSpinner.getAdapter().getCount() < 1) {
                Log.w("Muse Headband", "There is nothing to connect to");
            }
            else {
                muse = pairedMuses.get(musesSpinner.getSelectedItemPosition());
                ConnectionState state = muse.getConnectionState();
                if (state == ConnectionState.CONNECTED ||
                        state == ConnectionState.CONNECTING) {
                    Log.w("Muse Headband",
                            "doesn't make sense to connect second time to the same muse");
                    return;
                }
                configureLibrary();
                fileWriter.open();
                fileWriter.addAnnotationString(1, "Connect clicked");
                /**
                 * In most cases libmuse native library takes care about
                 * exceptions and recovery mechanism, but native code still
                 * may throw in some unexpected situations (like bad bluetooth
                 * connection). Print all exceptions here.
                 */
                try {
                    muse.runAsynchronously();
                } catch (Exception e) {
                    Log.e("Muse Headband", e.toString());
                }
            }
        }
        else if (v.getId() == R.id.disconnect) {
            if (muse != null) {
                /**
                 * true flag will force libmuse to unregister all listeners,
                 * BUT AFTER disconnecting and sending disconnection event.
                 * If you don't want to receive disconnection event (for ex.
                 * you call disconnect when application is closed), then
                 * unregister listeners first and then call disconnect:
                 * muse.unregisterAllListeners();
                 * muse.disconnect(false);
                 */
//                pingArduino();
                Firebase myFirebaseRef = new Firebase("https://wayke.firebaseio.com/");
                myFirebaseRef.child("message").setValue("Exit");
                muse.disconnect(true);
                fileWriter.addAnnotationString(1, "Disconnect clicked");
                fileWriter.flush();
                fileWriter.close();
            }
        }
        else if (v.getId() == R.id.pause) {
            dataTransmission = !dataTransmission;
            if (muse != null) {
                muse.enableDataTransmission(dataTransmission);
            }
        }
    }

    /*
     * Simple example of getting data from the "*.muse" file
     */
    private void playMuseFile(String name) {
        File dir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        File file = new File(dir, name);
        final String tag = "Muse File Reader";
        if (!file.exists()) {
            Log.w(tag, "file doesn't exist");
            return;
        }
        MuseFileReader fileReader = MuseFileFactory.getMuseFileReader(file);
        while (fileReader.gotoNextMessage()) {
            MessageType type = fileReader.getMessageType();
            int id = fileReader.getMessageId();
            long timestamp = fileReader.getMessageTimestamp();
            Log.i(tag, "type: " + type.toString() +
                    " id: " + Integer.toString(id) +
                    " timestamp: " + String.valueOf(timestamp));
            switch(type) {
                case EEG: case BATTERY: case ACCELEROMETER: case QUANTIZATION:
                    MuseDataPacket packet = fileReader.getDataPacket();
                    Log.i(tag, "data packet: " + packet.getPacketType().toString());
                    break;
                case VERSION:
                    MuseVersion version = fileReader.getVersion();
                    Log.i(tag, "version" + version.getFirmwareType());
                    break;
                case CONFIGURATION:
                    MuseConfiguration config = fileReader.getConfiguration();
                    Log.i(tag, "config" + config.getBluetoothMac());
                    break;
                case ANNOTATION:
                    AnnotationData annotation = fileReader.getAnnotation();
                    Log.i(tag, "annotation" + annotation.getData());
                    break;
                default:
                    break;
            }
        }
    }


    private void configureLibrary() {
        muse.registerConnectionListener(connectionListener);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.ACCELEROMETER);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.EEG);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.ALPHA_RELATIVE);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.BETA_RELATIVE);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.ARTIFACTS);
        muse.registerDataListener(dataListener,
                MuseDataPacketType.BATTERY);
        muse.setPreset(MusePreset.PRESET_14);
        muse.enableDataTransmission(dataTransmission);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    //    class SampleDynamicXYDatasource implements Runnable {
//
//        // encapsulates management of the observers watching this datasource for update events:
//        class MyObservable extends Observable {
//            @Override
//            public void notifyObservers() {
//                setChanged();
//                super.notifyObservers();
//            }
//        }
//
//        private static final double FREQUENCY = 5; // larger is lower frequency
//        private static final int MAX_AMP_SEED = 100;
//        private static final int MIN_AMP_SEED = 10;
//        private static final int AMP_STEP = 1;
//        public static final int SINE1 = 0;
//        public static final int SINE2 = 1;
//        private static final int SAMPLE_SIZE = 30;
//        private int phase = 0;
//        private int sinAmp = 1;
//        private MyObservable notifier;
//        private boolean keepRunning = false;
//
//        {
//            notifier = new MyObservable();
//        }
//
//        public void stopThread() {
//            keepRunning = false;
//        }
//
//        //@Override
//        public void run() {
//            try {
//                keepRunning = true;
//                boolean isRising = true;
//                while (keepRunning) {
//
//                    Thread.sleep(10); // decrease or remove to speed up the refresh rate.
//                    phase++;
//                    if (sinAmp >= MAX_AMP_SEED) {
//                        isRising = false;
//                    } else if (sinAmp <= MIN_AMP_SEED) {
//                        isRising = true;
//                    }
//
//                    if (isRising) {
//                        sinAmp += AMP_STEP;
//                    } else {
//                        sinAmp -= AMP_STEP;
//                    }
//                    notifier.notifyObservers();
//                }
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//
//        public int getItemCount(int series) {
//            return SAMPLE_SIZE;
//        }
//
//        public Number getX(int series, int index) {
//            if (index >= SAMPLE_SIZE) {
//                throw new IllegalArgumentException();
//            }
//            return index;
//        }
//
//        public Number getY(int series, int index) {
//            if (index >= SAMPLE_SIZE) {
//                throw new IllegalArgumentException();
//            }
//            double angle = (index + (phase))/FREQUENCY;
//            double amp = sinAmp * Math.sin(angle);
//            switch (series) {
//                case SINE1:
//                    return amp;
//                case SINE2:
//                    return -amp;
//                default:
//                    throw new IllegalArgumentException();
//            }
//        }
//
//        public void addObserver(Observer observer) {
//            notifier.addObserver(observer);
//        }
//
//        public void removeObserver(Observer observer) {
//            notifier.deleteObserver(observer);
//        }
//
//    }
//

//    private void pingArduino() {
//        HttpResponse response = null;
//        try {
//            HttpClient client = new DefaultHttpClient();
//            HttpGet request = new HttpGet();
//            request.setURI(new URI("http://localhost:3000"));
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }

    public void showNotification() {
        PendingIntent pi = PendingIntent.getActivity(this, 0, new Intent(this, ShowNotificationDetailActivity.class), 0);
        Resources r = getResources();
        Notification notification = new NotificationCompat.Builder(this)
                .setTicker(r.getString(R.string.notification_title))
                .setSmallIcon(android.R.drawable.ic_menu_report_image)
                .setContentTitle(r.getString(R.string.notification_title))
                .setContentText(r.getString(R.string.notification_text))
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        notificationManager.notify(0, notification);
    }

    class LiveDataSource implements Runnable {
        // encapsulates management of the observers watching this datasource for update events:
        class MyObservable extends Observable {
            @Override
            public void notifyObservers() {
                setChanged();
                super.notifyObservers();
            }
        }

        private static final double FREQUENCY = 5; // larger is lower frequency
        private static final int MAX_AMP_SEED = 100;
        private static final int MIN_AMP_SEED = 10;
        private static final int AMP_STEP = 1;
        public static final int SINE1 = 0;
        public static final int SINE2 = 1;
        private static final int SAMPLE_SIZE = 30;
        private int phase = 0;
        private int sinAmp = 0;
        private double alphaAmp = 0;
        private double betaAmp = 0;
        private MyObservable notifier;
        private boolean keepRunning = false;

        {
            notifier = new MyObservable();
        }

        public void stopThread() {
            keepRunning = false;
        }

        //@Override
        public void run() {
            try {
                keepRunning = true;
                boolean isRising = true;
                while (keepRunning) {

                    Thread.sleep(10); // decrease or remove to speed up the refresh rate.
                    phase++;

                    TextView avg_alpha = (TextView) findViewById(R.id.avg_alpha);
                    TextView avg_beta = (TextView) findViewById(R.id.avg_beta);

                    alphaAmp = Double.parseDouble(avg_alpha.getText().toString());
                    betaAmp = Double.parseDouble(avg_beta.getText().toString());

                    if (alphaAmp > betaAmp) {
                        count++;
                        if (count > 500) {
                            Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                            // Vibrate for 500 milliseconds
                            v.vibrate(500);
                            count = 0;
                            showNotification();
                            Firebase myFirebaseRef = new Firebase("https://wayke.firebaseio.com/");
                            myFirebaseRef.child("message").setValue(count);
//                            pingArduino();
                        }
                    }

                    notifier.notifyObservers();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        public int getItemCount(int series) {
            return SAMPLE_SIZE;
        }

        public Number getX(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }
            return index;
        }

        public Number getY(int series, int index) {
            if (index >= SAMPLE_SIZE) {
                throw new IllegalArgumentException();
            }

            int randomNum = 8 + (int)(Math.random() * (10 - 8));

            double angle = (index + (phase))/FREQUENCY;
            double bAngle = (index + (phase+randomNum))/FREQUENCY;
            double aAmp = alphaAmp * Math.sin(angle);
            double bAmp = betaAmp * Math.sin(bAngle);
            double amp = sinAmp*Math.sin(angle);
            switch (series) {
                case SINE1:
//                    return amp;
                    return aAmp-1;
                case SINE2:
//                    return -amp;
                    return bAmp;
                default:
                    throw new IllegalArgumentException();
            }
        }

        public void addObserver(Observer observer) {
            notifier.addObserver(observer);
        }

        public void removeObserver(Observer observer) {
            notifier.deleteObserver(observer);
        }
    }

    class SampleDynamicSeries implements XYSeries {
        //        private SampleDynamicXYDatasource datasource;
        private LiveDataSource datasource;
        private int seriesIndex;
        private String title;

//        public SampleDynamicSeries(SampleDynamicXYDatasource datasource, int seriesIndex, String title) {
//            this.datasource = datasource;
//            this.seriesIndex = seriesIndex;
//            this.title = title;
//        }

        public SampleDynamicSeries(LiveDataSource datasource, int seriesIndex, String title) {
            this.datasource = datasource;
            this.seriesIndex = seriesIndex;
            this.title = title;
        }

        @Override
        public String getTitle() {
            return title;
        }

        @Override
        public int size() {
            return datasource.getItemCount(seriesIndex);
        }

        @Override
        public Number getX(int index) {
            return datasource.getX(seriesIndex, index);
        }

        @Override
        public Number getY(int index) {
            return datasource.getY(seriesIndex, index);
        }
    }

}
