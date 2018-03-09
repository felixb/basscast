package de.ub0r.android.basscast.tasks;

import android.app.Activity;

import de.ub0r.android.basscast.model.Stream;

public class DeleteStreamTask extends StreamTask {

    public DeleteStreamTask(Activity activity, boolean finishOnPostExecute) {
        super(activity, finishOnPostExecute);
    }

    @Override
    protected Void doInBackground(Stream... streams) {
        mDao.delete(streams);
        return null;
    }
}
