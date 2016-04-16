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

import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.ListIterator;

import org.json.JSONArray;
import org.json.JSONException;

import junit.framework.Assert;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;

/**
 * Contains the list of currently playing songs, implements repeat and shuffle
 * support, and contains methods to fetch more songs from the MediaStore.
 */
public final class SongTimeline {
	/**
	 * Stop playback.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_STOP = 0;
	/**
	 * Repeat from the beginning.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_REPEAT = 1;
	/**
	 * Repeat the current song. This behavior is implemented entirely in
	 * {@link PlaybackService#onCompletion(android.media.MediaPlayer)};
	 * pressing the next or previous buttons will advance the song as normal;
	 * only allowing the song to play until the end will repeat it.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_REPEAT_CURRENT = 2;
	/**
	 * Stop playback after current song. This behavior is implemented entirely
	 * in {@link PlaybackService#onCompletion(android.media.MediaPlayer)};
	 * pressing the next or previous buttons will advance the song as normal;
	 * only allowing the song to play until the end.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_STOP_CURRENT = 3;
	/**
	 * Add random songs to the playlist.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public static final int FINISH_RANDOM = 4;

	/**
	 * Icons corresponding to each of the finish actions.
	 */
	public static final int[] FINISH_ICONS =
		{ R.drawable.no_shuffle_24, R.drawable.repeat_all_24, R.drawable.repeat_one_24, R.drawable.stop_after_current_24, R.drawable.repeat_random_24 };

	/**
	 * Clear the timeline and use only the provided songs.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY = 0;
	/**
	 * Clear the queue and add the songs after the current song.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY_NEXT = 1;
	/**
	 * Add the songs at the end of the timeline, clearing random songs.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE = 2;
	/**
	 * Like play mode, but make the song at the given position play first by
	 * removing the songs before the given position in the query and appending
	 * them to the end of the queue.
	 *
	 * Pass the position in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY_POS_FIRST = 3;
	/**
	 * Like play mode, but make the song with the given id play first by
	 * removing the songs before the song in the query and appending
	 * them to the end of the queue. If there are multiple songs with
	 * the given id, picks the first song with that id.
	 *
	 * Pass the id in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_PLAY_ID_FIRST = 4;
	/**
	 * Like enqueue mode, but make the song with the given id play first by
	 * removing the songs before the song in the query and appending
	 * them to the end of the queue. If there are multiple songs with
	 * the given id, picks the first song with that id.
	 *
	 * Pass the id in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE_ID_FIRST = 5;
	/**
	 * Like enqueue mode, but make the song at the given position play first by
	 * removing the songs before the given position in the query and appending
	 * them to the end of the queue.
	 *
	 * Pass the position in QueryTask.data.
	 *
	 * @see SongTimeline#addSongs(Context, QueryTask)
	 */
	public static final int MODE_ENQUEUE_POS_FIRST = 6;

	/**
	 * Disable shuffle.
	 *
	 * @see SongTimeline#setShuffleMode(int)
	 */
	public static final int SHUFFLE_NONE = 0;
	/**
	 * Randomize order of songs.
	 *
	 * @see SongTimeline#setShuffleMode(int)
	 */
	public static final int SHUFFLE_SONGS = 1;
	/**
	 * Randomize order of albums, preserving the order of tracks inside the
	 * albums.
	 *
	 * @see SongTimeline#setShuffleMode(int)
	 */
	public static final int SHUFFLE_ALBUMS = 2;

	/**
	 * Icons corresponding to each of the shuffle actions.
	 */
	public static final int[] SHUFFLE_ICONS =
		{ R.drawable.shuffle_inactive, R.drawable.shuffle_active, R.drawable.shuffle_album_active };

