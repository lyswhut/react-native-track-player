package com.guichaguri.trackplayer.service.player;

import static com.google.android.exoplayer2.Player.PLAYBACK_SUPPRESSION_REASON_NONE;
import static com.google.android.exoplayer2.Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS;
import static com.google.android.exoplayer2.Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY;
import static com.google.android.exoplayer2.Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS;

import android.content.Context;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.Timeline.Window;
import com.google.android.exoplayer2.Tracks;
import com.google.android.exoplayer2.metadata.Metadata;
import com.google.android.exoplayer2.metadata.MetadataOutput;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.common.collect.ImmutableList;
import com.guichaguri.trackplayer.service.MusicManager;
import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.models.Track;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * @author Guichaguri
 */
public abstract class ExoPlayback<T extends Player> implements Player.Listener, MetadataOutput {

    protected final Context context;
    protected final MusicManager manager;
    protected final T player;

    protected List<Track> queue = Collections.synchronizedList(new ArrayList<>());

    // https://github.com/google/ExoPlayer/issues/2728
    protected int lastKnownWindow = C.INDEX_UNSET;
    protected long lastKnownPosition = C.POSITION_UNSET;
    protected int previousState = PlaybackStateCompat.STATE_NONE;
    protected float volumeMultiplier = 1.0F;
    protected boolean autoUpdateMetadata;

    public ExoPlayback(Context context, MusicManager manager, T player, boolean autoUpdateMetadata) {
        this.context = context;
        this.manager = manager;
        this.player = player;
        this.autoUpdateMetadata = autoUpdateMetadata;

        // Player.MetadataComponent component = player.getMetadataComponent();
        // if(component != null) component.addMetadataOutput(this);
    }

    public void initialize() {
        player.addListener(this);
    }

    public List<Track> getQueue() {
        return queue;
    }

    public abstract void add(Track track, int index, Promise promise);

    public abstract void add(Collection<Track> tracks, int index, Promise promise);

    public abstract void remove(List<Integer> indexes, Promise promise);

    public abstract void removeUpcomingTracks();

    public abstract void enableAudioOffload(boolean enabled);

    public abstract void setRepeatMode(int repeatMode);

    public abstract int getRepeatMode();

    public void updateTrack(int index, Track track) {
        int currentIndex = player.getCurrentMediaItemIndex();

        queue.set(index, track);

        if(currentIndex == index)
            manager.getMetadata().updateMetadata(this, track, Utils.isPlaying(getState()));
    }

    public Integer getCurrentTrackIndex() {
        int index = player.getCurrentMediaItemIndex();
        return index < 0 || index >= queue.size() ? null : index;
    }

    public Track getCurrentTrack() {
        int index = player.getCurrentMediaItemIndex();
        return index < 0 || index >= queue.size() ? null : queue.get(index);
    }

    public void skip(int index, Promise promise) {
        if(index < 0 || index >= queue.size()) {
            promise.reject("index_out_of_bounds", "The index is out of bounds");
            return;
        }

        // lastKnownWindow = player.getCurrentWindowIndex();
        // lastKnownPosition = player.getCurrentPosition();

        player.seekToDefaultPosition(index);
        promise.resolve(null);
    }

    public void skipToPrevious(Promise promise) {
        int prev = player.getPreviousMediaItemIndex();

        if(prev == C.INDEX_UNSET) {
            promise.reject("no_previous_track", "There is no previous track");
            return;
        }

        // lastKnownWindow = player.getCurrentWindowIndex();
        // lastKnownPosition = player.getCurrentPosition();

        player.seekToDefaultPosition(prev);
        promise.resolve(null);
    }

    public void skipToNext(Promise promise) {
        int next = player.getNextMediaItemIndex();

        if(next == C.INDEX_UNSET) {
            promise.reject("queue_exhausted", "There is no tracks left to play");
            return;
        }

        // lastKnownWindow = player.getCurrentWindowIndex();
        // lastKnownPosition = player.getCurrentPosition();

        player.seekToDefaultPosition(next);
        promise.resolve(null);
    }

    public void play() {
        player.setPlayWhenReady(true);
    }

    public void pause() {
        player.setPlayWhenReady(false);
    }

    public void stop() {
        lastKnownWindow = C.INDEX_UNSET;
        lastKnownPosition = C.POSITION_UNSET;

        player.stop();
        player.setPlayWhenReady(false);
        player.seekTo(0);
    }

    public void reset() {
        lastKnownWindow = C.INDEX_UNSET;
        lastKnownPosition = C.POSITION_UNSET;

        player.stop();
        player.clearMediaItems();
        player.setPlayWhenReady(false);
    }

    public boolean isRemote() {
        return false;
    }

    public boolean shouldAutoUpdateMetadata() {
        return autoUpdateMetadata;
    }

    public long getPosition() {
        return player.getCurrentPosition();
    }

    public long getBufferedPosition() {
        return player.getBufferedPosition();
    }

    public long getDuration() {
        Track current = getCurrentTrack();

        if (current != null && current.duration > 0) {
            return current.duration;
        }

        long duration = player.getDuration();

        return duration == C.TIME_UNSET ? 0 : duration;
    }

    public void seekTo(long time) {
        if (queue.size() < 1) return;
        lastKnownWindow = player.getCurrentMediaItemIndex();
        lastKnownPosition = player.getCurrentPosition();

        player.seekTo(time);
    }

    public float getVolume() {
        return getPlayerVolume() / volumeMultiplier;
    }

    public void setVolume(float volume) {
        setPlayerVolume(volume * volumeMultiplier);
    }

