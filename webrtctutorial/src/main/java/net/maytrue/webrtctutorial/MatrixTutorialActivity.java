package net.maytrue.webrtctutorial;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

public class MatrixTutorialActivity extends AppCompatActivity {
    private final String TAG = "MatrixTutorialActivity";
    private Button btnMirror;
    private Button btnScale;
    private ImageView imageViewSource;
    private ImageView imageViewProcessed;
    private Bitmap bitmapSource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_matrix_tutorial);

        imageViewSource = findViewById(R.id.img_view_source);
        imageViewProcessed = findViewById(R.id.img_view_processed);

        try {
            InputStream inputStream = getAssets().open("lena.png");
            bitmapSource = BitmapFactory.decodeStream(inputStream);
            inputStream.close();
            imageViewSource.setImageBitmap(bitmapSource);
        } catch (IOException e) {
            Log.d(TAG, "open asset:" + e.getMessage());
        }

        btnMirror = findViewById(R.id.btn_mirror);
        btnMirror.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (bitmapSource == null) {
                    Log.d(TAG, "bitmapSource null");
                    return;
                }

                Matrix matrix = new Matrix();
                matrix.setTranslate(bitmapSource.getWidth(), 0);
                matrix.preScale(-1, 1);

                Bitmap bitmap = Bitmap.createBitmap(bitmapSource.getWidth(),
                        bitmapSource.getHeight(), bitmapSource.getConfig());

                Canvas canvas = new Canvas(bitmap);
                Paint paint = new Paint();
                canvas.drawBitmap(bitmapSource, matrix, paint);

                imageViewProcessed.setImageBitmap(bitmap);
            }
        });

        btnScale = findViewById(R.id.btn_scale);
        btnScale.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (bitmapSource == null) {
                    Log.d(TAG, "bitmapSource null");
                    return;
                }

                Matrix matrix = new Matrix();
                // matrix.setTranslate(bitmapSource.getWidth() / 2, 0);
                matrix.setScale(0.5f, 0.5f);

                Bitmap bitmap = Bitmap.createBitmap(bitmapSource.getWidth(),
                        bitmapSource.getHeight(), bitmapSource.getConfig());

                Canvas canvas = new Canvas(bitmap);
                Paint paint = new Paint();
                canvas.drawBitmap(bitmapSource, matrix, paint);

                imageViewProcessed.setImageBitmap(bitmap);
            }
        });
    }

}