package barcode;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.cognex.dataman.sdk.CameraMode;
import com.cognex.dataman.sdk.ConnectionState;
import com.cognex.dataman.sdk.PreviewOption;
import com.cognex.mobile.barcode.sdk.ReadResult;
import com.cognex.mobile.barcode.sdk.ReadResults;
import com.cognex.mobile.barcode.sdk.ReaderDevice;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcode;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetector;
import com.google.firebase.ml.vision.barcode.FirebaseVisionBarcodeDetectorOptions;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import example.gaurav.mlkit.barcode.R;

/**
 * Camera Preview Activity
 * control preview screen and overlays
 */
public class CameraPreviewActivity extends AppCompatActivity {

    private Camera mCamera;
    private CameraView camView;
    private OverlayView overlay;
    private double overlayScale = -1;
    Rect tmpRect;

    ImageButton flashButton;
    ObjectAnimator animator = null;
    View scannerBar;
    RelativeLayout mDetectionBox = null;

    boolean isScanning = false;



    private interface OnBarcodeListener {
        void onIsbnDetected(FirebaseVisionBarcode barcode);
        void onMultipleIsbnDetected(List<FirebaseVisionBarcode> barcodes);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Full Screen
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        // Fix orientation : portrait
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Set layout
        setContentView(R.layout.activity_camera_preview);

