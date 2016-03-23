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

import java.util.ArrayList;
import java.util.Arrays;

import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * PagerAdapter that manages the library media ListViews.
 */
public class LibraryPagerAdapter
	extends PagerAdapter
	implements Handler.Callback
	         , ViewPager.OnPageChangeListener
	         , View.OnCreateContextMenuListener
	         , AdapterView.OnItemClickListener
{
	/**
	 * The number of unique list types. The number of visible lists may be
	 * smaller.
	 */
	public static final int MAX_ADAPTER_COUNT = 3;
	/**
	 * The human-readable title for each list. The positions correspond to the
	 * MediaUtils ids, so e.g. TITLES[MediaUtils.TYPE_SONG] = R.string.songs
	 */
	public static final int[] TITLES = { R.string.files, R.string.local_library, R.string.dropbox };
	/**
	 * Default tab order.
	 */
	public static final int[] DEFAULT_ORDER = { MediaUtils.TYPE_DROPBOX, MediaUtils.TYPE_FILE, MediaUtils.TYPE_UNIFIED };
	/**
	 * The user-chosen tab order.
	 */
	int[] mTabOrder;
	/**
	 * The number of visible tabs.
	 */
	private int mTabCount;
	/**
	 * The LinearLayout containing the ListView for each adapter. Each index corresponds to that list's
	 * MediaUtils id.
	 */
	final ViewGroup[] mContainingLayouts = new ViewGroup[MAX_ADAPTER_COUNT];
	/**
	 * The adapters. Each index corresponds to that adapter's MediaUtils id.
	 */
	public LibraryAdapter[] mAdapters = new LibraryAdapter[MAX_ADAPTER_COUNT];
	/**
	 * Whether the adapter corresponding to each index has stale data.
	 */
	private final boolean[] mRequeryNeeded = new boolean[MAX_ADAPTER_COUNT];
	/**
	 * The file adapter instance, also stored at mAdapters[MediaUtils.TYPE_FILE].
	 */
	FileSystemAdapter mFilesAdapter;
	/**
	 * A back stack of limiters for the file adapter, used to handle back key presses.
	 */
	private ArrayList<Limiter> mFilesAdapterBackStack = new ArrayList<Limiter>();
	/**
	 * A back stack of limiters for the Dropbox adapter, used to handle back key presses.
	 */
	private ArrayList<Limiter> mDropboxAdapterBackStack = new ArrayList<Limiter>();
	/**
	 * The local library adapter instance, also stored at mAdapters[MediaUtils.TYPE_UNIFIED].
	 */
	UnifiedAdapter mUnifiedAdapter;
	/**
	 * The Dropbox adapter instance, also stored at mAdapters[MediaUtils.TYPE_UNIFIED].
	 */
	DropboxAdapter mDropboxAdapter;
	/**
	 * A back stack of limiters for the local library adapter, used to handle back key presses.
	 */
	private ArrayList<Limiter> mUnifiedAdapterBackStack = new ArrayList<Limiter>();
	/**
	 * The adapter of the currently visible list.
	 */
	private LibraryAdapter mCurrentAdapter;
	/**
	 * The index of the current page.
	 */
	int mCurrentPage;
	/**
	 * List positions stored in the saved state, or null if none were stored.
	 */
	private int[] mSavedPositions;
	/**
	 * The LibraryActivity that owns this adapter. The adapter will be notified
	 * of changes in the current page.
	 */
	private final LibraryActivity mActivity;
	/**
	 * A Handler running on the UI thread.
	 */
	private final Handler mUiHandler;
	/**
	 * A Handler running on a worker thread.
	 */
	private final Handler mWorkerHandler;
	/**
	 * The text to be displayed in the first row of the artist, album, and
	 * song limiters.
	 */
	private String mHeaderText;
	private TextView mArtistHeader;
	private TextView mAlbumHeader;
	private TextView mSongHeader;
	/**
	 * The current filter text, or null if none.
	 */
	private String mFilter;
	/**
	 * The position of the songs page, or -1 if it is hidden.
	 */
	public int mSongsPosition = -1;
	/**
	 * The position of the albums page, or -1 if it is hidden.
	 */
	public int mAlbumsPosition = -1;
	/**
	 * The position of the artists page, or -1 if it is hidden.
	 */
	public int mArtistsPosition = -1;
	/**
	 * The position of the genres page, or -1 if it is hidden.
	 */
	public int mGenresPosition = -1;

	private HorizontalScrollView mLimiterScroller;

	private final ContentObserver mPlaylistObserver = new ContentObserver(null) {
		@Override
		public void onChange(boolean selfChange)
		{
			/* if (mPlaylistAdapter != null) {
				postRequestRequery(mPlaylistAdapter);
			} */
		}
	};

	/**
	 * Create the LibraryPager.
	 *
	 * @param activity The LibraryActivity that will own this adapter. The activity
	 * will receive callbacks from the ListViews.
	 * @param workerLooper A Looper running on a worker thread.
	 */
	public LibraryPagerAdapter(LibraryActivity activity, Looper workerLooper)
	{
		mActivity = activity;
		mUiHandler = new Handler(this);
		mWorkerHandler = new Handler(workerLooper, this);
		mCurrentPage = -1;
		//activity.getContentResolver().registerContentObserver(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI, true, mPlaylistObserver);
	}

	/**
	 * Load the tab order from SharedPreferences.
	 *
	 * @return True if order has changed.
	 */
	public boolean loadTabOrder()
	{
		String in = PlaybackService.getSettings(mActivity).getString(PrefKeys.TAB_ORDER, null);
		int[] order;
		int count;
		if (in == null || in.length() != MAX_ADAPTER_COUNT) {
			order = DEFAULT_ORDER;
			count = MAX_ADAPTER_COUNT;
		} else {
			char[] chars = in.toCharArray();
			order = new int[MAX_ADAPTER_COUNT];
			count = 0;
			for (int i = 0; i != MAX_ADAPTER_COUNT; ++i) {
				char v = chars[i];
				if (v >= 128) {
					v -= 128;
					if (v >= MediaUtils.TYPE_COUNT) {
						// invalid media type; use default order
						order = DEFAULT_ORDER;
						count = MAX_ADAPTER_COUNT;
						break;
					}
					order[count++] = v;
				}
			}
		}

		if (count != mTabCount || !Arrays.equals(order, mTabOrder)) {
			mTabOrder = order;
			mTabCount = count;
			notifyDataSetChanged();
			computeExpansions();
			return true;
		}

		return false;
	}

	/**
	 * Determines whether adapters should be expandable from the visibility of
	 * the adapters each expands to. Also updates mSongsPosition/mAlbumsPositions.
	 */
	public void computeExpansions()
	{
		int[] order = mTabOrder;
		int songsPosition = -1;
		int albumsPosition = -1;
		int artistsPosition = -1;
		int genresPosition = -1;
		for (int i = mTabCount; --i != -1; ) {
			/* switch (order[i]) {
			case MediaUtils.TYPE_ALBUM:
				albumsPosition = i;
				break;
			case MediaUtils.TYPE_SONG:
				songsPosition = i;
				break;
			case MediaUtils.TYPE_ARTIST:
				artistsPosition = i;
				break;
			case MediaUtils.TYPE_GENRE:
				genresPosition = i;
				break;
			} */
		}

		/* if (mArtistAdapter != null)
			mArtistAdapter.setExpandable(songsPosition != -1 || albumsPosition != -1);
		if (mAlbumAdapter != null)
			mAlbumAdapter.setExpandable(songsPosition != -1);
		if (mGenreAdapter != null)
			mGenreAdapter.setExpandable(songsPosition != -1); */

		mSongsPosition = songsPosition;
		mAlbumsPosition = albumsPosition;
		mArtistsPosition = artistsPosition;
		mGenresPosition = genresPosition;
	}

	@Override
	public Object instantiateItem(ViewGroup container, int position)
	{
		int type = mTabOrder[position];
		ViewGroup containingLayout = mContainingLayouts[type];

		if (containingLayout == null) {
			LibraryActivity activity = mActivity;
			LayoutInflater inflater = activity.getLayoutInflater();
			LibraryAdapter adapter;
			
			containingLayout = (RelativeLayout) inflater.inflate(R.layout.listview, null);

			switch (type) {
			case MediaUtils.TYPE_FILE:
				adapter = mFilesAdapter = new FileSystemAdapter(activity, null);
				break;
			case MediaUtils.TYPE_UNIFIED:
				adapter = mUnifiedAdapter = new UnifiedAdapter(activity, null);
				break;
			case MediaUtils.TYPE_DROPBOX:				
				adapter = mDropboxAdapter = new DropboxAdapter(activity, null);
				//if linked with Dropbox, query the root
				//TODO: maybe use a field in activity or somewhere to save linked state instead of checking prefs here
		        SharedPreferences prefs = activity.getSharedPreferences(LibraryActivity.ACCOUNT_PREFS_NAME, 0);
		        String key = prefs.getString(LibraryActivity.ACCESS_KEY_NAME, null);
		        String secret = prefs.getString(LibraryActivity.ACCESS_SECRET_NAME, null);
		        
		        /* if we're linked with Dropbox, show the loading message and retrieve the root folder's contents */
		        if (!(key == null || secret == null || key.length() == 0 || secret.length() == 0)) {
		        	
					View screen = containingLayout.findViewById(R.id.list_view_loading_screen);
					screen.setOnClickListener(new View.OnClickListener() { //will stick for the entire session
						@Override
						public void onClick(View v) { }
					});
					screen.setVisibility(View.VISIBLE);
					
					
			    	activity.requeryDropbox(null);
		        }
		        
				break;
			default:
				throw new IllegalArgumentException("Invalid media type: " + type);
			}
			
			mLimiterScroller = (HorizontalScrollView) containingLayout.findViewById(R.id.new_limiter_scroller);
			ListView view = (ListView) containingLayout.findViewById(R.id.actual_list_view);
			view.setOnCreateContextMenuListener(this);
			view.setOnItemClickListener(this);
			view.setTag(type);
			view.setAdapter(adapter);
			adapter.setFilter(mFilter);

			mAdapters[type] = adapter;
			mContainingLayouts[type] = containingLayout;
			mRequeryNeeded[type] = true;

		}

		requeryIfNeeded(type);
		container.addView(containingLayout);
		return containingLayout;
	}

	//TODO: this method turned into a mess, clean it up
	@Override
	public int getItemPosition(Object item)
	{
		if(item != null && ((View)item).getTag() != null) {
			int type = (Integer)((View)item).getTag();
			int[] order = mTabOrder;
			for (int i = mTabCount; --i != -1; ) {
				if (order[i] == type)
					return i;
			}
		}
		return POSITION_NONE;
	}

	@Override
	public void destroyItem(ViewGroup container, int position, Object object)
	{
		container.removeView((View)object);
	}

	@Override
	public CharSequence getPageTitle(int position)
	{
		return mActivity.getResources().getText(TITLES[mTabOrder[position]]);
	}

	@Override
	public int getCount()
	{
		return mTabCount;
	}

	@Override
	public boolean isViewFromObject(View view, Object object)
	{
		return view == object;
	}

	@Override
	public void setPrimaryItem(ViewGroup container, int position, Object object)
	{		
		int type = mTabOrder[position];
		LibraryAdapter adapter = mAdapters[type];
		
		if(adapter == null) return; //TODO: this is a hacky fix for a crash, try to get to the root of the problem
		
		if (position != mCurrentPage || adapter != mCurrentAdapter) {
			requeryIfNeeded(type);
			mCurrentAdapter = adapter;
			mCurrentPage = position;
			mActivity.onPageChanged(position, adapter);
		}
	}
	
	/**
	 * Gets the type of items that are currently being displayed.
	 * 
	 * @return One of the values defined as constants in {@link MediaUtils}.
	 */
	int getCurrentType() {
		return(mTabOrder[mCurrentPage]);
	}

	/**
	 * Sets the text to be displayed in the first row of the artist, album, and
	 * song lists.
	 */
	public void setHeaderText(String text)
	{
		if (mArtistHeader != null)
			mArtistHeader.setText(text);
		if (mAlbumHeader != null)
			mAlbumHeader.setText(text);
		if (mSongHeader != null)
			mSongHeader.setText(text);
		mHeaderText = text;
	}

	/**
	 * Clear a limiter.
	 *
	 * @param type Which type of limiter to clear.
	 */
	public void clearLimiter(int type)
	{
		if (type == MediaUtils.TYPE_FILE) {
			mFilesAdapter.setLimiter(null);
			requestRequery(mFilesAdapter);
		} else if (type == MediaUtils.TYPE_UNIFIED || type > 10) {
			mUnifiedAdapter.setLimiter(null);
			requestRequery(mUnifiedAdapter);
		} else if(type == MediaUtils.TYPE_DROPBOX) {
			mActivity.requeryDropbox(null);
		}
	}

	/**
	 * Update the current adapter with the given limiter.
	 * The current limiter, even if null, is backed up
	 * to mFilesAdapterBackStack or one of its counterparts
	 * before being overwritten.
	 *
	 * @param limiter The limiter to set.
	 */
	public void setLimiter(Limiter limiter)
	{		
		int adapterType = getCurrentType();

		switch (adapterType) {
		case MediaUtils.TYPE_UNIFIED:
			mUnifiedAdapterBackStack.add(mUnifiedAdapter.getLimiter());
			mUnifiedAdapter.setLimiter(limiter);
			requestRequery(mUnifiedAdapter);
			break;
		case MediaUtils.TYPE_FILE:
			mFilesAdapterBackStack.add(mFilesAdapter.getLimiter());
			mFilesAdapter.setLimiter(limiter);
			requestRequery(mFilesAdapter);
			break;
		case MediaUtils.TYPE_DROPBOX:
			mDropboxAdapterBackStack.add(mDropboxAdapter.getLimiter());
			//mDropboxAdapter.setLimiter(limiter);
			mActivity.requeryDropbox(limiter);
		}
	}

	/**
	 * Removes the last used limiter from the "back stack"
	 * and re-applies it to this adapter.
	 *
	 * @return False if the back stack was empty (i.e. there's nothing to go back to), true otherwise.
	 */
	public boolean goBackToPreviousLimiter()
	{
		if(getCurrentLimiter() == null) return false;
		
		int adapterType = getCurrentLimiter().type > 10 ? MediaUtils.TYPE_UNIFIED : getCurrentLimiter().type;
		
		switch (adapterType) {
		case MediaUtils.TYPE_UNIFIED:
			if(mUnifiedAdapterBackStack.isEmpty()) return false;
			mUnifiedAdapter.setLimiter(mUnifiedAdapterBackStack.remove(mUnifiedAdapterBackStack.size()-1));
			requestRequery(mUnifiedAdapter);
			return true;
		case MediaUtils.TYPE_FILE:
			if(mFilesAdapterBackStack.isEmpty()) return false;
			mFilesAdapter.setLimiter(mFilesAdapterBackStack.remove(mFilesAdapterBackStack.size()-1));
			requestRequery(mFilesAdapter);
			return true;
		case MediaUtils.TYPE_DROPBOX:
			if(mDropboxAdapterBackStack.isEmpty()) return false;
			mActivity.requeryDropbox(mDropboxAdapterBackStack.remove(mDropboxAdapterBackStack.size()-1));
			return true;
		default:
			return false;
		}
	}

	/**
	 * Returns the limiter set on the current adapter or null if there is none.
	 */
	public Limiter getCurrentLimiter()
	{
		LibraryAdapter current = mCurrentAdapter;
		if (current == null)
			return null;
		return current.getLimiter();
	}

	/**
	 * Run on query on the adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_RUN_QUERY = 0;
	/**
	 * Save the sort mode for the adapter passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_SAVE_SORT = 1;
	/**
	 * Call {@link LibraryPagerAdapter#requestRequery(LibraryAdapter)} on the adapter
	 * passed in obj.
	 *
	 * Runs on worker thread.
	 */
	private static final int MSG_REQUEST_REQUERY = 2;
	/**
	 * Commit the cursor passed in obj to the adapter at the index passed in
	 * arg1.
	 *
	 * Runs on UI thread.
	 */
	private static final int MSG_COMMIT_QUERY = 3;

	@Override
	public boolean handleMessage(Message message)
	{
		switch (message.what) {
		case MSG_RUN_QUERY: {
			//we are on the worker thread here
			LibraryAdapter adapter = (LibraryAdapter) message.obj;
			Handler handler = mUiHandler;
			handler.sendMessage(handler.obtainMessage(MSG_COMMIT_QUERY, adapter.getMediaType(), 0, adapter.query()));
			break;
		}
		case MSG_COMMIT_QUERY: {
			//we are on the UI thread here
			int index = message.arg1;
			if(index == MediaUtils.TYPE_UNIFIED) {
				//MediaInfoHolder mih = (MediaInfoHolder) message.obj;
				mAdapters[index].commitQuery(message.obj);
				//showInLocalLibrary(mih);
			} else {
				mAdapters[index].commitQuery(message.obj);
				int pos;
				if (mSavedPositions == null) {
					pos = 0;
				} else {
					pos = mSavedPositions[index];
					mSavedPositions[index] = 0;
				}
				ListView listView = (ListView) mContainingLayouts[index].findViewById(R.id.actual_list_view);
				listView.setSelection(pos);
			}
			((ListView) mContainingLayouts[getCurrentType()].findViewById(R.id.actual_list_view)).smoothScrollToPosition(0);;
			break;
		}
		case MSG_SAVE_SORT: {
			MediaAdapter adapter = (MediaAdapter)message.obj;
			SharedPreferences.Editor editor = PlaybackService.getSettings(mActivity).edit();
			editor.putInt(String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType()), adapter.getSortMode());
			editor.commit();
			break;
		}
		case MSG_REQUEST_REQUERY:
			requestRequery((LibraryAdapter)message.obj);
			break;
		default:
			return false;
		}

		return true;
	}

	/**
	 * Requery the given adapter. If it is the current adapter, requery
	 * immediately. Otherwise, mark the adapter as needing a requery and requery
	 * when its tab is selected.
	 *
	 * Must be called on the UI thread.
	 */
	public void requestRequery(LibraryAdapter adapter)
	{
		if (adapter == mCurrentAdapter) {
			postRunQuery(adapter);
		} else {
			mRequeryNeeded[adapter.getMediaType()] = true;
			// Clear the data for non-visible adapters (so we don't show the old
			// data briefly when we later switch to that adapter)
			adapter.clear();
		}
	}

	/**
	 * Call {@link LibraryPagerAdapter#requestRequery(LibraryAdapter)} on the UI
	 * thread.
	 *
	 * @param adapter The adapter, passed to requestRequery.
	 */
	public void postRequestRequery(LibraryAdapter adapter)
	{
		Handler handler = mUiHandler;
		handler.sendMessage(handler.obtainMessage(MSG_REQUEST_REQUERY, adapter));
	}

	/**
	 * Schedule a query to be run for the given adapter on the worker thread.
	 *
	 * @param adapter The adapter to run the query for.
	 */
	private void postRunQuery(LibraryAdapter adapter)
	{
		mRequeryNeeded[adapter != null ? adapter.getMediaType() : MediaUtils.TYPE_UNIFIED] = false;
		Handler handler = mWorkerHandler;
		handler.removeMessages(MSG_RUN_QUERY, adapter);
		handler.sendMessage(handler.obtainMessage(MSG_RUN_QUERY, adapter));
	}

	/**
	 * Requery the adapter of the given type if it exists and needs a requery.
	 *
	 * @param type One of MediaUtils.TYPE_*
	 */
	private void requeryIfNeeded(int type)
	{
		LibraryAdapter adapter = mAdapters[type];
		if (adapter != null && mRequeryNeeded[type]) {
			postRunQuery(adapter);
		}
	}

	/**
	 * Invalidate the data for all adapters.
	 */
	public void invalidateData() //TODO: make this handle the local library tab properly
	{
		for (LibraryAdapter adapter : mAdapters) {
			if (adapter != null) {
				postRequestRequery(adapter);
			}
		}
	}

	/**
	 * Set the saved sort mode for the given adapter. The adapter should
	 * be re-queried after calling this.
	 *
	 * @param adapter The adapter to load for.
	 */
	public void loadSortOrder(MediaAdapter adapter)
	{
		String key = String.format("sort_%d_%d", adapter.getMediaType(), adapter.getLimiterType());
		int def = adapter.getDefaultSortMode();
		int sort = PlaybackService.getSettings(mActivity).getInt(key, def);
		adapter.setSortMode(sort);
	}

	/**
	 * Set the sort mode for the current adapter. Current adapter must be a
	 * MediaAdapter. Saves this sort mode to preferences and updates the list
	 * associated with the adapter to display the new sort mode.
	 *
	 * @param mode The sort mode. See {@link MediaAdapter#setSortMode(int)} for
	 * details.
	 */
	public void setSortMode(int mode)
	{
		MediaAdapter adapter = (MediaAdapter)mCurrentAdapter;
		if (mode == adapter.getSortMode())
			return;

		adapter.setSortMode(mode);
		requestRequery(adapter);

		Handler handler = mWorkerHandler;
		handler.sendMessage(handler.obtainMessage(MSG_SAVE_SORT, adapter));
	}

	/**
	 * Set a new filter on all the adapters.
	 */
	public void setFilter(String text)
	{
		if (text.length() == 0)
			text = null;

		mFilter = text;
		for (LibraryAdapter adapter : mAdapters) {
			if (adapter != null) {
				adapter.setFilter(text);
				requestRequery(adapter);
			}
		}
	}

	@Override
	public void onPageScrollStateChanged(int state)
	{
	}

	@Override
	public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
	{
	}

	@Override
	public void onPageSelected(int position)
	{
		//TODO: doesn't this cause excess redrawing? investigate
		// onPageSelected and setPrimaryItem are called in similar cases, and it
		// would be nice to use just one of them, but each has caveats:
		// - onPageSelected isn't called when the ViewPager is first
		//   initialized
		// - setPrimaryItem isn't called until scrolling is complete, which
		//   makes tab bar and limiter updates look bad
		// So we use both.
		setPrimaryItem(null, position, null);
	}

	/**
	 * Creates the row data used by LibraryActivity.
	 */
	private static Intent createHeaderIntent(View header)
	{
		int type = (Integer)header.getTag();
		Intent intent = new Intent();
		intent.putExtra(LibraryAdapter.DATA_ID, LibraryAdapter.HEADER_ID);
		intent.putExtra(LibraryAdapter.DATA_TYPE, type);
		return intent;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo)
	{
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)menuInfo;
		View targetView = info.targetView;
		Intent intent = info.id == -1 ? createHeaderIntent(targetView) : mCurrentAdapter.createData(targetView);
		mActivity.onCreateContextMenu(menu, intent);
	}

	@Override
	public void onItemClick(AdapterView<?> list, View view, int position, long id)
	{
		Intent intent = id == -1 ? createHeaderIntent(view) : mCurrentAdapter.createData(view);
		mActivity.onItemClicked(intent);
	}
	
	void updateLimiterViews() {
		
		LinearLayout limiterViews = (LinearLayout) mContainingLayouts[mTabOrder[mCurrentPage]].findViewById(R.id.new_limiter_layout);
		
		if(limiterViews == null) {
			return;
		}
		
		limiterViews.removeAllViews();
		
		Limiter limiterData = getCurrentLimiter();

		if(mTabOrder[mCurrentPage] == MediaUtils.TYPE_FILE || mTabOrder[mCurrentPage] == MediaUtils.TYPE_DROPBOX) { //always create an element representing the root directory
			TextView textView1 = (TextView) mActivity.getLayoutInflater().inflate(R.layout.limiter_text_view, null);
			textView1.setText(R.string.root_directory);
			textView1.setTag(LibraryActivity.TAG_DELIMITER_ROOT); //used to handle click event properly
			textView1.setOnClickListener(mActivity);
			limiterViews.addView(textView1);
			mLimiterScroller.setVisibility(View.VISIBLE);
		} else if(mTabOrder[mCurrentPage] == MediaUtils.TYPE_UNIFIED) {
			// always create a link to the root of the library
			TextView textView1 = (TextView) mActivity.getLayoutInflater().inflate(R.layout.limiter_text_view, null);
			textView1.setText(R.string.library);
			textView1.setTag(LibraryActivity.TAG_DELIMITER_ROOT); //used to handle click event properly
			textView1.setOnClickListener(mActivity);
			limiterViews.addView(textView1);
			
			//create a link to all albums, artists or songs if applicable
			if(limiterData != null && (limiterData.type != UnifiedAdapter.ITEM_TYPE_MORE_ALBUMS &&
				limiterData.type != UnifiedAdapter.ITEM_TYPE_MORE_ARTISTS &&
				limiterData.type != UnifiedAdapter.ITEM_TYPE_MORE_SONGS &&
				limiterData.type != UnifiedAdapter.ITEM_TYPE_MORE_PLAYLISTS))
			{
				textView1 = (TextView) mActivity.getLayoutInflater().inflate(R.layout.limiter_text_view, null);
				textView1.setText(R.string.limiter_separator);
				textView1.setTextColor(0xa3ffffff);
				limiterViews.addView(textView1);
				
				textView1 = (TextView) mActivity.getLayoutInflater().inflate(R.layout.limiter_text_view, null);
				
				switch(limiterData.type) {
				case UnifiedAdapter.ITEM_TYPE_ARTIST:
					textView1.setText(R.string.artists);
					textView1.setTag(LibraryActivity.TAG_ARTISTS_ROOT);
					break;
				case UnifiedAdapter.ITEM_TYPE_ALBUM:
					textView1.setText(R.string.albums);
					textView1.setTag(LibraryActivity.TAG_ALBUMS_ROOT);
					break;
				case UnifiedAdapter.ITEM_TYPE_PLAYLIST:
					textView1.setText(R.string.playlists);
					textView1.setTag(LibraryActivity.TAG_PLAYLISTS_ROOT);
					break;
				}
				
				
				textView1.setOnClickListener(mActivity);
				limiterViews.addView(textView1);
			}
		}

		if (limiterData != null) {
			String[] limiter = limiterData.names;
			
			for (int i = 0; i != limiter.length; ++i) {
				TextView textView = (TextView) mActivity.getLayoutInflater().inflate(R.layout.limiter_text_view, null);
				textView.setText(R.string.limiter_separator);
				textView.setTextColor(0xa3ffffff);
				limiterViews.addView(textView);
				
				textView = (TextView) mActivity.getLayoutInflater().inflate(R.layout.limiter_text_view, null);
				textView.setText(limiter[i]);
				textView.setTag(i);
				textView.setOnClickListener(mActivity);
				limiterViews.addView(textView);
			}
		}
		
	}

}