	/**
	 * Move current position to the previous album.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_PREVIOUS_ALBUM = -2;
	/**
	 * Move current position to the previous song.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_PREVIOUS_SONG = -1;
	/**
	 * Move current position to the next song.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_NEXT_SONG = 1;
	/**
	 * Move current position to the next album.
	 *
	 * @see SongTimeline#shiftCurrentSong(int)
	 */
	public static final int SHIFT_NEXT_ALBUM = 2;

	private final Context mContext;
	/**
	 * All the songs currently contained in the timeline. Each Song object
	 * should be unique, even if it refers to the same media.
	 */
	private ArrayList<Song> mSongs = new ArrayList<Song>(12);
	/**
	 * The position of the current song (i.e. the playing song).
	 */
	private int mCurrentPos;
	/**
	 * How to shuffle/whether to shuffle. One of SongTimeline.SHUFFLE_*.
	 */
	private int mShuffleMode;
	/**
	 * What to do when the end of the playlist is reached.
	 * Must be one of SongTimeline.FINISH_*.
	 */
	private int mFinishAction;

	// for shuffleAll()
	private ArrayList<Song> mShuffledSongs;

	// for saveActiveSongs()
	private Song mSavedPrevious;
	private Song mSavedCurrent;
	private Song mSavedNext;
	private int mSavedPos;
	private int mSavedSize;

	/**
	 * Interface to respond to timeline changes.
	 */
	public interface Callback {
		/**
		 * Called when an active song in the timeline is replaced by a method
		 * other than shiftCurrentSong()
		 *
		 * @param delta The distance from the current song. Will always be -1,
		 * 0, or 1.
		 * @param song The new song at the position
		 */
		public void activeSongReplaced(int delta, Song song);

		/**
		 * Called when the timeline state has changed and should be saved to
		 * storage.
		 */
		public void timelineChanged();

		/**
		 * Called when the length of the timeline has changed.
		 */
		public void positionInfoChanged();
	}
	/**
	 * The current Callback, if any.
	 */
	private Callback mCallback;

	public SongTimeline(Context context)
	{
		mContext = context;
	}

	/**
	 * Compares the ids of songs.
	 */
	public static class IdComparator implements Comparator<Song> {
		@Override
		public int compare(Song a, Song b)
		{
			if (a.id == b.id)
				return 0;
			if (a.id > b.id)
				return 1;
			return -1;
		}
	}

	/**
	 * Compares the flags of songs.
	 */
	public static class FlagComparator implements Comparator<Song> {
		@Override
		public int compare(Song a, Song b)
		{
			return a.flags - b.flags;
		}
	}

	/**
	 * Initializes the timeline with data read from the stream. Data should have
	 * been saved by a call to {@link SongTimeline#writeState(DataOutputStream)}.
	 *
	 * @param in The stream to read from.
	 */
	public void readState(SharedPreferences prefs) throws JSONException
	{
		synchronized (this) {
					
			ArrayList<Song> songs = new ArrayList<Song>();

			JSONArray jsonBourneArray = new JSONArray(prefs.getString(PlaybackService.PREFS_SAVED_SONGS, "[]"));
			
			for(int i=0; i != jsonBourneArray.length(); i++) {
				Song song = Song.fromJsonObject(jsonBourneArray.getJSONObject(i));
				songs.add(song);
			}
			
			mSongs = songs;

			//mCurrentPos = Math.min(mSongs == null ? 0 : mSongs.size(), in.readInt());
			mCurrentPos = 0;
			//mFinishAction = in.readInt();
			//mShuffleMode = in.readInt();
		}
	}

	/**
	 * Writes the current songs and state to the given SharedPreferences.
	 */
	public void writeState(SharedPreferences prefs) throws JSONException
	{
		// Must update PlaybackService.STATE_VERSION when changing behavior here.
		synchronized (this) {
			ArrayList<Song> songs = mSongs;
			
			JSONArray jsonBourneArray = new JSONArray();
			
			for(Song song : songs) {
				if(song != null) {
					jsonBourneArray.put(song.toJsonObject());
				}
			}
			
			SharedPreferences.Editor editor = prefs.edit();
			editor.putString(PlaybackService.PREFS_SAVED_SONGS, jsonBourneArray.toString());
			editor.commit();
		}
	}

