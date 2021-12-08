package com.example.classroom_screen_recorder;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


import com.hbisoft.hbrecorder.HBRecorder;
import com.hbisoft.hbrecorder.HBRecorderCodecInfo;
import com.hbisoft.hbrecorder.HBRecorderListener;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.app.Activity.RESULT_OK;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static com.hbisoft.hbrecorder.Constants.MAX_FILE_SIZE_REACHED_ERROR;
import static com.hbisoft.hbrecorder.Constants.SETTINGS_ERROR;

/** ClassroomScreenRecorderPlugin */
public class ClassroomScreenRecorderPlugin implements FlutterPlugin, MethodCallHandler,ActivityAware ,HBRecorderListener {
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  Activity activity;
  HBRecorder hbRecorder;
  String filePath;
  //Permissions
  private static final String VALUE_RES_ERROR = "error";
  private static final String VALUE_RES_SUCCESS = "success";

  private static final String KEY_RES = "res";
  private static final String KEY_MSG = "msg";
  private static final String KEY_FILEPATH = "file";
  private static final int SCREEN_RECORD_REQUEST_CODE = 777;
  private static final int PERMISSION_REQ_ID_RECORD_AUDIO = 22;
  private static final int PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = PERMISSION_REQ_ID_RECORD_AUDIO + 1;
  private boolean hasPermissions = false;

