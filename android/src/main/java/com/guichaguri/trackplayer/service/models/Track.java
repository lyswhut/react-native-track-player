package com.guichaguri.trackplayer.service.models;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_MEDIA_URI;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;

import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.util.Util;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DataSpec;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.datasource.RawResourceDataSource;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.dash.DefaultDashChunkSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.smoothstreaming.DefaultSsChunkSource;
import androidx.media3.exoplayer.smoothstreaming.SsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.extractor.DefaultExtractorsFactory;

import com.guichaguri.trackplayer.service.Utils;
import com.guichaguri.trackplayer.service.player.LocalPlayback;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Guichaguri
 */
@UnstableApi
public class Track extends TrackMetadata {

    public static List<Track> createTracks(Context context, List objects, int ratingType) {
        List<Track> tracks = new ArrayList<>();

        for(Object o : objects) {
            if(o instanceof Bundle) {
                tracks.add(new Track(context, (Bundle)o, ratingType));
            } else {
                return null;
            }
        }

        return tracks;
    }

    public Uri uri;
    public int resourceId;

    public TrackType type = TrackType.DEFAULT;

    public String contentType;
    public String userAgent;

    public Bundle originalItem;

    public Map<String, String> headers;

    public final long queueId;

    public Track(Context context, Bundle bundle, int ratingType) {
        resourceId = Utils.getRawResourceId(context, bundle, "url");

        if(resourceId == 0) {
            uri = Utils.getUri(context, bundle, "url");
        } else {
            uri = new Uri.Builder().scheme(ContentResolver.SCHEME_ANDROID_RESOURCE).path(Integer.toString(resourceId)).build();
        }

        String trackType = bundle.getString("type", "default");

        for(TrackType t : TrackType.values()) {
            if(t.name.equalsIgnoreCase(trackType)) {
                type = t;
                break;
            }
        }

        contentType = bundle.getString("contentType");
        userAgent = bundle.getString("userAgent");

        Bundle httpHeaders = bundle.getBundle("headers");
        if(httpHeaders != null) {
            headers = new HashMap<>();
            for(String header : httpHeaders.keySet()) {
                headers.put(header, httpHeaders.getString(header));
            }
        }

        setMetadata(context, bundle, ratingType);

        queueId = System.currentTimeMillis();
        originalItem = bundle;
    }

    @Override
    public void setMetadata(Context context, Bundle bundle, int ratingType) {
        super.setMetadata(context, bundle, ratingType);

        if (originalItem != null && originalItem != bundle)
            originalItem.putAll(bundle);
    }

    @Override
    public MediaMetadataCompat.Builder toMediaMetadata() {
        MediaMetadataCompat.Builder builder = super.toMediaMetadata();

        builder.putString(METADATA_KEY_MEDIA_URI, uri.toString());

        return builder;
    }

    public QueueItem toQueueItem() {
        MediaDescriptionCompat descr = new MediaDescriptionCompat.Builder()
                .setTitle(title)
                .setSubtitle(artist)
                .setMediaUri(uri)
                .setIconUri(artwork)
                .build();

        return new QueueItem(descr, queueId);
    }

    public MediaSource toMediaSource(Context ctx, LocalPlayback playback) {
        // Updates the user agent if not set
        if(userAgent == null || userAgent.isEmpty())
            userAgent = Util.getUserAgent(ctx, "react-native-track-player");

        DataSource.Factory ds;

        if(resourceId != 0) {

            try {
                RawResourceDataSource raw = new RawResourceDataSource(ctx);
                raw.open(new DataSpec(uri));
                ds = () -> raw;
            } catch(IOException ex) {
                // Should never happen
                throw new RuntimeException(ex);
            }

        } else if(Utils.isLocal(uri)) {

            // Creates a local source factory
            ds = new DefaultDataSource.Factory(ctx);

        } else {

            // Creates a default http source factory, enabling cross protocol redirects
            DefaultHttpDataSource.Factory factory = new DefaultHttpDataSource.Factory()
                    .setUserAgent(userAgent)
                    .setConnectTimeoutMs(DefaultHttpDataSource.DEFAULT_CONNECT_TIMEOUT_MILLIS)
                    .setReadTimeoutMs(DefaultHttpDataSource.DEFAULT_READ_TIMEOUT_MILLIS)
                    .setAllowCrossProtocolRedirects(true);

            if(headers != null) {
                factory.setDefaultRequestProperties(headers);
            }

            ds = playback.enableCaching(factory);

        }

        switch(type) {
            case DASH:
                return createDashSource(ds);
            case HLS:
                return createHlsSource(ds);
            case SMOOTH_STREAMING:
                return createSsSource(ds);
            default:
                return new ProgressiveMediaSource.Factory(ds, new DefaultExtractorsFactory()
                        .setConstantBitrateSeekingEnabled(true))
                        .createMediaSource(MediaItem.fromUri(uri));
        }
    }

    private MediaSource createDashSource(DataSource.Factory factory) {
        return new DashMediaSource.Factory(new DefaultDashChunkSource.Factory(factory), factory)
                .createMediaSource(MediaItem.fromUri(uri));
    }

    private MediaSource createHlsSource(DataSource.Factory factory) {
        return new HlsMediaSource.Factory(factory)
                .createMediaSource(MediaItem.fromUri(uri));
    }

    private MediaSource createSsSource(DataSource.Factory factory) {
        return new SsMediaSource.Factory(new DefaultSsChunkSource.Factory(factory), factory)
                .createMediaSource(MediaItem.fromUri(uri));
    }

}
