package www.esp32totp.com;

import android.app.PendingIntent;
import android.content.*;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.*;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.hoho.android.usbserial.driver.*;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

public class MainActivity extends AppCompatActivity implements SerialInputOutputManager.Listener {

    private static final String ACTION_USB_PERMISSION = "www.esp32totp.USB_PERMISSION";

    private static final int ESP_VID       = 0x303A;
    private static final int ESP_PID_C3    = 0x1001;
    private static final int ESP_PID_S2_S3 = 0x0002;

    private UsbManager               usbManager;
    private UsbSerialPort            serialPort;
    private SerialInputOutputManager ioManager;
    private boolean                  isOpening     = false;
    private UsbDevice                pendingDevice = null;

    private enum PendingOp { NONE, LIST_FOR_REMOVE }
    private PendingOp pendingOp = PendingOp.NONE;

    private final StringBuilder serialBuffer = new StringBuilder();

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    private void syncTime() {
        if (serialPort == null) return;
        long unixSeconds = System.currentTimeMillis() / 1000L;
        sendCommand("TIME " + unixSeconds + "\n");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);

        Button connectBtn   = findViewById(R.id.connectButton);
        Button createNewBtn = findViewById(R.id.createNewButton);
        Button removeBtn    = findViewById(R.id.removeButton);
        Button wipeBtn      = findViewById(R.id.wipeButton);
        TextView githubLink = findViewById(R.id.githubLink);

        connectBtn.setOnClickListener(v -> connectUsb());
        createNewBtn.setOnClickListener(v -> showCreateAccountDialog());

        removeBtn.setOnClickListener(v -> {
            if (serialPort == null) {
                Toast.makeText(this, "Not connected — tap Connect first", Toast.LENGTH_SHORT).show();
                return;
            }
            pendingOp = PendingOp.LIST_FOR_REMOVE;
            sendCommand("LIST\n");
        });

        wipeBtn.setOnClickListener(v -> showWipeConfirmDialog());

