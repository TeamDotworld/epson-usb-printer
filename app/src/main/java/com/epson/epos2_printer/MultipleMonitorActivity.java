package com.epson.epos2_printer;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.epson.epos2.Epos2Exception;
import com.epson.epos2.printer.Printer;
import com.epson.epos2.printer.PrinterStatusInfo;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;


public class MultipleMonitorActivity extends Activity implements View.OnClickListener {

    private static final int MONITOR_INTERVAL = 3000;//millseconds
    private static final int DISCONNECT_INTERVAL = 500;//millseconds
    private static final int THREAD_TIMEOUT = 70;//seconds

    private Context mContext = null;

    private Button mBtnStartGetstatus = null;
    private Button mBtnStopGetstatus = null;
    public static EditText mEdtStatusMulti = null;

    private Printer mPrinter = null;
    private Boolean mIsMonitoring = false;//　Exclusive control is necessary
    private ExecutorService mExecutor = null;
    private Future mFuture = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_multiplemonitor);

        mContext = this;

        mBtnStartGetstatus= (Button)findViewById(R.id.btnStartGetstatus);
        mBtnStartGetstatus.setOnClickListener(this);
        mBtnStartGetstatus.setEnabled(true);

        mBtnStopGetstatus= (Button)findViewById(R.id.btnStopGetstatus);
        mBtnStopGetstatus.setOnClickListener(this);
        mBtnStopGetstatus.setEnabled(false);

        mEdtStatusMulti = (EditText)findViewById(R.id.edtStatusMulti);
        mEdtStatusMulti.setText("");

        mExecutor = Executors.newSingleThreadExecutor();

        initializeObject();

    }
    public void onDestroy() {

        if(mIsMonitoring){
            stopGetStatus();
        }

        finalizeObject();

        try {
            mExecutor.shutdown();
            if (!mExecutor.awaitTermination(THREAD_TIMEOUT, TimeUnit.SECONDS)) {
                mExecutor.shutdownNow();
            }
        }catch(Exception e){

        }

        super.onDestroy();
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btnStartGetstatus:

                startGetStatus();
                break;

            case R.id.btnStopGetstatus:

                stopGetStatus();
                break;

            default:
                // Do nothing
                break;
        }

        if(mIsMonitoring){
            mBtnStartGetstatus.setEnabled(false);
            mBtnStopGetstatus.setEnabled(true);
        }else{
            mBtnStartGetstatus.setEnabled(true);
            mBtnStopGetstatus.setEnabled(false);
        }
    }

    protected boolean initializeObject() {
        try {
            mPrinter = new Printer(((SpnModelsItem) MainActivity.mSpnSeries.getSelectedItem()).getModelConstant(),
                    ((SpnModelsItem) MainActivity.mSpnLang.getSelectedItem()).getModelConstant(),
                    mContext);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "Printer", mContext);
            return false;
        }

        return true;
    }

    protected void finalizeObject() {
        if (mPrinter == null) {
            return;
        }

        mPrinter = null;

    }

    protected boolean startGetStatus(){
        if (mIsMonitoring){
            return false;
        }

        if (mPrinter == null) {
            return false;
        }

        mIsMonitoring = true;
        mFuture = mExecutor.submit(new Runnable() {
            public void run() {

                PrinterStatusInfo statusInfo = null;
                while (mIsMonitoring) {

                    try {
                        mPrinter.connect(MainActivity.mEditTarget.getText().toString(), Printer.PARAM_DEFAULT);

                        statusInfo = mPrinter.getStatus();
                        final String msg = makeStatusMassage(statusInfo);
                        runOnUiThread(new Runnable(){
                            public synchronized void run() {
                                mEdtStatusMulti.setText(msg);
                            }
                        });

                        while (true) {
                            try {
                                mPrinter.disconnect();
                                break;
                            } catch (Exception e) {
                                if (e instanceof Epos2Exception) {
                                    //Note: If printer is processing such as printing and so on, the disconnect API returns ERR_PROCESSING.
                                    if (((Epos2Exception) e).getErrorStatus() == Epos2Exception.ERR_PROCESSING) {
                                        try {
                                            Thread.sleep(DISCONNECT_INTERVAL);
                                        } catch (Exception ex) {
                                        }
                                    }else{
                                        break;
                                    }
                                }else{
                                    break;
                                }
                            }
                        }

                        //The short INTERVAL value generates a heavy network load.
                        //Please set a appropriate value in accordance with the network situation.
                        try {
                            Thread.sleep(MONITOR_INTERVAL);
                        } catch (Exception e) {

                        }
                    } catch (Exception e) {
                        statusInfo = mPrinter.getStatus();
                        final String msg = makeStatusMassage(statusInfo);
                        runOnUiThread(new Runnable(){
                            public synchronized void run() {
                                mEdtStatusMulti.setText(msg);
                            }
                        });

                        try {
                            Thread.sleep(MONITOR_INTERVAL);
                        } catch (Exception ex) {

                        }
                    }
                }
            }
        });

        return true;
    }

    protected boolean stopGetStatus(){
        if (!mIsMonitoring){
            return false;
        }
        if (mPrinter == null) {
            return false;
        }
        mIsMonitoring = false;
        try {
            mFuture.get(); //wait for task to complete
        } catch (Exception e) {

        }
        return true;
    }

    public String makeStatusMassage(PrinterStatusInfo statusInfo) {
        String msg = "";

        msg += "connection:";
        switch (statusInfo.getConnection()) {
            case Printer.TRUE:
                msg += "CONNECT";
                break;
            case Printer.FALSE:
                msg += "DISCONNECT";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "online:";
        switch (statusInfo.getOnline()) {
            case Printer.TRUE:
                msg += "ONLINE";
                break;
            case Printer.FALSE:
                msg += "OFFLINE";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "coverOpen:";
        switch (statusInfo.getCoverOpen()) {
            case Printer.TRUE:
                msg += "COVER_OPEN";
                break;
            case Printer.FALSE:
                msg += "COVER_CLOSE";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "paper:";
        switch (statusInfo.getPaper()) {
            case Printer.PAPER_OK:
                msg += "PAPER_OK";
                break;
            case Printer.PAPER_NEAR_END:
                msg += "PAPER_NEAR_END";
                break;
            case Printer.PAPER_EMPTY:
                msg += "PAPER_EMPTY";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "paperFeed:";
        switch (statusInfo.getPaperFeed()) {
            case Printer.TRUE:
                msg += "PAPER_FEED";
                break;
            case Printer.FALSE:
                msg += "PAPER_STOP";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "panelSwitch:";
        switch (statusInfo.getPanelSwitch()) {
            case Printer.TRUE:
                msg += "SWITCH_ON";
                break;
            case Printer.FALSE:
                msg += "SWITCH_OFF";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "drawer:";
        switch (statusInfo.getDrawer()) {
            case Printer.DRAWER_HIGH:
                //This status depends on the drawer setting.
                msg += "DRAWER_HIGH(Drawer close)";
                break;
            case Printer.DRAWER_LOW:
                //This status depends on the drawer setting.
                msg += "DRAWER_LOW(Drawer open)";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "errorStatus:";
        switch (statusInfo.getErrorStatus()) {
            case Printer.NO_ERR:
                msg += "NO_ERR";
                break;
            case Printer.MECHANICAL_ERR:
                msg += "MECHANICAL_ERR";
                break;
            case Printer.AUTOCUTTER_ERR:
                msg += "AUTOCUTTER_ERR";
                break;
            case Printer.UNRECOVER_ERR:
                msg += "UNRECOVER_ERR";
                break;
            case Printer.AUTORECOVER_ERR:
                msg += "AUTOCUTTER_ERR";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "autoRecoverErr:";
        switch (statusInfo.getAutoRecoverError()) {
            case Printer.HEAD_OVERHEAT:
                msg += "HEAD_OVERHEAT";
                break;
            case Printer.MOTOR_OVERHEAT:
                msg += "MOTOR_OVERHEAT";
                break;
            case Printer.BATTERY_OVERHEAT:
                msg += "BATTERY_OVERHEAT";
                break;
            case Printer.WRONG_PAPER:
                msg += "WRONG_PAPER";
                break;
            case Printer.COVER_OPEN:
                msg += "COVER_OPEN";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "adapter:";
        switch (statusInfo.getAdapter()) {
            case Printer.TRUE:
                msg += "AC ADAPTER CONNECT";
                break;
            case Printer.FALSE:
                msg += "AC ADAPTER DISCONNECT";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "batteryLevel:";
        switch (statusInfo.getBatteryLevel()) {
            case Printer.BATTERY_LEVEL_0:
                msg += "BATTERY_LEVEL_0";
                break;
            case Printer.BATTERY_LEVEL_1:
                msg += "BATTERY_LEVEL_1";
                break;
            case Printer.BATTERY_LEVEL_2:
                msg += "BATTERY_LEVEL_2";
                break;
            case Printer.BATTERY_LEVEL_3:
                msg += "BATTERY_LEVEL_3";
                break;
            case Printer.BATTERY_LEVEL_4:
                msg += "BATTERY_LEVEL_4";
                break;
            case Printer.BATTERY_LEVEL_5:
                msg += "BATTERY_LEVEL_5";
                break;
            case Printer.BATTERY_LEVEL_6:
                msg += "BATTERY_LEVEL_6";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "removalWaiting:";
        switch (statusInfo.getRemovalWaiting()) {
            case Printer.REMOVAL_WAIT_PAPER:
                msg += "WAITING_FOR_PAPER_REMOVAL";
                break;
            case Printer.REMOVAL_WAIT_NONE:
                msg += "NOT_WAITING_FOR_PAPER_REMOVAL";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "paperTakenSensor:";
        switch (statusInfo.getPaperTakenSensor()) {
            case Printer.REMOVAL_DETECT_PAPER:
                msg += "REMOVAL_DETECT_PAPER";
                break;
            case Printer.REMOVAL_DETECT_PAPER_NONE:
                msg += "REMOVAL_DETECT_PAPER_NONE";
                break;
            case Printer.REMOVAL_DETECT_UNKNOWN:
                msg += "REMOVAL_DETECT_UNKNOWN";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";

        msg += "unrecoverError:";
        switch (statusInfo.getUnrecoverError()) {
            case Printer.HIGH_VOLTAGE_ERR:
                msg += "HIGH_VOLTAGE_ERR";
                break;
            case Printer.LOW_VOLTAGE_ERR:
                msg += "LOW_VOLTAGE_ERR";
                break;
            case Printer.UNKNOWN:
                msg += "UNKNOWN";
                break;
            default:
                break;
        }
        msg += "\n";


        return msg;
    }
}

