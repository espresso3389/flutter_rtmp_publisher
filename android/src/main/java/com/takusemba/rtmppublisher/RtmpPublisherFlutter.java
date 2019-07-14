package com.takusemba.rtmppublisher;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.opengl.EGL14;
import android.opengl.EGLContext;

import io.flutter.view.TextureRegistry;
import jp.espresso3389.video_player.SurfaceTextureForwarder;

public class RtmpPublisherFlutter {
  private SurfaceTextureForwarder ftex;
  private Activity activity;
  private TextureRegistry.SurfaceTextureEntry flutterTexture;
  private CameraClient camera;
  private Streamer streamer;
  private int width;
  private int height;
  private int fps;
  private CameraMode mode = CameraMode.BACK;
  private Camera.Size cameraSize;
  private int audioBitrate;
  private int videoBitrate;
  private boolean pausing = false;

  public RtmpPublisherFlutter(Activity activity,
                       TextureRegistry.SurfaceTextureEntry flutterTexture,
                       PublisherListener listener) {

    this.activity = activity;
    this.flutterTexture = flutterTexture;

    this.streamer = new Streamer();
    this.streamer.setMuxerListener(listener);

    initSurface();
  }

  void initCamera() {
    if (this.camera == null) {
      this.camera = new CameraClient(activity, mode);
    } else if (this.camera.getMode() != mode) {
      this.camera.swap();
    }
  }

  public void switchCamera() {
    camera.swap();
  }

  public void setCaptureConfig(int width, int height, int fps, CameraMode mode, int audioBitrate, int videoBitrate) {
    this.width = width;
    this.height = height;
    this.fps = fps;
    this.mode = mode;
    initCamera();

    this.audioBitrate = audioBitrate;
    this.videoBitrate = videoBitrate;
  }

  public void startPublishing(String url) {
    streamer.open(url, width, height);
    final EGLContext context = EGL14.eglGetCurrentContext();
    streamer.startStreaming(context, width, height, audioBitrate, videoBitrate);
    pausing = false;
  }

  public void stopPublishing() {
    if (streamer.isStreaming()) {
      streamer.stopStreaming();
    }
  }

  public boolean isPublishing() {
    return streamer.isStreaming();
  }

  public Camera.Size getCameraSize() { return cameraSize; }

  void initSurface() {
    if (ftex != null)
      return;

    initCamera();

    Camera.Parameters params = camera.open();
    cameraSize = params.getPreviewSize();

    ftex = new SurfaceTextureForwarder(flutterTexture.surfaceTexture());
    ftex.setupDestSurface(cameraSize.width, cameraSize.height);

    ftex.setOnFrameDrawn(new SurfaceTextureForwarder.OnFrameDrawn() {
      @Override
      public void onFrameDrawn(int textureId, SurfaceTexture surfaceTexture) {
        if (pausing)
          return;
        float[] tx = new float[16];
        surfaceTexture.getTransformMatrix(tx);
        streamer.getVideoHandlerListener().onFrameDrawn(textureId, tx, surfaceTexture.getTimestamp());
      }
    });

    camera.startPreview(ftex.getSurfaceTexture());
  }

  public void resume() {
    pausing = false;
  }

  public void pause() {
    pausing = true;
  }

  public void deinitSurface() {
    if (camera != null) {
      camera.close();
    }

    if (ftex != null) {
      ftex.release();
      ftex = null;
    }

    if (streamer.isStreaming()) {
      streamer.stopStreaming();
      streamer = null;
    }
  }

  public void release() {
    deinitSurface();
    camera = null;
  }

  // should be explicitly called by Flutter code
  public void onResume() {
    initSurface();
  }

  // should be explicitly called by Flutter code
  public void onPause() {
    deinitSurface();
  }
}
