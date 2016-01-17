package de.ub0r.android.basscast.model;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;

import android.content.ContentUris;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;

import ckm.simple.sql_provider.annotation.SimpleSQLColumn;
import ckm.simple.sql_provider.annotation.SimpleSQLTable;

/**
 * @author flx
 */
@SimpleSQLTable(table = "streams", provider = "StreamProvider")
public class Stream {

    @SimpleSQLColumn(value = "_id", primary = true, autoincrement = true)
    private long id = -1;

    @SimpleSQLColumn("base_id")
    private long baseId = -1;

    @SimpleSQLColumn("parent_id")
    private long parentId = -1;

    @SimpleSQLColumn("updated")
    private long updated = System.currentTimeMillis();

    @SimpleSQLColumn("url")
    private String url;

    @SimpleSQLColumn("title")
    private String title;

    @SimpleSQLColumn("mime_type")
    // shadow only for creating the column, do not use!
    private String mimeType;

    private MimeType mMimeType;

    public Stream() {
        // empty default constructor
    }

    public Stream(final String url, final String title, final MimeType mimeType) {
        this.url = url;
        this.title = title;
        this.mMimeType = mimeType;
    }

    public Stream(@NonNull final Stream parentStream, final String url, final String title,
            final MimeType mimeType) {
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
        this.updated = bundle.getLong(StreamsTable.FIELD_UPDATED, System.currentTimeMillis());
        this.url = bundle.getString(StreamsTable.FIELD_URL);
        this.title = bundle.getString(StreamsTable.FIELD_TITLE);
        setMimeType(bundle.getString(StreamsTable.FIELD_MIME_TYPE));
    }

    public Stream(final Uri uri) {
        this.url = uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        this.title = uri.getQueryParameter(StreamsTable.FIELD_TITLE);
        setMimeType(uri.getQueryParameter(StreamsTable.FIELD_MIME_TYPE));
    }

    public long getId() {
        return id;
    }

    public void setId(final long id) {
        this.id = id;
    }

    public long getBaseId() {
        return baseId;
    }

    public void setBaseId(final long baseId) {
        this.baseId = baseId;
    }

    public long getParentId() {
        return parentId;
    }

    public void setParentId(final long parentId) {
        this.parentId = parentId;
    }

    public long getUpdated() {
        return updated;
    }

    public void setUpdated(final long updated) {
        this.updated = updated;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(final String title) {
        this.title = title;
    }

    public String getMimeType() {
        if (mMimeType == null) {
            return null;
        } else {
            return mMimeType.getMimeType();
        }
    }

    public void setMimeType(final String mimeType) {
        if (mimeType == null) {
            mMimeType = null;
        } else {
            this.mMimeType = new MimeType(mimeType);
        }
    }

    public MediaInfo getMediaMetadata() {
        MediaMetadata mediaMetadata = new MediaMetadata(mMimeType.getType());
        mediaMetadata.putString(MediaMetadata.KEY_TITLE, title);
        return new MediaInfo.Builder(url)
                .setContentType(getMimeType())
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
        b.putLong(StreamsTable.FIELD_UPDATED, updated);
        b.putString(StreamsTable.FIELD_URL, url);
        b.putString(StreamsTable.FIELD_TITLE, title);
        b.putString(StreamsTable.FIELD_MIME_TYPE, getMimeType());
        return b;
    }

    public Uri toSharableUri() {
        return Uri.parse(url).buildUpon()
                .appendQueryParameter(StreamsTable.FIELD_MIME_TYPE, getMimeType())
                .appendQueryParameter(StreamsTable.FIELD_TITLE, title)
                .build();
    }

    @Override
    public String toString() {
        return "Stream: " + toBundle();
    }

    public boolean isPlayable() {
        return mMimeType != null && mMimeType.isPlayable();
    }

    public boolean isSupported() {
        return mMimeType != null && mMimeType.isSupported();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Stream stream = (Stream) o;
        return id == stream.id &&
                baseId == stream.baseId &&
                parentId == stream.parentId &&
                url.equals(stream.url) &&
                title.equals(stream.title) &&
                mMimeType.equals(stream.mMimeType);
    }
}
