package com.guichaguri.trackplayer.service.player;

import androidx.media3.common.Metadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.container.MdtaMetadataEntry;
import androidx.media3.extractor.metadata.icy.IcyHeaders;
import androidx.media3.extractor.metadata.icy.IcyInfo;
import androidx.media3.extractor.metadata.id3.TextInformationFrame;
import androidx.media3.extractor.metadata.id3.UrlLinkFrame;
import androidx.media3.extractor.metadata.vorbis.VorbisComment;

import com.guichaguri.trackplayer.service.MusicManager;

import java.nio.charset.StandardCharsets;

@UnstableApi
public class SourceMetadata {

    /**
     * Reads metadata and triggers the metadata-received event
     */
    public static void handleMetadata(MusicManager manager, Metadata metadata) {
        handleId3Metadata(manager, metadata);
        handleIcyMetadata(manager, metadata);
        handleVorbisCommentMetadata(manager, metadata);
        handleQuickTimeMetadata(manager, metadata);
    }

    /**
     * ID3 Metadata (MP3)
     *
     * https://en.wikipedia.org/wiki/ID3
     */
    private static void handleId3Metadata(MusicManager manager, Metadata metadata) {
        String title = null, url = null, artist = null, album = null, date = null, genre = null;

        for(int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (entry instanceof TextInformationFrame) {
                // ID3 text tag
                TextInformationFrame id3 = (TextInformationFrame) entry;
                String id = id3.id.toUpperCase();

              switch (id) {
                case "TIT2":
                case "TT2":
                  title = id3.values.get(0);
                  break;
                case "TALB":
                case "TOAL":
                case "TAL":
                  album = id3.values.get(0);
                  break;
                case "TOPE":
                case "TPE1":
                case "TP1":
                  artist = id3.values.get(0);
                  break;
                case "TDRC":
                case "TOR":
                  date = id3.values.get(0);
                  break;
                case "TCON":
                case "TCO":
                  genre = id3.values.get(0);
                  break;
              }

            } else if (entry instanceof UrlLinkFrame) {
                // ID3 URL tag
                UrlLinkFrame id3 = (UrlLinkFrame) entry;
                String id = id3.id.toUpperCase();

                if (id.equals("WOAS") || id.equals("WOAF") || id.equals("WOAR") || id.equals("WAR")) {
                    url = id3.url;
                }

            }
        }

        if (title != null || url != null || artist != null || album != null || date != null || genre != null) {
            manager.onMetadataReceived("id3", title, url, artist, album, date, genre);
        }
    }

    /**
     * Shoutcast / Icecast metadata (ICY protocol)
     *
     * https://cast.readme.io/docs/icy
     */
    private static void handleIcyMetadata(MusicManager manager, Metadata metadata) {
        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if(entry instanceof IcyHeaders) {
                // ICY headers
                IcyHeaders icy = (IcyHeaders)entry;

                manager.onMetadataReceived("icy-headers", icy.name, icy.url, null, null, null, icy.genre);

            } else if(entry instanceof IcyInfo) {
                // ICY data
                IcyInfo icy = (IcyInfo)entry;

                String artist, title;
                int index = icy.title == null ? -1 : icy.title.indexOf(" - ");

                if (index != -1) {
                    artist = icy.title.substring(0, index);
                    title = icy.title.substring(index + 3);
                } else {
                    artist = null;
                    title = icy.title;
                }

                manager.onMetadataReceived("icy", title, icy.url, artist, null, null, null);

            }
        }
    }

    /**
     * Vorbis Comments (Vorbis, FLAC, Opus, Speex, Theora)
     *
     * https://xiph.org/vorbis/doc/v-comment.html
     */
    private static void handleVorbisCommentMetadata(MusicManager manager, Metadata metadata) {
        String title = null, url = null, artist = null, album = null, date = null, genre = null;

        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (!(entry instanceof VorbisComment)) continue;

            VorbisComment comment = (VorbisComment) entry;
            String key = comment.key;

          switch (key) {
            case "TITLE":
              title = comment.value;
              break;
            case "ARTIST":
              artist = comment.value;
              break;
            case "ALBUM":
              album = comment.value;
              break;
            case "DATE":
              date = comment.value;
              break;
            case "GENRE":
              genre = comment.value;
              break;
            case "URL":
              url = comment.value;
              break;
          }
        }

        if (title != null || url != null || artist != null || album != null || date != null || genre != null) {
            manager.onMetadataReceived("vorbis-comment", title, url, artist, album, date, genre);
        }
    }

    /**
     * QuickTime MDTA metadata (mov, qt)
     *
     * https://developer.apple.com/library/archive/documentation/QuickTime/QTFF/Metadata/Metadata.html
     */
    private static void handleQuickTimeMetadata(MusicManager manager, Metadata metadata) {
        String title = null, artist = null, album = null, date = null, genre = null;

        for (int i = 0; i < metadata.length(); i++) {
            Metadata.Entry entry = metadata.get(i);

            if (!(entry instanceof MdtaMetadataEntry)) continue;

            MdtaMetadataEntry mdta = (MdtaMetadataEntry) entry;
            String key = mdta.key;

            try {
              switch (key) {
                case "com.apple.quicktime.title":
                  title = new String(mdta.value, StandardCharsets.UTF_8);
                  break;
                case "com.apple.quicktime.artist":
                  artist = new String(mdta.value, StandardCharsets.UTF_8);
                  break;
                case "com.apple.quicktime.album":
                  album = new String(mdta.value, StandardCharsets.UTF_8);
                  break;
                case "com.apple.quicktime.creationdate":
                  date = new String(mdta.value, StandardCharsets.UTF_8);
                  break;
                case "com.apple.quicktime.genre":
                  genre = new String(mdta.value, StandardCharsets.UTF_8);
                  break;
              }
            } catch(Exception ex) {
                // Ignored
            }
        }

        if (title != null || artist != null || album != null || date != null || genre != null) {
            manager.onMetadataReceived("quicktime", title, null, artist, album, date, genre);
        }
    }

}
