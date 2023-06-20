package com.epson.epos2_printer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.epson.epos2.Epos2Exception;
import com.epson.epos2.printer.Printer;
import com.epson.epos2.printer.FirmwareInfo;
import com.epson.epos2.printer.FirmwareUpdateListener;
import com.epson.epos2.Epos2CallbackCode;


import java.util.ArrayList;

public class FirmwareUpdateActivity extends Activity implements View.OnClickListener, AdapterView.OnItemSelectedListener, FirmwareUpdateListener {

    private static final int DISCONNECT_INTERVAL = 500;//millseconds

    private Context mContext = null;
    private Printer mPrinter = null;
    private FirmwareUpdateListener mFirmwareUpdateListener = null;

    private TextView mTextCurrentFirmware = null;
    private EditText mEditPrinterModel = null;
    private EditText mEditOption = null;
    private Spinner mSpnFirmwareList = null;

    private Button mBtnGetPrinterFirmware = null;
    private Button mBtnDownloadFirmwareList = null;
    private Button mBtnUpdateFirmware = null;

    private AlertDialog mProgressDialog = null;
    private TextView mProgressDialogMessage = null;

    private FirmwareInfo[] firmwareInfoList = null;
    private FirmwareInfo targetFirmwareInfo = null;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firmwareupdate);

        mContext = this;
        mFirmwareUpdateListener = this;

        mTextCurrentFirmware = (TextView)findViewById(R.id.textCurrentFirmware);
        mEditPrinterModel = (EditText)findViewById(R.id.edtPrinterModel);
        mEditOption = (EditText)findViewById(R.id.edtOption);
        mSpnFirmwareList = (Spinner)findViewById(R.id.spnFirmwareList);
        mBtnGetPrinterFirmware = (Button)findViewById(R.id.btnGetPrinterFirmware);
        mBtnDownloadFirmwareList = (Button)findViewById(R.id.btnDownloadFirmwareList);
        mBtnUpdateFirmware = (Button)findViewById(R.id.btnUpdateFirmware);

        ArrayAdapter<SpnModelsItem> listAdapter = new ArrayAdapter<SpnModelsItem>(this, android.R.layout.simple_spinner_item);
        listAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        listAdapter.add(new SpnModelsItem("-", 0));
        mSpnFirmwareList.setAdapter(listAdapter);
        mSpnFirmwareList.setSelection(0);
        mSpnFirmwareList.setOnItemSelectedListener(this);
        mBtnGetPrinterFirmware.setOnClickListener(this);
        mBtnDownloadFirmwareList.setOnClickListener(this);
        mBtnUpdateFirmware.setOnClickListener(this);

        mSpnFirmwareList.setEnabled(false);
        mBtnGetPrinterFirmware.setEnabled(false);
        mBtnUpdateFirmware.setEnabled(false);

        if(MainActivity.mEditTarget.getText().toString().contains("[")) {
            ShowMsg.showMsg(getString(R.string.error_msg_firm_update), mContext);
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                if(!initializeObject()) {
                    return;
                }

                if(!connectPrinter()) {
                    finalizeObject();
                    return;
                }

                runOnUiThread(new Runnable(){
                    @Override
                    public void run() {
                        mBtnGetPrinterFirmware.setEnabled(true);
                    }
                });
            }
        }).start();




    }

    public void onDestroy() {
        disconnectPrinter();
        finalizeObject();

        mContext = null;
        mPrinter = null;
        mFirmwareUpdateListener = null;

        super.onDestroy();
    }

    @Override
    public void onClick(final View v) {
        switch (v.getId()) {
            case R.id.btnGetPrinterFirmware:
                getPrinterFirmware();
                break;

            case R.id.btnDownloadFirmwareList:
                downloadFirmwareList();
                break;

            case R.id.btnUpdateFirmware:
                updateFirmware();
                break;

            default:
                // Do nothing
                break;
        }
    }

    private void getPrinterFirmware() {
        beginProgress(getString(R.string.progress_msg));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mPrinter.getPrinterFirmwareInfo(60000, mFirmwareUpdateListener);
                } catch(Exception e) {
                    endProgress();
                    ShowMsg.showException(e, "getPrinterFirmware", mContext);
                }

            }
        }).start();
    }

    private void downloadFirmwareList() {
        beginProgress(getString(R.string.progress_msg));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mPrinter.downloadFirmwareList(mEditPrinterModel.getText().toString(),mEditOption.getText().toString(), mFirmwareUpdateListener);
                } catch (Exception e) {
                    endProgress();
                    ShowMsg.showException(e, "downloadFirmwareList", mContext);
                }
            }
        }).start();
    }

    private void updateFirmware() {
        if(targetFirmwareInfo == null) {
            return ;
        }

        beginProgress(getString(R.string.progress_msg));

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mPrinter.updateFirmware(targetFirmwareInfo, mFirmwareUpdateListener, mContext);
                } catch (Exception e) {
                    endProgress();
                    ShowMsg.showException(e, "updateFirmware", mContext);
                }
            }
        }).start();
    }

    private void verifyUpdate(final FirmwareInfo targetFirmwareInfo) {
        if(targetFirmwareInfo == null) {
            endProgress();
            return ;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mPrinter.verifyUpdate(targetFirmwareInfo, mFirmwareUpdateListener);
                } catch (Exception e) {
                    endProgress();
                    ShowMsg.showException(e, "verifyUpdate", mContext);
                }
            }
        }).start();
    }

    @Override
    public void onReceiveFirmwareInformation(FirmwareInfo firmwareInfo) {
        if(firmwareInfo == null) {
            endProgress();
            return;
        }

        setCurrentFirmwareText(firmwareInfo.getVersion());

        endProgress();
    }

    @Override
    public void onDownloadFirmwareList(int code, FirmwareInfo[] firmwareList) {
        if(code != Epos2CallbackCode.CODE_SUCCESS) {
            endProgress();
            ShowMsg.showResult(code, "onDownloadFirmwareList", mContext);
            return;
        }

        if(firmwareList == null) {
            endProgress();
            return;
        }

        firmwareInfoList = firmwareList;
        updateFirmwareList();

        endProgress();
    }

    @Override
    public void onUpdateFirmware(int code, int maxWaitTime) {
        if(code != Epos2CallbackCode.CODE_SUCCESS) {
            endProgress();
            ShowMsg.showResult(code, "onUpdateFirmware", mContext);
            return;
        }

        updateWaitingMessage(getString(R.string.reconnect_message));

        disconnectPrinter();

        try{
            Thread.sleep(maxWaitTime * 1000);
        }catch (Exception e){
            ;
        }

        if(!connectPrinter()) {
            endProgress();
            return;
        }

        updateWaitingMessage(getString(R.string.verify_message));

        // Verify firmware version.
        verifyUpdate(targetFirmwareInfo);
    }

    @Override
    public void onFirmwareUpdateProgress(String task, float progress){
        updateWaitingMessage(task, progress*100);
    }

    @Override
    public void onUpdateVerify(int code) {
        endProgress();
        ShowMsg.showResult(code, "onUpdateFirmware", mContext);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if(parent.getId() == R.id.spnFirmwareList) {
            if(firmwareInfoList != null){
                targetFirmwareInfo = firmwareInfoList[position];
            } else {
                targetFirmwareInfo = null;
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        ;
    }

    // Printer control
    private boolean initializeObject() {
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

    private void finalizeObject() {
        if (mPrinter == null) {
            return;
        }

        mPrinter = null;
    }

    protected boolean connectPrinter() {
        if (mPrinter == null) {
            return false;
        }

        try {
            // For USB, change the target to "USB:".
            // Because USB port changes each time the printer restarts.
            // Please refer to the manual for details.
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

    // View control
    private void updateWaitingMessage(final String msg) {
        String progressMsg = String.format("%s\n\n%s", msg, notesMessage());
        changeProgress(progressMsg);
    }

    private void updateWaitingMessage(final String msg, final float count) {
        String progressMsg = String.format("%s: %3.2f%%\n\n%s", msg, count, notesMessage());
        changeProgress(progressMsg);
    }

    private void setCurrentFirmwareText(final String version) {
        runOnUiThread(new Runnable() {
            public synchronized void run() {

                mTextCurrentFirmware.setText(version);
            }
        });
    }

    private void updateFirmwareList() {
        runOnUiThread(new Runnable() {
            public synchronized void run() {

                ArrayList<String> firmwareVersionList = new ArrayList<String>();
                ArrayAdapter<SpnModelsItem> listAdapter = new ArrayAdapter<SpnModelsItem>(mContext, android.R.layout.simple_spinner_item);
                listAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                int firmwareListCount = firmwareInfoList.length;
                for (int i = 0; i < firmwareListCount; i++) {
                    String version = firmwareInfoList[i].getVersion();
                    firmwareVersionList.add(version);
                    listAdapter.add(new SpnModelsItem(version, i));
                }

                mSpnFirmwareList.setAdapter(listAdapter);
                mSpnFirmwareList.setSelection(0);
                mSpnFirmwareList.setEnabled(true);
                mBtnUpdateFirmware.setEnabled(true);
            }
        });
    }

    private String notesMessage() {
        String ss = String.format("1. %s\n", getString(R.string.note1_1));
        ss += String.format("   %s\n", getString(R.string.note1_2));
        ss += String.format("   %s\n\n", getString(R.string.note1_3));
        ss += String.format("2. %s\n\n", getString(R.string.note2));
        ss += String.format("3. %s\n\n", getString(R.string.note3));
        ss += String.format("4. %s\n\n", getString(R.string.note4));
        ss += String.format("5. %s\n\n", getString(R.string.note5));
        ss += String.format("6. %s\n\n", getString(R.string.note6));
        ss += String.format("7. %s\n\n", getString(R.string.note7));
        ss += String.format("8. %s\n\n", getString(R.string.note8));
        ss += String.format("9. %s\n\n", getString(R.string.note9));
        return ss;
    }

    // Indicator
    // Begin progress dialog
    protected void beginProgress(final String msg) {
        if(mProgressDialog != null){
            changeProgress(msg);
            return;
        }

        runOnUiThread(new Runnable() {
            public synchronized void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
                LayoutInflater inflater = LayoutInflater.from(mContext);
                View view = inflater.inflate(R.layout.dialog_progress, null);
                mProgressDialogMessage = view.findViewById(R.id.progressMessage);
                builder.setView(view);
                mProgressDialog = builder.create();
                mProgressDialog.setCancelable(false);
                mProgressDialogMessage.setText(msg);
                mProgressDialog.show();
            }
        });
    }

    // Change progress dialog
    protected void changeProgress(final String msg) {
        runOnUiThread(new Runnable() {
            public synchronized void run() {
                mProgressDialogMessage.setText(msg);
            }
        });
    }

    // End progress dialog
    protected void endProgress() {
        runOnUiThread(new Runnable() {
            public synchronized void run() {
                mProgressDialog.dismiss();
                mProgressDialog = null;
            }
        });
    }
}
