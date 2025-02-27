package com.guichaguri.trackplayer.service;

import android.os.Binder;
import android.os.Bundle;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.media3.common.util.UnstableApi;

import com.facebook.react.bridge.Promise;
import com.guichaguri.trackplayer.service.metadata.MetadataManager;
import com.guichaguri.trackplayer.service.models.NowPlayingMetadata;
import com.guichaguri.trackplayer.service.player.ExoPlayback;

/**
 * @author Guichaguri
 */
@UnstableApi
public class MusicBinder extends Binder {

    private final MusicService service;
    private final MusicManager manager;

    public MusicBinder(MusicService service, MusicManager manager) {
        this.service = service;
        this.manager = manager;
    }

    public void post(Runnable r) {
        service.handler.post(r);
    }

    public ExoPlayback getPlayback() {
        ExoPlayback playback = manager.getPlayback();

        // TODO remove?
        if(playback == null) {
            playback = manager.createLocalPlayback(new Bundle());
            manager.switchPlayback(playback);
        }

        return playback;
    }

    public void setupPlayer(Bundle bundle, Promise promise) {
        manager.switchPlayback(manager.createLocalPlayback(bundle));
        promise.resolve(null);
    }

    public void updateOptions(Bundle bundle) {
        manager.setStopWithApp(bundle.getBoolean("stopWithApp", false));
        // manager.setAlwaysPauseOnInterruption(bundle.getBoolean("alwaysPauseOnInterruption", false));
        manager.getMetadata().updateOptions(bundle);
    }

    public void updateNowPlayingMetadata(NowPlayingMetadata nowPlaying, boolean isPlaying) {
        MetadataManager metadata = manager.getMetadata();

        // TODO elapsedTime
        metadata.updateMetadata(getPlayback(), nowPlaying, isPlaying);
        metadata.setActive(true);
    }

    public void clearNowPlayingMetadata() {
        manager.getMetadata().setActive(false);
    }

    public void updateNowPlayingTitles(long duration, String title, String artist, String album) {
      MetadataManager metadata = manager.getMetadata();
      metadata.updateNowPlayingTitles(getPlayback(), duration, title, artist, album);
      MediaSessionCompat session = metadata.getSession();
      if (session.isActive()) return;
      session.setActive(true);
    }

    public int getRatingType() {
        return manager.getMetadata().getRatingType();
    }

    public void destroy() {
        service.destroy();
        service.stopSelf();
    }

}
