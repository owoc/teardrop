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

import mp.teardrop.SongTimeline.Callback;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Message;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The primary playback screen with playback controls and large cover display.
 */
public class FullPlaybackActivity extends PlaybackActivity
	implements SeekBar.OnSeekBarChangeListener
	         , View.OnLongClickListener,
	         Callback
{
	public static final int DISPLAY_INFO_OVERLAP = 0;
	public static final int DISPLAY_INFO_BELOW = 1;
	public static final int DISPLAY_INFO_WIDGETS = 2;

	private TextView mOverlayText;

	private SeekBar mSeekBar;
	private TextView mElapsedView;
	private TextView mDurationView;
	private TextView mQueuePosView;

	private TextView mTitle;
	private TextView mAlbum;
	private TextView mArtist;

	/**
	 * Current song duration in milliseconds.
	 */
	private long mDuration = 0;
	private boolean mSeekBarTracking;
	private boolean mPaused;

	/**
	 * The current display mode, which determines layout and cover render style.
	 */
	private int mDisplayMode = 2;

	private Action mCoverPressAction;
	private Action mCoverLongPressAction;

	/**
	 * Cached StringBuilder for formatting track position.
	 */
	private final StringBuilder mTimeBuilder = new StringBuilder();
	/**
	 * The currently playing song.
	 */
	private Song mCurrentSong;

	private String mGenre;
	private TextView mGenreView;
	private String mTrack;
	private TextView mTrackView;
	private String mYear;
	private TextView mYearView;
	private String mComposer;
	private TextView mComposerView;
	private String mFormat;
	private TextView mFormatView;
	private String mReplayGain;
	private TextView mReplayGainView;

	private ListView mListView;
	private ShowQueueAdapter listAdapter;

	@Override
	public void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		setTitle(R.string.playback_view);

		int layout = R.layout.full_playback;
		int coverStyle = CoverBitmap.STYLE_NO_INFO;

		setContentView(layout);

		CoverView coverView = (CoverView)findViewById(R.id.cover_view);
		coverView.setup(mLooper, this, coverStyle);
		coverView.setOnClickListener(this);
		coverView.setOnLongClickListener(this);
		mCoverView = coverView;

		View previousButton = findViewById(R.id.previous);
		previousButton.setOnClickListener(this);
		mPlayPauseButton = (ImageButton)findViewById(R.id.play_pause);
		mPlayPauseButton.setOnClickListener(this);
		View nextButton = findViewById(R.id.next);
		nextButton.setOnClickListener(this);

		mTitle = (TextView)findViewById(R.id.title);
		mAlbum = (TextView)findViewById(R.id.album);
		mArtist = (TextView)findViewById(R.id.artist);

		mElapsedView = (TextView)findViewById(R.id.elapsed);
		mDurationView = (TextView)findViewById(R.id.duration);
		mSeekBar = (SeekBar)findViewById(R.id.seek_bar);
		mSeekBar.setMax(1000);
		mSeekBar.setOnSeekBarChangeListener(this);
		mQueuePosView = (TextView)findViewById(R.id.queue_pos);

		mGenreView = (TextView)findViewById(R.id.genre);
		mTrackView = (TextView)findViewById(R.id.track);
		mYearView = (TextView)findViewById(R.id.year);
		mComposerView = (TextView)findViewById(R.id.composer);
		mFormatView = (TextView)findViewById(R.id.format);
		mReplayGainView = (TextView)findViewById(R.id.replaygain);

		mEndButton = (ImageButton) findViewById(R.id.new_end_action);
		mEndButton.setOnClickListener(this);
		registerForContextMenu(mEndButton);
		
		mListView = (ListView) findViewById(R.id.integrated_list);
		listAdapter = new ShowQueueAdapter(this, R.layout.showqueue_row);
		mListView.setAdapter(listAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				jumpToSong(position);
			}});
	}

	@Override
	public void onStart()
	{
		super.onStart();

		SharedPreferences settings = PlaybackService.getSettings(this);
		if (mDisplayMode != Integer.parseInt(settings.getString(PrefKeys.DISPLAY_MODE, "2"))) {
			finish();
			startActivity(new Intent(this, FullPlaybackActivity.class));
		}

		mCoverPressAction = Action.getAction(settings, PrefKeys.COVER_PRESS_ACTION, Action.ToggleControls);
		mCoverLongPressAction = Action.getAction(settings, PrefKeys.COVER_LONGPRESS_ACTION, Action.PlayPause);
	}

	@Override
	public void onResume()
	{
		super.onResume();
		mPaused = false;
		updateElapsedTime();
		refreshSongQueueList();
	}

	@Override
	public void onPause()
	{
		super.onPause();
		mPaused = true;
	}

	@Override
	protected void onStateChange(int state, int toggled)
	{
		super.onStateChange(state, toggled);

		if ((toggled & (PlaybackService.FLAG_NO_MEDIA|PlaybackService.FLAG_EMPTY_QUEUE)) != 0) {
			if ((state & PlaybackService.FLAG_NO_MEDIA) != 0) {
				//showOverlayMessage(R.string.no_songs);
				//TODO: display some info
			} else if ((state & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
				//showOverlayMessage(R.string.empty_queue);
				//TODO: display some info
			}
		}

		if ((state & PlaybackService.FLAG_PLAYING) != 0)
			updateElapsedTime();
	}

	@Override
	protected void onSongChange(Song song)
	{
		super.onSongChange(song);

		//setDuration(song == null ? 0 : song.duration);

		if (mTitle != null) {
			if (song == null) {
				mTitle.setText(null);
				mAlbum.setText(null);
				mArtist.setText(null);
			} else {
				mTitle.setText(song.title);
				mAlbum.setText(song.album);
				mArtist.setText(song.artist);
			}
			//updateQueuePosition();
		}

		mCurrentSong = song;
		updateElapsedTime();
		hideLoadingMessage();
		refreshSongQueueList();
	}
	
	@Override
	public void displayCurrentSongDuration(long duration) {
		setDuration(duration);
	}
	
	@Override
	public void displayLoadingMessage() {
		findViewById(R.id.current_song_info).setVisibility(View.GONE);
		findViewById(R.id.current_song_buffering_message).setVisibility(View.VISIBLE);
	}
	
	@Override
	public void hideLoadingMessage() {
		findViewById(R.id.current_song_info).setVisibility(View.VISIBLE);
		findViewById(R.id.current_song_buffering_message).setVisibility(View.GONE);
	}

	/**
	 * Update the queue position display. mQueuePos must not be null.
	 */
	private void updateQueuePosition()
	{
		if (PlaybackService.finishAction(mState) == SongTimeline.FINISH_RANDOM) {
			// Not very useful in random mode; it will always show something
			// like 11/13 since the timeline is trimmed to 10 previous songs.
			// So just hide it.
			mQueuePosView.setText(null);
		} else {
			PlaybackService service = PlaybackService.get(this);
			mQueuePosView.setText((service.getTimelinePosition() + 1) + "/" + service.getTimelineLength());
		}
	}

	@Override
	public void onPositionInfoChanged()
	{
		if (mQueuePosView != null)
			mUiHandler.sendEmptyMessage(MSG_UPDATE_POSITION);
	}

	/**
	 * Update the current song duration fields.
	 *
	 * @param duration The new duration, in milliseconds.
	 */
	private void setDuration(long duration)
	{
		mDuration = duration;
		mUiHandler.sendEmptyMessage(MSG_UPDATE_DURATION);
	}
	
	/**
	 * Updates the UI to show the current song's duration.
	 */
	private void updateDuration() {
		mDurationView.setText(DateUtils.formatElapsedTime(mTimeBuilder, mDuration / 1000));
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		super.onCreateOptionsMenu(menu);
		menu.add(0, MENU_CLEAR_QUEUE, 0, R.string.clear_queue).setIcon(R.drawable.ic_menu_close_clear_cancel);
		//menu.add(0, MENU_ENQUEUE_ALBUM, 0, R.string.enqueue_current_album).setIcon(R.drawable.ic_menu_add);
		//menu.add(0, MENU_ENQUEUE_ARTIST, 0, R.string.enqueue_current_artist).setIcon(R.drawable.ic_menu_add);
		//menu.add(0, MENU_ENQUEUE_GENRE, 0, R.string.enqueue_current_genre).setIcon(R.drawable.ic_menu_add);
		//menu.add(0, MENU_SONG_FAVORITE, 0, R.string.add_to_favorites);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId()) {
		case android.R.id.home:
		case MENU_LIBRARY:
			openLibrary(null);
			break;
		/* case MENU_ENQUEUE_ALBUM:
			PlaybackService.get(this).enqueueFromCurrent(MediaUtils.TYPE_ALBUM);
			break;
		case MENU_ENQUEUE_ARTIST:
			PlaybackService.get(this).enqueueFromCurrent(MediaUtils.TYPE_ARTIST);
			break;
		case MENU_ENQUEUE_GENRE:
			PlaybackService.get(this).enqueueFromCurrent(MediaUtils.TYPE_GENRE);
			break; */
		case MENU_CLEAR_QUEUE:
			PlaybackService.get(this).clearQueue(this);
			break;
		default:
			return super.onOptionsItemSelected(item);
		}

		return true;
	}

	@Override
	public boolean onSearchRequested()
	{
		openLibrary(null);
		return false;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		switch (keyCode) {
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			shiftCurrentSong(SongTimeline.SHIFT_NEXT_SONG);
			findViewById(R.id.next).requestFocus();
			return true;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			shiftCurrentSong(SongTimeline.SHIFT_PREVIOUS_SONG);
			findViewById(R.id.previous).requestFocus();
			return true;
		}

		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Update seek bar progress and schedule another update in one second
	 */
	private void updateElapsedTime()
	{
		long position = PlaybackService.hasInstance() ? PlaybackService.get(this).getPosition() : 0;

		if (!mSeekBarTracking) {
			long duration = mDuration;
			mSeekBar.setProgress(duration == 0 ? 0 : (int)(1000 * position / duration));
		}

		mElapsedView.setText(DateUtils.formatElapsedTime(mTimeBuilder, position / 1000));

		if (!mPaused && (mState & PlaybackService.FLAG_PLAYING) != 0) {
			// Try to update right after the duration increases by one second
			long next = 1050 - position % 1000;
			mUiHandler.removeMessages(MSG_UPDATE_PROGRESS);
			mUiHandler.sendEmptyMessageDelayed(MSG_UPDATE_PROGRESS, next);
		}
	}

	/**
	 * Retrieve the extra metadata for the current song.
	 */
	private void loadExtraInfo()
	{
		Song song = mCurrentSong;

		mGenre = null;
		mTrack = null;
		mYear = null;
		mComposer = null;
		mFormat = null;
		mReplayGain = null;
		
		if(song != null) {
			
			MediaMetadataRetriever data = new MediaMetadataRetriever();
			
			try {
				data.setDataSource(song.path);
			} catch (Exception e) {
				Log.w("OrchidMP", "Failed to extract metadata from " + song.path);
			}
			
			mGenre = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
			mTrack = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER);
			String composer = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER);
			if (composer == null)
				composer = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_WRITER);
			mComposer = composer;

			String year = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR);
			if (year == null || "0".equals(year)) {
				year = null;
			} else {
				int dash = year.indexOf('-');
				if (dash != -1)
					year = year.substring(0, dash);
			}
			mYear = year;

			StringBuilder sb = new StringBuilder(12);
			sb.append(decodeMimeType(data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)));
			String bitrate = data.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE);
			if (bitrate != null && bitrate.length() > 3) {
				sb.append(' ');
				sb.append(bitrate.substring(0, bitrate.length() - 3));
				sb.append("kbps");
			}
			mFormat = sb.toString();
			
			if(song.path != null) { /* ICS bug? */
				float[] rg = PlaybackService.get(this).getReplayGainValues(song.path);
				mReplayGain = "track="+rg[0]+"dB, album="+rg[1]+"dB";
			}
			
			data.release();
		}

		mUiHandler.sendEmptyMessage(MSG_COMMIT_INFO);
	}

	/**
	 * Decode the given mime type into a more human-friendly description.
	 */
	private static String decodeMimeType(String mime)
	{
		if ("audio/mpeg".equals(mime)) {
			return "MP3";
		} else if ("audio/mp4".equals(mime)) {
			return "AAC";
		} else if ("audio/vorbis".equals(mime)) {
			return "Ogg Vorbis";
		} else if ("audio/flac".equals(mime)) {
			return "FLAC";
		}
		return mime;
	}

	/**
	 * Update the seekbar progress with the current song progress. This must be
	 * called on the UI Handler.
	 */
	private static final int MSG_UPDATE_PROGRESS = 10;
	/**
	 * Save the hidden_controls preference to storage.
	 */
	private static final int MSG_SAVE_CONTROLS = 14;
	/**
	 * Call {@link #loadExtraInfo()}.
	 */
	private static final int MSG_LOAD_EXTRA_INFO = 15;
	/**
	 * Pass obj to mExtraInfo.setText()
	 */
	private static final int MSG_COMMIT_INFO = 16;
	/**
	 * Calls {@link #updateQueuePosition()}.
	 */
	private static final int MSG_UPDATE_POSITION = 17;
	/**
	 * Calls {@link #seekToProgress()}.
	 */
	private static final int MSG_SEEK_TO_PROGRESS = 18;
	/**
	 * Calls {@link #updateDuration()}.
	 */
	private static final int MSG_UPDATE_DURATION = 21;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_UPDATE_PROGRESS:
			updateElapsedTime();
			break;
		case MSG_LOAD_EXTRA_INFO:
			loadExtraInfo();
			break;
		case MSG_COMMIT_INFO: {
			mGenreView.setText(mGenre);
			mTrackView.setText(mTrack);
			mYearView.setText(mYear);
			mComposerView.setText(mComposer);
			mFormatView.setText(mFormat);
			mReplayGainView.setText(mReplayGain);
			break;
		}
		/* case MSG_UPDATE_POSITION:
			updateQueuePosition();
			break; */
		case MSG_UPDATE_DURATION:
			updateDuration();
			break;
		case MSG_SEEK_TO_PROGRESS:
			PlaybackService.get(this).seekToProgress(message.arg1);
			updateElapsedTime();
			break;
		default:
			return super.handleMessage(message);
		}

		return true;
	}

	@Override
	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
	{
		if (fromUser) {
			mElapsedView.setText(DateUtils.formatElapsedTime(mTimeBuilder, progress * mDuration / 1000000));
			mUiHandler.removeMessages(MSG_SEEK_TO_PROGRESS);
			mUiHandler.sendMessageDelayed(mUiHandler.obtainMessage(MSG_SEEK_TO_PROGRESS, progress, 0), 150);
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = true;
	}

	@Override
	public void onStopTrackingTouch(SeekBar seekBar)
	{
		mSeekBarTracking = false;
	}

	public void performAction(Action action)
	{
		PlaybackService.get(this).performAction(action, this);
	}

	@Override
	public void onClick(View view)
	{
		if (view == mOverlayText && (mState & PlaybackService.FLAG_EMPTY_QUEUE) != 0) {
			setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_RANDOM));
		} else if (view == mCoverView) {
			performAction(mCoverPressAction);
		} else if (view.getId() == R.id.info_table) {
			openLibrary(mCurrentSong);
		} else if(view == mEndButton) {
			openContextMenu(view);
		} else {
			super.onClick(view);
		}
	}

	@Override
	public boolean onLongClick(View view)
	{
		switch (view.getId()) {
		case R.id.cover_view:
			performAction(mCoverLongPressAction);
			break;
		default:
			return false;
		}

		return true;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View view,
			ContextMenuInfo menuInfo) {
		if (view == mEndButton) {
		    menu.add(PlaybackActivity.GROUP_FINISH, SongTimeline.FINISH_STOP, 0, R.string.no_repeat);
			menu.add(PlaybackActivity.GROUP_FINISH, SongTimeline.FINISH_REPEAT, 0, R.string.repeat);
			menu.add(PlaybackActivity.GROUP_FINISH, SongTimeline.FINISH_REPEAT_CURRENT, 0, R.string.repeat_current_song);
			menu.add(PlaybackActivity.GROUP_FINISH, SongTimeline.FINISH_STOP_CURRENT, 0, R.string.stop_current_song);
			menu.add(PlaybackActivity.GROUP_FINISH, SongTimeline.FINISH_RANDOM, 0, R.string.random);
			menu.add(0, MENU_SHUFFLE_QUEUE, 0, R.string.shuffle_queue);
		}
	}
	
	private void refreshSongQueueList() {
		int i, stotal, spos;
		PlaybackService service = PlaybackService.get(this);
		
		stotal = service.getTimelineLength();   /* Total number of songs in queue */
		spos   = service.getTimelinePosition(); /* Current position in queue      */
		
		listAdapter.clear();                    /* Flush all existing entries...  */
		listAdapter.highlightRow(spos);         /* and highlight current position */
		
		for(i=0 ; i<stotal; i++) {
			listAdapter.add(service.getSongByQueuePosition(i));
		}
		
		/* scroll to current song if needed
		 * smoothScrollToPosition is a bit bugged (see Stack Overflow 14479078),
		 * but the bug shouldn't apply to the way it's used here */
		int last = mListView.getLastVisiblePosition();  
		int first = mListView.getFirstVisiblePosition();
		if(spos - 1 < first || spos + 1 > last) mListView.smoothScrollToPosition(spos);
	}

	@Override
	public void activeSongReplaced(int delta, Song song) {
		//not needed
	}

	@Override
	public void timelineChanged() {
		refreshSongQueueList();
	}

	public void timelineShuffled() {
		refreshSongQueueList();
		setState(PlaybackService.get(this).setFinishAction(SongTimeline.FINISH_STOP));
		Toast.makeText(this, R.string.queue_shuffled, Toast.LENGTH_SHORT).show();
	}

	@Override
	public void positionInfoChanged() {
		//not needed
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		if(item.getItemId() == MENU_SHUFFLE_QUEUE) {
			PlaybackService.get(this).randomlyReorderQueue(this);
			return true;
		}
		
		int group = item.getGroupId();
		int id = item.getItemId();
		if (group == GROUP_FINISH) {
			setState(PlaybackService.get(this).setFinishAction(id));
		}
		return true;
	}
	
}
