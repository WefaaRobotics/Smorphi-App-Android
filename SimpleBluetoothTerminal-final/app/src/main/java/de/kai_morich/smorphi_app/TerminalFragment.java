package de.kai_morich.smorphi_app;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Random;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    enum Connected { False, Pending, True }

    public String deviceAddress;
    public SerialService service;

    public TextView receiveText;
    public TextView sendText;
    public TextUtil.HexWatcher hexWatcher;

    public Connected connected = Connected.False;
    public boolean initialStart = true;
    public boolean hexEnabled = false;
    public boolean pendingNewline = false;
    public String newline = TextUtil.newline_crlf;

    private static TerminalFragment instance;

    public int global_strength = 0;
    public int global_angle = 0;
    public int turn_left_flag = 2;
    public int turn_right_flag = 2;
    public int count = 0;
    public int old_global_strength = 0;
    public int old_global_angle = 0;


    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            getActivity().startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        instance = this;

        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
//        receiveText = view.findViewById(R.id.receive_text);                          // TextView performance decreases with number of spans
//        receiveText.setTextColor(getResources().getColor(R.color.colorRecieveText)); // set as default color to reduce number of spans
//        receiveText.setMovementMethod(ScrollingMovementMethod.getInstance());

//        sendText = view.findViewById(R.id.send_text);
//        hexWatcher = new TextUtil.HexWatcher(sendText);
//        hexWatcher.enable(hexEnabled);
//        sendText.addTextChangedListener(hexWatcher);
//        sendText.setHint(hexEnabled ? "HEX mode" : "");


        View sendBtn_O = view.findViewById(R.id.o);
        sendBtn_O.setOnClickListener(v -> send("shape_o"));

        View sendBtn_I = view.findViewById(R.id.i);
        sendBtn_I.setOnClickListener(v -> send("shape_i"));

        View sendBtn_L = view.findViewById(R.id.l);
        sendBtn_L.setOnClickListener(v -> send("shape_l"));

        View sendBtn_Z = view.findViewById(R.id.z);
        sendBtn_Z.setOnClickListener(v -> send("shape_z"));

        View sendBtn_S = view.findViewById(R.id.s);
        sendBtn_S.setOnClickListener(v -> send("shape_s"));

        View sendBtn_T = view.findViewById(R.id.t);
        sendBtn_T.setOnClickListener(v -> send("shape_t"));

        View sendBtn_J = view.findViewById(R.id.j);
        sendBtn_J.setOnClickListener(v -> send("shape_j"));

        View shapeBtn1 = view.findViewById(R.id.shape1);
        shapeBtn1.setOnClickListener(v -> send("shape_1"));

        View shapeBtn_2 = view.findViewById(R.id.shape2);
        shapeBtn_2.setOnClickListener(v -> send("shape_2"));

        View turnLBtn = view.findViewById(R.id.antiClock);
        turnLBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    turn_left_flag = 1;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    turn_left_flag = 0;
                }
                return true;
            }
        });

        View turnRBtn = view.findViewById(R.id.clock);
        turnRBtn.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if(event.getAction() == MotionEvent.ACTION_DOWN) {
                    turn_right_flag = 1;
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    turn_right_flag = 0;
                }
                return true;
            }
        });

        JoystickView joystickLeft = view.findViewById(R.id.joystickView_left);
        joystickLeft.setOnMoveListener(new JoystickView.OnMoveListener() {
            @Override
            public void onMove(int angle, int strength) {
                Log.d("Check_strength", String.valueOf(strength));
                Log.d("Check_angle", String.valueOf(angle));
                global_strength = strength;
                global_angle = angle;

            }
        });

        Random rand = new Random();
        Handler handler = new Handler();

        Runnable r=new Runnable() {
            public void run() {
//                int value = rand.nextInt(10);
                handler.postDelayed(this,1);

                if(turn_left_flag == 1){
                    send("pivot_left");
                    turn_left_flag = 2;
                }
                else if (turn_left_flag == 0){
                    send("st");
                    turn_left_flag = 2;
                }

                if(turn_right_flag == 1){
                    send("pivot_right");
                    turn_right_flag = 2;
                }
                else if (turn_right_flag == 0){
                    send("st");
                    turn_right_flag = 2;
                }

                if(global_strength > 0){
                    if(global_angle>=67.5 && global_angle<112.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("forwardslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("forwardmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("forwardmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("forwardfast");
                                old_global_strength = global_strength;
                            }
                        }

                    }



                    else if(global_angle>=157.5 && global_angle<202.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("leftslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("leftmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("leftmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("leftfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }


                    else if(global_angle>=337.5 || global_angle<=22.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("rightslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("rightmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("rightmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("rightfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }


                    else if(global_angle>=247.5 && global_angle<292.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("backwardslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("backwardmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("backwardmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("backwardfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }

                    else if(global_angle>=112.5 && global_angle<157.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("upleftslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("upleftmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("upleftmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("upleftfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }

                    else if(global_angle>=22.5 && global_angle<67.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("uprightslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("uprightmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("uprightmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("uprightfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }

                    else if(global_angle>=202.5 && global_angle<247.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("downleftslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("downleftmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("downleftmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("downleftfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }

                    else if(global_angle>=292.5 && global_angle<337.5){
                        if (global_strength <= 25 && global_strength > 0 ){
                            if (global_strength != old_global_strength) {
                                send("downrightslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 25 && global_strength<=50){
                            if (global_strength != old_global_strength) {
                                send("downrightmidslow");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 50 && global_strength<=75){
                            if (global_strength != old_global_strength) {
                                send("downrightmidfast");
                                old_global_strength = global_strength;
                            }
                        }
                        else if (global_strength > 75 && global_strength<=100){
                            if (global_strength != old_global_strength) {
                                send("downrightfast");
                                old_global_strength = global_strength;
                            }
                        }
                    }

                }


                else if (global_strength == 0){
                    if (global_strength != old_global_strength) {
                        Log.d("Check_stop", "stop!!! ");
                        send("st");
                        old_global_strength = global_strength;
                    }
                }
            }
        };
        handler.postDelayed(r, 1);


        return view;
    }

    public static TerminalFragment getInstance(){
        return instance;
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
//        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.reconnect) {
            connect();
            return true;
        }

        if (id == R.id.settings) {
            Intent intent = new Intent(service.getApplicationContext(), Settings.class);
            startActivity(intent);
            return true;
        }


        else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    public void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            Toast.makeText(service.getApplicationContext(), "Connecting...", Toast.LENGTH_SHORT).show();
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(getActivity().getApplicationContext(), device);
            service.connect(socket);

            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    if(connected == Connected.True){
                        Toast.makeText(service.getApplicationContext(), "Connected!!", Toast.LENGTH_SHORT).show();
                    }
                }
            }, 5000);

        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    public void disconnect() {
        connected = Connected.False;
        service.disconnect();
    }

    public void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Log.d("Check_send", "sending!!!!");
            String msg;
            byte[] data;
//            if(hexEnabled) {
//                StringBuilder sb = new StringBuilder();
//                TextUtil.toHexString(sb, TextUtil.fromHexString(str));
//                TextUtil.toHexString(sb, newline.getBytes());
//                msg = sb.toString();
//                data = TextUtil.fromHexString(msg);
//            }
//            else {
            msg = str;
//            data = (str+newline).getBytes();
            data = (str).getBytes();
//            }

//            SpannableStringBuilder spn = new SpannableStringBuilder(msg + '\n');
//            spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorSendText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//            receiveText.append(spn);
            service.write(data);
            Log.d("Check_send", String.valueOf(msg));
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

//    public void receive(byte[] data) {
//        if(hexEnabled) {
//            receiveText.append(TextUtil.toHexString(data) + '\n');
//        }
//        else {
//            String msg = new String(data);
//            if(newline.equals(TextUtil.newline_crlf) && msg.length() > 0) {
//                // don't show CR as ^M if directly before LF
//                msg = msg.replace(TextUtil.newline_crlf, TextUtil.newline_lf);
//                // special handling if CR and LF come in separate fragments
//                if (pendingNewline && msg.charAt(0) == '\n') {
//                    Editable edt = receiveText.getEditableText();
//                    if (edt != null && edt.length() > 1)
//                        edt.replace(edt.length() - 2, edt.length(), "");
//                }
//                pendingNewline = msg.charAt(msg.length() - 1) == '\r';
//            }
//            receiveText.append(TextUtil.toCaretString(msg, newline.length() != 0));
//        }
//    }

    public void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str + '\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
//        receiveText.append(spn);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;

    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
//        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
