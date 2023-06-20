package com.epson.epos2_printer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class SettingActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        int[] target = {
                R.id.btnPrinterSettingEx,
                R.id.btnMaintenanceCounterPrinterSetting,
                R.id.btnFirmwareUpdate
        };

        for (int i = 0; i < target.length; i++) {
            Button button = (Button)findViewById(target[i]);
            button.setOnClickListener(this);
        }
    }

    @Override
    public void onClick(View v) {
        Intent intent = null;

        switch (v.getId()) {
            case R.id.btnPrinterSettingEx:
                intent = new Intent(this, PrinterSettingExActivity.class);
                startActivityForResult(intent, 0);
                break;

            case R.id.btnMaintenanceCounterPrinterSetting:
                intent = new Intent(this, PrinterSettingActivity.class);
                startActivityForResult(intent, 0);
                break;

            case R.id.btnFirmwareUpdate:
                intent = new Intent(this, FirmwareUpdateActivity.class);
                startActivityForResult(intent, 0);
                break;

            default:
                // Do nothing
                break;
        }
    }

}
