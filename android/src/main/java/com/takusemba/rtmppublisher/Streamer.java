package com.takusemba.rtmppublisher;

import android.opengl.EGLContext;

class Streamer
  implements VideoHandler.OnVideoEncoderStateListener, AudioHandler.OnAudioEncoderStateListener {

  private VideoHandler videoHandler;
  private AudioHandler audioHandler;
  private Muxer muxer = new Muxer();
  private boolean paused = false;

  Streamer() {
    this.videoHandler = new VideoHandler();
    this.audioHandler = new AudioHandler();
  }

  void open(String url, int width, int height) {
    muxer.open(url, width, height);
  }

  void startStreaming(EGLContext context, int width, int height, int audioBitrate,
                      int videoBitrate) {
    paused = false;
    if (muxer.isConnected()) {
      long startStreamingAt = System.currentTimeMillis();
      videoHandler.setOnVideoEncoderStateListener(this);
      audioHandler.setOnAudioEncoderStateListener(this);
      videoHandler.start(width, height, videoBitrate, context, startStreamingAt);
      audioHandler.start(audioBitrate, startStreamingAt);
    }
  }

  void stopStreaming() {
    videoHandler.stop();
    audioHandler.stop();
    muxer.close();
  }

  boolean isStreaming() {
    return muxer.isConnected();
  }

  boolean isPaused() { return paused && isStreaming(); }

  void resume() {
    paused = false;
  }

  void pause() {
    paused = true;
  }

  @Override
  public void onVideoDataEncoded(byte[] data, int size, int timestamp) {
    if (paused)
      return;
    muxer.sendVideo(data, size, timestamp);
  }

  @Override
  public void onAudioDataEncoded(byte[] data, int size, int timestamp) {
    if (paused)
      return;
    muxer.sendAudio(data, size, timestamp);
  }

  CameraSurfaceRenderer.OnRendererStateChangedListener getVideoHandlerListener() {
    return videoHandler;
  }

  void setMuxerListener(Muxer.StatusListener listener) {
    muxer.setOnMuxerStateListener(listener);
  }

}
