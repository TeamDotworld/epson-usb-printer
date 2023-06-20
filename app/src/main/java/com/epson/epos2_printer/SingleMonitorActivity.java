package com.epson.epos2_printer;


import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.epson.epos2.Epos2Exception;
import com.epson.epos2.printer.Printer;
import com.epson.epos2.printer.StatusChangeListener;

public class SingleMonitorActivity  extends Activity implements View.OnClickListener,StatusChangeListener{

    private static final int DISCONNECT_INTERVAL = 500;//millseconds
    private Context mContext = null;

    private Button mBtnStartMonitor = null;
    private Button mBtnStopMonitor = null;
    public static EditText mEdtStatus = null;

    private Printer mPrinter = null;
    private Boolean mIsMonitoring = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_singlemonitor);

        mContext = this;

        mBtnStartMonitor= (Button)findViewById(R.id.btnStartMonitor);
        mBtnStartMonitor.setOnClickListener(this);
        mBtnStartMonitor.setEnabled(true);

        mBtnStopMonitor= (Button)findViewById(R.id.btnStopMonitor);
        mBtnStopMonitor.setOnClickListener(this);
        mBtnStopMonitor.setEnabled(false);

        mEdtStatus = (EditText)findViewById(R.id.edtStatus);
        mEdtStatus.setText("");

        initializeObject();

    }
    public void onDestroy() {
        if(mIsMonitoring) {
            if (stopMonitorPrinter()) {
                mIsMonitoring = false;
            }

            disconnectPrinter();
        }
        finalizeObject();
        super.onDestroy();
    }

    @Override
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btnStartMonitor:

                if (!connectPrinter()) {
                    return;
                }
                if (!startMonitorPrinter()) {
                    disconnectPrinter();
                    return;
                }
                mIsMonitoring = true;
                break;

            case R.id.btnStopMonitor:

                if (!stopMonitorPrinter()) {
                    return;
                }

                disconnectPrinter();
                mIsMonitoring = false;
                break;

            default:
                // Do nothing
                break;
        }

        if(mIsMonitoring){
            mBtnStartMonitor.setEnabled(false);
            mBtnStopMonitor.setEnabled(true);
        }else{
            mBtnStartMonitor.setEnabled(true);
            mBtnStopMonitor.setEnabled(false);
        }
    }

    @Override
    public void onPtrStatusChange(Printer printerObj, int eventType){
        final int event = eventType;
        runOnUiThread(new Runnable(){
            public synchronized void run() {
                mEdtStatus.append(makeStatusMassage(event));
            }

        });

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

        mPrinter.setStatusChangeEventListener(this);
        return true;
    }

    protected void finalizeObject() {
        if (mPrinter == null) {
            return;
        }

        mPrinter.setStatusChangeEventListener(null);
        mPrinter = null;

    }

    protected boolean connectPrinter(){
        if (mPrinter == null) {
            return false;
        }

        try {
            mPrinter.connect(MainActivity.mEditTarget.getText().toString(), Printer.PARAM_DEFAULT);
        }
        catch (Exception e) {
            ShowMsg.showException(e, "connect", mContext);
            return false;
        }
        return true;
    }

    protected void disconnectPrinter() {
        if (mPrinter == null) {
            return;
        }

        while (true) {
            try {
                mPrinter.disconnect();
                break;
            } catch (final Exception e) {
                if (e instanceof Epos2Exception) {
                    //Note: If printer is processing such as printing and so on, the disconnect API returns ERR_PROCESSING.
                    if (((Epos2Exception) e).getErrorStatus() == Epos2Exception.ERR_PROCESSING) {
                        try {
                            Thread.sleep(DISCONNECT_INTERVAL);
                        } catch (Exception ex) {
                        }
                    }else{
                        runOnUiThread(new Runnable() {
                            public synchronized void run() {
                                ShowMsg.showException(e, "disconnect", mContext);
                            }
                        });
                        break;
                    }
                }else{
                    runOnUiThread(new Runnable() {
                        public synchronized void run() {
                            ShowMsg.showException(e, "disconnect", mContext);
                        }
                    });
                    break;
                }
            }
        }
    }

    protected boolean startMonitorPrinter(){
        if (mPrinter == null) {
            return false;
        }

        try {
            mPrinter.startMonitor();
        }
        catch (Exception e) {
            ShowMsg.showException(e, "startMonitor", mContext);
            return false;
        }
        return true;
    }

    protected boolean stopMonitorPrinter(){
        if (mPrinter == null) {
            return false;
        }

        try {
            mPrinter.stopMonitor();
        }
        catch (Exception e) {
            ShowMsg.showException(e, "stopMonitor", mContext);
            return false;
        }

        return true;
    }

    public String makeStatusMassage(int type) {
        String msg = "";

        switch (type) {
            case Printer.EVENT_ONLINE:
                msg += "ONLINE";
                break;
            case Printer.EVENT_OFFLINE:
                msg += "OFFLINE";
                break;
            case Printer.EVENT_POWER_OFF:
                msg += "POWER_OFF";
                break;
            case Printer.EVENT_COVER_CLOSE:
                msg += "COVER_CLOSE";
                break;
            case Printer.EVENT_COVER_OPEN:
                msg += "COVER_OPEN";
                break;
            case Printer.EVENT_PAPER_OK:
                msg += "PAPER_OK";
                break;
            case Printer.EVENT_PAPER_NEAR_END:
                msg += "PAPER_NEAR_END";
                break;
            case Printer.EVENT_PAPER_EMPTY:
                msg += "PAPER_EMPTY";
                break;
            case Printer.EVENT_DRAWER_HIGH:
                //This status depends on the drawer setting.
                msg += "DRAWER_HIGH(Drawer close)";
                break;
            case Printer.EVENT_DRAWER_LOW:
                //This status depends on the drawer setting.
                msg += "DRAWER_LOW(Drawer open)";
                break;
            case Printer.EVENT_BATTERY_ENOUGH:
                msg += "BATTERY_ENOUGH";
                break;
            case Printer.EVENT_BATTERY_EMPTY:
                msg += "BATTERY_EMPTY";
                break;
            case Printer.EVENT_REMOVAL_WAIT_PAPER:
                msg += "WAITING_FOR_PAPER_REMOVAL";
                break;
            case Printer.EVENT_REMOVAL_WAIT_NONE:
                msg += "NOT_WAITING_FOR_PAPER_REMOVAL";
                break;
            case Printer.EVENT_REMOVAL_DETECT_PAPER:
                msg += "REMOVAL_DETECT_PAPER";
                break;
            case Printer.EVENT_REMOVAL_DETECT_PAPER_NONE:
                msg += "REMOVAL_DETECT_PAPER_NONE";
                break;
            case Printer.EVENT_REMOVAL_DETECT_UNKNOWN:
                msg += "REMOVAL_DETECT_UNKNOWN";
                break;
            case Printer.EVENT_AUTO_RECOVER_ERROR:
                msg += "AUTO_RECOVER_ERROR";
                break;
            case Printer.EVENT_AUTO_RECOVER_OK:
                msg += "AUTO_RECOVER_OK";
                break;
            case Printer.EVENT_UNRECOVERABLE_ERROR:
                msg += "UNRECOVERABLE_ERROR";
                break;
            default:
                break;
        }
        msg += "\n";
        return msg;
    }
}
