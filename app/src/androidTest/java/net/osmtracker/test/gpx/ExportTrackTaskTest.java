package net.osmtracker.test.gpx;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.test.ActivityInstrumentationTestCase2;

import junit.framework.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import net.osmtracker.OSMTracker;
import net.osmtracker.activity.TrackManager;
import net.osmtracker.db.DataHelper;
import net.osmtracker.gpx.ExportToStorageTask;
import net.osmtracker.test.util.MockData;

public class ExportTrackTaskTest extends ActivityInstrumentationTestCase2<TrackManager> {

	private long trackId;
	private File trackFile;
	

	public ExportTrackTaskTest() {
		super("net.osmtracker", TrackManager.class);
	}
	
	@Override
	protected void setUp() throws Exception {
		// Delete file entry in media library
		getActivity().getContentResolver().delete(
				MediaStore.Files.getContentUri("external"),
				MediaStore.Files.FileColumns.DATA + " LIKE ?",
				new String[] {"%/net.osmtracker/gpx-test"});

		Cursor cursor = getActivity().managedQuery(
				MediaStore.Files.getContentUri("external"),
				null,
				MediaStore.Files.FileColumns.DATA + " LIKE ?",
				new String[] {"%/net.osmtracker/gpx-test"},
				null);
		Assert.assertEquals(0, cursor.getCount());

		trackFile = new File(Environment.getExternalStorageDirectory(), "net.osmtracker/gpx-test.gpx");
		if (trackFile.exists()) {
			Assert.assertTrue(trackFile.delete());
		}
		
		trackId = MockData.mockTrack(getActivity());
				
		new DataHelper(getActivity()).stopTracking(trackId);
		
		// Ensure easy filename
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		Editor e = prefs.edit();
		e.clear();
		e.putString(OSMTracker.Preferences.KEY_OUTPUT_FILENAME, OSMTracker.Preferences.VAL_OUTPUT_FILENAME_NAME);
		e.putBoolean(OSMTracker.Preferences.KEY_OUTPUT_DIR_PER_TRACK, false);
		e.putBoolean(OSMTracker.Preferences.KEY_OUTPUT_GPX_HDOP_APPROXIMATION, true);
		Assert.assertTrue(e.commit());
	}
	
	public void test() throws Exception {

		new ExportToStorageTask(getActivity(), trackId).execute().get();

		// Ensure file contents are OK
		Assert.assertTrue(trackFile.exists());
		Assert.assertEquals(
				readFully(
						getInstrumentation().getContext().getAssets().open("gpx/gpx-test.gpx")),
				readFully(new FileInputStream(trackFile)));

		// Ensure the media library has been refreshed
		// We have to wait for the refresh Intent to be dispatched
		long maxWaitTime = 1000 * 5;
		long waited = 0;
		Cursor c = null;
		while (waited < maxWaitTime) {
			c = getActivity().managedQuery(
					MediaStore.Files.getContentUri("external"),
					null,
					MediaStore.Files.FileColumns.DATA + " LIKE ?",
					new String[]{"%/net.osmtracker/gpx-test.gpx"},
					null);
			if (c.moveToFirst()) {
				break;
			}

			Thread.sleep(250);
			waited += 250;
		}

		Assert.assertEquals(1, c.getCount());
		Assert.assertEquals(0, c.getInt(c.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)));
		Assert.assertEquals("gpx-test", c.getString(c.getColumnIndex(MediaStore.Files.FileColumns.TITLE)));

	}

	private static String readFully(InputStream is) throws IOException {
		BufferedReader reader = new BufferedReader(
				new InputStreamReader(is));
		
		StringBuilder sb = new StringBuilder();
		String line;
		while( (line=reader.readLine()) != null ) {
			sb.append(line).append(System.getProperty("line.separator"));
		}
		reader.close();
		
		return sb.toString();
	}
	
}