	/**
	 * Sets the current callback to <code>callback</code>.
	 */
	public void setCallback(Callback callback)
	{
		mCallback = callback;
	}

	/**
	 * Return the current shuffle mode.
	 *
	 * @return The shuffle mode. One of SongTimeline.SHUFFLE_*.
	 */
	public int getShuffleMode()
	{
		return mShuffleMode;
	}

	/**
	 * Return the finish action.
	 *
	 * @see SongTimeline#setFinishAction(int)
	 */
	public int getFinishAction()
	{
		return mFinishAction;
	}

	/**
	 * Set how to shuffle. Will shuffle the current set of songs when enabling
	 * shuffling if random mode is not enabled.
	 *
	 * @param mode One of SongTimeline.MODE_*
	 */
	public void setShuffleMode(int mode)
	{
		if (mode == mShuffleMode)
			return;

		synchronized (this) {
			saveActiveSongs();
			mShuffledSongs = null;
			mShuffleMode = mode;
			if (mode != SHUFFLE_NONE && mFinishAction != FINISH_RANDOM && !mSongs.isEmpty()) {
				shuffleAll();
				ArrayList<Song> songs = mShuffledSongs;
				mShuffledSongs = null;
				mCurrentPos = songs.indexOf(mSavedCurrent);
				mSongs = songs;
			}
			broadcastChangedSongs();
		}

		changed();
	}

	/**
	 * Set what to do when the end of the playlist is reached. Must be one of
	 * SongTimeline.FINISH_* (stop, repeat, or add random song).
	 */
	public void setFinishAction(int action)
	{
		saveActiveSongs();
		mFinishAction = action;
		broadcastChangedSongs();
		changed();
	}

	/**
	 * Shuffle all the songs in the timeline, storing the result in
	 * mShuffledSongs.
	 *
	 * @return The first song from the shuffled songs.
	 */
	private Song shuffleAll()
	{
		if (mShuffledSongs != null)
			return mShuffledSongs.get(0);

		ArrayList<Song> songs = new ArrayList<Song>(mSongs);
		MediaUtils.shuffle(songs, mShuffleMode == SHUFFLE_ALBUMS);
		mShuffledSongs = songs;
		return songs.get(0);
	}

	/**
	 * Returns the song <code>delta</code> places away from the current
	 * position. Returns null if there is a problem retrieving the song.
	 *
	 * @param delta The offset from the current position. Must be -1, 0, or 1.
	 */
	public Song getSong(int delta)
	{
		Assert.assertTrue(delta >= -1 && delta <= 1);

		ArrayList<Song> timeline = mSongs;
		Song song;

		synchronized (this) {
			int pos = mCurrentPos + delta;
			int size = timeline.size();

			if (pos < 0) {
				if (size == 0)
					return null;
				song = timeline.get(Math.max(0, size - 1));
			} else if (pos > size) {
				return null;
			} else if (pos == size) {
				if (size == 0)
					// empty queue
					return null;
				else if (mShuffleMode != SHUFFLE_NONE)
					song = shuffleAll();
				else
					song = timeline.get(0);
			} else {
				song = timeline.get(pos);
			}
		}

		if (song == null)
			// we have no songs in the library
			return null;

		return song;
	}

