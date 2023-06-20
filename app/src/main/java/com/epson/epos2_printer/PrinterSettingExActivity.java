package com.epson.epos2_printer;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.epson.epos2.Epos2CallbackCode;
import com.epson.epos2.Epos2Exception;
import com.epson.epos2.printer.Printer;
import com.epson.epos2.printer.VerifyPasswordListener;
import com.epson.epos2.printer.GetPrinterSettingExListener;
import com.epson.epos2.printer.SetPrinterSettingExListener;

import org.json.JSONException;
import org.json.JSONObject;

public class PrinterSettingExActivity extends Activity implements View.OnClickListener, GetPrinterSettingExListener, SetPrinterSettingExListener,VerifyPasswordListener{

    public static final String KEY_SETTING = "PrinterSpec";
    public static final String KEY_PRODUCT = "Product";
    public static final String KEY_SERIALNO = "SerialNo";

    private static final int DISCONNECT_INTERVAL = 500;//millseconds
    private static final int RESTART_INTERVAL = 60000;//millseconds
    private Context mContext = null;
    private Printer mPrinter = null;

    private AlertDialog mProgressDialog = null;
    private TextView mProgressDialogMessage = null;

    private EditText mEditPassword = null;
    private EditText mEditText = null;

    private Boolean mUpdatePassword = Boolean.FALSE;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_printersetting_ex);

        mContext = this;
        mEditPassword = findViewById(R.id.edtAdministratorPassword);
        mEditText = findViewById(R.id.edtJsonText);
        final int[] target = {
                R.id.btnGetPrinterSettingEx,
                R.id.btnSetPrinterSettingEx
        };

        for (int i = 0; i < target.length; i++) {
            Button button = (Button)findViewById(target[i]);
            button.setOnClickListener(this);
            button.setEnabled(false);
        }

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
                        for (int i = 0; i < target.length; i++) {
                            Button button = (Button)findViewById(target[i]);
                            button.setOnClickListener((View.OnClickListener) mContext);
                            button.setEnabled(true);
                        }
                    }
                });
            }
        }).start();
    }

    public void onDestroy() {
        disconnectPrinter();
        finalizeObject();
        super.onDestroy();
    }
    public void onClick(View v) {

        switch (v.getId()) {
            case R.id.btnGetPrinterSettingEx:
                getPrinterSettingEx();
                break;
            case R.id.btnSetPrinterSettingEx:
                verifyPasswordAndSetSetting();
                break;
            default:
                break;
        }
    }

    // Get printer settings at once.
    protected void getPrinterSettingEx() {
        mUpdatePassword = Boolean.TRUE;
        try {
            mPrinter.getPrinterSettingEx(Printer.PARAM_DEFAULT);
            beginProgress(getString(R.string.get_setting_ex_msg));
        }
        catch(Exception e) {
            ShowMsg.showException(e, "getPrinterSettingEx", mContext);
        }
    }

    protected void verifyPasswordAndSetSetting() {
        try {
            mPrinter.verifyPassword(Printer.PARAM_DEFAULT, mEditPassword.getText().toString());

            // Show wating restart message. onVerifyPassword stop this message.
            beginProgress(getString(R.string.verify_password_msg));
        }
        catch(Exception e) {
            ShowMsg.showException(e, "verifyPassword", mContext);
        }
    }

    // Set printer settings at once.
    protected void setPrinterSettingEx(String password) {
        try {
            mPrinter.setPrinterSettingEx(Printer.PARAM_DEFAULT, mEditText.getText().toString(),password);

            // Show wating restart message. onSetPrinterSetting stop this message.
            beginProgress(getString(R.string.restart_SettingEx_msg));
        }
        catch(Exception e) {
            ShowMsg.showException(e, "setPrinterSettingEx", mContext);
        }
    }
    public void onVerifyPassword(Printer printer, final int code) {
        runOnUiThread(new Runnable() {
            public synchronized void run() {
                endProgress();
                if ((code != Epos2CallbackCode.CODE_SUCCESS) && (code != Epos2CallbackCode.CODE_NO_PASSWORD)) {
                    ShowMsg.showResult(code, "onVerifyPassword", mContext);
                    return;
                }
                String password = mEditPassword.getText().toString();
                if (code == Epos2CallbackCode.CODE_NO_PASSWORD) {
                    password = null;
                }

                setPrinterSettingEx(password);

            }
        });
    }

    public void onGetPrinterSettingEx(Printer printer, final int code, final String jsonString) {
        runOnUiThread(new Runnable() {
            public synchronized void run() {
                endProgress();
                if(code != Epos2CallbackCode.CODE_SUCCESS) {
                    ShowMsg.showResult(code, "onGetPrinterSettingEx", mContext);
                    return;
                }
                JSONObject json = null;
                try {
                    json = new JSONObject(jsonString);
                    mEditText.setText(json.toString(4));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                if(mUpdatePassword){
                    try {
                        String serialNumber = json.getJSONObject(KEY_SETTING).getJSONObject(KEY_PRODUCT).getString(KEY_SERIALNO);
                        mEditPassword.setText(serialNumber);
                    }catch(Exception e){
                        e.printStackTrace();
                    }
                }


            }
        });
    }

    public void onSetPrinterSettingEx(Printer printer, final int code) {
        runOnUiThread(new Runnable() {
            public synchronized void run() {

                if(code != Epos2CallbackCode.CODE_SUCCESS) {
                    endProgress();
                    ShowMsg.showResult(code, "onSetPrinterSettingEx", mContext);
                    return;
                }
                mEditText.setText("");
                new Thread(new Runnable() {
                    public void run() {
                        reconnectPrinter_And_GetPrinterSetting();
                    }
                }).start();
            }
        });
    }

    protected void reconnectPrinter_And_GetPrinterSetting() {
        // Reconect printer with 3 sec interval until 120 sec done.
        boolean ret = reconnect(120, 3);

        // If error, back connection setting view to confirm connection setting.
        if(!ret) {
            runOnUiThread(new Runnable() {
                public synchronized void run() {
                    String msg = String.format(
                            "\t%s\n\t%s\n\n\t%s\n\t%s\n",
                            mContext.getString(R.string.title_msg_result),
                            "ERR_CONNECT",
                            mContext.getString(R.string.title_msg_description),
                            "reconnect");
                    ShowMsg.showMsg(msg, mContext);
                    endProgress();
                }
            });
            return;
        }

        mUpdatePassword = Boolean.FALSE;
        // When restart success, get current setting to confirmation.
        try {
            mPrinter.getPrinterSettingEx(Printer.PARAM_DEFAULT);
        }
        catch(Exception e) {
            ShowMsg.showException(e, "getPrinterSettingEx", mContext);
            return;
        }

        //Wait onGetPrinterSetting callback to avoid multiple API calling.
        try {
            Thread.sleep(200);
        }
        catch(InterruptedException e) {
        }

    }

    protected boolean reconnect(int maxWait, int interval) {
        int timeout = 0;
        do{
            try {
                mPrinter.disconnect();
                break;
            }
            catch(Exception e) {
                try {
                    Thread.sleep(interval*1000);
                }
                catch(InterruptedException ex) {
                }
            }
            timeout += interval;    // Not correct measuring. When result is not TIMEOUT, wraptime do not equal interval.

            if(timeout > maxWait) {
                return false;
            }
        }while(true);


        // Sleep RESTART_INTERVAL sec due to some printer do not available immediately after power on. see your printer's spec sheet.
        // Please set the sleep time according to the printer.
        try {
            Thread.sleep(RESTART_INTERVAL);
        }
        catch(InterruptedException e) {
        }

        timeout += 30;


        do {
            try {
                // For USB, change the target to "USB:".
                // Because USB port changes each time the printer restarts.
                // Please refer to the manual for details.
                mPrinter.connect(MainActivity.mEditTarget.getText().toString(), interval*1000);
                break;
            }
            catch (Exception e) {
                if(((Epos2Exception) e).getErrorStatus()== Epos2Exception.ERR_CONNECT){
                    try {
                        Thread.sleep(interval*1000);
                    }
                    catch(InterruptedException ex) {
                    }
                }
            }

            timeout += interval;    // Not correct measuring. When result is not TIMEOUT, wraptime do not equal interval.

            if(timeout > maxWait) {
                return false;
            }
        }while(true);

        return true;
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
        mPrinter.setVerifyPasswordListener(this);
        mPrinter.setGetPrinterSettingExListener(this);
        mPrinter.setSetPrinterSettingExListener(this);

        return true;
    }

    private void finalizeObject() {
        if (mPrinter == null) {
            return;
        }

        mPrinter.setVerifyPasswordListener(null);
        mPrinter.setGetPrinterSettingExListener(null);
        mPrinter.setSetPrinterSettingExListener(null);
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
                if(mProgressDialog != null) {
                    mProgressDialog.dismiss();
                    mProgressDialog = null;
                }
            }
        });

    }

}
