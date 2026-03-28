package www.esp32totp.com;

import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.util.SerialInputOutputManager;

import java.io.IOException;
import java.util.concurrent.Executors;

public class SerialSocket {

    private UsbSerialPort port;
    private SerialInputOutputManager ioManager;
    private SerialListener listener;

    public SerialSocket(UsbSerialPort port, SerialListener listener) {
        this.port = port;
        this.listener = listener;
    }

    public void start() {
        ioManager = new SerialInputOutputManager(port, new SerialInputOutputManager.Listener() {
            @Override
            public void onNewData(byte[] data) {
                if (listener != null) listener.onSerialRead(data);
            }

            @Override
            public void onRunError(Exception e) {
                if (listener != null) listener.onSerialError(e);
            }
        });

        Executors.newSingleThreadExecutor().submit(ioManager);
    }

    public void send(String data) throws IOException {
        port.write(data.getBytes(), 1000);
    }

    public void stop() {
        if (ioManager != null) ioManager.stop();
    }
}