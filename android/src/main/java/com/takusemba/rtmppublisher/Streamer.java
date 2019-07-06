package com.takusemba.rtmppublisher;

import android.opengl.EGLContext;

public class Streamer
        implements VideoHandler.OnVideoEncoderStateListener, AudioHandler.OnAudioEncoderStateListener {

    private VideoHandler videoHandler;
    private AudioHandler audioHandler;
    private Muxer muxer = new Muxer();

    public Streamer() {
        this.videoHandler = new VideoHandler();
        this.audioHandler = new AudioHandler();
    }

    public void open(String url, int width, int height) {
        muxer.open(url, width, height);
    }

    public void startStreaming(EGLContext context, int width, int height, int audioBitrate,
                        int videoBitrate) {
        if (muxer.isConnected()) {
            long startStreamingAt = System.currentTimeMillis();
            videoHandler.setOnVideoEncoderStateListener(this);
            audioHandler.setOnAudioEncoderStateListener(this);
            videoHandler.start(width, height, videoBitrate, context, startStreamingAt);
            audioHandler.start(audioBitrate, startStreamingAt);
        }
    }

    public void stopStreaming() {
        videoHandler.stop();
        audioHandler.stop();
        muxer.close();
    }

    public boolean isStreaming() {
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
