package de.kai_morich.smorphi_app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class HomeActivity extends AppCompatActivity {

    private Button pair_new_button;
    private Button pair_old_button;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_starting_page);

        pair_new_button = (Button) findViewById(R.id.pair_new);
        pair_new_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openPairingPage();
            }
        });

        pair_old_button = (Button) findViewById(R.id.pair_old);
        pair_old_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openConnectionPage();
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
}