    public void setVolumeMultiplier(float multiplier) {
        setPlayerVolume(getVolume() * multiplier);
        this.volumeMultiplier = multiplier;
    }

    public abstract float getPlayerVolume();

    public abstract void setPlayerVolume(float volume);

    public float getRate() {
        return player.getPlaybackParameters().speed;
    }

    public void setRate(float rate) {
        player.setPlaybackParameters(new PlaybackParameters(rate, player.getPlaybackParameters().pitch));
    }

    public int getState() {
        switch(player.getPlaybackState()) {
            case Player.STATE_BUFFERING:
                return player.getPlayWhenReady() ? PlaybackStateCompat.STATE_BUFFERING : PlaybackStateCompat.STATE_CONNECTING;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_STOPPED;
            case Player.STATE_IDLE:
                return PlaybackStateCompat.STATE_NONE;
            case Player.STATE_READY:
                return player.getPlayWhenReady() && player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        }
        return PlaybackStateCompat.STATE_NONE;
    }

    public void destroy() {
        player.release();
    }

    @Override
    public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
        if(lastKnownWindow != player.getCurrentMediaItemIndex()) {
            Integer prevIndex = lastKnownWindow == C.INDEX_UNSET ? null : lastKnownWindow;
            Integer nextIndex = getCurrentTrackIndex();
            Track next = nextIndex == null ? null : queue.get(nextIndex);

            // Track changed because it ended
            // We'll use its duration instead of the last known position
            if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION && lastKnownWindow != C.INDEX_UNSET) {
                if (lastKnownWindow >= player.getCurrentTimeline().getWindowCount()) return;
                long duration = player.getCurrentTimeline().getWindow(lastKnownWindow, new Window()).getDurationMs();
                if(duration != C.TIME_UNSET) lastKnownPosition = duration;
            }

            manager.onTrackUpdate(prevIndex, lastKnownPosition, nextIndex, next);
        }
        lastKnownWindow = player.getCurrentMediaItemIndex();
        lastKnownPosition = player.getCurrentPosition();
    }

    @Override
    public void onTracksChanged(Tracks tracksInfo) {
      ImmutableList<Tracks.Group> trackGroupsInfo = tracksInfo.getGroups();

      for(int i = 0; i < trackGroupsInfo.size(); i++) {
        // Loop through all track groups.
        // As for the current implementation, there should be only one
        TrackGroup group = trackGroupsInfo.get(i).getMediaTrackGroup();
        for(int f = 0; f < group.length; f++) {
          // Loop through all formats inside the track group
          Format format = group.getFormat(f);

          // Parse the metadata if it is present
          if (format.metadata != null) {
            onMetadata(format.metadata);
          }
        }
      }
    }

    // @Override
    // public void onLoadingChanged(boolean isLoading) {
    //     // Buffering updates
    // }

    @Override
    public void onPlaybackStateChanged(int state) {
        handlePlaybackStateChange();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        handlePlaybackStateChange();
        Log.d(Utils.LOG, "reason: " + reason);


        switch (reason) {
            case PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS:
            manager.onAudioFocusChange(true, true, false);
            break;
            case PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY:
            manager.onAudioFocusChange(false, true, false);
            break;
        }
    }

    @Override
    public void onPlaybackSuppressionReasonChanged(int playbackSuppressionReason) {
        handlePlaybackStateChange();
        boolean ducking = false;

        switch (playbackSuppressionReason) {
            case PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS:
                ducking = true;
                break;
            case PLAYBACK_SUPPRESSION_REASON_NONE:
                break;
        }

        manager.onAudioFocusChange(false, ducking, ducking);
    }

    @Override
    public void onPlayerError(PlaybackException error) {
        String code;
        Throwable cause = error.getCause();
        if (cause instanceof HttpDataSource.HttpDataSourceException) {
            code = "playback-source";
        } else if (cause instanceof ExoPlaybackException) {
            code = "playback-renderer";
        } else {
            code = "playback"; // Other unexpected errors related to the playback
        }
        // if(error.type == ExoPlaybackException.TYPE_SOURCE) {
        //     code = "playback-source";
        // } else if(error.type == ExoPlaybackException.TYPE_RENDERER) {
        //    code = "playback-renderer";
        // } else {
        //     code = "playback"; // Other unexpected errors related to the playback
        // }

        manager.onError(code, Objects.requireNonNull(error.getCause()).getMessage());
    }

    @Override
    public void onPlaybackParametersChanged(@NonNull PlaybackParameters playbackParameters) {
        // Speed or pitch changes
    }

    // @Override
    // public void onSeekProcessed() {
    //     // Finished seeking
    // }

    private void handlePlaybackStateChange() {
        int state = getState();

        if(state != previousState) {
            if(Utils.isPlaying(state) && !Utils.isPlaying(previousState)) {
                manager.onPlay();
            } else if(Utils.isPaused(state) && !Utils.isPaused(previousState)) {
                manager.onPause();
            } else if(Utils.isStopped(state) && !Utils.isStopped(previousState)) {
                manager.onStop();
            }

            manager.onStateChange(state);
            previousState = state;

            if(state == PlaybackStateCompat.STATE_STOPPED) {
                Integer previous = getCurrentTrackIndex();
                long position = getPosition();
                manager.onTrackUpdate(previous, position, null, null);
                manager.onEnd(getCurrentTrackIndex(), getPosition());
            }
        }
    }

    @Override
    public void onMetadata(@NonNull Metadata metadata) {
        SourceMetadata.handleMetadata(manager, metadata);
    }
}
