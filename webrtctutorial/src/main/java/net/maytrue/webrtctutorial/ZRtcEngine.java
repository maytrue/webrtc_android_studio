package net.maytrue.webrtctutorial;

import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.TextureBufferImpl;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nullable;

public class ZRtcEngine {

    private static final String TAG = "ZRtcEngine";
    private final EglBase rootEglBase;
    private final Context appContext;
    private SurfaceViewRenderer localView;
    private VideoCapturer videoCapturer;
    @Nullable private SurfaceTextureHelper surfaceTextureHelper;

    private int videoWidth = 1280;
    private int videoHeight = 720;
    private int videoFps = 15;
    private AtomicBoolean isFrameCaptured = new AtomicBoolean(false);

    private static final String VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";


    public ZRtcEngine(Context appContext, EglBase eglBase) {
        this.appContext = appContext;
        if (eglBase != null) {
            this.rootEglBase = eglBase;
        } else {
            this.rootEglBase = EglBase.create();
        }
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", rootEglBase.getEglBaseContext());

        PeerConnectionFactory.initialize(
                PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setFieldTrials(getFieldTrials())
                        .setEnableInternalTracer(true)
                        .createInitializationOptions());
    }

    public void setLocalView(SurfaceViewRenderer localView) {
        this.localView = localView;
        this.localView.init(rootEglBase.getEglBaseContext(), new RendererCommon.RendererEvents() {
            @Override
            public void onFirstFrameRendered() {
                Log.d(TAG, "onFirstFrameRendered");
            }

            @Override
            public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {
                Log.d(TAG, "onFrameResolutionChanged videoWidth:" + videoWidth + ", videoHeight:" + videoHeight + ", rotation:" + rotation);
            }
        });
        this.localView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
    }

    public void startPreview() {
        if (videoCapturer == null) {
            videoCapturer = createCameraCapturer(new Camera2Enumerator(appContext));
            videoCapturer.initialize(surfaceTextureHelper, appContext, new CapturerObserver() {
                @Override
                public void onCapturerStarted(boolean success) {
                    Log.d(TAG, "onCapturerStarted success:" + success);
                }

                @Override
                public void onCapturerStopped() {
                    Log.d(TAG, "onCapturerStopped");
                }

                @Override
                public void onFrameCaptured(VideoFrame frame) {
                    if (localView != null) {
                        localView.onFrame(frame);
                    }
                    Log.d(TAG, "onFrameCaptured width:" + frame.getRotatedWidth() + ", height:" + frame.getRotatedHeight() + ", rotation:" + frame.getRotation());

                    VideoFrame.Buffer origBuffer = frame.getBuffer();
                    TextureBufferImpl origTextureBuffer = (TextureBufferImpl)origBuffer;

                    Matrix origTransform = origTextureBuffer.getTransformMatrix();
                    float[] transformData = new float[9];
                    origTransform.getValues(transformData);
                    Log.d(TAG, "onFrameCaptured transformData:" + Arrays.toString(transformData));

                    if (isFrameCaptured.get()) {
                        // VideoFrame.I420Buffer buffer = frame.getBuffer().toI420();

                        // VideoFrame.Buffer cropBuffer = frame.getBuffer().cropAndScale(0, 0, 960, 540, 960, 540);
                        // VideoFrame.I420Buffer buffer = cropBuffer.toI420();
                        // cropBuffer.release();

                        Matrix transform = new Matrix();
                        //transform.setRotate(frame.getRotation());

                        VideoFrame.Buffer rotateBuffer = origTextureBuffer.applyTransformMatrix(transform, frame.getRotatedWidth(), frame.getRotatedHeight());
                        VideoFrame.I420Buffer buffer = rotateBuffer.toI420();

                        dumpFrame(buffer);

                        isFrameCaptured.set(false);
                        buffer.release();
                        rotateBuffer.release();
                    }
                }
            });
        }
        videoCapturer.startCapture(videoWidth, videoHeight, videoFps);
    }

    public void stopPreview() {
        if (videoCapturer == null) {
            Log.d(TAG, "stopPreview videoCapturer null");
            return;
        }
        try {
            videoCapturer.stopCapture();
        } catch (InterruptedException e) {
            Log.d(TAG, e.getMessage());
        }
    }

    public void switchCamera() {
        if (videoCapturer == null) {
            Log.d(TAG, "switchCamera videoCapturer null");
            return;
        }
        CameraVideoCapturer cameraVideoCapturer = (CameraVideoCapturer) videoCapturer;
        cameraVideoCapturer.switchCamera(new CameraVideoCapturer.CameraSwitchHandler() {
            @Override
            public void onCameraSwitchDone(boolean isFrontCamera) {
                Log.d(TAG, "onCameraSwitchDone isFrontCamera:" + isFrontCamera);
            }

            @Override
            public void onCameraSwitchError(String errorDescription) {
                Log.d(TAG, "onCameraSwitchError:" + errorDescription);
            }
        });
    }

    public void capture() {
        if (isFrameCaptured.get()) {
            return;
        }
        isFrameCaptured.set(true);
    }

    private @Nullable
    VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        return null;
    }

    private String getFieldTrials() {
        String fieldTrials = "";
        fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
        Log.d(TAG, "Enable FlexFEC field trial.");
        return fieldTrials;
    }

    private void dumpFrame(VideoFrame.I420Buffer buffer) {
        File directory = appContext.getExternalFilesDir("webrtc_tutorial");
        String fileName = String.format("%d-%d-%d.yuv", buffer.getWidth(), buffer.getHeight(), System.currentTimeMillis());
        File file = new File(directory, fileName);
        Log.d(TAG, "onFrameCaptured fileName:" + file.getAbsolutePath());

        try {
            FileOutputStream outputStream = new FileOutputStream(file);

            int width = buffer.getWidth();
            int height = buffer.getHeight();

            byte[] bytesY = new byte[width * height];
            buffer.getDataY().get(bytesY, 0, width * height);
            outputStream.write(bytesY);

            byte[] bytesU = new byte[width * height / 4];
            buffer.getDataU().get(bytesU, 0, width * height / 4);
            outputStream.write(bytesU);

            byte[] bytesV = new byte[width * height / 4];
            buffer.getDataV().get(bytesV, 0, width * height / 4);
            outputStream.write(bytesV);

            Log.d(TAG, "onFrameCaptured strideY:" + buffer.getStrideY());
            Log.d(TAG, "onFrameCaptured strideU:" + buffer.getStrideU());
            Log.d(TAG, "onFrameCaptured strideV:" + buffer.getStrideV());

        } catch (FileNotFoundException e) {
            Log.d(TAG, "onFrameCaptured:" + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "onFrameCaptured:" + e.getMessage());
        }
    }
}
