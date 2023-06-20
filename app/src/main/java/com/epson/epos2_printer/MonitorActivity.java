package com.epson.epos2_printer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MonitorActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_monitor);

        int[] target = {
                R.id.btnSingleMonitor,
                R.id.btnMultipleMonitor
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
            case R.id.btnSingleMonitor:
                intent = new Intent(this, SingleMonitorActivity.class);
                startActivityForResult(intent, 0);
                break;

            case R.id.btnMultipleMonitor:
                intent = new Intent(this, MultipleMonitorActivity.class);
                startActivityForResult(intent, 0);
                break;

            default:
                // Do nothing
                break;
        }
    }
}
