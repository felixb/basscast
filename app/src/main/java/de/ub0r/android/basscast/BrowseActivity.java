package de.ub0r.android.basscast;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.google.android.gms.cast.framework.CastButtonFactory;

import java.util.List;

import de.ub0r.android.basscast.fetcher.FetchTask;
import de.ub0r.android.basscast.fetcher.StreamFetcher;
import de.ub0r.android.basscast.model.Stream;
import de.ub0r.android.basscast.tasks.StreamTask;

public class BrowseActivity extends AppCompatActivity {

    private static class InsertStreamsTask extends StreamTask {

        InsertStreamsTask(Activity activity) {
            super(activity, false);
        }

        @Override
        protected Void doInBackground(Stream... streams) {
            Log.i(TAG, "inserting " + streams.length + " default streams");
            mDao.insert(streams);
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(mActivity);
            prefs.edit()
                    .putBoolean(PREFS_DEFAULT_STREAMS_INSERTED, true)
                    .apply();
            super.onPostExecute(aVoid);
        }
    }

    private static final String TAG = "BrowserActivity";
    private static final String PREFS_DEFAULT_STREAMS_INSERTED = "default_streams_inserted";

    final View.OnClickListener mOnHomeClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            Intent intent = new Intent(BrowseActivity.this, BrowseActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }
    };

    private StreamFetcher mFetcher;
    private StreamController mController;
    private Toolbar mToolbar;
    private FloatingActionButton mFloatingActionButton;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        mController = new StreamController(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_browse);
        mToolbar = findViewById(R.id.toolbar);
        mFloatingActionButton = findViewById(R.id.fab);
        setSupportActionBar(mToolbar);

        if (savedInstanceState == null) {
            insertDefaultStreams();
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, BrowseFragment.getInstance(null))
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commit();
        }

        mFetcher = new StreamFetcher(this);
    }

    void setFloatingActionButtonModeAddStream() {
        mFloatingActionButton.setVisibility(View.VISIBLE);
        mFloatingActionButton.setImageResource(R.drawable.ic_content_add);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(Intent.ACTION_INSERT, null, BrowseActivity.this,
                        EditStreamActivity.class));
            }
        });

    }

    void setFloatingActionButtonModeAddAllToQueue(final List<Stream> streams) {
        mFloatingActionButton.setVisibility(View.VISIBLE);
        mFloatingActionButton.setImageResource(R.drawable.ic_playlist_add);
        mFloatingActionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mController.queueStreams(streams);
            }
        });
    }

    void setFloatingActionButtonDisabled() {
        mFloatingActionButton.setVisibility(View.GONE);
    }

    @Override
    protected void onResume() {
        mController.initCastSession();
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mController.endCastSession();
    }

    @Override
    protected void onDestroy() {
        mController = null;
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        getMenuInflater().inflate(R.menu.menu_browse_activity, menu);
        CastButtonFactory.setUpMediaRouteButton(getApplicationContext(), menu, R.id.action_cast);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        boolean showQueueItem = mController.isConnected() && mController.getMediaQueue().getItemCount() > 0;
        menu.findItem(R.id.action_queue_show).setVisible(showQueueItem);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_about:
                startActivity(new Intent(this, AboutActivity.class));
                return true;
            case R.id.action_queue_show:
                showQueue();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setSubtitle(final String title) {
        mToolbar.setSubtitle(title);
    }

    public void setHomeAsUp(final boolean enable) {
        if (enable) {
            mToolbar.setNavigationIcon(R.drawable.ic_action_arrow_back);
            mToolbar.setNavigationOnClickListener(mOnHomeClickListener);
        } else {
            mToolbar.setNavigationIcon(null);
            mToolbar.setNavigationOnClickListener(null);
        }
    }

    public void onStreamClick(final Stream stream) {
        if (stream.isPlayable()) {
            if (mController.isConnected()) {
                mController.castStream(stream);
            } else {
                Toast.makeText(this, R.string.error_not_connected, Toast.LENGTH_LONG).show();
            }
        } else {
            showStream(stream);
        }
    }

    public StreamController getStreamController() {
        return mController;
    }

    private void showStream(final Stream parentStream) {
        BrowseFragment fragment = BrowseFragment.getInstance(parentStream);
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
        new FetchTask(mFetcher, parentStream, fragment).execute((Void[]) null);
    }

    private void showQueue() {
        QueueFragment fragment = QueueFragment.getInstance();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                .addToBackStack(null)
                .commit();
    }

    private void insertDefaultStreams() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        if (!prefs.getBoolean(PREFS_DEFAULT_STREAMS_INSERTED, false)) {
            new InsertStreamsTask(this).execute(getDefaultStreams());
        }
    }

    private Stream[] getDefaultStreams() {
        String[] uris = getResources().getStringArray(R.array.default_streams);
        Stream[] streams = new Stream[uris.length];
        for (int i = 0; i < uris.length; i++) {
            streams[i] = new Stream(Uri.parse(uris[i]));
        }
        return streams;
    }
}
