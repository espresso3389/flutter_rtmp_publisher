package com.takusemba.rtmppublisher;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

class AudioEncoder implements Encoder {

  private final int TIMEOUT_USEC = 10000;

  private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
  private static final int CHANNEL_COUNT = 1;

  private MediaCodec encoder;
  private AudioEncoderThread encoderThread;

  private long startedEncodingAt = 0;
  private boolean isEncoding = false;
  private AudioHandler.OnAudioEncoderStateListener listener;

  void setOnAudioEncoderStateListener(AudioHandler.OnAudioEncoderStateListener listener) {
    this.listener = listener;
  }

  /**
   * prepare the Encoder. call this before start the encoder.
   */
  void prepare(int bitrate, int sampleRate, long startStreamingAt) {
    int bufferSize = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO,
      AudioFormat.ENCODING_PCM_16BIT);
    MediaFormat audioFormat =
      MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, sampleRate, CHANNEL_COUNT);
    audioFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
      MediaCodecInfo.CodecProfileLevel.AACObjectLC);
    audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
    audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize);
    startedEncodingAt = startStreamingAt;
    try {
      encoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
      encoder.configure(audioFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    } catch (IOException | IllegalStateException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void start() {
    encoder.start();
    isEncoding = true;
    encoderThread = new AudioEncoderThread();
    encoderThread.start();
  }

  @Override
  public void stop() {
    if (isEncoding) {
      int inputBufferId = encoder.dequeueInputBuffer(TIMEOUT_USEC);
      encoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
      try {
        encoderThread.join();
        Log.i("AudioRecorder", "Encoder thread terminated.");
      } catch (InterruptedException e) {
        Log.i("AudioEncoder", e.toString());
      }
    }
    encoderThread = null;
  }

  @Override
  public boolean isEncoding() {
    return encoder != null && isEncoding;
  }

  void enqueueData(byte[] data, int offset, int length) {
    if (encoder == null) return;
    final long timestamp = System.currentTimeMillis() - startedEncodingAt;
    while (length > 0) {
      final int inputBufferId = encoder.dequeueInputBuffer(TIMEOUT_USEC);
      if (inputBufferId >= 0) {
        ByteBuffer inputBuf = encoder.getInputBuffer(inputBufferId);
        inputBuf.clear();
        final int toWrite = Math.min(inputBuf.remaining(), length);
        inputBuf.put(data, offset, toWrite);
        encoder.queueInputBuffer(inputBufferId, 0, toWrite, timestamp * 1000, 0);
        offset += toWrite;
        length -= toWrite;
      }
    }
  }

  class AudioEncoderThread extends Thread {
    public void run() {
      try {
        byte[] buffer = null;
        final MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        while (isEncoding) {
          int outputBufferId = encoder.dequeueOutputBuffer(bufferInfo, TIMEOUT_USEC);
          if (outputBufferId >= 0) {
            ByteBuffer encodedData = encoder.getOutputBuffer(outputBufferId);
            if (encodedData == null) {
              continue;
            }

            encodedData.position(bufferInfo.offset);
            encodedData.limit(bufferInfo.offset + bufferInfo.size);

            final int length = encodedData.remaining();
            if (buffer == null || buffer.length < length) {
              buffer = new byte[length];
            }
            //encodedData.get(buffer);
            encodedData.get(buffer, 0, bufferInfo.size);
            encodedData.position(bufferInfo.offset);

            listener.onAudioDataEncoded(buffer, 0, length, (int) (System.currentTimeMillis() - startedEncodingAt));

            encoder.releaseOutputBuffer(outputBufferId, false);
          } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            // format should not be changed
          }
          if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            //end of stream
            break;
          }
        }
      } catch (Exception e) {
        onError(e);
      } finally {
        release();
      }
    }
  }

  private void release() {
    if (encoder != null) {
      isEncoding = false;
      try { encoder.stop(); } catch (Exception e) { onError(e); }
        try { encoder.release(); } catch (Exception e) { onError(e); }
      encoder = null;
    }
  }

  private void onError(Exception e) {
    if (listener != null)
      listener.onAudioError(e);
  }
}