        // Set ui button actions
        findViewById(R.id.btn_finish_preview).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                CameraPreviewActivity.this.finish();
            }
        });

        // Initialize Camera
        mCamera = getCameraInstance();

        final View scannerLayout = this.getLayoutInflater().inflate(R.layout.scanner_overlay, null);
        scannerBar = scannerLayout.findViewById(R.id.scannerBar);
        flashButton = scannerLayout.findViewById(R.id.flash_button);
        mDetectionBox = scannerLayout.findViewById(R.id.detection_area);



        // Set-up preview screen
        if(mCamera != null) {
            // Create overlay view
            overlay = new OverlayView(this);

            // Create barcode processor for ISBN
            CustomPreviewCallback camCallback = new CustomPreviewCallback(CameraView.PREVIEW_WIDTH, CameraView.PREVIEW_HEIGHT);
            camCallback.setBarcodeDetectedListener(new OnBarcodeListener() {
                @Override
                public void onIsbnDetected(FirebaseVisionBarcode barcode) {

                    int[] l = new int[2];
                    mDetectionBox.getLocationOnScreen(l);

                    //For Bigger screen S9
                    Rect rect = new Rect(l[0] + 10, l[1] - 220, l[0] + 700, l[1] + 200);

                    overlay.setOverlay(fitOverlayRect(rect), barcode.getRawValue());
                    overlay.invalidate();
                    if(isViewContains(mDetectionBox, barcode.getBoundingBox())){
                        Toast.makeText(CameraPreviewActivity.this, "Inside view", Toast.LENGTH_SHORT).show();
                    }else {
                        Toast.makeText(CameraPreviewActivity.this, "Outside view", Toast.LENGTH_SHORT).show();
                    }

                }

                @Override
                public void onMultipleIsbnDetected(List<FirebaseVisionBarcode> barcodes) {
                    /*for (FirebaseVisionBarcode barcode: barcodes) {
                        Log.d("Barcode", "value : " + barcode.getRawValue());
                        overlay = new OverlayView(CameraPreviewActivity.this);
                        overlay.setOverlay(fitOverlayRect(barcode.getBoundingBox()), barcode.getRawValue());
                        overlay.invalidate();Your location
                    }*/
                }
            });

            // Create camera preview
            camView = new CameraView(this, mCamera);
            camView.setPreviewCallback(camCallback);

            // Add view to UI
            FrameLayout preview = findViewById(R.id.frm_preview);
            preview.addView(camView);
            preview.addView(overlay);


            ViewTreeObserver vto = scannerLayout.getViewTreeObserver();
            vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    scannerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
                        scannerLayout.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    } else {
                        scannerLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    }

                    float destination = (float)(scannerLayout.getY() + scannerLayout.getHeight());
                    animator = ObjectAnimator.ofFloat(scannerBar, "translationY", scannerLayout.getY(), destination);
                    animator.setRepeatMode(ValueAnimator.REVERSE);
                    animator.setRepeatCount(ValueAnimator.INFINITE);
                    animator.setInterpolator(new AccelerateDecelerateInterpolator());
                    animator.setDuration(2000);
                    animator.start();

                }
            });

            flashButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        if (flashButton.getTag() != null && flashButton.getTag().equals("OFF")){
                            startFlashMode(true);
                            flashButton.setImageResource(R.drawable.baseline_flash_on_white_18dp);
                            flashButton.setTag("ON");
                        } else {
                            startFlashMode(false);
                            flashButton.setImageResource(R.drawable.baseline_flash_off_white_18dp);
                            flashButton.setTag("OFF");
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            preview.addView(scannerLayout);
        }
    }

    private boolean isViewContains(View view, Rect detectedRect) {
        int[] l = new int[2];
        view.getLocationOnScreen(l);

        //For Bigger screen S9
        Rect rect = new Rect(l[0] + 10, l[1] - 220, l[0] + 700, l[1] + 200);

        return rect.contains(detectedRect);
    }

    @Override
    protected void onDestroy() {
        try{
            if(mCamera != null) mCamera.release();
        }catch (Exception e){
            e.printStackTrace();
        }

        super.onDestroy();
    }

    public void startFlashMode(boolean isFlashOn) throws IOException {
        Camera.Parameters parameters = mCamera.getParameters();
        if (isFlashOn){
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
        } else {
            parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
        }
        mCamera.setParameters(parameters);
    }

    /** Get facing back camera instance */
    public static Camera getCameraInstance()
    {
        int camId = -1;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        for (int i = 0; i < Camera.getNumberOfCameras(); ++i) {
            Camera.getCameraInfo(i, cameraInfo);
            if (cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                camId = i;
                break;
            }
        }

        if(camId == -1) return null;

        Camera c=null;
        try{
            c=Camera.open(camId);
        }catch(Exception e){
            e.printStackTrace();
        }
        return c;
    }

    /** Calculate overlay scale factor */
    private Rect fitOverlayRect(Rect r) {
        if(overlayScale <= 0) {
            Camera.Size prevSize = camView.getPreviewSize();
            overlayScale = (double) overlay.getWidth()/(double)prevSize.height;
        }

        return new Rect((int)(r.left*overlayScale), (int)(r.top*overlayScale), (int)(r.right*overlayScale), (int)(r.bottom*overlayScale));
    }


    /** Post-processor for preview image streams */
    private class CustomPreviewCallback implements Camera.PreviewCallback, OnSuccessListener<List<FirebaseVisionBarcode>>, OnFailureListener {

        public void setBarcodeDetectedListener(OnBarcodeListener mBarcodeDetectedListener) {
            this.mBarcodeDetectedListener = mBarcodeDetectedListener;
        }

        // ML Kit instances
        private FirebaseVisionBarcodeDetectorOptions options;
        private FirebaseVisionBarcodeDetector detector;
        private FirebaseVisionImageMetadata metadata;

        /**
         * Event Listener for post processing
         *
         * We'll set up the detector only for EAN-13 barcode format and ISBN barcode type.
         * This OnBarcodeListener aims of notifying 'ISBN barcode is detected' to other class.
         */
        private OnBarcodeListener mBarcodeDetectedListener = null;

        /** size of input image */
        private int mImageWidth, mImageHeight;

        /**
         * Constructor
         * @param imageWidth preview image width (px)
         * @param imageHeight preview image height (px)
         */
        CustomPreviewCallback(int imageWidth, int imageHeight){
            mImageWidth = imageWidth;
            mImageHeight = imageHeight;

            // set-up detector options for find EAN-13 format (commonly used 1-D barcode)
            options =new FirebaseVisionBarcodeDetectorOptions.Builder()
                    .setBarcodeFormats(FirebaseVisionBarcode.FORMAT_EAN_13,
                            FirebaseVisionBarcode.FORMAT_QR_CODE,
                            FirebaseVisionBarcode.FORMAT_DATA_MATRIX,
                            FirebaseVisionBarcode.FORMAT_CODE_93,
                            FirebaseVisionBarcode.FORMAT_CODE_39,
                            FirebaseVisionBarcode.FORMAT_CODE_128)
                    .build();

            detector = FirebaseVision.getInstance().getVisionBarcodeDetector(options);

            // build detector
            metadata = new FirebaseVisionImageMetadata.Builder()
                    .setFormat(ImageFormat.NV21)
                    .setWidth(mImageWidth)
                    .setHeight(mImageHeight)
                    .setRotation(FirebaseVisionImageMetadata.ROTATION_90)
                    .build();
        }

        /** Start detector if camera preview shows */
        @Override public void onPreviewFrame(byte[] data, Camera camera) {
            try {

                detector.detectInImage(FirebaseVisionImage.fromByteArray(data, metadata))
                        .addOnSuccessListener(this)
                        .addOnFailureListener(this);
            } catch (Exception e) {
                Log.d("CameraView", "parse error");
            }
        }


        /** Barcode is detected successfully */
        @Override public void onSuccess(List<FirebaseVisionBarcode> barcodes) {
            // Task completed successfully
            for (FirebaseVisionBarcode barcode: barcodes) {
                Log.d("Barcode", "value : "+barcode.getRawValue());

                mBarcodeDetectedListener.onIsbnDetected(barcode);
                //mBarcodeDetectedListener.onMultipleIsbnDetected(barcodes);
                return;

            }
        }

        /** Barcode is not recognized */
        @Override
        public void onFailure(@NonNull Exception e) {
            // Task failed with an exception
            Log.i("Barcode", "read fail");
        }
    }
}
