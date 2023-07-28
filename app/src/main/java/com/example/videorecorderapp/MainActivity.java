package com.example.videorecorderapp;

import android.Manifest;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.videorecorderapp.fragment.DeviceListDialogFragment;
import com.example.videorecorderapp.fragment.VideoFormatDialogFragment;
import com.example.videorecorderapp.utils.Permissions;
import com.example.videorecorderapp.utils.SaverHelper;
import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;
import com.serenegiant.usb.UVCParam;
import com.serenegiant.utils.FileUtils;
import com.serenegiant.utils.UriHelper;
import com.serenegiant.widget.AspectRatioSurfaceView;

import java.io.File;
import java.util.Calendar;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final boolean DEBUG = true;
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int DEFAULT_WIDTH = 640;
    private static final int DEFAULT_HEIGHT = 480;
    private final Object mSync = new Object();

    private int mPreviewWidth = DEFAULT_WIDTH;
    /**
     * Camera preview height
     */
    private int mPreviewHeight = DEFAULT_HEIGHT;


    private static final int PERMISSION_REQUEST_CODE = 4;
    private final Handler handler = new Handler();

    private UsbDevice mUsbDevice;
    private long savingTime = 660L;
    boolean isExceedMilliseconds = false;

    private Runnable stopPeriodTaskAndStart;
    private DeviceListDialogFragment mDeviceListDialog;

    private boolean mIsCameraConnected = false;

    private Runnable startPeriodTaskAndStop;
    private ICameraHelper mCameraHelper;
    private VideoFormatDialogFragment mVideoFormatDialog;


    private AspectRatioSurfaceView mCameraViewMain;

    private ImageView bthCaptureVideo;
    private SaverHelper saverHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Video recording");

        getPermissions();
        initViews();
        initRunnables();
        initListeners();
    }

    private void initListeners() {
        saverHelper = new SaverHelper(this);

        ImageView ivVideoFormat = findViewById(R.id.ic_video_format);
        ivVideoFormat.setOnClickListener(this);

        ImageView btnSettings = findViewById(R.id.ic_options);
        btnSettings.setOnClickListener(this);

    }

    private void getPermissions() {
        boolean hasPermissions = Permissions.checkPermissions(this);
        if (hasPermissions) {
            // Barcha huquqlar olingan
        } else {
            // Huquqlarni so'raymiz
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void showVideoFormatDialog() {
        if (mVideoFormatDialog != null && mVideoFormatDialog.isAdded()) {
            return;
        }

        mVideoFormatDialog = new VideoFormatDialogFragment(
                mCameraHelper.getSupportedFormatList(),
                mCameraHelper.getPreviewSize());

        mVideoFormatDialog.setOnVideoFormatSelectListener(size -> {
            if (mCameraHelper != null && mCameraHelper.isCameraOpened()) {
                mCameraHelper.stopPreview();
                mCameraHelper.setPreviewSize(size);
                mCameraHelper.startPreview();

                resizePreviewView(size);
            }
        });

        mVideoFormatDialog.show(getSupportFragmentManager(), "video format dialog");

    }

    private void resizePreviewView(Size size) {
        // Update the preview size
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        // Set the aspect ratio of SurfaceView to match the aspect ratio of the camera
        mCameraViewMain.setAspectRatio(mPreviewWidth, mPreviewHeight);
    }

    private void initRunnables() {

        stopPeriodTaskAndStart = () -> {
            stopRecord();
            handler.postDelayed(startPeriodTaskAndStop, savingTime);
        };

        startPeriodTaskAndStop = () -> {
            Log.d("TTT", "startPeriodTaskAndStop: I am here");
            Calendar calendar = Calendar.getInstance();
            if (calendar.get(Calendar.MILLISECOND) >= 850) {
                isExceedMilliseconds = true;
            }
            startRecord();
            if (isExceedMilliseconds) {
                Log.d("TTT", "isExceedMilliseconds: EXCEEDED");
                handler.postDelayed(stopPeriodTaskAndStart, 59_500 - savingTime);
                isExceedMilliseconds = false;
            } else {
                Log.d("TTT", "isExceedMilliseconds:  NOT EXCEEDED");
                handler.postDelayed(stopPeriodTaskAndStart, 60_000 - savingTime);
            }
        };

    }

    private void initViews() {
        mCameraViewMain = findViewById(R.id.svCameraViewMain);
        mCameraViewMain.setAspectRatio(DEFAULT_WIDTH, DEFAULT_HEIGHT);

        mCameraViewMain.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.addSurface(holder.getSurface(), false);
                }
            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {

            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                if (mCameraHelper != null) {
                    mCameraHelper.removeSurface(holder.getSurface());
                }
            }
        });

        bthCaptureVideo = findViewById(R.id.bthCaptureVideo);
        bthCaptureVideo.setOnClickListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        initCameraHelper();
    }

    @Override
    protected void onStop() {
        super.onStop();
        clearCameraHelper();
    }

    public void initCameraHelper() {
        if (DEBUG) Log.d(TAG, "initCameraHelper:");
        if (mCameraHelper == null) {
            mCameraHelper = new CameraHelper();
            mCameraHelper.setStateCallback(mStateListener);
        }
    }

    private void clearCameraHelper() {
        if (DEBUG) Log.d(TAG, "clearCameraHelper:");
        if (mCameraHelper != null) {
            mCameraHelper.release();
            mCameraHelper = null;
        }
    }

    private void selectDevice(final UsbDevice device) {
        if (DEBUG) Log.v(TAG, "selectDevice:device=" + device.getDeviceName());
        mUsbDevice = device;
        mCameraHelper.selectDevice(device);
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {

            synchronized (mSync) {
                if (mUsbDevice == null && !device.equals(mUsbDevice)) {
                    selectDevice(device);
                }
            }
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (mCameraHelper != null && device.equals(mUsbDevice)) {
                UVCParam param = new UVCParam();
                param.setQuirks(UVCCamera.UVC_QUIRK_FIX_BANDWIDTH);
                mCameraHelper.openCamera(param);
            }
        }

        @Override
        public void onCameraOpen(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraOpen:");

            mCameraHelper.startPreview();

            Size size = mCameraHelper.getPreviewSize();
            if (size != null) {
                int width = size.width;
                int height = size.height;
                //auto aspect ratio
                mCameraViewMain.setAspectRatio(width, height);
            }

            mCameraHelper.addSurface(mCameraViewMain.getHolder().getSurface(), false);
            mIsCameraConnected = true;
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
            }
            mIsCameraConnected = false;

        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:");
            if (device.equals(mUsbDevice)) {
                mUsbDevice = null;
            }
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:");
            if (device.equals(mUsbDevice)) {
                mUsbDevice = null;
            }
        }

    };

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.bthCaptureVideo) {
            handler.removeCallbacks(startPeriodTaskAndStop);
            handler.removeCallbacks(stopPeriodTaskAndStart);

            if (mCameraHelper != null) {
                if (mCameraHelper.isRecording()) {
                    stopRecord();
                } else {
                    startRecordEveryMinute();
                }
            }
        }

        if (v.getId() == R.id.ic_video_format){
            showVideoFormatDialog();
        } else if (v.getId()==R.id.ic_options) {
            showDeviceListDialog();
        }
    }

    private void showDeviceListDialog() {
        mDeviceListDialog = new DeviceListDialogFragment(mCameraHelper, mIsCameraConnected ? mUsbDevice : null);
        mDeviceListDialog.setOnDeviceItemSelectListener(usbDevice -> {
            if (mCameraHelper != null && mIsCameraConnected) {
                mCameraHelper.closeCamera();
            }
            selectDevice(usbDevice);
        });

        mDeviceListDialog.show(getSupportFragmentManager(), "device_list_left");
    }

    private void startRecordEveryMinute() {
        Calendar calendar = Calendar.getInstance();
        long currentSecond = calendar.get(Calendar.SECOND);
        long currentMilliSecond = calendar.get(Calendar.MILLISECOND);
        Log.d("TTT", "currentMilliSecond: " + currentMilliSecond);

        // Calculate the delay until the next minute
        long delay = 60_000 - (currentSecond*1000+currentMilliSecond);
        startRecord();

        handler.postDelayed(stopPeriodTaskAndStart, delay);

    }

    private void startRecord() {
//        isGoingToStart = true;
        Log.d("TTT", "Video is gonna start: " + System.currentTimeMillis());
//        String videoFileName = saverHelper.generateVideoFileName();
//        Log.d("TTT", "videoFileName: "+videoFileName);
//        File file = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), videoFileName);
        File file = FileUtils.getCaptureFile(this, Environment.DIRECTORY_MOVIES, ".mp4");
        VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(file).build();


        mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
                Calendar calendar = Calendar.getInstance();
                Log.d("TTT", "onStartVideoMIlliSeconds: " + calendar.get(Calendar.MILLISECOND));

            }

            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                Log.d("TTT", "onVideoSaved: " + System.currentTimeMillis());
                Log.d("TTT", "VideoSaved Succesfully: " + "save \"" + UriHelper.getPath(MainActivity.this, outputFileResults.getSavedUri()) + "\"");

                bthCaptureVideo.setColorFilter(0);

            }

            @Override
            public void onError(int videoCaptureError, @NonNull String message, @Nullable Throwable cause) {
                Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show();

                bthCaptureVideo.setColorFilter(0);
            }
        });

        bthCaptureVideo.setColorFilter(0x7fff0000);
    }

    private void stopRecord() {
        if (mCameraHelper != null) {
            mCameraHelper.stopRecording();
            Log.d("TTT", "Video is stopped: " + System.currentTimeMillis());

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(startPeriodTaskAndStop);
        handler.removeCallbacks(stopPeriodTaskAndStart);
    }
}