	/**
	 * Internal implementation for shiftCurrentSong. Does all the work except
	 * broadcasting the timeline change: updates mCurrentPos and handles
	 * shuffling, repeating, and random mode.
	 *
	 * @param delta -1 to move to the previous song or 1 for the next.
	 */
	private void shiftCurrentSongInternal(int delta)
	{
		int pos = mCurrentPos + delta;

		if (/* mFinishAction != FINISH_RANDOM  && */ pos == mSongs.size()) {
			if (mShuffleMode != SHUFFLE_NONE && !mSongs.isEmpty()) {
				if (mShuffledSongs == null)
					shuffleAll();
				mSongs = mShuffledSongs;
			}

			pos = 0;
		} else if (pos < 0) {
			/* if (mFinishAction == FINISH_RANDOM)
				pos = 0;
			else */
			pos = Math.max(0, mSongs.size() - 1);
		}

		mCurrentPos = pos;
		mShuffledSongs = null;
	}
	
	/**
	 * Hard-Jump to given queue position
	*/
	public Song setCurrentQueuePosition(int pos) {
		mCurrentPos = pos;
		mShuffledSongs = null;
		return getSong(0);
	}
	
	/**
	 * Returns 'Song' at given position in queue
	*/
	public Song getSongByQueuePosition(int id) {
		return mSongs.get(id);
	}
	
	/**
	 * Move to the next or previous song or album.
	 *
	 * @param delta One of SongTimeline.SHIFT_*.
	 * @return The Song at the new position
	 */
	public Song shiftCurrentSong(int delta)
	{
		synchronized (this) {
			if (delta == SHIFT_PREVIOUS_SONG || delta == SHIFT_NEXT_SONG) {
				shiftCurrentSongInternal(delta);
			} else {
				Song song = getSong(0);
				long currentAlbum = song.albumId;
				long currentSong = song.id;
				delta = delta > 0 ? 1 : -1;
				do {
					shiftCurrentSongInternal(delta);
					song = getSong(0);
				} while (currentAlbum == song.albumId && currentSong != song.id);
			}
		}
		changed();
		return getSong(0);
	}

	/**
	 * Run the given query and add the results to the song timeline.
	 *
	 * @param context A context to use.
	 * @param query The query to be run. The mode variable must be initialized
	 * to one of SongTimeline.MODE_*. The type and data variables may also need
	 * to be initialized depending on the given mode.
	 * @return The number of songs that were added.
	 */
	public int addSongs(Context context, QueryTask query)
	{
		Cursor cursor = query.runQuery(context.getContentResolver());
		if (cursor == null) {
			return 0;
		}

		int count = cursor.getCount();
		if (count == 0) {
			return 0;
		}

		int mode = query.mode;
		int type = query.type;
		long data = query.data;

		ArrayList<Song> timeline = mSongs;
		synchronized (this) {
			saveActiveSongs();

			switch (mode) {
			case MODE_ENQUEUE:
			case MODE_ENQUEUE_POS_FIRST:
			case MODE_ENQUEUE_ID_FIRST:
				break;
			case MODE_PLAY_NEXT:
				timeline.subList(mCurrentPos + 1, timeline.size()).clear();
				break;
			case MODE_PLAY:
			case MODE_PLAY_POS_FIRST:
			case MODE_PLAY_ID_FIRST:
				timeline.clear();
				mCurrentPos = 0;
				break;
			default:
				throw new IllegalArgumentException("Invalid mode: " + mode);
			}

			int start = timeline.size();

			Song jumpSong = null;
			for (int j = 0; j != count; ++j) {
				cursor.moveToPosition(j);
				Song song = new Song(-1);
				song.populate(cursor);
				timeline.add(song);

				if (jumpSong == null) {
					if ((mode == MODE_PLAY_POS_FIRST || mode == MODE_ENQUEUE_POS_FIRST) && j == data) {
						jumpSong = song;
					} else if (mode == MODE_PLAY_ID_FIRST || mode == MODE_ENQUEUE_ID_FIRST) {
						long id;
						switch (type) {
						/* case MediaUtils.TYPE_ARTIST:
							id = song.artistId;
							break;
						case MediaUtils.TYPE_ALBUM:
							id = song.albumId;
							break;
						case MediaUtils.TYPE_SONG:
							id = song.id;
							break; */
						default:
							throw new IllegalArgumentException("Unsupported id type: " + type);
						}
						/* if (id == data)
							jumpSong = song; */
					}
				}
			}

			if (mShuffleMode != SHUFFLE_NONE)
				MediaUtils.shuffle(timeline.subList(start, timeline.size()), mShuffleMode == SHUFFLE_ALBUMS);

			if (jumpSong != null) {
				int jumpPos = timeline.indexOf(jumpSong);
				if (jumpPos != start) {
					// Get the sublist twice to avoid a ConcurrentModificationException.
					timeline.addAll(timeline.subList(start, jumpPos));
					timeline.subList(start, jumpPos).clear();
				}
			}

			broadcastChangedSongs();
		}

		changed();

		return count;
	}
	
