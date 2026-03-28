package www.esp32totp.com;

public interface SerialListener {
    void onSerialRead(byte[] data);
    void onSerialError(Exception e);
}