package de.ub0r.android.basscast.model;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import java.util.List;

@Dao
public interface StreamDao {
    @Query("SELECT * FROM stream")
    List<Stream> getAll();

    @Query("SELECT * FROM stream WHERE _id = :streamId")
    Stream get(long streamId);

    @Query("SELECT * FROM stream WHERE parent_id = :parentId")
    List<Stream> getWithParent(final long parentId);

    @Query("SELECT * FROM stream WHERE parent_id = :parentId ORDER BY title ASC")
    LiveData<List<Stream>> getWithParentSync(final long parentId);

    @Insert
    void insert(Stream... streams);

    @Update
    void update(Stream... streams);

    @Delete
    void delete(Stream... streams);
}
