package com.textdetection.myapplication;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity {
    Button button_capture, button_copy;
    ProgressBar progressBar;
    Bitmap bitmap;
    private TextView responseText;

    private static final MediaType MEDIA_TYPE_PNG = MediaType.get("image/png");
    private static final int REQUEST_CAMERA_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        progressBar=findViewById(R.id.progressBar);

        responseText=findViewById(R.id.responseText);
        button_capture=findViewById(R.id.button_capture);
        button_copy=findViewById(R.id.button_copy);

        if (ContextCompat.checkSelfPermission(MainActivity.this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED){
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{
                    Manifest.permission.CAMERA
            },REQUEST_CAMERA_CODE);
        }

        button_capture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                CropImage.activity().setGuidelines(CropImageView.Guidelines.ON).start(MainActivity.this);
                responseText.setText(" ");

            }
        });

        button_copy.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String scanned_text = responseText.getText().toString();
                copyToClipBoard(scanned_text);
            }
        });


    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            if (resultCode==RESULT_OK){
                Uri resultUri = result.getUri();
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), resultUri);
                    saveImage(bitmap);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }


    public void detectText(String path, String fname){

        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(120, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("image", String.valueOf(fname),
                        RequestBody.create(MEDIA_TYPE_PNG, new File(String.valueOf(path))))
                .build();

        Request request = new Request.Builder()
                .url("http://192.168.1.14:5000/")
                .post(requestBody)
                .build();

        progressBar.setVisibility(View.VISIBLE);
        responseText.setVisibility(View.INVISIBLE);
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override

            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, "server down", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.INVISIBLE);
                        responseText.setVisibility(View.VISIBLE);
                        responseText.setText("error connecting to the server");
                        button_capture.setText("Retry");
                    }
                });
            }
            @Override

            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                String results = response.body().string();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        progressBar.setVisibility(View.INVISIBLE);
                        responseText.setVisibility(View.VISIBLE);
                        responseText.setText("Detected Text:\n \n " + results);

                        button_copy.setVisibility(View.VISIBLE);
                        button_capture.setText("Recapture");
                    }
                });
            }
        });
    }

    private void saveImage(Bitmap bitmap) {

        File myDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                + File.separator + "TextDetection" + File.separator + "photos");
        myDir.mkdirs();

        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fname = timeStamp + ".jpg";
        Log.i("Image path: ", fname);
        File file = new File(myDir, fname);
        if (file.exists()) file.delete();
        try {
            FileOutputStream out = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        String path = file.getAbsolutePath();
        System.out.println(path);
        detectText(path,fname);
    }


    private void copyToClipBoard(String text){
        ClipboardManager clipboard=(ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip=ClipData.newPlainText("Copied data", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(MainActivity.this, "Copied To Clipboard", Toast.LENGTH_SHORT).show();
    }

}
