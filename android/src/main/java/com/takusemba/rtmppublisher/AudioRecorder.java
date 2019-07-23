package com.takusemba.rtmppublisher;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

class AudioRecorder {

  private AudioRecord audioRecord;
  private final int sampleRate;
  private OnAudioRecorderStateChangedListener listener;
  private RecordingThread recordingThread;

  interface OnAudioRecorderStateChangedListener {
    void onAudioRecorded(byte[] data, int length);
    void onAudioError(Exception e);
  }

  void setOnAudioRecorderStateChangedListener(OnAudioRecorderStateChangedListener listener) {
    this.listener = listener;
  }

  AudioRecorder(int sampleRate) {
    this.sampleRate = sampleRate;
  }

  public void start() {
    final int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
    audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
    audioRecord.startRecording();
    recordingThread = new RecordingThread(bufferSize);
    recordingThread.start();
  }

  class RecordingThread extends Thread {
    public RecordingThread(int bufferSize) {
      this.bufferSize = bufferSize;
    }
    private int bufferSize;
    public void run() {
      try {
        int bufferReadResult;
        byte[] data = new byte[bufferSize];
        // keep running... so use a different thread.
        while (isRecording() && (bufferReadResult = audioRecord.read(data, 0, bufferSize)) > 0) {
          listener.onAudioRecorded(data, bufferReadResult);
        }
      } catch (Exception e) {
        onError(e);
      }
    }
  }

  void stop() {
    if (isRecording()) {
      audioRecord.stop();
      audioRecord.release();
      audioRecord = null;
    }
    if (recordingThread != null) {
      try {
        recordingThread.join();
        Log.i("AudioRecorder", "Recording thread terminated.");
      } catch (InterruptedException e) {
        onError(e);
      }
      recordingThread = null;
    }
  }

  void onError(Exception e) {
    if (listener != null)
      listener.onAudioError(e);
  }

  boolean isRecording() {
    return audioRecord != null
      && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING;
  }
}
