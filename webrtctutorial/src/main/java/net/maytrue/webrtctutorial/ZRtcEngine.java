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
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoFrame;
import org.webrtc.YuvHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
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
    private FileOutputStream fileOutputStream = null;

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

                    VideoFrame.Buffer origBuffer = frame.getBuffer();
                    TextureBufferImpl origTextureBuffer = (TextureBufferImpl)origBuffer;

                    Matrix origTransform = origTextureBuffer.getTransformMatrix();
                    float[] transformData = new float[9];
                    origTransform.getValues(transformData);
                    // Log.d(TAG, "onFrameCaptured transformData:" + Arrays.toString(transformData));

                    if (isFrameCaptured.get()) {
                        Matrix transform = new Matrix();
                        transform.preTranslate(0.5f, 0.5f);
                        transform.preRotate(frame.getRotation());
                        transform.preTranslate(-0.5f, -0.5f);

                        VideoFrame.Buffer rotateBuffer = origTextureBuffer.applyTransformMatrix(transform, frame.getRotatedWidth(), frame.getRotatedHeight());
                        VideoFrame.I420Buffer buffer = rotateBuffer.toI420();
                        dumpFrame(buffer);
                        buffer.release();
                        rotateBuffer.release();
                        //isFrameCaptured.set(false);
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

    private String getDumpFileName(int width, int height) {
        File directory = appContext.getExternalFilesDir("webrtc_tutorial");
        String fileName = String.format("%d-%d-%d.yuv", width, height, System.currentTimeMillis());
        File file = new File(directory, fileName);
        Log.d(TAG, "onFrameCaptured fileName:" + file.getAbsolutePath());
        return file.getAbsolutePath();
    }

    private void dumpFrame(VideoFrame.I420Buffer buffer) {
        try {
            if (fileOutputStream == null) {
                File directory = appContext.getExternalFilesDir("webrtc_tutorial");
                String fileName = String.format("%d-%d-%d.yuv", buffer.getWidth(), buffer.getHeight(), System.currentTimeMillis());
                File file = new File(directory, fileName);
                Log.d(TAG, "onFrameCaptured fileName:" + file.getAbsolutePath());
                fileOutputStream = new FileOutputStream(file);
            }

            int width = buffer.getWidth();
            int height = buffer.getHeight();

            int outputFrameSize = width * height * 3 / 2;
            ByteBuffer outputFrameBuffer = ByteBuffer.allocateDirect(outputFrameSize);

            YuvHelper.I420Copy(buffer.getDataY(), buffer.getStrideY(), buffer.getDataU(), buffer.getStrideU(),
                    buffer.getDataV(), buffer.getStrideV(), outputFrameBuffer, width, height);
            fileOutputStream.write(outputFrameBuffer.array(), outputFrameBuffer.arrayOffset(), outputFrameSize);

        } catch (FileNotFoundException e) {
            Log.d(TAG, "onFrameCaptured:" + e.getMessage());
        } catch (IOException e) {
            Log.d(TAG, "onFrameCaptured:" + e.getMessage());
        }
    }
}
