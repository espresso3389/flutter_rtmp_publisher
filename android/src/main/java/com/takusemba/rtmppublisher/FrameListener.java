package com.takusemba.rtmppublisher;


import android.graphics.SurfaceTexture;

public interface FrameListener {
  void onNewFrame(int textureId, float[] transform, long timestamp);
}
