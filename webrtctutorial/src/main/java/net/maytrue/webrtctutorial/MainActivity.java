package net.maytrue.webrtctutorial;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import org.webrtc.SurfaceViewRenderer;

import javax.annotation.Nullable;

public class MainActivity extends AppCompatActivity {

    @Nullable
    private SurfaceViewRenderer fullscreenRenderer;
    @Nullable
    private ZRtcEngine rtcEngine;

    private Button btnOpenCamera;
    private Button btnCloseCamera;
    private Button btnSwitchCamera;
    private Button btnCaptureCamera;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        fullscreenRenderer = findViewById(R.id.fullscreen_video_view);
        rtcEngine = new ZRtcEngine(getApplicationContext(), null);
        rtcEngine.setLocalView(fullscreenRenderer);

        btnOpenCamera = findViewById(R.id.btn_open_camera);
        btnOpenCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rtcEngine != null) {
                    rtcEngine.startPreview();
                }
            }
        });

        btnSwitchCamera = findViewById(R.id.btn_switch_camera);
        btnSwitchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rtcEngine != null) {
                    rtcEngine.switchCamera();
                }
            }
        });

        btnCloseCamera = findViewById(R.id.btn_close_camera);
        btnCloseCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rtcEngine != null) {
                    rtcEngine.stopPreview();
                }
            }
        });

        btnCaptureCamera = findViewById(R.id.btn_capture_camera);
        btnCaptureCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (rtcEngine != null) {
                    rtcEngine.capture();
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}