  //Reference to checkboxes and radio buttons
  boolean wasHDSelected = true;
  boolean isAudioEnabled = true;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "classroom_screen_recorder");
    channel.setMethodCallHandler(this);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else if(call.method.equals("startScreenRecord")) {
      HashMap<String,String> res = this.startScreenRecord();
      result.success(res);
    } else if(call.method.equals("stopScreenRecord")) {
      HashMap<String,String>  message = this.stopRecord();
      result.success(message);
    }else {
      result.notImplemented();
    }
  }
  HashMap<String,String>  stopRecord(){
    if(hbRecorder == null)
    {
      HashMap<String, String> map = new HashMap<String, String>() ;
      map.put(KEY_RES, VALUE_RES_ERROR);
      map.put(KEY_MSG, "record init failed");
      return map;
    }
    hbRecorder.stopScreenRecording();
    HashMap<String, String> map = new HashMap<String, String>() ;
    map.put(KEY_RES, VALUE_RES_SUCCESS);
    map.put(KEY_FILEPATH, this.filePath);
    return map;
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
  }
  //Check if permissions was granted
  private boolean checkSelfPermission(String permission, int requestCode) {
    if (ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED) {
      ActivityCompat.requestPermissions(activity, new String[]{permission}, requestCode);
      return false;
    }
    return true;
  }

  HashMap<String,String> startScreenRecord(){
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      //first check if permissions was granted
      if (checkSelfPermission(Manifest.permission.RECORD_AUDIO, PERMISSION_REQ_ID_RECORD_AUDIO) && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE)) {
        hasPermissions = true;
      }else{
        hasPermissions = false;
      }

      if (hasPermissions) {
        if (hbRecorder.isBusyRecording()) {
          ;//hbRecorder.stopScreenRecording();
          HashMap<String, String> map = new HashMap<String, String>() ;
          map.put(KEY_RES, VALUE_RES_ERROR);
          map.put(KEY_MSG, "is in recording");
          return map;
        }else {
          _startRecordingScreen();
        }
      }else{
        HashMap<String, String> map = new HashMap<String, String>() ;
        map.put(KEY_RES, VALUE_RES_ERROR);
        map.put(KEY_MSG, "no permisssion");
        return map;
      }
    } else {
//      showLongToast("This library requires API 21>");
      HashMap<String, String> map = new HashMap<String, String>() ;
      map.put(KEY_RES, VALUE_RES_ERROR);
      map.put(KEY_MSG, "This library requires API 21>");
      return map;

    }
    HashMap<String, String> map = new HashMap<String, String>() ;
    map.put(KEY_RES, VALUE_RES_SUCCESS);

    return map;
  }
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void _startRecordingScreen() {
    quickSettings();
    MediaProjectionManager mediaProjectionManager = (MediaProjectionManager) activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
    Intent permissionIntent = mediaProjectionManager != null ? mediaProjectionManager.createScreenCaptureIntent() : null;
    activity.startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void quickSettings() {
    hbRecorder.setAudioBitrate(128000);
    hbRecorder.setAudioSamplingRate(44100);
    hbRecorder.recordHDVideo(wasHDSelected);
    hbRecorder.isAudioEnabled(isAudioEnabled);
    //Customise Notification
//    hbRecorder.setNotificationSmallIcon(drawable2ByteArray(R.drawable.icon));
    hbRecorder.setNotificationTitle("fatboyli Recording your screen");
    hbRecorder.setNotificationDescription("Some settings are not supported by your device");
  }


  //For Android 10> we will pass a Uri to HBRecorder
  //This is not necessary - You can still use getExternalStoragePublicDirectory
  //But then you will have to add android:requestLegacyExternalStorage="true" in your Manifest
  //IT IS IMPORTANT TO SET THE FILE NAME THE SAME AS THE NAME YOU USE FOR TITLE AND DISPLAY_NAME
  ContentResolver resolver;
  ContentValues contentValues;
  Uri mUri;
  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void setOutputPath() {
    String filename = generateFileName();
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      resolver = activity.getContentResolver();
      contentValues = new ContentValues();
      contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/" + "HBRecorder");
      contentValues.put(MediaStore.Video.Media.TITLE, filename);
      contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);
      contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
      mUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);
      //FILE NAME SHOULD BE THE SAME
      this.filePath = mUri.getPath();
      hbRecorder.setFileName(filename);
      hbRecorder.setOutputUri(mUri);
    }else{
      createFolder();
      hbRecorder.setOutputPath(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) +"/HBRecorder");
      this.filePath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES) +"/HBRecorder"+hbRecorder.getFileName();
    }
  }

  //Generate a timestamp to be used as a file name
  private String generateFileName() {
    SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault());
    Date curDate = new Date(System.currentTimeMillis());
    return formatter.format(curDate).replace(" ", "");
  }
  private void createFolder() {
    File f1 = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "HBRecorder");
    if (!f1.exists()) {
      if (f1.mkdirs()) {
        Log.i("Folder ", "created");
      }
    }
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void onAttachedToActivity(@NonNull  ActivityPluginBinding binding) {
    activity =  binding.getActivity();
    binding.addActivityResultListener(new PluginRegistry.ActivityResultListener() {
      @Override
      public boolean onActivityResult(int requestCode, int resultCode, Intent data) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
              //Set file path or Uri depending on SDK version
              setOutputPath();
              //Start screen recording
              hbRecorder.startScreenRecording(data, resultCode, activity);

            }
          }
        }
        return false;
      }
    }
    );
    binding.addRequestPermissionsResultListener(new PluginRegistry.RequestPermissionsResultListener() {
      @Override
      public boolean onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
          case PERMISSION_REQ_ID_RECORD_AUDIO:
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE);
            } else {
              hasPermissions = false;
              showLongToast("No permission for " + Manifest.permission.RECORD_AUDIO);
            }
            break;
          case PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE:
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
              hasPermissions = true;
              //Permissions was provided
              //Start screen recording
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                _startRecordingScreen();
              }
            } else {
              hasPermissions = false;
              showLongToast("No permission for " + Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            break;
          default:
            break;

        }
        return false;
      }
    });
    if(hbRecorder == null)
    {
      hbRecorder =  new HBRecorder(activity, this);
    }
    HBRecorderCodecInfo hbRecorderCodecInfo = new HBRecorderCodecInfo();
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
      int mWidth = hbRecorder.getDefaultWidth();
      int mHeight = hbRecorder.getDefaultHeight();
      String mMimeType = "video/avc";
      int mFPS = 30;
      if (hbRecorderCodecInfo.isMimeTypeSupported(mMimeType)) {
        String defaultVideoEncoder = hbRecorderCodecInfo.getDefaultVideoEncoderName(mMimeType);
        boolean isSizeAndFramerateSupported = hbRecorderCodecInfo.isSizeAndFramerateSupported(mWidth, mHeight, mFPS, mMimeType, ORIENTATION_PORTRAIT);
        Log.e("EXAMPLE", "THIS IS AN EXAMPLE OF HOW TO USE THE (HBRecorderCodecInfo) TO GET CODEC INFO:");
        Log.e("HBRecorderCodecInfo", "defaultVideoEncoder for (" + mMimeType + ") -> " + defaultVideoEncoder);
        Log.e("HBRecorderCodecInfo", "MaxSupportedFrameRate -> " + hbRecorderCodecInfo.getMaxSupportedFrameRate(mWidth, mHeight, mMimeType));
        Log.e("HBRecorderCodecInfo", "MaxSupportedBitrate -> " + hbRecorderCodecInfo.getMaxSupportedBitrate(mMimeType));
        Log.e("HBRecorderCodecInfo", "isSizeAndFramerateSupported @ Width = "+mWidth+" Height = "+mHeight+" FPS = "+mFPS+" -> " + isSizeAndFramerateSupported);
        Log.e("HBRecorderCodecInfo", "isSizeSupported @ Width = "+mWidth+" Height = "+mHeight+" -> " + hbRecorderCodecInfo.isSizeSupported(mWidth, mHeight, mMimeType));
        Log.e("HBRecorderCodecInfo", "Default Video Format = " + hbRecorderCodecInfo.getDefaultVideoFormat());

        HashMap<String, String> supportedVideoMimeTypes = hbRecorderCodecInfo.getSupportedVideoMimeTypes();
        for (Map.Entry<String, String> entry : supportedVideoMimeTypes.entrySet()) {
          Log.e("HBRecorderCodecInfo", "Supported VIDEO encoders and mime types : " + entry.getKey() + " -> " + entry.getValue());
        }

        HashMap<String, String> supportedAudioMimeTypes = hbRecorderCodecInfo.getSupportedAudioMimeTypes();
        for (Map.Entry<String, String> entry : supportedAudioMimeTypes.entrySet()) {
          Log.e("HBRecorderCodecInfo", "Supported AUDIO encoders and mime types : " + entry.getKey() + " -> " + entry.getValue());
        }

        ArrayList<String> supportedVideoFormats = hbRecorderCodecInfo.getSupportedVideoFormats();
        for (int j = 0; j < supportedVideoFormats.size(); j++) {
          Log.e("HBRecorderCodecInfo", "Available Video Formats : " + supportedVideoFormats.get(j));
        }
      }else{
        Log.e("HBRecorderCodecInfo", "MimeType not supported");
      }

    }
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {

  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull  ActivityPluginBinding binding) {

  }

  @Override
  public void onDetachedFromActivity() {

  }

  @Override
  public void HBRecorderOnStart() {
    
  }

  @Override
  public void HBRecorderOnComplete() {
//      showLongToast("Saved Successfully");

  }
  @Override
  public void HBRecorderOnError(int errorCode, String reason) {

      // Error 38 happens when
      // - the selected video encoder is not supported
      // - the output format is not supported
      // - if another app is using the microphone

      //It is best to use device default

      if (errorCode == SETTINGS_ERROR) {
        showLongToast("部分设置不支持");
      } else if ( errorCode == MAX_FILE_SIZE_REACHED_ERROR) {
        showLongToast("文件已达到最大");
      } else {
        showLongToast("录音失败");
        Log.e("HBRecorderOnError", reason);
      }
    channel.invokeMethod("onRecordError",errorCode);

  }
  //Show Toast
  private void showLongToast(final String msg) {
    Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
  }

//  //drawable to byte[]
//  private byte[] drawable2ByteArray(@DrawableRes int drawableId) {
//    Bitmap icon = BitmapFactory.decodeResource(getResources(), drawableId);
//    ByteArrayOutputStream stream = new ByteArrayOutputStream();
//    icon.compress(Bitmap.CompressFormat.PNG, 100, stream);
//    return stream.toByteArray();
//  }
}
