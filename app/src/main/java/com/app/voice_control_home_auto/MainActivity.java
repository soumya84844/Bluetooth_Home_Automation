package com.app.voice_control_home_auto;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.RecognizerIntent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    Button buttonON, showDevices;
    ImageButton speak;
    ListView deviceList;
    TextView status;
    ArrayList<String> stringArrayList = new ArrayList<String>();
    ArrayAdapter<String> arrayAdapter;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice[] btArray = new BluetoothDevice[100];
    int index = 0;
    Intent btEnablingIntent;
    int requestCodeForEnable;
    private int BLUETOOTH_PERMISSION_CODE = 1;
    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    private static final String APP_NAME = "BTTest";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    OutputStream outputStream;
    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                try {
                    if (!stringArrayList.contains(device.getName())) {
                        stringArrayList.add(device.getName());
                        btArray[index++] = device;
                    }
                    arrayAdapter.notifyDataSetChanged();
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(receiver);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        buttonON = (Button) findViewById(R.id.btON);
        showDevices = (Button) findViewById(R.id.showDevices);
        speak = (ImageButton) findViewById(R.id.speak);
        deviceList = (ListView) findViewById(R.id.deviceList);
        status = (TextView) findViewById(R.id.status);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        btEnablingIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        requestCodeForEnable = 1;

        requestBluetoothScanPermission();
        requestBluetoothConnectPermission();

        bluetoothON();
        onClickShowDevices();
        deviceClickListener();
        onClickSpeak();
    }

    private void onClickSpeak() {
        speak.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Start Speaking");
                startActivityForResult(intent, 100);
            }
        });
    }
    private void deviceClickListener() {
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                ClientClass clientClass = new ClientClass(btArray[i]);
                clientClass.start();
                status.setText("Connecting");
            }
        });
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(@NonNull Message msg) {
            switch (msg.what) {
                case STATE_LISTENING:
                    status.setText("Listening");
                    break;
                case STATE_CONNECTING:
                    status.setText("Connecting");
                    break;
                case STATE_CONNECTED:
                    status.setText("Connected");
                    break;
                case STATE_CONNECTION_FAILED:
                    status.setText("Connection Failed");
                    break;
            }
            return true;
        }
    });

    private void onClickShowDevices() {
        showDevices.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    requestBluetoothScanPermission();
                    return;
                }
                stringArrayList.clear();
                bluetoothAdapter.startDiscovery();


                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                IntentFilter intentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
                registerReceiver(receiver, intentFilter);

                arrayAdapter = new ArrayAdapter<String>(getApplicationContext(), android.R.layout.simple_list_item_1, stringArrayList);
                deviceList.setAdapter(arrayAdapter);
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == requestCodeForEnable) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(getApplicationContext(), "Bluetooth is Enabled", Toast.LENGTH_LONG).show();
            } else if (resultCode == RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Bluetooth Enabling Cancelled", Toast.LENGTH_LONG).show();
            }
        }

        if(requestCode == 100 && resultCode == RESULT_OK) {
            String command = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS).get(0);
            encodeCommand(command);
        }
    }

    private void encodeCommand(String command) {
        status.setText(command);
        String encodedCommand = "";
        if(command.equalsIgnoreCase("fan off") || command.equalsIgnoreCase("fan of")) encodedCommand = "0";
        else if(command.equalsIgnoreCase("fan on") || command.equalsIgnoreCase("speed 5")) encodedCommand = "1";
        else if(command.equalsIgnoreCase("light off") || command.equalsIgnoreCase("light of")) encodedCommand = "2";
        else if(command.equalsIgnoreCase("light on")) encodedCommand = "3";
        else if(command.equalsIgnoreCase("speed 1")) encodedCommand = "4";
        else if(command.equalsIgnoreCase("speed 2")) encodedCommand = "5";
        else if(command.equalsIgnoreCase("speed 3")) encodedCommand = "6";
        else if(command.equalsIgnoreCase("speed 4")) encodedCommand = "7";
        else if(command.equalsIgnoreCase("door open") || command.equalsIgnoreCase("doors open")) encodedCommand = "8";
        else if(command.equalsIgnoreCase("door close") || command.equalsIgnoreCase("doors close")) encodedCommand = "9";
        else if(command.equalsIgnoreCase("plug on")) encodedCommand = "A";
        else if(command.equalsIgnoreCase("plug off") || command.equalsIgnoreCase("plug of")) encodedCommand = "B";
        else status.setText("Command not Recognized");
        sendCommand(encodedCommand + "\r\n");
    }

    private void sendCommand(String command) {
        try {
            outputStream.write(command.getBytes());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void bluetoothON() {
        buttonON.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothTurnON();
                } else {
                    requestBluetoothConnectPermission();
                }
            }
        });
    }

    private void bluetoothTurnON() {
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Bluetooth not Supported", Toast.LENGTH_LONG).show();
        } else {
            if (!bluetoothAdapter.isEnabled()) {
                startActivityForResult(btEnablingIntent, requestCodeForEnable);
            }
        }
    }

    private void requestBluetoothConnectPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_CONNECT)) {
            new AlertDialog.Builder(this).setTitle("Permission needed").setMessage("This permission is needed").setPositiveButton("ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION_CODE);
                }
            }).setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            }).create().show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, BLUETOOTH_PERMISSION_CODE);
        }
    }

    private void requestBluetoothScanPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.BLUETOOTH_SCAN)) {
            new AlertDialog.Builder(this).setTitle("Permission needed").setMessage("This permission is needed").setPositiveButton("ok", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, BLUETOOTH_PERMISSION_CODE);
                }
            }).setNegativeButton("cancel", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialogInterface.dismiss();
                }
            }).create().show();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, BLUETOOTH_PERMISSION_CODE);
        }
    }

    private class ClientClass extends Thread {
        private BluetoothDevice device;
        private BluetoothSocket socket;

        public ClientClass(BluetoothDevice device1) {
            device = device1;

            try {
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                bluetoothAdapter.cancelDiscovery();
                socket.connect();
                outputStream = socket.getOutputStream();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                handler.sendMessage(message);
            }
            catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                handler.sendMessage(message);
            }
        }
    }
}