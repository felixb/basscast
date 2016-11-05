package de.ub0r.android.basscast.model;

import com.google.android.gms.cast.MediaMetadata;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.HashMap;

/**
 * @author flx
 */
public class MimeType {

    public static final HashMap<String, MimeType> EXTENSIONS = new HashMap<>();

    static {
        EXTENSIONS.put(".html", new MimeType("text/html"));
        EXTENSIONS.put(".htm", new MimeType("text/html"));
        EXTENSIONS.put(".php", new MimeType("text/html"));
        EXTENSIONS.put(".jsp", new MimeType("text/html"));
        EXTENSIONS.put(".asp", new MimeType("text/html"));
        EXTENSIONS.put(".mp3", new MimeType("audio/mp3"));
        EXTENSIONS.put(".ogg", new MimeType("audio/ogg"));
        EXTENSIONS.put(".wma", new MimeType("audio/wma"));
        EXTENSIONS.put(".avi", new MimeType("video/avi"));
        EXTENSIONS.put(".mp4", new MimeType("video/mp4"));
        EXTENSIONS.put(".mkv", new MimeType("video/mkv"));
        EXTENSIONS.put(".webm", new MimeType("video/webm"));
        EXTENSIONS.put(".png", new MimeType("image/png"));
        EXTENSIONS.put(".gif", new MimeType("image/gif"));
        EXTENSIONS.put(".jpg", new MimeType("image/jpeg"));
        EXTENSIONS.put(".jpeg", new MimeType("image/jpeg"));
    }

    @Nullable
    public static MimeType guessMimeType(@NonNull final String url) {
        final String lowerUrl = url.toLowerCase();
        for (String ext : EXTENSIONS.keySet()) {
            if (lowerUrl.endsWith(ext)) {
                return EXTENSIONS.get(ext);
            }
        }
        return null;
    }

    private final String mMimeType;

    private final int mType;

    public MimeType(@NonNull final String mimeType) {
        mMimeType = mimeType.split(";", 2)[0].trim().toLowerCase();
        mType = convertMimeTypeToType(mMimeType);
    }

    private int convertMimeTypeToType(@NonNull String mimeType) {
        if (mimeType.startsWith("audio/")) {
            return MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
        } else if (mimeType.startsWith("video/")) {
            return MediaMetadata.MEDIA_TYPE_MOVIE;
        } else if (mimeType.startsWith("text/")) {
            return MediaMetadata.MEDIA_TYPE_USER;
        } else {
            return -1;
        }
    }

    public boolean isPlayable() {
        return mType == MediaMetadata.MEDIA_TYPE_MUSIC_TRACK
                || mType == MediaMetadata.MEDIA_TYPE_MOVIE;
    }

    public boolean isBrowsable() {
        return mType == MediaMetadata.MEDIA_TYPE_USER;
    }

    public boolean isSupported() {
        return mType >= 0;
    }

    public String getMimeType() {
        return mMimeType;
    }

    public int getType() {
        return mType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        MimeType mimeType = (MimeType) o;
        return mMimeType.equals(mimeType.mMimeType);
    }

    @Override
    public int hashCode() {
        return mMimeType.hashCode();
    }
}
