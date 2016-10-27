package mp.teardrop;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A list adapter that provides a view of the filesystem. The active directory
 * is set through a {@link Limiter} and rows are displayed using MediaViews.
 */
public class UnifiedAdapter
	extends BaseAdapter
	implements LibraryAdapter
	         //, View.OnClickListener
{
	static final int ID_LINK_TO_PARENT_DIR = -10;

    private final LayoutInflater mInflater;
	
	/**
	 * The owner LibraryActivity.
	 */
	final LibraryActivity mActivity;
    /**
	 * The currently active limiter, set by a row expander being clicked.
	 */
	private Limiter mLimiter;
	/**
	 * The currently displayed artists, albums, songs, etc.
	 */
	private MediaInfoHolder mMih;
	
	/** 
	 * Variables to indicate which section (artists, albums etc.) starts and ends where.
	 * A value of -1 means the section is absent.
	 */
	private int mPosArtistHeading = -1, mPosArtistFirst = -1, mPosArtistLast = -1,
		mPosAlbumHeading = -1, mPosAlbumFirst = -1, mPosAlbumLast = -1,
		mPosSongHeading = -1, mPosSongFirst = -1, mPosSongLast = -1,
		mPosPlaylistHeading = -1, mPosPlaylistFirst = -1, mPosPlaylistLast = -1;

	static final int ITEM_TYPE_ARTIST = 11;
	static final int ITEM_TYPE_ALBUM = 12;
	static final int ITEM_TYPE_SONG = 13;
	static final int ITEM_TYPE_PLAYLIST = 14;
	static final int ITEM_TYPE_MORE_ARTISTS = 21;
	static final int ITEM_TYPE_MORE_ALBUMS = 22;
	static final int ITEM_TYPE_MORE_SONGS = 23;
	static final int ITEM_TYPE_MORE_PLAYLISTS = 24;
	static final int ITEM_TYPE_MORE_GENRES = 25;
	static final int ITEM_TYPE_HEADING = 31;

	/**
	 * Create a UnifiedAdapter.
	 *
	 * @param activity The LibraryActivity that will contain this adapter.
	 * Called on to requery this adapter when the contents of the directory
	 * change.
     * @param limiter An initial limiter to set. If none is given,
     * a few items of each type will be displayed, with links to more.
     *
     */
    public UnifiedAdapter(LibraryActivity activity, Limiter limiter) {
        mActivity = activity;
        mLimiter = limiter;
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        setLimiter(limiter);
    }

	@Override
	public Object query()
	{
		MediaInfoHolder mih = new MediaInfoHolder();


        if(!MediaUtils.hasPermissionToReadStorage(mActivity)) {
            mih.permissionToReadStorageWasDenied = true;
			return mih;
        }

		
		if(mLimiter == null) {
			/* get top artists */
			PlayCountsHelper pch = new PlayCountsHelper(mActivity);
			ArrayList<Long> ids = pch.getTopMedia(ITEM_TYPE_ARTIST, 3);
			String idsString = ids.toString();
			idsString = '(' + idsString.substring(1, idsString.length()-1) + ')';
			if(idsString.length() > 2) {
				Uri uri = android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
				Cursor cursor = mActivity.getContentResolver().query(uri, null, MediaStore.Audio.Artists._ID + " IN " + idsString, null, null);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Artists._ID);
					do {
						mih.artists.add(mih.new ArtistInfo(cursor.getString(nameColumn), cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
			}
			//check if we got all artists: if so, don't include the more link
			Uri uri1 = android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
			String[] projection1 = { MediaStore.Audio.Artists._ID };
			Cursor cursor1 = mActivity.getContentResolver().query(uri1, projection1, null, null, null);
			if(cursor1.getCount() > mih.artists.size()) mih.artists.add(mih.new ArtistInfo(null, 0)); //null name = more link
			
			/* get top albums */
			ids = pch.getTopMedia(ITEM_TYPE_ALBUM, 3); //TODO: only seems to return up to 2, fix this (after fixing the hacky try..catch in pch)
			idsString = ids.toString();
			idsString = '(' + idsString.substring(1, idsString.length()-1) + ')';
			if(idsString.length() > 2) {
				Uri uri = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
				Cursor cursor = mActivity.getContentResolver().query(uri, null, MediaStore.Audio.Albums._ID + " IN " + idsString, null, null);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Albums._ID);
					do {
						long albumId = cursor.getLong(idColumn);
						mih.albums.add(mih.new AlbumInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),albumId,getAlbumCover(mActivity, albumId)));
					} while (cursor.moveToNext());
				}
			}
			uri1 = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
			String[] projection2 = { MediaStore.Audio.Albums._ID };
			cursor1 = mActivity.getContentResolver().query(uri1, projection2, null, null, null);
			if(cursor1.getCount() > mih.albums.size()) mih.albums.add(mih.new AlbumInfo(null, null, 0, null));
			
			ids = pch.getTopMedia(ITEM_TYPE_SONG, 5);
			idsString = ids.toString();
			idsString = '(' + idsString.substring(1, idsString.length()-1) + ')';
			if(idsString.length() > 2) {
				Uri uri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				Cursor cursor = mActivity.getContentResolver().query(uri, null,
					MediaStore.Audio.Media._ID + " IN " + idsString + " AND " + MediaStore.Audio.Media.IS_MUSIC + " != 0"
					, null, null);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
					do {
						mih.songs.add(mih.new SongInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
			}
			uri1 = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
			String[] projection3 = { MediaStore.Audio.Media._ID };
			cursor1 = mActivity.getContentResolver().query(uri1, projection3, null, null, null);
			if(cursor1.getCount() > mih.albums.size()) mih.songs.add(mih.new SongInfo(null, null, 0));
			
			/* add a link to all playlists */
			mih.playlists.add(mih.new PlaylistInfo(null, 0));
		
		} else if(mLimiter.type > 20) { //show one of: all artists, all albums, all songs, all playlists or all genres
			
			Uri uri;
			Cursor cursor;
			switch(mLimiter.type) {
			case ITEM_TYPE_MORE_ARTISTS:
				/* get all artists */
				uri = android.provider.MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
				cursor = mActivity.getContentResolver().query(uri, null, null, null, null);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Artists._ID);
					do {
						mih.artists.add(mih.new ArtistInfo(cursor.getString(nameColumn), cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
				break;
			case ITEM_TYPE_MORE_ALBUMS:
				/* get all albums */
				uri = android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
				cursor = mActivity.getContentResolver().query(uri, null, null, null, null);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Albums._ID);
					do {
						long albumId = cursor.getLong(idColumn);
						mih.albums.add(mih.new AlbumInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),albumId,getAlbumCover(mActivity, albumId)));
					} while (cursor.moveToNext());
				}
				break;
			case ITEM_TYPE_MORE_SONGS:
				/* get all songs */
				uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				cursor = mActivity.getContentResolver().query(
					uri, null, MediaStore.Audio.Media.IS_MUSIC + " != 0", null, MediaStore.Audio.Media.TITLE);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
					do {
						mih.songs.add(mih.new SongInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
				break;
			case ITEM_TYPE_MORE_PLAYLISTS:
				/* get all playlists */
				uri = MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI;
				cursor = mActivity.getContentResolver().query(uri, null, null, null, MediaStore.Audio.Playlists.NAME);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.NAME);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists._ID);
					do {
						mih.playlists.add(mih.new PlaylistInfo(cursor.getString(nameColumn),cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
				break;
			}
			
		} else {
			
			Uri uri;
			Cursor cursor;
			switch(mLimiter.type) {
			case ITEM_TYPE_ALBUM:	
				/* get all songs in the album */
				uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
				cursor = mActivity.getContentResolver().query(
					uri, null, MediaStore.Audio.Media.ALBUM_ID + " = " + mLimiter.data.toString(), null, MediaStore.Audio.Media.TRACK);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
					do {
						mih.songs.add(mih.new SongInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
				break;
			case ITEM_TYPE_ARTIST:
				/* get all albums by artist */
				/* note: there ain't no such thing as a song without an album - 
				 * if a song file has no tags, Android will use the name of
				 * the containing folder as the album name. As such, getting
				 * all of an artist's albums will always cover all their songs. */
				uri = MediaStore.Audio.Artists.Albums.getContentUri("external", (Long) mLimiter.data);
				cursor = mActivity.getContentResolver().query(uri, null, null, null, null);
				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ALBUM);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Albums.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Albums._ID);
					do {
						long albumId = cursor.getLong(idColumn);
						mih.albums.add(mih.new AlbumInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),albumId,getAlbumCover(mActivity, albumId)));
					} while (cursor.moveToNext());
				}
				break;
			case ITEM_TYPE_PLAYLIST:	
				/* get all songs in the playlist */
				String[] projection1 = {
					MediaStore.Audio.Playlists.Members.AUDIO_ID,
					MediaStore.Audio.Playlists.Members.ARTIST,
					MediaStore.Audio.Playlists.Members.TITLE,
					MediaStore.Audio.Playlists.Members._ID };
				uri = MediaStore.Audio.Playlists.Members.getContentUri("external", (Long) mLimiter.data);
				cursor = mActivity.getContentResolver().query(uri, projection1, null, null, MediaStore.Audio.Playlists.Members.PLAY_ORDER);

				if(cursor != null && cursor.moveToFirst()) {
					int nameColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.TITLE);
					int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.ARTIST);
					int idColumn = cursor.getColumnIndex(MediaStore.Audio.Playlists.Members.AUDIO_ID);
					do {
						mih.songs.add(mih.new SongInfo(cursor.getString(nameColumn),cursor.getString(artistColumn),cursor.getLong(idColumn)));
					} while (cursor.moveToNext());
				}
				break;
			}
			
		}
		
		return mih;
	}

	@Override
	public void commitQuery(Object data)
	{
        if(data == null) {
            return;
        }

		mMih = (MediaInfoHolder) data;
		
		mPosArtistHeading = mMih.artists.isEmpty() ? -1 : 0;
		mPosArtistFirst = mMih.artists.isEmpty() ? -1 : 1;
		mPosArtistLast = mMih.artists.isEmpty() ? -1 : mMih.artists.size();
		
		mPosAlbumHeading = mMih.albums.isEmpty() ? -1 : mPosArtistLast + 1;
		mPosAlbumFirst = mMih.albums.isEmpty() ? -1 : mPosAlbumHeading + 1;
		mPosAlbumLast = mMih.albums.isEmpty() ? -1 : mPosAlbumHeading + mMih.albums.size();
		
		mPosSongHeading = mMih.songs.isEmpty() ? -1 : Math.max(mPosArtistLast, mPosAlbumLast) + 1;
		mPosSongFirst = mMih.songs.isEmpty() ? -1 : mPosSongHeading + 1;
		mPosSongLast = mMih.songs.isEmpty() ? -1 : mPosSongHeading + mMih.songs.size();
		
		mPosPlaylistHeading = mMih.playlists.isEmpty() ? -1 : Math.max(Math.max(mPosArtistLast, mPosAlbumLast), mPosSongLast) + 1;
		if(mMih.playlists.size() == 1 && mMih.playlists.get(0).name == null) { //show no playlists, just a link to all playlists
			mPosPlaylistFirst = -1;
			mPosPlaylistLast = -1;
		} else {
			mPosPlaylistFirst= mMih.playlists.isEmpty() ? -1 : mPosPlaylistHeading + 1;
			mPosPlaylistLast = mMih.playlists.isEmpty() ? -1 : mPosPlaylistHeading + mMih.playlists.size();
		}
		
		notifyDataSetInvalidated();
	}

	@Override
	public void clear()
	{
		mMih = null;
		notifyDataSetInvalidated();
	}

	@Override
	public int getCount()
	{
		if(mMih == null) {
            return 0;
        }

        if(mMih.permissionToReadStorageWasDenied) {
            return 1;
        }
		
		int count = 0;
		
		if(mPosArtistLast > count) count = mPosArtistLast;
		if(mPosAlbumLast > count) count = mPosAlbumLast;
		if(mPosSongLast > count) count = mPosSongLast;
		
		if(mPosPlaylistHeading > count) count = mPosPlaylistHeading;
		if(mPosPlaylistLast > count) count = mPosPlaylistLast;
		
		return count + 1;
	}

	@Override
	public Object getItem(int pos)
	{
		return null;
	}

	@Override
	public long getItemId(int pos)
	{
		return pos;
	}

	private static class ViewHolder {
		public long id;
		public String title;
		public int type;
		public TextView text;
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent) //TODO: optimize this a little
	{
        if(mMih.permissionToReadStorageWasDenied) {
            return getNoPermissionView();
        }

		// always create a new view to get rid of old touch feedback
		View view = null;
		ViewHolder holder = new ViewHolder();
		
		if(pos == mPosArtistHeading) {
			
			if(mLimiter != null) {
				view = new View(mActivity);
				view.setVisibility(View.GONE);
			} else {
				
				if(mLimiter == null && mPosArtistFirst == mPosArtistLast) { //there are no artists, just the more link
					view = mActivity.getLayoutInflater().inflate(R.layout.local_library_empty_section_first, null);
					TextView textView = (TextView) (view.findViewById(R.id.empty_section_heading));
					textView.setText(R.string.artists);
					textView = (TextView) (view.findViewById(R.id.empty_section_content));
					textView.setText(R.string.empty_artists_section);
					textView.setCompoundDrawablesWithIntrinsicBounds(mActivity.getResources().getDrawable(R.drawable.artist_24), null, null, null);
				} else {
					view = mActivity.getLayoutInflater().inflate(R.layout.local_library_heading_first, null);
					((TextView) view).setText(R.string.artists);
				}
				holder.type = ITEM_TYPE_HEADING;
			
			}
			
		} else if(pos >= mPosArtistFirst && pos <= mPosArtistLast) {
			
			view = mActivity.getLayoutInflater().inflate(
				(mMih.artists.get(pos - mPosArtistFirst).name == null) ? R.layout.local_library_more_link : R.layout.local_library_artist,
				null);
			TextView textView = (TextView) view.findViewById(R.id.text);
			if(mMih.artists.get(pos - mPosArtistFirst).name == null) {
				textView.setText((mPosArtistFirst == mPosArtistLast) ? R.string.all_artists_ellipsis : R.string.more_link_ellipsis);
				holder.type = ITEM_TYPE_MORE_ARTISTS;
			} else {
				textView.setText(mMih.artists.get(pos - mPosArtistFirst).name);
				holder.title = mMih.artists.get(pos - mPosArtistFirst).name;
				holder.id = mMih.artists.get(pos - mPosArtistFirst).databaseId;
				holder.type = ITEM_TYPE_ARTIST;
			}
			
		} else if (pos == mPosAlbumHeading) {

			if(mLimiter != null) {
				view = new View(mActivity);
				view.setVisibility(View.GONE);
			} else {
				if(mLimiter == null && mPosAlbumFirst == mPosAlbumLast) {
					view = mActivity.getLayoutInflater().inflate(R.layout.local_library_empty_section, null);
					TextView textView = (TextView) (((LinearLayout) view).findViewById(R.id.empty_section_heading));
					textView.setText(R.string.albums);
					textView = (TextView) (((LinearLayout) view).findViewById(R.id.empty_section_content));
					textView.setText(R.string.empty_albums_section);
					textView.setCompoundDrawablesWithIntrinsicBounds(mActivity.getResources().getDrawable(R.drawable.album_24), null, null, null);
				} else {
					view = mActivity.getLayoutInflater().inflate(R.layout.local_library_heading, null);
					((TextView) view).setText(R.string.albums);
				}
				holder.type = ITEM_TYPE_HEADING;
			}
			
		} else if (pos >= mPosAlbumFirst && pos <= mPosAlbumLast) {
			
			view = mActivity.getLayoutInflater().inflate(
				(mMih.albums.get(pos - mPosAlbumFirst).name == null) ? R.layout.local_library_more_link : R.layout.local_library_album,
				null);
			TextView textView = (TextView) view.findViewById(R.id.text);
			if(mMih.albums.get(pos - mPosAlbumFirst).name == null) {
				textView.setText((mPosAlbumFirst == mPosAlbumLast) ? R.string.all_albums_ellipsis : R.string.more_link_ellipsis);
				holder.type = ITEM_TYPE_MORE_ALBUMS;
			} else {
				
				Spannable s = new SpannableString(
						mMih.albums.get(pos - mPosAlbumFirst).name + " \u2013 " + mMih.albums.get(pos - mPosAlbumFirst).artistName);
				s.setSpan(new ForegroundColorSpan(0x8affffff),
					mMih.albums.get(pos - mPosAlbumFirst).name.length(),
					s.length(),
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				textView.setText(s);
				
				if(mMih.albums.get(pos - mPosAlbumFirst).cover != null) {
					BitmapDrawable cover = new BitmapDrawable(mActivity.getResources(), mMih.albums.get(pos - mPosAlbumFirst).cover);
					DisplayMetrics metrics = new DisplayMetrics();
					mActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
					float logicalDensity = metrics.density;
					int desiredSizeRawPixels = (int) Math.floor(24 * logicalDensity);
					cover.setBounds(0, 0, desiredSizeRawPixels, desiredSizeRawPixels);
					textView.setCompoundDrawables(cover, null, null, null);
				}
				
				holder.title = mMih.albums.get(pos - mPosAlbumFirst).name;
				holder.id = mMih.albums.get(pos - mPosAlbumFirst).databaseId;
				holder.type = ITEM_TYPE_ALBUM;
			}
			
		} else if (pos == mPosSongHeading) {
			
			if(mLimiter != null) {
				view = new View(mActivity);
				view.setVisibility(View.GONE);
			} else {
			
				if(mLimiter == null && mPosSongFirst == mPosSongLast) {
					view = mActivity.getLayoutInflater().inflate(R.layout.local_library_empty_section, null);
					TextView textView = (TextView) (((LinearLayout) view).findViewById(R.id.empty_section_heading));
					textView.setText(R.string.songs);
					textView = (TextView) (((LinearLayout) view).findViewById(R.id.empty_section_content));
					textView.setText(R.string.empty_songs_section);
					textView.setCompoundDrawablesWithIntrinsicBounds(mActivity.getResources().getDrawable(R.drawable.eighth_note), null, null, null);
				} else {
					view = mActivity.getLayoutInflater().inflate(R.layout.local_library_heading, null);
					((TextView) view).setText(R.string.songs);
				}
			
			}
			
			holder.type = ITEM_TYPE_HEADING;
			
		} else if (pos >= mPosSongFirst && pos <= mPosSongLast) {
			
			view = mActivity.getLayoutInflater().inflate(
				(mMih.songs.get(pos - mPosSongFirst).name == null) ? R.layout.local_library_more_link : R.layout.local_library_song,
				null);
			TextView textView = (TextView) view.findViewById(R.id.text);
			if(mMih.songs.get(pos - mPosSongFirst).name == null) {
				textView.setText((mPosSongFirst == mPosSongLast) ? R.string.all_songs_ellipsis : R.string.more_link_ellipsis);
				holder.type = ITEM_TYPE_MORE_SONGS;
			} else {
				
				Spannable s = new SpannableString(
					mMih.songs.get(pos - mPosSongFirst).name + " \u2013 " + mMih.songs.get(pos - mPosSongFirst).artistName);
				s.setSpan(new ForegroundColorSpan(0x8affffff),
					mMih.songs.get(pos - mPosSongFirst).name.length(),
					s.length(),
					Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
				textView.setText(s);
	
				holder.id = mMih.songs.get(pos - mPosSongFirst).databaseId;
				holder.type = ITEM_TYPE_SONG;
				
			}
		} else if (pos == mPosPlaylistHeading) {
			
			if(mLimiter != null) {
				view = new View(mActivity);
				view.setVisibility(View.GONE);
				holder.type = ITEM_TYPE_HEADING;
			} else {
				view = mActivity.getLayoutInflater().inflate(R.layout.local_library_heading_link, null);
				((TextView) view).setText(R.string.all_playlists_ellipsis);
				holder.type = ITEM_TYPE_MORE_PLAYLISTS;
			}
			
		} else if (pos >= mPosPlaylistFirst && pos <= mPosPlaylistLast) {
			
			view = mActivity.getLayoutInflater().inflate(R.layout.local_library_song, null);
			TextView textView = (TextView) view.findViewById(R.id.text);

			textView.setText(mMih.playlists.get(pos - mPosPlaylistFirst).name);

			holder.title = mMih.playlists.get(pos - mPosPlaylistFirst).name;
			holder.id = mMih.playlists.get(pos - mPosPlaylistFirst).databaseId;
			holder.type = ITEM_TYPE_PLAYLIST;
			
		}
		
		holder.text = (TextView)view.findViewById(R.id.text);
		view.setTag(holder);
		
		return view;

	}

	private View getNoPermissionView() {
        View view = mInflater.inflate(R.layout.library_row_storage_permission, null);
        TextView textView = (TextView) view.findViewById(R.id.text);
        textView.setText(R.string.grant_storage_access);
        return view;
    }
	
	@Override
	public void setLimiter(Limiter limiter)
	{
		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	@Override
	public Limiter buildLimiter(int type, long id, String preGeneratedName)
	{
		String[] names = { preGeneratedName };
		return new Limiter(type, names, id);
	}

	static Limiter buildLimiterForMoreLink(int type, String limiterTitle)
	{
		String[] names = { limiterTitle };
		return new Limiter(type, names, 0);
	}

	@Override
	public int getMediaType()
	{
		return MediaUtils.TYPE_UNIFIED;
	}

	@Override
	public Intent createData(View view)
	{

		Intent intent = new Intent();

        if(mMih.permissionToReadStorageWasDenied) {
            intent.putExtra(LibraryAdapter.DATA_LINK_WITH_DROPBOX, true);
            return intent;
        }
		
		ViewHolder holder = (ViewHolder)view.getTag();
		
		if(holder.type == ITEM_TYPE_HEADING) return null;
		
		if(holder.id == ID_LINK_TO_PARENT_DIR) { //TODO: rework this to use type and not id!
			intent.putExtra(LibraryAdapter.DATA_GO_UP, true);
		} else if(holder.type == ITEM_TYPE_MORE_SONGS) {
			intent.putExtra(LibraryAdapter.DATA_GO_TO_ALL_SONGS, true);
		} else if(holder.type == ITEM_TYPE_MORE_ARTISTS) {
			intent.putExtra(LibraryAdapter.DATA_GO_TO_ALL_ARTISTS, true);
		} else if(holder.type == ITEM_TYPE_MORE_ALBUMS) {
			intent.putExtra(LibraryAdapter.DATA_GO_TO_ALL_ALBUMS, true);
		} else if(holder.type == ITEM_TYPE_MORE_PLAYLISTS) {
			intent.putExtra(LibraryAdapter.DATA_GO_TO_ALL_PLAYLISTS, true);
		} else {
			intent.putExtra(LibraryAdapter.DATA_TYPE, holder.type);
			intent.putExtra(LibraryAdapter.DATA_ID, holder.id);
			intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, holder.type != ITEM_TYPE_SONG);
			if(holder.type != ITEM_TYPE_SONG) {
				intent.putExtra(LibraryAdapter.DATA_PRE_GENERATED_NAME, holder.title);
			}
		}
		return intent;
	}
	
	/**
	 * Queries the MediaStore for the cover art
	 * of an album. Should be run on a worker thread.
	 */
    static Bitmap getAlbumCover(Context context, long albumId) {

    	Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
    	BitmapFactory.Options sBitmapOptions = new BitmapFactory.Options();
    	
        ContentResolver res = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(sArtworkUri, albumId);
        
        if (uri != null) {
            InputStream in = null;
            try {
                in = res.openInputStream(uri);
                return BitmapFactory.decodeStream(in, null, sBitmapOptions);
            } catch (IOException e) { }
        }
        
        return null;
    }
    
	@Override
	public void setFilter(String filter) {
		//not implemented, TODO: remove from superclass
	}

}