	int addCloudSongs(ArrayList<CloudSongMetadata> cloudSongs, int mode) {
		ArrayList<Song> timeline = mSongs;
		synchronized (this) {
			saveActiveSongs();
			
			switch (mode) {
			case MODE_ENQUEUE:
			case MODE_ENQUEUE_POS_FIRST:
			case MODE_ENQUEUE_ID_FIRST:
				break;
			case MODE_PLAY_NEXT:
				timeline.subList(mCurrentPos + 1, timeline.size()).clear();
				break;
			case MODE_PLAY:
			case MODE_PLAY_POS_FIRST:
			case MODE_PLAY_ID_FIRST:
				timeline.clear();
				mCurrentPos = 0;
				break;
			default:
				throw new IllegalArgumentException("Invalid mode: " + mode);
			}

			/* int start = timeline.size();

			Song jumpSong = null; */
			for (CloudSongMetadata cloudSong : cloudSongs) {
                Song song = new Song(true, cloudSong.path, cloudSong.title, cloudSong.album,
                        cloudSong.artist, 1);
                song.dropboxLinkCreated = new Date(); //it was created only seconds ago when the
                                                      // user picked the songs in the library
                if(cloudSong.rgTrack != 0f) song.rgTrack = cloudSong.rgTrack;
				if(cloudSong.rgAlbum != 0f) song.rgAlbum = cloudSong.rgAlbum;
				song.dbPath = cloudSong.dbPath;
				timeline.add(song);
			}

			/* if (mShuffleMode != SHUFFLE_NONE)
				MediaUtils.shuffle(timeline.subList(start, timeline.size()), mShuffleMode == SHUFFLE_ALBUMS); */

			broadcastChangedSongs();
		}

		changed();

		return cloudSongs.size();
	}

	/**
	 * Removes any songs greater than 10 songs before the current song when in
	 * random mode.
	 */
	public void purge()
	{
		/* synchronized (this) {
			if (mFinishAction == FINISH_RANDOM) {
				while (mCurrentPos > 10) {
					mSongs.remove(0);
					--mCurrentPos;
				}
			}
		} */
	}

	/**
	 * Clear the song queue.
	 */
	public void clearQueue()
	{
		synchronized (this) {
			if (mCurrentPos + 1 < mSongs.size())
				mSongs.subList(mCurrentPos + 1, mSongs.size()).clear();
		}

		if (mCallback != null) {
			mCallback.activeSongReplaced(+1, getSong(+1));
			mCallback.positionInfoChanged();
		}

		changed();
	}

	//TODO: reduce redundancy - make one clearQueue method call the other
	/**
	 * Clear the song queue.
	 */
	public void clearQueue(Callback callback)
	{
		synchronized (this) {
			if (mCurrentPos + 1 < mSongs.size())
				mSongs.subList(mCurrentPos + 1, mSongs.size()).clear();
			mSongs.subList(0, mCurrentPos).clear();
			mCurrentPos = 0;
		}

		if (mCallback != null) {
			mCallback.activeSongReplaced(+1, getSong(+1));
			mCallback.positionInfoChanged();
		}

		callback.timelineChanged();
	}
	
