package com.takusemba.rtmppublisher;

import android.opengl.EGLContext;
import android.os.Handler;
import android.util.Log;

class Streamer
  implements Muxer.StatusListener, VideoHandler.OnVideoEncoderStateListener, AudioHandler.OnAudioEncoderStateListener {

  public interface StreamerListener {
    void onError(String component, Exception e);
  }

  private Handler handler;
  private StreamerListener listener;
  private Muxer.StatusListener muxerListener;
  private VideoHandler videoHandler;
  private AudioHandler audioHandler;
  private Muxer muxer;
  private boolean paused = false;

  Streamer() {
    muxer = new Muxer();
    muxer.setOnMuxerStateListener(this);
    this.videoHandler = new VideoHandler();
    this.audioHandler = new AudioHandler();
  }

  void open(String url, int width, int height) {
    Log.i("Streamer", String.format("open: %d x %d", width, height));
    handler = new Handler();
    muxer.open(url, width, height);
  }

  void startStreaming(EGLContext context, int width, int height, int fps, int audioBitrate,
                      int videoBitrate) {
    Log.i("Streamer", String.format("startStreaming: %d x %d", width, height));
    paused = false;
    if (muxer.isConnected()) {
      long startStreamingAt = System.currentTimeMillis();
      videoHandler.setOnVideoEncoderStateListener(this);
      audioHandler.setOnAudioEncoderStateListener(this);
      videoHandler.start(width, height, fps, videoBitrate, context, startStreamingAt);
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
  public void onVideoDataEncoded(byte[] data, int offset, int size, int timestamp) {
    if (paused)
      return;
    muxer.sendVideo(data, offset, size, timestamp);
  }

  @Override
  public void onAudioDataEncoded(byte[] data, int offset, int size, int timestamp) {
    if (paused)
      return;
    muxer.sendAudio(data, offset, size, timestamp);
  }

  @Override
  public void onVideoError(Exception e) {
    notifyError("video", e);
  }

  @Override
  public void onAudioError(Exception e) {
    notifyError("audio", e);
  }

  private void notifyError(final String component, final Exception e) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        if (listener != null)
          listener.onError(component, e);
      }
    });
  }

  CameraSurfaceRenderer.OnRendererStateChangedListener getVideoHandlerListener() {
    return videoHandler;
  }

  void setMuxerListener(Muxer.StatusListener listener) {
    muxerListener = listener;
  }

  void setListener(StreamerListener listener) { this.listener = listener; }


  public void onConnected() {
    if (muxerListener != null)
      muxerListener.onConnected();
  }
  public void onFailedToConnect() {
    if (muxerListener != null)
      muxerListener.onFailedToConnect();
  }
  public void onPaused() {
    if (muxerListener != null)
      muxerListener.onPaused();
  }
  public void onResumed() {
    if (muxerListener != null)
      muxerListener.onResumed();
  }
  public void onDisconnected() {

    stopStreaming();

    if (muxerListener != null)
      muxerListener.onDisconnected();
  }
}
