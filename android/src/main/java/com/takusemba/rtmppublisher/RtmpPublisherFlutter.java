package com.takusemba.rtmppublisher;

import android.app.Activity;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.OnLifecycleEvent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;

import io.flutter.view.TextureRegistry;

public class RtmpPublisherFlutter implements Publisher, SurfaceTexture.OnFrameAvailableListener {
  FrameAndMaskToRgbaSurfaceTexture ftex;
  private TextureRegistry.SurfaceTextureEntry tex;
  private CameraClient camera;
  private Streamer streamer;

  private String url;
  private int width;
  private int height;
  private int audioBitrate;
  private int videoBitrate;

  RtmpPublisherFlutter(Activity activity,
                       TextureRegistry.SurfaceTextureEntry tex,
                       String url,
                       int width,
                       int height,
                       int audioBitrate,
                       int videoBitrate,
                       CameraMode mode,
                       PublisherListener listener) {

    this.tex = tex;
    this.url = url;
    this.width = width;
    this.height = height;
    this.audioBitrate = audioBitrate;
    this.videoBitrate = videoBitrate;

    this.camera = new CameraClient(activity, mode);
    this.streamer = new Streamer();
    this.streamer.setMuxerListener(listener);
  }

  @Override
  public void switchCamera() {
    camera.swap();
  }

  @Override
  public void startPublishing() {
    streamer.open(url, width, height);
    final EGLContext context = EGL14.eglGetCurrentContext();
    streamer.startStreaming(context, width, height, audioBitrate, videoBitrate);
  }

  @Override
  public void stopPublishing() {
    if (streamer.isStreaming()) {
      streamer.stopStreaming();
    }
  }

  @Override
  public boolean isPublishing() {
    return streamer.isStreaming();
  }

  // should be explicitly called by Flutter code
  public void onResume() {
    Camera.Parameters params = camera.open();
    final Camera.Size size = params.getPreviewSize();
    //glView.onResume();
    //renderer.setCameraPreviewSize(size.width, size.height);
  }

  // should be explicitly called by Flutter code
  public void onPause() {
    if (camera != null) {
      camera.close();
    }
    //glView.onPause();
    //renderer.pause();
    if (streamer.isStreaming()) {
      streamer.stopStreaming();
    }
  }

  public void onSurfaceCreated(SurfaceTexture surfaceTexture) {
    surfaceTexture.setOnFrameAvailableListener(this);
    camera.startPreview(surfaceTexture);
  }

  @Override
  public void onFrameAvailable(SurfaceTexture surfaceTexture) {
    //glView.requestRender();
  }
}