	/**
	 * Randomly reorder the songs in the queue. The current song will always be the first.
	 */
	public void randomlyReorderQueue(Callback callback)
	{
		synchronized (this) { 
			ArrayList<Song> randomizeThis = new ArrayList<Song>();
			
			if (mCurrentPos + 1 < mSongs.size()) {
				randomizeThis.addAll(mSongs.subList(mCurrentPos + 1, mSongs.size()));
				mSongs.subList(mCurrentPos + 1, mSongs.size()).clear();
			}
			randomizeThis.addAll(mSongs.subList(0, mCurrentPos));
			mSongs.subList(0, mCurrentPos).clear();
			
			Collections.shuffle(randomizeThis);
			
			mSongs.addAll(randomizeThis);
			
			mCurrentPos = 0;
		}

		if (mCallback != null) {
			mCallback.activeSongReplaced(+1, getSong(+1));
			mCallback.positionInfoChanged();
		}

		((FullPlaybackActivity) callback).timelineShuffled();
	}

	/**
	 * Save the active songs for use with broadcastChangedSongs().
	 *
	 * @see SongTimeline#broadcastChangedSongs()
	 */
	private void saveActiveSongs()
	{
		mSavedPrevious = getSong(-1);
		mSavedCurrent = getSong(0);
		mSavedNext = getSong(+1);
		mSavedPos = mCurrentPos;
		mSavedSize = mSongs.size();
	}

	/**
	 * Broadcast the active songs that have changed since the last call to
	 * saveActiveSongs()
	 *
	 * @see SongTimeline#saveActiveSongs()
	 */
	private void broadcastChangedSongs()
	{
		if (mCallback == null) return;

		Song previous = getSong(-1);
		Song current = getSong(0);
		Song next = getSong(+1);

		if (Song.getDropboxPath(mSavedPrevious) != Song.getDropboxPath(previous) || Song.getId(mSavedPrevious) != Song.getId(previous))
			mCallback.activeSongReplaced(-1, previous);
		if (Song.getDropboxPath(mSavedNext) != Song.getDropboxPath(next) || Song.getId(mSavedNext) != Song.getId(next))
			mCallback.activeSongReplaced(1, next);
		if (Song.getDropboxPath(mSavedCurrent) != Song.getDropboxPath(current) || Song.getId(mSavedCurrent) != Song.getId(current))
			mCallback.activeSongReplaced(0, current);

		if (mCurrentPos != mSavedPos || mSongs.size() != mSavedSize)
			mCallback.positionInfoChanged();
	}

	/**
	 * Remove the song with the given id from the timeline.
	 *
	 * @param id The MediaStore id of the song to remove.
	 */
	public void removeSong(long id)
	{
		synchronized (this) {
			saveActiveSongs();

			ArrayList<Song> songs = mSongs;
			ListIterator<Song> it = songs.listIterator();
			while (it.hasNext()) {
				int i = it.nextIndex();
				if (Song.getId(it.next()) == id) {
					if (i < mCurrentPos)
						--mCurrentPos;
					it.remove();
				}
			}

			broadcastChangedSongs();
		}

		changed();
	}

	/**
	 * Broadcasts that the timeline state has changed.
	 */
	private void changed()
	{
		if (mCallback != null)
			mCallback.timelineChanged();
	}

	/**
	 * Return true if the finish action is to stop at the end of the queue and
	 * the current song is the last in the queue.
	 */
	public boolean isEndOfQueue()
	{
		synchronized (this) {
			return mFinishAction == FINISH_STOP && mCurrentPos == mSongs.size() - 1;
		}
	}

	/**
	 * Returns the position of the current song in the timeline.
	 */
	public int getPosition()
	{
		return mCurrentPos;
	}

	/**
	 * Returns the current number of songs in the timeline.
	 */
	public int getLength()
	{
		return mSongs.size();
	}
}
