package com.example.videorecorderapp;

import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.herohan.uvcapp.CameraHelper;
import com.herohan.uvcapp.ICameraHelper;
import com.herohan.uvcapp.VideoCapture;
import com.serenegiant.usb.Size;
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
    private final Handler handler = new Handler();
//    private  boolean isGoingToStart = false;

    private long savingTime = 200L;

    private  Runnable stopPeriodTaskAndStart;

    private  Runnable startPeriodTaskAndStop;
    private ICameraHelper mCameraHelper;


    private AspectRatioSurfaceView mCameraViewMain;

    private ImageView bthCaptureVideo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("Video recording");

        initViews();
        initRunnables();
    }

    private void initRunnables() {

   stopPeriodTaskAndStart = ()->{
            stopRecord();
            handler.postDelayed(startPeriodTaskAndStop, savingTime);
        };

   startPeriodTaskAndStop = () -> {
            startRecord();
            handler.postDelayed(stopPeriodTaskAndStart, 60_000-savingTime);
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
        mCameraHelper.selectDevice(device);
    }

    private final ICameraHelper.StateCallback mStateListener = new ICameraHelper.StateCallback() {
        @Override
        public void onAttach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onAttach:");
            selectDevice(device);
        }

        @Override
        public void onDeviceOpen(UsbDevice device, boolean isFirstOpen) {
            if (DEBUG) Log.v(TAG, "onDeviceOpen:");
            mCameraHelper.openCamera();
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
        }

        @Override
        public void onCameraClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCameraClose:");

            if (mCameraHelper != null) {
                mCameraHelper.removeSurface(mCameraViewMain.getHolder().getSurface());
            }
        }

        @Override
        public void onDeviceClose(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDeviceClose:");
        }

        @Override
        public void onDetach(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onDetach:");
        }

        @Override
        public void onCancel(UsbDevice device) {
            if (DEBUG) Log.v(TAG, "onCancel:");
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
    }

    private void startRecordEveryMinute() {
        Calendar calendar = Calendar.getInstance();
        int currentSecond = calendar.get(Calendar.SECOND);

        // Calculate the delay until the next minute
        long delay = (60 - currentSecond) * 1000;
        startRecord();

        handler.postDelayed(stopPeriodTaskAndStart, delay);

    }

    private void startRecord() {
//        isGoingToStart = true;
        Log.d("TTT", "Video is gonna start: " + System.currentTimeMillis());
        File file = FileUtils.getCaptureFile(this, Environment.DIRECTORY_MOVIES, ".mp4");
        VideoCapture.OutputFileOptions options =
                new VideoCapture.OutputFileOptions.Builder(file).build();

//        ContentValues contentValues = new ContentValues();
//        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO");
//        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
//
//        VideoCapture.OutputFileOptions options = new VideoCapture.OutputFileOptions.Builder(
//                getContentResolver(),
//                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
//                contentValues).build();

        mCameraHelper.startRecording(options, new VideoCapture.OnVideoCaptureCallback() {
            @Override
            public void onStart() {
//                isGoingToStart = false;
                Log.d("TTT", "Video is started: " + System.currentTimeMillis());
            }

            @Override
            public void onVideoSaved(@NonNull VideoCapture.OutputFileResults outputFileResults) {
                Log.d("TTT", "onVideoSaved: "+System.currentTimeMillis());
                Toast.makeText(
                        MainActivity.this,
                        "save \"" + UriHelper.getPath(MainActivity.this, outputFileResults.getSavedUri()) + "\"",
                        Toast.LENGTH_SHORT).show();

                bthCaptureVideo.setColorFilter(0);
                Log.d("TTT", "Video is saved: " + System.currentTimeMillis());
//                if (isGoingToStart){
//                    startRecord();
//                }
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
            Log.d("TTT", "Video is stopped: " + System.currentTimeMillis());
            mCameraHelper.stopRecording();

        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(startPeriodTaskAndStop);
        handler.removeCallbacks(stopPeriodTaskAndStart);
    }
}