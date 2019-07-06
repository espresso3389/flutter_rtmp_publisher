package com.takusemba.rtmppublisher;

public interface PublisherListener {

    /**
     * Called when started publishing
     */
    void onStarted();

    /**
     * Called when stopped publishing
     */
    void onStopped();

    /**
     * Called when stream is disconnected
     */
    void onDisconnected();

    /**
     * Called when failed to connect
     */
    void onFailedToConnect();

}
