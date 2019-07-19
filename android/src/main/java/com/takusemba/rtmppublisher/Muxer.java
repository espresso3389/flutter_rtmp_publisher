package com.takusemba.rtmppublisher;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import net.butterflytv.rtmp_client.RTMPMuxer;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public class Muxer {

  private final Handler uiHandler = new Handler(Looper.getMainLooper());
  private RTMPMuxer rtmpMuxer = new RTMPMuxer();
  private StatusListener listener;

  private HandlerThread muxerThread;
  private Handler muxerThreadHandler;

  private boolean disconnected = false;
  private AtomicBoolean paused = new AtomicBoolean();

  public void setOnMuxerStateListener(StatusListener listener) {
    this.listener = listener;
  }

  public Muxer() {
  }

  private boolean ensureConnected() {
    if (isConnectedNoHandler())
      return true;
    postDisconnected();
    return false;
  }

  private void postDisconnected() {
    if (listener != null) {
      uiHandler.post(new Runnable() {
        @Override
        public void run() {
          if (disconnected) return;
          listener.onDisconnected();
          disconnected = true;
        }
      });
    }
  }

  public void open(final String url, final int width, final int height) {
    closeInternal();
    if (muxerThread == null) {
      muxerThread = new HandlerThread("Muxer");
      muxerThread.start();
      muxerThreadHandler = new Handler(muxerThread.getLooper());
    }
    muxerThreadHandler.post(new Runnable() {
      @Override
      public void run() {
        rtmpMuxer.open(url, width, height);
        if (listener != null) {
          uiHandler.post(new Runnable() {
            @Override
            public void run() {
              if (isConnectedNoHandler()) {
                listener.onConnected();
                disconnected = false;
              } else {
                listener.onFailedToConnect();
              }
            }
          });
        }
      }
    });
  }

  public void sendVideo(final byte[] data, final int length, final int timestamp) {
    if (muxerThreadHandler == null)
      return;
    muxerThreadHandler.post(new Runnable() {
      @Override
      public void run() {
        if (ensureConnected() && !paused.get())
          rtmpMuxer.writeVideo(data, 0, length, timestamp);
      }
    });
  }

  public void sendAudio(final byte[] data, final int length, final int timestamp) {
    if (muxerThreadHandler == null)
      return;
    muxerThreadHandler.post(new Runnable() {
      @Override
      public void run() {
        if (ensureConnected() && !paused.get())
          rtmpMuxer.writeAudio(data, 0, length, timestamp);
      }
    });
  }

  private void closeInternal() {
    if (muxerThreadHandler == null)
      return;
    muxerThreadHandler.post(new Runnable() {
      @Override
      public void run() {
        rtmpMuxer.close();
      }
    });
  }

  public void close() {
    closeInternal();
    muxerThreadHandler = null;
    muxerThread.quitSafely();
    muxerThread = null;
    postDisconnected();
  }

  public void pause() {
    if (paused.getAndSet(true) == false) {
      if (listener != null) {
        uiHandler.post(new Runnable() {
          @Override
          public void run() {
            listener.onPaused();
          }
        });
      }
    }
  }

  public void resume() {
    if (paused.getAndSet(false) == true) {
      if (listener != null) {
        uiHandler.post(new Runnable() {
          @Override
          public void run() {
            listener.onResumed();
          }
        });
      }
    }
  }

  public boolean isConnected() {
    if (muxerThread == null)
      return false; // apparently, we don't connect to any server
    try {
      final AtomicBoolean ret = new AtomicBoolean();
      final CountDownLatch latch = new CountDownLatch(1);
      muxerThreadHandler.post(new Runnable() {
        @Override
        public void run() {
          ret.set(isConnectedNoHandler());
          latch.countDown();
        }
      });
      latch.await();
      return ret.get();
    } catch (InterruptedException e) {
      return false; // FIXME
    }
  }

  private boolean isConnectedNoHandler() {
    return rtmpMuxer.isConnected() != 0;
  }

  public interface StatusListener {
    void onConnected();
    void onFailedToConnect();
    void onPaused();
    void onResumed();
    void onDisconnected();
  }
}
