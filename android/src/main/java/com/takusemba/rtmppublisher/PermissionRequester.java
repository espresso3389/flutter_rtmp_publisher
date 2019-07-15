package com.takusemba.rtmppublisher;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.flutter.plugin.common.PluginRegistry;

public class PermissionRequester implements PluginRegistry.RequestPermissionsResultListener {
  private Handler handler = new Handler();
  private int requestCode = 3424815;
  private PluginRegistry.Registrar registrar;
  private PermissionsGranted permissionsGranted;

  private PermissionRequester(PluginRegistry.Registrar registrar) {
    this.registrar = registrar;
    registrar.addRequestPermissionsResultListener(this);
  }

  public static abstract class PermissionsGranted implements Runnable {
    boolean allGranted;

    abstract void onResult(boolean allGranted);

    @Override
    public void run() {
      onResult(allGranted);
    }
  }

  private static PermissionRequester instance;

  public static void requestPermissions(PluginRegistry.Registrar registrar, String[] permissions, PermissionsGranted permissionsGranted) {
    if (instance == null) {
      instance = new PermissionRequester(registrar);
    }
    instance.requestPermission(permissions, permissionsGranted);
  }

  public static void requestPermission(PluginRegistry.Registrar registrar, String permission, PermissionsGranted permissionsGranted) {
    requestPermissions(registrar, new String[] { permission }, permissionsGranted);
  }

  private void requestPermission(String[] permissions, PermissionsGranted permissionsGranted) {
    List<String> perms = new ArrayList<String>();
    for (int i = 0; i < permissions.length; i++) {
      final String permission = permissions[i];
      int permissionCheck = ContextCompat.checkSelfPermission(registrar.activity(), permission);
      if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
        perms.add(permission);
      }
    }
    if (perms.isEmpty()) {
      permissionsGranted.allGranted = true;
      permissionsGranted.run();
      return;
    }

    this.permissionsGranted = permissionsGranted;
    ActivityCompat.requestPermissions(registrar.activity(), perms.toArray(new String[perms.size()]), requestCode);
  }

  @Override
  public boolean onRequestPermissionsResult(int id, String[] strings, int[] ints) {
    if (permissionsGranted != null && id == requestCode) {
      int count = 0;
      for (int i = 0; i < ints.length; i++) {
        if (ints[i] == PackageManager.PERMISSION_GRANTED)
          count++;
      }
      final PermissionsGranted tmp = permissionsGranted;
      permissionsGranted = null;
      tmp.allGranted = ints.length == count;
      handler.post(new Runnable() {
        @Override
        public void run() {
          tmp.run();
        }
      });
      return true;
    }
    return false;
  }
}
