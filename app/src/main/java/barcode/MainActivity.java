package barcode;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.cognex.dataman.sdk.ConnectionState;
import com.cognex.mobile.barcode.sdk.ReadResult;
import com.cognex.mobile.barcode.sdk.ReadResults;
import com.cognex.mobile.barcode.sdk.ReaderDevice;

import example.gaurav.mlkit.barcode.R;

public class MainActivity extends AppCompatActivity implements ReaderDevice.OnConnectionCompletedListener, ReaderDevice.ReaderDeviceListener{

    // Permission List
    private String[] REQUEST_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
    // Permission Request Code
    private int RESULT_PERMISSIONS = 0x9000;

    boolean isScanning = false;
    ReaderDevice readerDevice;
    boolean listeningForUSB = false;
    String selectedDevice = "";

    @Override
    public void onConnectionCompleted(ReaderDevice readerDevice, Throwable throwable) {

    }

    @Override
    public void onConnectionStateChanged(ReaderDevice readerDevice) {
        if (readerDevice.getConnectionState() == ConnectionState.Connected) {
            readerConnected();
        } else if (readerDevice.getConnectionState() == ConnectionState.Disconnected) {
            readerDisconnected();
        }
    }

    @Override
    public void onReadResultReceived(ReaderDevice readerDevice, ReadResults readResults) {
        if(readResults != null)
            Toast.makeText(this, "Inside read Result: " + readResults.getCount() , Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(this, "read Result is NULL " + readResults.getCount() , Toast.LENGTH_SHORT).show();
        if(readResults.getSubResults() != null)
            Toast.makeText(this, "Inside read Result: " + readResults.getSubResults().size() , Toast.LENGTH_SHORT).show();
        else{
            ReadResult result = readResults.getResultAt(0);
            Toast.makeText(this, "read getSubResults is " + result.getReadString() , Toast.LENGTH_SHORT).show();
        }

        //Toast.makeText(this, "Inside read Result: " + readResults.getSubResults().get(0).getReadString() , Toast.LENGTH_SHORT).show();
        if (readResults.getSubResults() != null && readResults.getSubResults().size() > 0) {
            for (ReadResult subResult : readResults.getSubResults()) {
                if (subResult.isGoodRead()) {
                    Log.d("****", subResult.getReadString());
                    Toast.makeText(this, "Result: " + subResult.getReadString(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    @Override
    public void onAvailabilityChanged(ReaderDevice readerDevice) {

        if (readerDevice.getAvailability() == ReaderDevice.Availability.AVAILABLE) {
            readerDevice.connect(MainActivity.this);
        } else {
            readerDisconnected();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.btn_show_preview_activity).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(isPermissionGranted()) startCameraPreviewActivity();
            }
        });
    }

    /** Go to camera preview */
    private void startCameraPreviewActivity(){
        startActivity(new Intent(this, CameraPreviewActivity.class));
    }

    /** Request permission and check */
    private boolean isPermissionGranted(){
        int sdkVersion = Build.VERSION.SDK_INT;
        if(sdkVersion >= Build.VERSION_CODES.M) {
            //Android6.0(Marshmallow) 이상인 경우 사용자 권한 요청
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, REQUEST_PERMISSIONS, RESULT_PERMISSIONS);
                return false;
            } else {
                return true;
            }
        }else{
            //Android6.0(Marshmallow) 이하는 권한확인 안함
            return true;
        }
    }

    /** Post-process for granted permissions */
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (RESULT_PERMISSIONS == requestCode) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                startCameraPreviewActivity();
            } else {
                // Rejected
                Toast.makeText(this, R.string.err_permission_not_granted, Toast.LENGTH_SHORT).show();
            }
            return;
        }

    }

    private void initDevice() {


        readerDevice = ReaderDevice.getMXDevice(this);

        if (!listeningForUSB) {
            readerDevice.startAvailabilityListening();

            listeningForUSB = true;
        }


        selectedDevice = "MX Mobile Terminal";
        // set listeners and connect to device
        readerDevice.setReaderDeviceListener(this);
        readerDevice.enableImage(true);
        readerDevice.enableImageGraphics(true);
        readerDevice.connect(MainActivity.this);
        Toast.makeText(this, "Scanner Initiated: " +readerDevice.getAvailability(), Toast.LENGTH_SHORT).show();

    }

    @Override
    protected void onStart() {
        super.onStart();
        initDevice();
    }

    @Override
    protected void onStop() {
        // call disconnect if device was already connected
        if (readerDevice != null) {
            readerDevice.disconnect();
        }

        // stop listening to device availability to avoid resource leaks
        try {
            readerDevice.stopAvailabilityListening();
        } catch (Exception e) {
            // if availability listening was already stopped or wasn't started
        }

        listeningForUSB = false;

        super.onStop();
    }

    private void readerConnected() {
        Log.d(getClass().getSimpleName(), "onConnected");


        isScanning = false;


        readerDevice.setSymbologyEnabled(ReaderDevice.Symbology.C128, true, null);
        readerDevice.setSymbologyEnabled(ReaderDevice.Symbology.DATAMATRIX, true, null);
        readerDevice.setSymbologyEnabled(ReaderDevice.Symbology.UPC_EAN, true, null);
        readerDevice.setSymbologyEnabled(ReaderDevice.Symbology.QR, true, null);

        // example on using DataManSystem.sendCommand
        readerDevice.getDataManSystem().sendCommand("SET SYMBOL.MICROPDF417 ON");
        readerDevice.getDataManSystem().sendCommand("SET IMAGE.SIZE 0");
    }

    private void readerDisconnected() {
        Log.d(getClass().getSimpleName(), "onDisconnected");


        isScanning = false;

    }
}
