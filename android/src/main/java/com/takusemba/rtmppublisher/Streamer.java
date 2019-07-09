package com.takusemba.rtmppublisher;

import android.opengl.EGLContext;

public class Streamer
  implements VideoHandler.OnVideoEncoderStateListener, AudioHandler.OnAudioEncoderStateListener {

  private VideoHandler videoHandler;
  private AudioHandler audioHandler;
  private Muxer muxer = new Muxer();

  public Streamer() {
  }

  public void open(String url, int width, int height) {
    muxer.open(url, width, height);
  }

  public void startStreaming(EGLContext context, int width, int height, int audioBitrate,
                             int videoBitrate) {
    if (muxer.isConnected()) {
      long startStreamingAt = System.currentTimeMillis();
      videoHandler = new VideoHandler();
      videoHandler.setOnVideoEncoderStateListener(this);
      audioHandler = new AudioHandler();
      audioHandler.setOnAudioEncoderStateListener(this);
      videoHandler.start(width, height, videoBitrate, context, startStreamingAt);
      audioHandler.start(audioBitrate, startStreamingAt);
    }
  }

  public void stopStreaming() {
    if (videoHandler != null) {
      videoHandler.stop();
      videoHandler = null;
    }
    if (audioHandler != null) {
      audioHandler.stop();
      audioHandler = null;
    }
  }

  public void release() {
    stopStreaming();
    muxer.close();
  }

  public boolean isStreaming() {
    return videoHandler != null;
  }

  public boolean isConnected() {
    return muxer.isConnected();
  }

  @Override
  public void onVideoDataEncoded(byte[] data, int size, int timestamp) {
    muxer.sendVideo(data, size, timestamp);
  }

  @Override
  public void onAudioDataEncoded(byte[] data, int size, int timestamp) {
    muxer.sendAudio(data, size, timestamp);
  }

  public FrameListener getVideoFrameListener() {
    return videoHandler;
  }

  public void setMuxerListener(PublisherListener listener) {
    muxer.setOnMuxerStateListener(listener);
  }
}
