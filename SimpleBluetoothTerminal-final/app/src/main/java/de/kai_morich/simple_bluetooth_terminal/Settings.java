package de.kai_morich.simple_bluetooth_terminal;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

public class Settings extends AppCompatActivity {

    public Button Setting_btn1;
    public Button Setting_btn2;
    public Button Setting_btn4;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_settings);

        Setting_btn1 = (Button) findViewById(R.id.setting1);
        Setting_btn2 = (Button) findViewById(R.id.setting2);
        Setting_btn4 = (Button) findViewById(R.id.setting4);

        Setting_btn1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPairingPage();
            }
        });

        Setting_btn2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openConnectionPage();
            }
        });

        Setting_btn4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openBluetoothPage();
            }
        });
    }


    public void openPairingPage(){
        Intent intent = new Intent(this, Pairing.class);
        startActivity(intent);
    }

    public void openConnectionPage(){
        Intent intent = new Intent(this, Connection.class);
        startActivity(intent);
    }

    public void openBluetoothPage(){
        Intent intent = new Intent();
        intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
        startActivity(intent);
    }
}
