package com.takusemba.rtmppublisher;

import android.os.Handler;
import android.os.HandlerThread;

class AudioHandler implements AudioRecorder.OnAudioRecorderStateChangedListener {

  private static final int SAMPLE_RATE = 44100;

  private AudioEncoder audioEncoder;
  private AudioRecorder audioRecorder;

  interface OnAudioEncoderStateListener {
    void onAudioDataEncoded(byte[] data, int size, int timestamp);
    void onAudioError(Exception e);
  }

  AudioHandler.OnAudioEncoderStateListener listener;

  void setOnAudioEncoderStateListener(AudioHandler.OnAudioEncoderStateListener listener) {
    audioEncoder.setOnAudioEncoderStateListener(listener);
    this.listener = listener;
  }

  public void onAudioError(Exception e) {
    if (listener != null)
      listener.onAudioError(e);
  }

  AudioHandler() {
    audioEncoder = new AudioEncoder();
    audioRecorder = new AudioRecorder(SAMPLE_RATE);
    audioRecorder.setOnAudioRecorderStateChangedListener(this);
  }

  void start(final int bitrate, final long startStreamingAt) {
    audioEncoder.prepare(bitrate, SAMPLE_RATE, startStreamingAt);
    audioEncoder.start();
    audioRecorder.start();
  }

  void stop() {
    if (audioRecorder.isRecording()) {
      audioRecorder.stop();
    }
    if (audioEncoder.isEncoding()) {
      audioEncoder.stop();
    }
  }

  @Override
  public void onAudioRecorded(final byte[] data, final int length) {
    audioEncoder.enqueueData(data, length);
  }
}
