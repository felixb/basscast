package de.ub0r.android.basscast.model;

import android.arch.persistence.room.ColumnInfo;
import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.arch.persistence.room.TypeConverter;
import android.arch.persistence.room.TypeConverters;
import android.content.ContentUris;
import android.net.Uri;
import android.nfc.NdefRecord;
import android.os.Bundle;
import android.support.annotation.NonNull;

import com.google.android.gms.cast.MediaInfo;
import com.google.android.gms.cast.MediaMetadata;

import java.nio.charset.Charset;

import de.ub0r.android.basscast.BuildConfig;

/**
 * @author flx
 */
@Entity
public class Stream {

    public static final Uri CONTENT_URI = Uri.parse("content://de.ub0r.android.basscast.streams");
    private static final String FIELD_ID = "_id";
    private static final String FIELD_BASE_ID = "base_id";
    private static final String FIELD_PARENT_ID = "parent_id";
    private static final String FIELD_BREADCRUMBS = "breadcrumbs";
    private static final String FIELD_UPDATED = "updated";
    private static final String FIELD_URL = "url";
    private static final String FIELD_TITLE = "title";
    private static final String FIELD_MIME_TYPE = "mime_type";

    public static class Converters {
        @TypeConverter
        public String fromMimeType(final MimeType mimeType) {
            return mimeType == null ? null : mimeType.getMimeType();
        }

        @TypeConverter
        public MimeType toMimeType(final String mimeType) {
            return mimeType == null ? null : new MimeType(mimeType);
        }
    }

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = FIELD_ID)
    private long id = 0;

    @ColumnInfo(name = FIELD_BASE_ID)
    private long baseId = -1;

    @ColumnInfo(name = FIELD_PARENT_ID)
    private long parentId = -1;

    @ColumnInfo(name = FIELD_BREADCRUMBS)
    // ";..;(parent-of-parent.id);(parent.id);"
    private String breadcrumbs;

    @ColumnInfo(name = FIELD_UPDATED)
    private long updated = System.currentTimeMillis();

    @ColumnInfo(name = FIELD_URL)
    private String url;

    @ColumnInfo(name = FIELD_TITLE)
    private String title;

    @ColumnInfo(name = FIELD_MIME_TYPE, typeAffinity = ColumnInfo.TEXT)
    @TypeConverters(Converters.class)
    private MimeType mMimeType;

    public Stream() {
        // empty default constructor
    }

    @Ignore
    public Stream(final String url, final String title, final MimeType mimeType) {
        this.url = url;
        this.title = title;
        this.mMimeType = mimeType;
    }

    @Ignore
    public Stream(@NonNull final Stream parentStream, final String url, final String title,
                  final MimeType mimeType) {
        this(url, title, mimeType);
        if (parentStream.baseId < 0) {
            this.baseId = parentStream.id;
            this.breadcrumbs = ";" + parentStream.id + ";";
        } else {
            this.baseId = parentStream.baseId;
            this.breadcrumbs = parentStream.breadcrumbs + parentStream.id + ";";
        }
        this.parentId = parentStream.id;
    }

    @Ignore
    public Stream(final Bundle bundle) {
        this.id = bundle.getLong(FIELD_ID, -1);
        this.baseId = bundle.getLong(FIELD_BASE_ID, -1);
        this.parentId = bundle.getLong(FIELD_PARENT_ID, -1);
        this.breadcrumbs = bundle.getString(FIELD_BREADCRUMBS);
        this.updated = bundle.getLong(FIELD_UPDATED, System.currentTimeMillis());
        this.url = bundle.getString(FIELD_URL);
        this.title = bundle.getString(FIELD_TITLE);
        setMimeType(bundle.getString(FIELD_MIME_TYPE));
    }

    @Ignore
    public Stream(final Uri uri) {
        this.url = uri.getScheme() + "://" + uri.getHost() + uri.getPath();
        this.title = uri.getQueryParameter(FIELD_TITLE);
        setMimeType(uri.getQueryParameter(FIELD_MIME_TYPE));
    }

    @Ignore
    public Stream(final NdefRecord record) {
        this(Uri.parse(new String(parseNdefRecord(record), Charset.forName("UTF-8"))));
    }

    private static byte[] parseNdefRecord(final NdefRecord record) {
        if (record.getTnf() != NdefRecord.TNF_EXTERNAL_TYPE) {
            throw new IllegalArgumentException("Invalid TNF: " + record.getTnf());
        }
        final byte[] type = record.getType();
        if (type == null) {
            throw new NullPointerException("Type must not be null");
        }
        final String typeString = new String(type);
        if (!typeString.equalsIgnoreCase(BuildConfig.APPLICATION_ID + ":stream")) {
            throw new IllegalArgumentException("Invalid type: " + typeString);
        }

        final byte[] bytes = record.getPayload();
        if (bytes == null) {
            throw new NullPointerException("Payload must not be null");
        }
        return bytes;
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

    public String getBreadcrumbs() {
        return breadcrumbs;
    }

    public void setBreadcrumbs(final String breadcrumbs) {
        this.breadcrumbs = breadcrumbs;
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

    public MimeType getMimeType() {
        return mMimeType;
    }

    public String getMimeTypeAsString() {
        return mMimeType == null ? null : mMimeType.getMimeType();
    }

    public void setMimeType(final MimeType mimeType) {
        mMimeType = mimeType;
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
                .setContentType(getMimeTypeAsString())
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED)
                .setMetadata(mediaMetadata)
                .build();
    }

    public Uri getUri() {
        return ContentUris.withAppendedId(CONTENT_URI, id);
    }

    public Bundle toBundle() {
        final Bundle b = new Bundle();
        b.putLong(FIELD_ID, id);
        b.putLong(FIELD_BASE_ID, baseId);
        b.putLong(FIELD_PARENT_ID, parentId);
        b.putString(FIELD_BREADCRUMBS, breadcrumbs);
        b.putLong(FIELD_UPDATED, updated);
        b.putString(FIELD_URL, url);
        b.putString(FIELD_TITLE, title);
        b.putString(FIELD_MIME_TYPE, getMimeTypeAsString());
        return b;
    }

    public Uri toSharableUri() {
        return Uri.parse(url).buildUpon()
                .appendQueryParameter(FIELD_MIME_TYPE, getMimeTypeAsString())
                .appendQueryParameter(FIELD_TITLE, title)
                .build();
    }

    public NdefRecord toNdefRecord() {
        return NdefRecord.createExternal(
                BuildConfig.APPLICATION_ID,
                "stream",
                toSharableUri()
                        .toString()
                        .getBytes(Charset.forName("UTF-8")));
    }

    @Override
    public String toString() {
        return "Stream: " + toBundle();
    }

    public boolean isBaseStream() {
        return parentId < 0;
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
