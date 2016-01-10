package de.ub0r.android.basscast.model;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;

import android.content.ContentUris;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import ckm.simple.sql_provider.annotation.SimpleSQLColumn;
import ckm.simple.sql_provider.annotation.SimpleSQLTable;
import de.ub0r.android.basscast.InputError;
import de.ub0r.android.basscast.R;

/**
 * @author flx
 */
@SimpleSQLTable(table = "streams", provider = "StreamProvider")
public class Stream {

    @SimpleSQLColumn(value = "_id", primary = true, autoincrement = true)
    public long id = -1;

    @SimpleSQLColumn("base_id")
    public long baseId = -1;

    @SimpleSQLColumn("parent_id")
    public long parentId = -1;

    @SimpleSQLColumn("inserted")
    public long inserted = System.currentTimeMillis();

    @SimpleSQLColumn("url")
    public String url;

    @SimpleSQLColumn("title")
    public String title;

    @SimpleSQLColumn("type")
    public int type;

    @SimpleSQLColumn("mime_type")
    public String mimeType;

    public Stream() {
        // empty default constructor
    }

    public Stream(final String url, final String title, final String mimeType) {
        this.url = url;
        this.title = title;
        this.mimeType = mimeType;
        parseMimeType();
    }

    public Stream(@NonNull final Stream parentStream, final String url, final String title, final String mimeType) {
        this(url, title, mimeType);
        if (parentStream.baseId < 0) {
            this.baseId = parentStream.id;
        } else {
            this.baseId = parentStream.baseId;
        }
        this.parentId = parentStream.id;
    }

    public Stream(final Bundle bundle) {
        this.id = bundle.getLong(StreamsTable.FIELD__ID, -1);
        this.baseId = bundle.getLong(StreamsTable.FIELD_BASE_ID, -1);
        this.parentId = bundle.getLong(StreamsTable.FIELD_PARENT_ID, -1);
        this.inserted = bundle.getLong(StreamsTable.FIELD_INSERTED, System.currentTimeMillis());
        this.url = bundle.getString(StreamsTable.FIELD_URL);
        this.title = bundle.getString(StreamsTable.FIELD_TITLE);
        this.type = bundle.getInt(StreamsTable.FIELD_TYPE);
        this.mimeType = bundle.getString(StreamsTable.FIELD_MIME_TYPE);
    }

    public Stream(final Uri uri) {
        this.url = uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        this.title = uri.getQueryParameter(StreamsTable.FIELD_TITLE);
        this.mimeType = uri.getQueryParameter(StreamsTable.FIELD_MIME_TYPE);
        parseMimeType();
    }

    public MediaInfo getMediaMetadata() {
        MediaMetadata mediaMetadata = new MediaMetadata(type);
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, title);
        return new MediaInfo.Builder(url)
                .setContentType(mimeType)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
    }

    public Uri getUri() {
        return ContentUris.withAppendedId(StreamsTable.CONTENT_URI, id);
    }

    public Bundle toBundle() {
        final Bundle b = new Bundle();
        b.putLong(StreamsTable.FIELD__ID, id);
        b.putLong(StreamsTable.FIELD_BASE_ID, baseId);
        b.putLong(StreamsTable.FIELD_PARENT_ID, parentId);
        b.putLong(StreamsTable.FIELD_INSERTED, inserted);
        b.putString(StreamsTable.FIELD_URL, url);
        b.putString(StreamsTable.FIELD_TITLE, title);
        b.putInt(StreamsTable.FIELD_TYPE, type);
        b.putString(StreamsTable.FIELD_MIME_TYPE, mimeType);
        return b;
    }

    public Uri toSharableUri() {
        return Uri.parse(url).buildUpon()
                .appendQueryParameter(StreamsTable.FIELD_MIME_TYPE, mimeType)
                .appendQueryParameter(StreamsTable.FIELD_TITLE, title)
                .build();
    }

    @Override
    public String toString() {
        return "Stream: " + toBundle();
    }

    @Override
    public boolean equals(final Object o) {
        Stream otherStream = (Stream) o;
        return id == otherStream.id &&
                baseId == otherStream.baseId &&
                parentId == otherStream.parentId &&
                url.equals(otherStream.url) &&
                title.equals(otherStream.title) &&
                type == otherStream.type &&
                mimeType.equals(otherStream.mimeType);
    }

    public void parseMimeType() {
        if (TextUtils.isEmpty(mimeType)) {
            throw new InputError("missing mandatory parameter: mimeType",
                    R.string.missing_mandatory_parameter);
        } else if (mimeType.startsWith("audio")) {
            type = MediaMetadata.MEDIA_TYPE_MUSIC_TRACK;
        } else if (mimeType.startsWith("video")) {
            type = MediaMetadata.MEDIA_TYPE_MOVIE;
        } else if (mimeType.startsWith("text")) {
            type = -1;
        } else {
            throw new InputError("unsupported mime type: " + mimeType,
                    R.string.unsupported_mime_type);
        }
    }
}
