/*
 * Copyright (C) 2012 Christopher Eby <kreed@kreed.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package mp.teardrop;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.view.PagerTabStrip;
import android.support.v4.view.ViewPager;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.dropbox.client2.DropboxAPI;
import com.dropbox.client2.DropboxAPI.DropboxLink;
import com.dropbox.client2.DropboxAPI.Entry;
import com.dropbox.client2.android.AndroidAuthSession;
import com.dropbox.client2.exception.DropboxException;
import com.dropbox.client2.exception.DropboxServerException;
import com.dropbox.client2.session.AppKeyPair;

/**
 * The library activity where songs to play can be selected from the library.
 */
public class LibraryActivity extends PlaybackActivity
        implements TextWatcher, DialogInterface.OnClickListener, DialogInterface.OnDismissListener {

    /**
     * Action for row click: play the row.
     */
    public static final int ACTION_PLAY = 0;
    /**
     * Action for row click: enqueue the row.
     */
    public static final int ACTION_ENQUEUE = 1;
    /**
     * Action for row click: perform the last used action.
     */
    public static final int ACTION_LAST_USED = 2;
    /**
     * Action for row click: play all the songs in the adapter, starting with
     * the current row.
     */
    public static final int ACTION_PLAY_ALL = 3;
    /**
     * Action for row click: enqueue all the songs in the adapter, starting with
     * the current row.
     */
    public static final int ACTION_ENQUEUE_ALL = 4;
    /**
     * Action for row click: do nothing.
     */
    public static final int ACTION_DO_NOTHING = 5;
    /**
     * Action for row click: expand the row.
     */
    public static final int ACTION_EXPAND = 6;
    /**
     * Action for row click: play if paused or enqueue if playing.
     */
    public static final int ACTION_PLAY_OR_ENQUEUE = 7;
    /**
     * The SongTimeline add song modes corresponding to each relevant action.
     */
    private static final int[] modeForAction =
            {SongTimeline.MODE_PLAY, SongTimeline.MODE_ENQUEUE, -1, SongTimeline.MODE_PLAY_ID_FIRST,
                    SongTimeline.MODE_ENQUEUE_ID_FIRST};
    /**
     * Identifies the first view in the limiter view, e.g. the root directory in the local files
     * tab. It should clear the limiter when clicked.
     */
    static final String TAG_DELIMITER_ROOT = "delimiterRoot";
    /**
     * Identifies the link to all artists in the UnifiedAdapter's delimiters.
     */
    static final String TAG_ARTISTS_ROOT = "artistsRoot";
    /**
     * Identifies the link to all albums in the UnifiedAdapter's delimiters.
     */
    static final String TAG_ALBUMS_ROOT = "albumsRoot";
    /**
     * Identifies the link to all playlists in the UnifiedAdapter's delimiters.
     */
    static final String TAG_PLAYLISTS_ROOT = "playlistsRoot";
    /**
     * Identifies the link to all genres in the UnifiedAdapter's delimiters.
     */
    static final String TAG_GENRES_ROOT = "albumsRoot";

    public ViewPager mViewPager;

    private View mActionControls;
    private View mControls;
    private TextView mTitle;
    private TextView mArtist;
    private ImageView mCover;
    private View mEmptyQueue;
    private ImageView mRoundPlayAllButton;

    /**
     * The action to execute when a row is tapped.
     */
    private int mDefaultAction;
    /**
     * The last used action from the menu. Used with ACTION_LAST_USED.
     */
    private int mLastAction = ACTION_PLAY;
    /**
     * The id of the media that was last pressed in the current adapter. Used to
     * open the playback activity when an item is pressed twice.
     */
    private long mLastActedId;
    /**
     * The pager adapter that manages each media ListView.
     */
    public LibraryPagerAdapter mPagerAdapter;
    /**
     * True if the current adapter is displaying any files that could be played,
     * false otherwise (e.g. unlinked Dropbox tab). Used to control visibility
     * of the floating play all button.
     */
    private boolean mSomethingToPlay;

    //used when retrieving metadata from Dropbox
    private static final String[] TAG_IDS_OF_INTEREST = {"TXXX", "TIT2", "TRCK", "TPE1", "TALB"};

    static final String PREFS_CLOUD_SONG_CACHE = "daocCloudsongNinja";
    static final String PREFS_CLOUD_DIR_HASHES = "daocCloudsongDirNinja";

    /* Dropbox API stuff */
    // You don't need to change these, leave them alone.
    final static String ACCOUNT_PREFS_NAME = "prefs";
    final static String ACCESS_KEY_NAME = "ACCESS_KEY";
    final static String ACCESS_SECRET_NAME = "ACCESS_SECRET";

    static DropboxAPI<AndroidAuthSession> mApi;


    public void linkOrUnlink() {
        if (mApi.getSession().isLinked()) {
            mApi.getSession().unlink();
            clearKeys();
            updateUi(false);
        } else {
            mApi.getSession().startOAuth2Authentication(this);
        }
    }


    private void clearKeys() {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        Editor edit = prefs.edit();
        edit.clear();
        edit.commit();
    }


    private AndroidAuthSession buildSession() {
        AppKeyPair appKeyPair = new AppKeyPair(DropboxApiKeys.APP_KEY, DropboxApiKeys.APP_SECRET);

        AndroidAuthSession session = new AndroidAuthSession(appKeyPair);
        loadAuth(session);
        return session;
    }


    private void loadAuth(AndroidAuthSession session) {
        SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(ACCESS_KEY_NAME, null);
        String secret = prefs.getString(ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) {
            return;
        }

        session.setOAuth2AccessToken(secret);
    }


    private void storeAuth(AndroidAuthSession session) {
        // Store the OAuth 2 access token, if there is one.
        String oauth2AccessToken = session.getOAuth2AccessToken();
        if (oauth2AccessToken != null) {
            SharedPreferences prefs = getSharedPreferences(ACCOUNT_PREFS_NAME, 0);
            Editor edit = prefs.edit();
            edit.putString(ACCESS_KEY_NAME, "oauth2:");
            edit.putString(ACCESS_SECRET_NAME, oauth2AccessToken);
            edit.commit();
            return;
        }
    }


    private void updateUi(boolean loggedIn) {
        if (loggedIn) {
            lockDropboxFileBrowser();
            mPagerAdapter.mDropboxAdapter.mLinkedWithDropbox = true;
            requeryDropbox(null);
        } else {
            mPagerAdapter.mDropboxAdapter.resetAfterDropboxUnlinked();
            unlockDropboxFileBrowser();
            //TODO: cancel any Dropbox async tasks?
        }
    }


    void requeryDropbox(Limiter limiter) {
        new DropboxLs(limiter).execute();
    }


    /**
     * Used to retrieve and display the contents of the directory specified by the limiter passed
     * to the constructor.
     */
    private class DropboxLs extends AsyncTask<Object, Object, List<Entry>> {

        private Limiter mLimiter;


        /**
         * Specifies the directory whose contents are to be retrieved.
         * Will default to the root directory if null is passed.
         */
        public DropboxLs(Limiter limiter) {
            mLimiter = limiter;
        }


        @Override
        protected List<Entry> doInBackground(Object... params) { //TODO: error handling!!!
            Entry someFiles = null;
            try {
                someFiles = LibraryActivity.this.mApi
                        .metadata((mLimiter == null ? "/" : (String) mLimiter.data), 0, null, true,
                                null);
            } catch (DropboxException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            List<Entry> sortingBuffer = someFiles.contents;
            Collections.sort(someFiles.contents, new Comparator<Entry>() {
                @Override
                public int compare(Entry lhs, Entry rhs) {
                    if (lhs.isDir && !rhs.isDir) {
                        return -1;
                    } else if (!lhs.isDir && rhs.isDir) {
                        return 1;
                    } else {
                        return lhs.fileName().compareToIgnoreCase(rhs.fileName());
                    }
                }
            });
            return sortingBuffer;
        }


        @Override
        protected void onPostExecute(List<Entry> result) {
            mPagerAdapter.mDropboxAdapter.setLimiter(mLimiter);
            mPagerAdapter.mDropboxAdapter.commitQuery(result);
            updateLimiterViews();
            unlockDropboxFileBrowser();
        }

    }


    private class DropboxPrepareMetadata
            extends AsyncTask<Void, Integer, ArrayList<CloudSongMetadata>> {

        private String mPath;
        private int mMode;
        private int totalSongs;
        private String mToastMessage = null;


        public DropboxPrepareMetadata(String path, int mode) {
            mPath = path;
            mMode = mode;
        }


        @Override
        protected void onPreExecute() {
            ((TextView) LibraryActivity.this.findViewById(R.id.progress_pane_text))
                    .setText(R.string.preparing_cloud_songs);

            LibraryActivity.this.findViewById(R.id.progress_pane).setVisibility(View.VISIBLE);
        }


        @Override
        protected ArrayList<CloudSongMetadata> doInBackground(Void... v) {
            try {

                SharedPreferences dirCachePrefs = LibraryActivity.this
                        .getSharedPreferences(LibraryActivity.PREFS_CLOUD_DIR_HASHES, 0);
                SharedPreferences songCachePrefs = LibraryActivity.this
                        .getSharedPreferences(LibraryActivity.PREFS_CLOUD_SONG_CACHE, 0);

                String localHash = dirCachePrefs.getString(mPath, null);

                Entry theFile = null; //will stay null if we have everything cached

                try {
                    theFile = LibraryActivity.this.mApi.metadata(mPath, 0, localHash, true, null);
                } catch (DropboxServerException e1) {
                    /*
                     * if 304 (good!), just leave theFile as null - further code will then
                     * retrieve everything from cache
					 * if not 304, some serious error occurred
					 */
                    if (e1.error != DropboxServerException._304_NOT_MODIFIED) {
                        return null;
                    }
                }

                ArrayList<String> songPaths = new ArrayList<String>();


                if (theFile != null &&
                        theFile.isDir) { //search online, recursively, for song files in the dir

                    ArrayList<String> pathsToCheck = new ArrayList<String>();
                    pathsToCheck.add(theFile.path);
                    while (!pathsToCheck.isEmpty()) {
                        Entry someDir = LibraryActivity.this.mApi
                                .metadata(pathsToCheck.remove(0), 0, null, true, null);
                        for (int i = 0; i != someDir.contents.size(); i++) {
                            Entry someFile = someDir.contents.get(i);
                            if (someFile.isDir) {
                                pathsToCheck.add(someFile.path);
                            } else if (someFile.fileName().toLowerCase().endsWith(
                                    ".mp3")) { //TODO: make this a comprehensive list of extensions
                                songPaths.add(someFile.path);
                            }
                        }
                    }

                } else if (theFile != null) { //just handle this one song
                    if (!theFile.path.toLowerCase().endsWith(".mp3")) {
                        //TODO: hardcoded string
                        mToastMessage = "Only mp3 files can be streamed for now. More coming soon!";
                        return null;
                    }
                    songPaths.add(theFile.path);
                } else { //we have everything cached! add the paths to songPaths to re-download
                    // data anyway if streaming links have expired

                    Set<String> cachedFilePaths = songCachePrefs.getAll().keySet();

                    for (String cachedFilePath : cachedFilePaths) {
                        if (cachedFilePath.toLowerCase().startsWith(mPath.toLowerCase())) {
                            songPaths.add(cachedFilePath);
                        }
                    }

                }

                if (songPaths.isEmpty()) {
                    //TODO: get rid of "an error occurred" toast that currently follows this,
                    // also, hardcoded string
                    mToastMessage = "No mp3 files to play in this directory.";
                    return null;
                }

                totalSongs = songPaths.size();

				/* now download metadata (ID3 tags etc.) for each song - unless up-to-date,
                locally cached data is available */

                ArrayList<CloudSongMetadata> cloudSongs = new ArrayList<CloudSongMetadata>();
                SharedPreferences.Editor songCachePrefsEditor = songCachePrefs.edit();

                int currentSongNumber = 0;
                for (String songPath : songPaths) {

                    currentSongNumber++;
                    publishProgress(currentSongNumber);

                    Entry currentSong =
                            LibraryActivity.this.mApi.metadata(songPath, 1, null, false, null);

                    String jsonString = songCachePrefs.getString(songPath, null);
                    if (jsonString != null) {
                        CloudSongMetadata cachedMetadata =
                                CloudSongMetadata.fromJsonString(jsonString);

                        /*TODO: don't re-download the entire metadata if
                          only the streaming link needs a refresh */

                        if (cachedMetadata != null &&
                                cachedMetadata.expires != null &&
                                cachedMetadata.expires.after(new Date()) &&
                                cachedMetadata.revision != null &&
                                cachedMetadata.revision.equals(currentSong.rev)) {

                            cloudSongs.add(cachedMetadata);
                            continue;

                        }
                    }


                    DropboxLink streamingLink = LibraryActivity.this.mApi.media(songPath, true);

                    CloudSongMetadata currentSongMeta =
                            new CloudSongMetadata(streamingLink, currentSong.path, currentSong.rev,
                                    0, 0, currentSong.fileName(), "?", "?", 0, 1);

					/* download a part of the file to read tags (title, artist, etc.) and
                    ReplayGain info */
                    URL url = null;
                    url = new URL(streamingLink.url);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    BufferedInputStream in = new BufferedInputStream(connection.getInputStream());

                    byte[] id3v2Header = new byte[10];
                    in.read(id3v2Header, 0, 10);

                    if (id3v2Header[0] == 0x49 && id3v2Header[1] == 0x44 &&
                            id3v2Header[2] == 0x33) { //the song actually has an id3v2 tag

                        //a signed int is fine, as a valid ID3 tag will never be as large as to
                        // require full 32 bits to represent its size
                        int id3v2TagSize =
                                id3v2Header[9] + id3v2Header[8] * 128 + id3v2Header[7] * 16384 +
                                        id3v2Header[6] * 2097152;

                        if ((id3v2Header[5] & 64) != 0) { //extended header is present
                            in.skip(1337); //TODO: use actual size
                        }

                        //now read tag frames
                        long grandTotal = 0; //total bytes read or skipped

                        while (grandTotal < id3v2TagSize -
                                10) { //if 10 bytes or less remain, they can only be padding

                            byte[] frameHeader = new byte[10];
                            grandTotal += in.read(frameHeader, 0, 10);

                            if (frameHeader[0] == 0x0) {
                                break; //we've reached padding
                            }

                            String frameId = new String(frameHeader, 0, 4, "US-ASCII");

                            //a signed int will hold the result no problem due to the entire
                            // tag's total size limit
                            int frameSize = (0xFFFFFFFF & frameHeader[7]) |
                                    ((0xFFFFFFFF & frameHeader[6]) << 8) |
                                    ((0xFFFFFFFF & frameHeader[5]) << 16) |
                                    ((0xFFFFFFFF & frameHeader[4]) << 24);

                            if (Arrays.asList(LibraryActivity.TAG_IDS_OF_INTEREST)
                                    .contains(frameId)) { //read the actual frame

                                //"TXXX", "TIT2", "TRCK", "TPE1", "TALB"

                                byte[] frameData = new byte[frameSize];
                                grandTotal += in.read(frameData, 0, frameSize);

                                if (frameId.equals("TXXX") &&
                                        frameData[0] == 0) { //looks like Replay Gain data
                                    String str = new String(frameData, "ISO-8859-1").toLowerCase();
                                    int index = str.indexOf("replaygain_track_gain");
                                    if (index != -1) {
                                        int index2 = str.indexOf(" ", index + 1);
                                        str = str.substring(
                                                index + "replaygain_track_gain".length() + 1,
                                                index2);
                                        currentSongMeta.rgTrack = Float.parseFloat(str);
                                    } else if ((index = str.indexOf("replaygain_album_gain")) !=
                                            -1) {
                                        int index2 = str.indexOf(" ", index + 1);
                                        str = str.substring(
                                                index + "replaygain_album_gain".length() + 1,
                                                index2);
                                        currentSongMeta.rgAlbum = Float.parseFloat(str);
                                    }
                                } else if (frameId.equals("TIT2")) {
                                    //TODO: handle all text encodings defined in ID3v2.3 and 2.4
                                    // properly
                                    currentSongMeta.title =
                                            new String(frameData, 1, frameData.length - 1, "UTF-8");
                                } else if (frameId.equals("TRCK")) {
                                    String str =
                                            new String(frameData, 1, frameData.length - 1, "UTF-8");
                                    int index;
                                    if ((index = str.indexOf("/")) != -1) {
                                        str = str.substring(0, index);
                                    }
                                    try {
                                        currentSongMeta.trackNumber = Integer.parseInt(str.trim());
                                    } catch (NumberFormatException e) {
                                        currentSongMeta.trackNumber = 1;
                                    }
                                } else if (frameId.equals("TPE1")) {
                                    currentSongMeta.artist =
                                            new String(frameData, 1, frameData.length - 1, "UTF-8");
                                } else if (frameId.equals("TALB")) {
                                    currentSongMeta.album =
                                            new String(frameData, 1, frameData.length - 1, "UTF-8");
                                }

                                //TODO: break if we have all the data we need

                            } else {
                                grandTotal += in.skip(frameSize);
                            }

                        }
                    }

                    in.close();
                    connection.disconnect();
                    cloudSongs.add(currentSongMeta);

                    String jsonString1 = currentSongMeta.toJsonObject().toString();
                    if (jsonString1 != null) {
                        songCachePrefsEditor.putString(songPath, jsonString1);
                    }


                }

                songCachePrefsEditor.commit();

                if (theFile != null &&
                        theFile.isDir) { //theFile is be null iff we had everything cached
                    SharedPreferences.Editor dirCachePrefsEditor = dirCachePrefs.edit();
                    dirCachePrefsEditor.putString(theFile.path, theFile.hash);
                    dirCachePrefsEditor.commit();
                }

                return cloudSongs;

            } catch (DropboxException e) {
                return null;
            } catch (MalformedURLException e) {
                return null;
            } catch (IOException e) {
                return null;
            }
        }


        @Override
        protected void onProgressUpdate(Integer... values) {

            //TODO use formatted String resource instead of this stringbuilder mess
            String text =
                    LibraryActivity.this.getResources().getString(R.string.preparing_cloud_songs) +
                            "(" + values[0] + "/" + totalSongs + ")";

            ((TextView) LibraryActivity.this.findViewById(R.id.progress_pane_text)).setText(text);
        }


        @Override
        protected void onPostExecute(ArrayList<CloudSongMetadata> result) {

            if (result == null) {
                Toast.makeText(getApplicationContext(),
                        mToastMessage == null ? "An error occurred. Please try again." :
                                mToastMessage, Toast.LENGTH_LONG).show(); //TODO: hardcoded string
            } else {
                PlaybackService.get(LibraryActivity.this).addCloudSongs(result, mMode);
            }

            LibraryActivity.this.findViewById(R.id.progress_pane).setVisibility(View.GONE);

        }

    }
    /* end Dropbox API stuff */


    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        if (state == null) {
            checkForLaunch(getIntent());
        }

        setContentView(R.layout.library_content);

		/* Dropbox API stuff */
        AndroidAuthSession session = buildSession();
        mApi = new DropboxAPI<AndroidAuthSession>(session);
        
        /* if(mApi.getSession().isLinked()) {
            updateUi(true);
        } */
        /* end Dropbox API stuff */

        LibraryPagerAdapter pagerAdapter = new LibraryPagerAdapter(this, mLooper);
        mPagerAdapter = pagerAdapter;

        ViewPager pager = (ViewPager) findViewById(R.id.pager);
        pager.setAdapter(pagerAdapter);
        mViewPager = pager;

        //apply some styles that can't be configured in XML
        PagerTabStrip strip = (PagerTabStrip) findViewById(R.id.pager_title_strip);
        strip.setDrawFullUnderline(false);
        strip.setTabIndicatorColor(0xc51162);

        pager.setOnPageChangeListener(pagerAdapter);

        mActionControls = findViewById(R.id.bottom_bar_controls);
        mTitle = (TextView) mActionControls.findViewById(R.id.title);
        mArtist = (TextView) mActionControls.findViewById(R.id.artist);
        mCover = (ImageView) mActionControls.findViewById(R.id.cover);

        mRoundPlayAllButton = (ImageButton) findViewById(R.id.round_play_all_button);
        mRoundPlayAllButton.setOnClickListener(this);
        registerForContextMenu(mRoundPlayAllButton);

        mPagerAdapter.loadTabOrder();

        loadAlbumIntent(getIntent());
    }


    @Override
    public void onStart() {
        super.onStart();

        SharedPreferences settings = PlaybackService.getSettings(this);
        if (settings.getBoolean(PrefKeys.CONTROLS_IN_SELECTOR, false) != (mControls != null)) {
            finish();
            startActivity(new Intent(this, LibraryActivity.class));
        }
        mDefaultAction = Integer.parseInt(settings.getString(PrefKeys.DEFAULT_ACTION_INT, "7"));
        mLastActedId = LibraryAdapter.INVALID_ID;
    }


    @Override
    protected void onRestart() {
        super.onRestart();
        mPagerAdapter.loadTabOrder();
        if (mSomethingToPlay) {
            mRoundPlayAllButton.setVisibility(View.VISIBLE);
        } else {
            mRoundPlayAllButton.setVisibility(View.GONE);
        }
    }


    @Override
    public void onResume() {
        super.onResume();

		/* Dropbox API stuff */
        AndroidAuthSession session = mApi.getSession();
        if (session.authenticationSuccessful()) {
            try {
                // Mandatory call to complete the auth
                session.finishAuthentication();

                // Store it locally in our app for later use
                storeAuth(session);
                updateUi(true);
            } catch (IllegalStateException e) {
                Toast.makeText(this,
                        "Couldn't authenticate with Dropbox:" + e.getLocalizedMessage(),
                        Toast.LENGTH_LONG).show();
                e.printStackTrace();
            }
        }
        /* end Dropbox API stuff */
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_UNLINK_WITH_DROPBOX, 91, R.string.unlink_with_dropbox);
        return true;
    }


    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (mApi != null && mApi.getSession().isLinked()) {
            menu.findItem(MENU_UNLINK_WITH_DROPBOX).setVisible(true);
        } else {
            menu.findItem(MENU_UNLINK_WITH_DROPBOX).setVisible(false);
        }
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_UNLINK_WITH_DROPBOX:
                if (mApi != null && mApi.getSession().isLinked()) {
                    linkOrUnlink();
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    /**
     * If this intent looks like a launch from icon/widget/etc, perform
     * launch actions.
     */
    private void checkForLaunch(Intent intent) {
        SharedPreferences settings = PlaybackService.getSettings(this);
        if (settings.getBoolean(PrefKeys.PLAYBACK_ON_STARTUP, false) &&
                Intent.ACTION_MAIN.equals(intent.getAction())) {
            startActivity(new Intent(this, FullPlaybackActivity.class));
        }
    }


    /**
     * If the given intent has album data, set a limiter built from that
     * data.
     */
    private void loadAlbumIntent(Intent intent) //TODO: test the use case for this method
    {
        long albumId = intent.getLongExtra("albumId", -1);
        if (albumId != -1) {
            String[] fields = {intent.getStringExtra("artist"), intent.getStringExtra("album")};
            String data = String.format("album_id=%d", albumId);
            //Limiter limiter = new Limiter(MediaUtils.TYPE_ALBUM, fields, data);
            Limiter limiter = new Limiter(-1, fields, data);
            mViewPager.setCurrentItem(mPagerAdapter.mTabOrder[mPagerAdapter.mCurrentPage]);
            mPagerAdapter.setLimiter(limiter);
            updateLimiterViews();

        }
    }


    @Override
    public void onNewIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        checkForLaunch(intent);
        loadAlbumIntent(intent);
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                ;
                if (!mPagerAdapter.goBackToPreviousLimiter()) {
                    finish();
                } else {
                    updateLimiterViews();
                }
                return true;
            default:
                return false;
        }
    }


    /**
     * Adds songs matching the data from the given intent to the song timelime.
     * If given an intent built by DropboxAdapter, it will first retrieve streaming URLs
     * and ReplayGain info for all music files in the directory (or the one music file
     * the intent points to), since this Activity already has an instance of DropboxAPI.
     *
     * @param intent An intent created with
     *               {@link LibraryAdapter#createData(View)}.
     *               If null, all songs in the MediaStore will be added.
     * @param action One of LibraryActivity.ACTION_*
     */
    private void pickSongs(Intent intent, int action) {

        if (intent.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID) ==
                MediaUtils.TYPE_DROPBOX) {
            Toast.makeText(this, "Preparing...", Toast.LENGTH_SHORT)
                    .show(); //TODO: hardcoded string
            int mode = (action == ACTION_PLAY_ALL || action == ACTION_PLAY) ? ACTION_PLAY :
                    ACTION_ENQUEUE;
            new DropboxPrepareMetadata((String) intent.getStringExtra(LibraryAdapter.DATA_FILE),
                    modeForAction[mode]).execute();
            return;
        }

        boolean all = false;
        int mode = action;
        if (action == ACTION_PLAY_ALL || action == ACTION_ENQUEUE_ALL) {
            if (mode == ACTION_ENQUEUE_ALL) {
                mode = ACTION_ENQUEUE;
            } else if (mode == ACTION_PLAY_ALL) {
                mode = ACTION_PLAY;
            } else {
                all = true;
            }
        }

        QueryTask query = (intent == null) ? MediaUtils.buildAllMediaQuery() :
                buildQueryFromIntent(intent, false, all);
        query.mode = modeForAction[mode];
        PlaybackService.get(this).addSongs(query);

        mLastActedId = (intent == null) ? LibraryAdapter.INVALID_ID :
                intent.getLongExtra("id", LibraryAdapter.INVALID_ID);

        if (mDefaultAction == ACTION_LAST_USED && mLastAction != action) {
            mLastAction = action;
        }
    }


    /**
     * "Expand" the view represented by the given intent by setting the limiter
     * from the view and switching to the appropriate tab.
     *
     * @param intent An intent created with
     *               {@link LibraryAdapter#createData(View)}.
     */
    private void expand(Intent intent) {
        int type = intent.getIntExtra("type", MediaUtils.TYPE_INVALID);
        long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);

        String preGeneratedName = intent.getStringExtra("preGeneratedName");

        mPagerAdapter.setLimiter(mPagerAdapter.mAdapters[type > 10 ? MediaUtils.TYPE_UNIFIED : type]
                .buildLimiter(type, id, preGeneratedName));

        if (mPagerAdapter.getCurrentType() !=
                MediaUtils.TYPE_DROPBOX) { //Dropbox limiter views are updated when the network
            // AsyncTask finishes
            updateLimiterViews();
        } else {
            lockDropboxFileBrowser();
        }
    }


    /**
     * Open the playback activity and close any activities above it in the
     * stack.
     */
    public void openPlaybackActivity() {
        startActivity(new Intent(this, FullPlaybackActivity.class));
    }


    /**
     * Called by LibraryAdapters when a row has been clicked.
     *
     * @param rowData The data for the row that was clicked.
     */
    public void onItemClicked(Intent rowData) {
        if (rowData == null) {
            return;
        }

        if (rowData.getBooleanExtra(LibraryAdapter.DATA_LINK_WITH_DROPBOX, false)) {
            linkOrUnlink();
            return;
        }

        if (rowData.getBooleanExtra(LibraryAdapter.DATA_GO_UP,
                false)) { //go to parent directory (local files or Dropbox)
            Limiter limiter = mPagerAdapter.getCurrentLimiter();
            if (limiter.names.length > 1) {
                if (limiter.type == MediaUtils.TYPE_FILE) {
                    File parentFile = ((File) limiter.data).getParentFile();
                    mPagerAdapter.setLimiter(FileSystemAdapter.buildLimiter(parentFile));
                } else if (limiter.type == MediaUtils.TYPE_DROPBOX) {
                    lockDropboxFileBrowser();
                    StringBuilder sb = new StringBuilder("/");
                    for (int i = 0; i < limiter.names.length - 1; i++) {
                        sb.append(limiter.names[i]).append("/");
                    }
                    mPagerAdapter.setLimiter(DropboxAdapter.buildLimiter(sb.toString()));
                }
            } else {
                if (limiter.type == MediaUtils.TYPE_DROPBOX) {
                    lockDropboxFileBrowser();
                }
                mPagerAdapter.clearLimiter(limiter.type);
            }
            updateLimiterViews();
            return;
        }

        if (rowData.getBooleanExtra(LibraryAdapter.DATA_GO_TO_ALL_ARTISTS, false)) {
            mPagerAdapter.setLimiter(UnifiedAdapter
                    .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_ARTISTS,
                            getResources().getString(R.string.artists)));
            updateLimiterViews();
            return;
        }
        if (rowData.getBooleanExtra(LibraryAdapter.DATA_GO_TO_ALL_ALBUMS, false)) {
            mPagerAdapter.setLimiter(UnifiedAdapter
                    .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_ALBUMS,
                            getResources().getString(R.string.albums)));
            updateLimiterViews();
            return;
        }
        if (rowData.getBooleanExtra(LibraryAdapter.DATA_GO_TO_ALL_SONGS, false)) {
            mPagerAdapter.setLimiter(UnifiedAdapter
                    .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_SONGS,
                            getResources().getString(R.string.songs)));
            updateLimiterViews();
            return;
        }
        if (rowData.getBooleanExtra(LibraryAdapter.DATA_GO_TO_ALL_PLAYLISTS, false)) {
            mPagerAdapter.setLimiter(UnifiedAdapter
                    .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_PLAYLISTS,
                            getResources().getString(R.string.playlists)));
            updateLimiterViews();
            return;
        }

        int action = mDefaultAction;
        if (action == ACTION_LAST_USED) {
            action = mLastAction;
        }

        if (rowData.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false)) {
            onItemExpanded(rowData);
        } else if (rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID) ==
                mLastActedId) {
            openPlaybackActivity();
        } else if (action != ACTION_DO_NOTHING) {
            if (action == ACTION_EXPAND) {
                // default to playing when trying to expand something that can't be expanded
                action = ACTION_PLAY;
            } else if (action == ACTION_PLAY_OR_ENQUEUE) {
                action =
                        (mState & PlaybackService.FLAG_PLAYING) == 0 ? ACTION_PLAY : ACTION_ENQUEUE;
            }
            pickSongs(rowData, action);
        }
    }


    /**
     * Called by LibraryAdapters when a row's expand arrow has been clicked.
     *
     * @param rowData The data for the row that was clicked.
     */
    public void onItemExpanded(Intent rowData) {
        expand(rowData);
    }


    @Override
    public void afterTextChanged(Editable editable) {
    }


    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }


    @Override
    public void onTextChanged(CharSequence text, int start, int before, int count) {
        mPagerAdapter.setFilter(text.toString());
    }


    /**
     * Create or recreate the limiter breadcrumbs.
     */
    public void updateLimiterViews() {
        mPagerAdapter.updateLimiterViews();
        if (mSomethingToPlay) {
            mRoundPlayAllButton.setVisibility(View.VISIBLE);
        } else {
            mRoundPlayAllButton.setVisibility(View.GONE);
        }
    }


    @Override
    public void onClick(View view) {
        if (view == mCover || view == mActionControls) {
            openPlaybackActivity();
        } else if (view == mEmptyQueue) {
            setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_RANDOM));
        } else if (view == mRoundPlayAllButton) {

            Limiter limiter = mPagerAdapter.getCurrentLimiter();
            Intent intent = null;

            switch (mPagerAdapter.getCurrentType()) {
                case MediaUtils.TYPE_FILE:
                    intent = new Intent();
                    intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_FILE);
                    intent.putExtra("file", limiter == null ? "/" : limiter.data.toString());
                    break;
                case MediaUtils.TYPE_UNIFIED:
                    if (limiter != null && limiter.type <= 20) {
                        intent = new Intent();
                        intent.putExtra(LibraryAdapter.DATA_TYPE, limiter.type);
                        intent.putExtra(LibraryAdapter.DATA_ID, (Long) limiter.data);
                    } //else continue with null limiter, which will play the entire MediaStore
                    break;
            }

            view.setVisibility(View.GONE);

            pickSongs(intent, (mState & PlaybackService.FLAG_PLAYING) == 0 ? ACTION_PLAY_ALL :
                    ACTION_ENQUEUE_ALL);

        } else if (view.getTag() != null) { // a limiter view was clicked

            //TODO: this method may be re-generating limiters needlessly when the last view is
            // clicked (which navigates to nowhere), investigate

            Limiter limiter = mPagerAdapter.getCurrentLimiter();

            if (limiter == null) {
                return; //clicking on limiters is used used to navigate up, so if we're already
                // at the root there's nothing to do
            }

            if (view.getTag().toString().equals(TAG_DELIMITER_ROOT)) {
                if (limiter.type == MediaUtils.TYPE_DROPBOX) {
                    lockDropboxFileBrowser();
                }
                mPagerAdapter.setLimiter(null);
            } else {
                if (limiter.type == UnifiedAdapter.ITEM_TYPE_MORE_ALBUMS ||
                        view.getTag().equals(TAG_ALBUMS_ROOT)) {
                    mPagerAdapter.setLimiter(UnifiedAdapter
                            .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_ALBUMS,
                                    getResources().getString(R.string.albums)));
                } else if (limiter.type == UnifiedAdapter.ITEM_TYPE_MORE_ARTISTS ||
                        view.getTag().equals(TAG_ARTISTS_ROOT)) {
                    mPagerAdapter.setLimiter(UnifiedAdapter
                            .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_ARTISTS,
                                    getResources().getString(R.string.artists)));
                } else if (limiter.type == UnifiedAdapter.ITEM_TYPE_MORE_PLAYLISTS ||
                        view.getTag().equals(TAG_PLAYLISTS_ROOT)) {
                    mPagerAdapter.setLimiter(UnifiedAdapter
                            .buildLimiterForMoreLink(UnifiedAdapter.ITEM_TYPE_MORE_PLAYLISTS,
                                    getResources().getString(R.string.playlists)));
                } else if (limiter.type == UnifiedAdapter.ITEM_TYPE_MORE_SONGS ||
                        limiter.type == UnifiedAdapter.ITEM_TYPE_SONG) {
                    return; //if such a limiter is visible, we're already viewing all songs
                } else if (limiter.type == MediaUtils.TYPE_DROPBOX) {
                    lockDropboxFileBrowser();
                    int i = (Integer) view.getTag();
                    if (i >= 0) {
                        StringBuilder sb = new StringBuilder("/");
                        for (int j = 0; j <= i; j++) {
                            sb.append(limiter.names[j]).append("/");
                        }
                        mPagerAdapter.setLimiter(DropboxAdapter.buildLimiter(sb.toString()));
                    }
                } else if (limiter.type == MediaUtils.TYPE_FILE) {
                    int i = (Integer) view.getTag();
                    if (i >= 0) {
                        File file = (File) limiter.data;
                        int diff = limiter.names.length - i;
                        while (--diff != 0) {
                            file = file.getParentFile();
                        }
                        mPagerAdapter.setLimiter(FileSystemAdapter.buildLimiter(file));
                    }
                }
            }

            updateLimiterViews();

        } else {
            super.onClick(view);
        }
    }


    /**
     * Set a new limiter of the given type built from the first
     * MediaStore.Audio.Media row that matches the selection.
     *
     * @param limiterType The type of limiter to create. Must be either
     *                    MediaUtils.TYPE_ARTIST or MediaUtils.TYPE_ALBUM.
     * @param selection   Selection to pass to the query.
     */
    private void setLimiter(int limiterType, String selection) {
        ContentResolver resolver = getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        String[] projection =
                new String[]{MediaStore.Audio.Media.ARTIST_ID, MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM};
        Cursor cursor = resolver.query(uri, projection, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToNext()) {
                String[] fields;
                String data;
                switch (limiterType) {
                /*case MediaUtils.TYPE_ARTIST:
                    fields = new String[] { cursor.getString(2) };
					data = String.format("artist_id=%d", cursor.getLong(0));
					break;
				case MediaUtils.TYPE_ALBUM:
					fields = new String[] { cursor.getString(2), cursor.getString(3) };
					data = String.format("album_id=%d", cursor.getLong(1));
					break;*/
                    default:
                        throw new IllegalArgumentException(
                                "setLimiter() does not support limiter type " + limiterType);
                }
                //mPagerAdapter.setLimiter(new Limiter(limiterType, fields, data));
            }
            cursor.close();
        }
    }


    /**
     * Builds a media query based off the data stored in the given intent.
     *
     * @param intent An intent created with
     *               {@link LibraryAdapter#createData(View)}.
     * @param empty  If true, use the empty projection (only query id).
     * @param all    If true query all songs in the adapter; otherwise query based
     *               on the row selected.
     */
    private QueryTask buildQueryFromIntent(Intent intent, boolean empty, boolean all) {
        int type = intent.getIntExtra("type", MediaUtils.TYPE_INVALID);

        String[] projection;
        //if (type == MediaUtils.TYPE_PLAYLIST)
        if (false) {
            projection = empty ? Song.EMPTY_PLAYLIST_PROJECTION : Song.FILLED_PLAYLIST_PROJECTION;
        } else {
            projection = empty ? Song.EMPTY_PROJECTION : Song.FILLED_PROJECTION;
        }

        long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
        QueryTask query;
        if (type == MediaUtils.TYPE_FILE) {
            query = MediaUtils.buildFileQuery(intent.getStringExtra("file"), projection);
        } else if (all || id == LibraryAdapter.HEADER_ID) {
            query = ((MediaAdapter) mPagerAdapter.mAdapters[type]).buildSongQuery(projection);
            query.data = id;
        } else {
            query = MediaUtils.buildQuery(type, id, projection, null);
        }

        return query;
    }


    private static final int MENU_PLAY = 0;
    private static final int MENU_ENQUEUE = 1;
    private static final int MENU_EXPAND = 2;
    private static final int MENU_ADD_TO_PLAYLIST = 3;
    private static final int MENU_NEW_PLAYLIST = 4;
    private static final int MENU_DELETE = 5;
    private static final int MENU_RENAME_PLAYLIST = 7;
    private static final int MENU_SELECT_PLAYLIST = 8;
    private static final int MENU_PLAY_ALL = 9;
    private static final int MENU_ENQUEUE_ALL = 10;
    private static final int MENU_MORE_FROM_ALBUM = 11;
    private static final int MENU_MORE_FROM_ARTIST = 12;

    private static final int MENU_GROUP_ROUND_BUTTON = 1;


    /**
     * Creates a context menu for an adapter row.
     *
     * @param menu    The menu to create.
     * @param rowData Data for the adapter row.
     */
    public void onCreateContextMenu(ContextMenu menu, Intent rowData) {
        if (rowData == null) {
            return;
        }

        if (rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID) ==
                LibraryAdapter.HEADER_ID) {
            //menu.setHeaderTitle(getString(R.string.all_songs));
            menu.add(0, MENU_PLAY_ALL, 0, R.string.play_all).setIntent(rowData);
            menu.add(0, MENU_ENQUEUE_ALL, 0, R.string.enqueue_all).setIntent(rowData);
            //menu.addSubMenu(0, MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).getItem()
            // .setIntent(rowData);
        } else {
            int type = rowData.getIntExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_INVALID);
            //boolean isAllAdapter = type <= MediaUtils.TYPE_SONG;
            boolean isAllAdapter = false;

            //menu.setHeaderTitle(rowData.getStringExtra(LibraryAdapter.DATA_TITLE));
            menu.add(0, MENU_PLAY, 0, R.string.play).setIntent(rowData);
            if (isAllAdapter) {
                menu.add(0, MENU_PLAY_ALL, 0, R.string.play_all).setIntent(rowData);
            }
            menu.add(0, MENU_ENQUEUE, 0, R.string.enqueue).setIntent(rowData);
            if (isAllAdapter) {
                menu.add(0, MENU_ENQUEUE_ALL, 0, R.string.enqueue_all).setIntent(rowData);
            }

			/* //if (type == MediaUtils.TYPE_PLAYLIST) {
			if(false) {
				menu.add(0, MENU_RENAME_PLAYLIST, 0, R.string.rename).setIntent(rowData);
			} else if (rowData.getBooleanExtra(LibraryAdapter.DATA_EXPANDABLE, false)) {
				menu.add(0, MENU_EXPAND, 0, R.string.expand).setIntent(rowData);
			} */
			/* if (type == MediaUtils.TYPE_ALBUM || type == MediaUtils.TYPE_SONG)
				menu.add(0, MENU_MORE_FROM_ARTIST, 0, R.string.more_from_artist).setIntent
				(rowData);
			if (type == MediaUtils.TYPE_SONG)
				menu.add(0, MENU_MORE_FROM_ALBUM, 0, R.string.more_from_album).setIntent(rowData)
				; */
			/* menu.addSubMenu(0, MENU_ADD_TO_PLAYLIST, 0, R.string.add_to_playlist).getItem()
			.setIntent(rowData);
			menu.add(0, MENU_DELETE, 0, R.string.delete).setIntent(rowData); */
        }
    }


    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        if (view == mRoundPlayAllButton) {
            menu.add(MENU_GROUP_ROUND_BUTTON, MENU_PLAY_ALL, 0, R.string.play_all);
            menu.add(MENU_GROUP_ROUND_BUTTON, MENU_ENQUEUE_ALL, 0, R.string.enqueue_all);
        }
    }


    /**
     * Add a set of songs represented by the intent to a playlist. Displays a
     * Toast notifying of success.
     *
     * @param playlistId The id of the playlist to add to.
     * @param intent     An intent created with
     *                   {@link LibraryAdapter#createData(View)}.
     */
    private void addToPlaylist(long playlistId, Intent intent) {
        QueryTask query = buildQueryFromIntent(intent, true, false);
        int count = Playlist.addToPlaylist(getContentResolver(), playlistId, query);

        String message = getResources().getQuantityString(R.plurals.added_to_playlist, count, count,
                intent.getStringExtra("playlistName"));
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    /**
     * Open the playlist editor for the playlist with the given id.
     */
    private void editPlaylist(Intent rowData) {
        Intent launch = new Intent(this, PlaylistActivity.class);
        launch.putExtra("playlist",
                rowData.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID));
        launch.putExtra("title", rowData.getStringExtra(LibraryAdapter.DATA_TITLE));
        startActivity(launch);
    }


    /**
     * Delete the media represented by the given intent and show a Toast
     * informing the user of this.
     *
     * @param intent An intent created with
     *               {@link LibraryAdapter#createData(View)}.
     */
    private void delete(Intent intent) {
        int type = intent.getIntExtra("type", MediaUtils.TYPE_INVALID);
        long id = intent.getLongExtra("id", LibraryAdapter.INVALID_ID);
        String message = null;
        Resources res = getResources();

        if (type == MediaUtils.TYPE_FILE) {
            String file = intent.getStringExtra("file");
            boolean success = MediaUtils.deleteFile(new File(file));
            if (!success) {
                message = res.getString(R.string.delete_file_failed, file);
            }
            //} else if (type == MediaUtils.TYPE_PLAYLIST) {
		/* } else if(false) {
			Playlist.deletePlaylist(getContentResolver(), id); */
        } else {
            int count = PlaybackService.get(this).deleteMedia(type, id);
            message = res.getQuantityString(R.plurals.deleted, count, count);
        }

        if (message == null) {
            message = res.getString(R.string.deleted_item, intent.getStringExtra("title"));
        }

        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }


    @Override
    public boolean onContextItemSelected(MenuItem item) {
        if (item.getGroupId() == MENU_GROUP_ROUND_BUTTON) {

            Limiter limiter = mPagerAdapter.getCurrentLimiter();
            Intent intent = null;

            switch (mPagerAdapter.getCurrentType()) {
                case MediaUtils.TYPE_FILE:
                    intent = new Intent();
                    intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_FILE);
                    intent.putExtra("file", limiter == null ? "/" : limiter.data.toString());
                    break;
                case MediaUtils.TYPE_UNIFIED:
                    if (limiter != null && limiter.type <= 20) {
                        intent = new Intent();
                        intent.putExtra(LibraryAdapter.DATA_TYPE, limiter.type);
                        intent.putExtra(LibraryAdapter.DATA_ID, (Long) limiter.data);
                    } //else continue with null limiter, which will play the entire MediaStore
                    break;
            }

            mRoundPlayAllButton.setVisibility(View.GONE);

            pickSongs(intent,
                    item.getItemId() == MENU_PLAY_ALL ? ACTION_PLAY_ALL : ACTION_ENQUEUE_ALL);

            return true;
        }


        if (item.getGroupId() != 0) {
            return super.onContextItemSelected(item);
        }

        final Intent intent = item.getIntent();

        switch (item.getItemId()) {
            case MENU_EXPAND:
                expand(intent);
                if (mDefaultAction == ACTION_LAST_USED && mLastAction != ACTION_EXPAND) {
                    mLastAction = ACTION_EXPAND;
                }
                break;
            case MENU_ENQUEUE:
                pickSongs(intent, ACTION_ENQUEUE);
                break;
            case MENU_PLAY:
                pickSongs(intent, ACTION_PLAY);
                break;
            case MENU_PLAY_ALL:
                pickSongs(intent, ACTION_PLAY_ALL);
                break;
            case MENU_ENQUEUE_ALL:
                pickSongs(intent, ACTION_ENQUEUE_ALL);
                break;
            case MENU_NEW_PLAYLIST: {
                NewPlaylistDialog dialog =
                        new NewPlaylistDialog(this, null, R.string.create, intent);
                dialog.setDismissMessage(mHandler.obtainMessage(MSG_NEW_PLAYLIST, dialog));
                dialog.show();
                break;
            }
            case MENU_RENAME_PLAYLIST: {
                NewPlaylistDialog dialog =
                        new NewPlaylistDialog(this, intent.getStringExtra("title"), R.string.rename,
                                intent);
                dialog.setDismissMessage(mHandler.obtainMessage(MSG_RENAME_PLAYLIST, dialog));
                dialog.show();
                break;
            }
            case MENU_DELETE:
                String delete_message =
                        getString(R.string.delete_item, intent.getStringExtra("title"));
                AlertDialog.Builder dialog = new AlertDialog.Builder(this);
                dialog.setTitle(R.string.delete);
                dialog.setMessage(delete_message)
                        .setPositiveButton(R.string.delete, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                mHandler.sendMessage(mHandler.obtainMessage(MSG_DELETE, intent));
                            }
                        })
                        .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                            }
                        });
                dialog.create().show();
                break;
            case MENU_ADD_TO_PLAYLIST: {
                SubMenu playlistMenu = item.getSubMenu();
                playlistMenu.add(0, MENU_NEW_PLAYLIST, 0, R.string.new_playlist).setIntent(intent);
                Cursor cursor = Playlist.queryPlaylists(getContentResolver());
                if (cursor != null) {
                    for (int i = 0, count = cursor.getCount(); i != count; ++i) {
                        cursor.moveToPosition(i);
                        long id = cursor.getLong(0);
                        String name = cursor.getString(1);
                        Intent copy = new Intent(intent);
                        copy.putExtra("playlist", id);
                        copy.putExtra("playlistName", name);
                        playlistMenu.add(0, MENU_SELECT_PLAYLIST, 0, name).setIntent(copy);
                    }
                    cursor.close();
                }
                break;
            }
            case MENU_SELECT_PLAYLIST:
                mHandler.sendMessage(mHandler.obtainMessage(MSG_ADD_TO_PLAYLIST, intent));
                break;
            case MENU_MORE_FROM_ARTIST: {
                String selection = "_id=";
                selection += intent.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID);
                setLimiter(-1, selection);
                updateLimiterViews();
                break;
            }
            case MENU_MORE_FROM_ALBUM:
                setLimiter(-1, "_id=" +
                        intent.getLongExtra(LibraryAdapter.DATA_ID, LibraryAdapter.INVALID_ID));
                updateLimiterViews();
                break;
        }

        return true;
    }


    /**
     * Call addToPlaylist with the results from a NewPlaylistDialog stored in
     * obj.
     */
    private static final int MSG_NEW_PLAYLIST = 11;
    /**
     * Delete the songs represented by the intent stored in obj.
     */
    private static final int MSG_DELETE = 12;
    /**
     * Call renamePlaylist with the results from a NewPlaylistDialog stored in
     * obj.
     */
    private static final int MSG_RENAME_PLAYLIST = 13;
    /**
     * Call addToPlaylist with data from the intent in obj.
     */
    private static final int MSG_ADD_TO_PLAYLIST = 15;
    /**
     * Save the current page, passed in arg1, to SharedPreferences.
     */
    private static final int MSG_SAVE_PAGE = 16;


    @Override
    public boolean handleMessage(Message message) {
        switch (message.what) {
            case MSG_ADD_TO_PLAYLIST: {
                Intent intent = (Intent) message.obj;
                addToPlaylist(intent.getLongExtra("playlist", -1), intent);
                break;
            }
            case MSG_NEW_PLAYLIST: {
                NewPlaylistDialog dialog = (NewPlaylistDialog) message.obj;
                if (dialog.isAccepted()) {
                    String name = dialog.getText();
                    long playlistId = Playlist.createPlaylist(getContentResolver(), name);
                    Intent intent = dialog.getIntent();
                    intent.putExtra("playlistName", name);
                    addToPlaylist(playlistId, intent);
                }
                break;
            }
            case MSG_DELETE:
                delete((Intent) message.obj);
                break;
            case MSG_RENAME_PLAYLIST: {
                NewPlaylistDialog dialog = (NewPlaylistDialog) message.obj;
                if (dialog.isAccepted()) {
                    long playlistId = dialog.getIntent().getLongExtra("id", -1);
                    Playlist.renamePlaylist(getContentResolver(), playlistId, dialog.getText());
                }
                break;
            }
            case MSG_SAVE_PAGE: {
                SharedPreferences.Editor editor = PlaybackService.getSettings(this).edit();
                editor.putInt("library_page", message.arg1);
                editor.commit();
                break;
            }
            default:
                return super.handleMessage(message);
        }

        return true;
    }


    @Override
    public void onMediaChange() {
        mPagerAdapter.invalidateData();
    }


    @Override
    protected void onStateChange(int state, int toggled) {
        super.onStateChange(state, toggled);

        if ((toggled & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
            ((TextView) findViewById(R.id.bottom_bar_hint)).setText(R.string.playback_queue_empty);
        }

        //FIXME unnecessary leftover code, remove
        if ((toggled & PlaybackService.FLAG_EMPTY_QUEUE) != 0 && mEmptyQueue != null) {
            mEmptyQueue.setVisibility(
                    (state & PlaybackService.FLAG_EMPTY_QUEUE) == 0 ? View.GONE : View.VISIBLE);
        }
    }


    @Override
    protected void onSongChange(Song song) {
        super.onSongChange(song);

        if (mTitle != null) {
            Bitmap cover = null;

            if (song == null) {
                if (mActionControls == null) {
                    mTitle.setText(R.string.none);
                    mArtist.setText(null);
                } else {
                    mTitle.setText(null);
                    mArtist.setText(null);
                    mCover.setImageDrawable(null);
                    return;
                }
            } else {
                Resources res = getResources();
                String title = song.title == null ? res.getString(R.string.unknown) : song.title;
                String artist = song.artist == null ? res.getString(R.string.unknown) : song.artist;
                mTitle.setText(title);
                mArtist.setText(artist);
                mActionControls.setOnClickListener(this);
                findViewById(R.id.bottom_bar_hint).setVisibility(View.GONE);
                cover = song.getCover(this);
            }

            if (Song.mCoverLoadMode == 0) {
                mCover.setVisibility(View.GONE);
            } else if (cover == null) {
                mCover.setImageResource(R.drawable.fallback_cover);
            } else {
                mCover.setImageBitmap(cover);
            }
        }
    }


    @Override
    public void onClick(DialogInterface dialog, int which) {
        dialog.dismiss();
    }


    @Override
    public void onDismiss(DialogInterface dialog) {
        ListView list = ((AlertDialog) dialog).getListView();
        // subtract 1 for header
        int which = list.getCheckedItemPosition() - 1;

        RadioGroup group = (RadioGroup) list.findViewById(R.id.sort_direction);
        if (group.getCheckedRadioButtonId() == R.id.descending) {
            which = ~which;
        }

        mPagerAdapter.setSortMode(which);
    }


    /**
     * Called when a new page becomes visible.
     *
     * @param position The position of the new page.
     * @param adapter  The new visible adapter.
     */
    public void onPageChanged(int position, LibraryAdapter adapter) {
        mLastActedId = LibraryAdapter.INVALID_ID;
        updateLimiterViews();
        if (adapter != null && adapter.getLimiter() ==
                null) { //TODO: this may not be working at the moment, fix it
            // Save current page so it is opened on next startup. Don't save if
            // the page was expanded to, as the expanded page isn't the starting
            // point.
            Handler handler = mHandler;
            handler.sendMessage(mHandler.obtainMessage(MSG_SAVE_PAGE, position, 0));
        }
    }


    /**
     * Used to lock the Dropbox file browser UI while retrieving directory contents from Dropbox.
     */
    private void lockDropboxFileBrowser() {
        ViewGroup dbLayout = mPagerAdapter.mContainingLayouts[MediaUtils.TYPE_DROPBOX];
        View screen = dbLayout.findViewById(R.id.list_view_loading_screen);
        screen.setVisibility(View.VISIBLE);
    }


    /**
     * Used to unlock the Dropbox file browser after we're done retrieving directory contents.
     */
    private void unlockDropboxFileBrowser() {
        ViewGroup dbLayout = mPagerAdapter.mContainingLayouts[MediaUtils.TYPE_DROPBOX];
        View screen = dbLayout.findViewById(R.id.list_view_loading_screen);
        screen.setVisibility(View.GONE);
    }

}