        // Opens the GitHub page in the browser
        githubLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/filippo-carta"));
            startActivity(browserIntent);
        });

        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        ContextCompat.registerReceiver(
                this, usbReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);

        handleUsbIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleUsbIntent(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(usbReceiver);
        disconnect();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // USB — intent routing, connect / open / disconnect
    // ─────────────────────────────────────────────────────────────────────────

    private void handleUsbIntent(Intent intent) {
        if (intent != null &&
                UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device != null) {
                if (usbManager.hasPermission(device)) {
                    open(device);
                } else {
                    pendingDevice = device;
                    PendingIntent pi = PendingIntent.getBroadcast(
                            this, 0,
                            new Intent(ACTION_USB_PERMISSION),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                    usbManager.requestPermission(device, pi);
                }
                return;
            }
        }

        List<UsbSerialDriver> drivers = buildProber().findAllDrivers(usbManager);
        if (!drivers.isEmpty()) {
            connectUsb();
        }
    }

    private UsbSerialProber buildProber() {
        ProbeTable table = UsbSerialProber.getDefaultProbeTable();
        table.addProduct(ESP_VID, ESP_PID_C3,    CdcAcmSerialDriver.class);
        table.addProduct(ESP_VID, ESP_PID_S2_S3, CdcAcmSerialDriver.class);
        return new UsbSerialProber(table);
    }

    private void connectUsb() {
        if (serialPort != null) {
            Toast.makeText(this, "Already connected", Toast.LENGTH_SHORT).show();
            return;
        }

        List<UsbSerialDriver> drivers = buildProber().findAllDrivers(usbManager);

        if (drivers.isEmpty()) {
            Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show();
            return;
        }

        UsbDevice device = drivers.get(0).getDevice();

        if (usbManager.hasPermission(device)) {
            open(device);
        } else {
            pendingDevice = device;
            PendingIntent pi = PendingIntent.getBroadcast(
                    this, 0,
                    new Intent(ACTION_USB_PERMISSION),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            usbManager.requestPermission(device, pi);
        }
    }

    private void open(UsbDevice device) {
        if (serialPort != null || isOpening) return;

        isOpening = true;

        UsbSerialDriver driver = buildProber().probeDevice(device);
        if (driver == null) {
            Toast.makeText(this, "No driver for this device", Toast.LENGTH_SHORT).show();
            isOpening = false;
            return;
        }

        UsbDeviceConnection connection = usbManager.openDevice(device);
        if (connection == null) {
            Toast.makeText(this, "Cannot open device — check permissions", Toast.LENGTH_SHORT).show();
            isOpening = false;
            return;
        }

        UsbSerialPort port = driver.getPorts().get(0);
        try {
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
            serialPort = port;
            ioManager = new SerialInputOutputManager(serialPort, this);
            ioManager.start();

            syncTime();

            Toast.makeText(this, "Connected successfully!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Open error", Toast.LENGTH_SHORT).show();
            try { connection.close(); } catch (Exception ignored) {}
            serialPort = null;
        } finally {
            isOpening = false;
        }
    }

    private void disconnect() {
        if (ioManager != null) {
            ioManager.stop();
            ioManager = null;
        }
        if (serialPort != null) {
            try { serialPort.close(); } catch (Exception ignored) {}
            serialPort = null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Serial I/O
    // ─────────────────────────────────────────────────────────────────────────

    private void sendCommand(String command) {
        try {
            if (serialPort == null) {
                Toast.makeText(this, "Serial port not open", Toast.LENGTH_SHORT).show();
                return;
            }
            serialPort.write(command.getBytes(), 1000);
        } catch (Exception e) {
            Toast.makeText(this, "Error while comunicating with the device", Toast.LENGTH_SHORT).show();
            disconnect();
        }
    }

    private void handleResponse(String line) {
        switch (pendingOp) {

            case LIST_FOR_REMOVE:
                pendingOp = PendingOp.NONE;
                showRemoveDialog(line);
                break;

            case NONE:
            default:
                if (!line.startsWith("OK:time_set")) {
                }
                break;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Dialogs
    // ─────────────────────────────────────────────────────────────────────────

    private void showCreateAccountDialog() {
        if (serialPort == null) {
            Toast.makeText(this, "Not connected — tap Connect first", Toast.LENGTH_SHORT).show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 0);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Account name  (e.g. Google)");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(nameInput);

        EditText secretInput = new EditText(this);
        secretInput.setHint("Base32 secret  (e.g. JBSWY3DP)");
        secretInput.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        layout.addView(secretInput);

        new AlertDialog.Builder(this)
                .setTitle("Add TOTP Account")
                .setView(layout)
                .setPositiveButton("Add", (dialog, which) -> {
                    String name   = nameInput.getText().toString().trim();
                    String secret = secretInput.getText().toString().trim().toUpperCase();

                    if (name.isEmpty()) {
                        Toast.makeText(this, "Name cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (name.length() > 12) {
                        Toast.makeText(this, "Name must be 12 characters or fewer", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (secret.length() < 8) {
                        Toast.makeText(this, "Secret must be at least 8 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    sendCommand("ADD " + name + " " + secret + "\n");
                    Toast.makeText(this, "Account added: " + name, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showRemoveDialog(String json) {
        try {
            JSONArray array = new JSONArray(json);

            if (array.length() == 0) {
                Toast.makeText(this, "No accounts to remove", Toast.LENGTH_SHORT).show();
                return;
            }

            String[] labels  = new String[array.length()];
            int[]    indexes = new int[array.length()];

            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                indexes[i] = obj.getInt("index");
                labels[i]  = "#" + indexes[i] + "  " + obj.getString("name");
            }

            new AlertDialog.Builder(this)
                    .setTitle("Remove Account")
                    .setItems(labels, (dialog, which) -> {
                        int idx = indexes[which];
                        sendCommand("REMOVE " + idx + "\n");
                        Toast.makeText(this, "Removed account #" + idx, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        } catch (Exception e) {
            Toast.makeText(this, "Failed to parse account list: Maybe locked? ",Toast.LENGTH_SHORT).show();
            pendingOp = PendingOp.NONE;
        }
    }

    private void showWipeConfirmDialog() {
        if (serialPort == null) {
            Toast.makeText(this, "Not connected — tap Connect first", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Wipe All Accounts")
                .setMessage("This will permanently delete every TOTP account from the device. Are you sure?")
                .setPositiveButton("Wipe", (dialog, which) -> {
                    sendCommand("CLEAR\n");
                    Toast.makeText(this, "All accounts wiped", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BroadcastReceiver
    // ─────────────────────────────────────────────────────────────────────────

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (!ACTION_USB_PERMISSION.equals(intent.getAction())) return;

            boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);

            UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
            if (device == null) device = pendingDevice;
            pendingDevice = null;

            if (granted && device != null) {
                final UsbDevice finalDevice = device;
                runOnUiThread(() -> open(finalDevice));
            } else {
                Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    };

    // ─────────────────────────────────────────────────────────────────────────
    // SerialInputOutputManager.Listener
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    public void onNewData(byte[] data) {
        serialBuffer.append(new String(data));

        int newlineIdx;
        while ((newlineIdx = serialBuffer.indexOf("\n")) != -1) {
            String line = serialBuffer.substring(0, newlineIdx).trim();
            serialBuffer.delete(0, newlineIdx + 1);
            if (!line.isEmpty()) {
                runOnUiThread(() -> handleResponse(line));
            }
        }
    }

    @Override
    public void onRunError(Exception e) {
        runOnUiThread(() -> {
            disconnect();
        });
    }
}