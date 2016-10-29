/*
 * Copyright (C) 2010, 2011 Christopher Eby <kreed@kreed.org>
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

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.FileObserver;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * A list adapter that provides a view of the filesystem. The active directory
 * is set through a {@link Limiter} and rows are displayed using MediaViews.
 */
public class FileSystemAdapter
	extends BaseAdapter
	implements LibraryAdapter
	         //, View.OnClickListener
{
	private static final Pattern SPACE_SPLIT = Pattern.compile("\\s+");
	private static final Pattern FILE_SEPARATOR = Pattern.compile(File.separator);
	static final int ID_LINK_TO_PARENT_DIR = -10;

    private boolean mPermissionToReadStorageWasDenied = false;

	/**
	 * The owner LibraryActivity.
	 */
	final LibraryActivity mActivity;
	/**
	 * A LayoutInflater to use.
	 */
	private final LayoutInflater mInflater;
	/**
	 * The currently active limiter, set by a row expander being clicked.
	 */
	private Limiter mLimiter;
	/**
	 * The files and folders in the current directory.
	 */
	private File[] mFiles;
	/**
	 * The folder icon shown for folder rows.
	 */
	private final Drawable mFolderIcon;
	/**
	 * The icon shown for audio files.
	 */
	private final Drawable mAudioFileIcon;
	/**
	 * The icon shown for other files.
	 */
	private final Drawable mMiscFileIcon;
	/**
	 * The currently active filter, entered by the user from the search box.
	 */
	String[] mFilter;
	/**
	 * Excludes dot files and files not matching mFilter.
	 */
	private final FilenameFilter mFileFilter = new FilenameFilter() {
		@Override
		public boolean accept(File dir, String filename)
		{
			if (filename.charAt(0) == '.')
				return false;
			if (mFilter != null) {
				filename = filename.toLowerCase();
				for (String term : mFilter) {
					if (!filename.contains(term))
						return false;
				}
			}
			return true;
		}
	};
	/**
	 * Sorts folders before files first, then sorts alphabetically by name.
	 */
	private final Comparator<File> mFileComparator = new Comparator<File>() {
		@Override
		public int compare(File a, File b)
		{
			boolean aIsFolder = a.isDirectory();
			boolean bIsFolder = b.isDirectory();
			if (bIsFolder == aIsFolder) {
				return a.getName().compareToIgnoreCase(b.getName());
			} else if (bIsFolder) {
				return 1;
			}
			return -1;
		}
	};
	/**
	 * The Observer instance for the current directory.
	 */
	private Observer mFileObserver;

	/**
	 * Create a FileSystemAdapter.
	 *
	 * @param activity The LibraryActivity that will contain this adapter.
	 * Called on to requery this adapter when the contents of the directory
	 * change.
	 * @param limiter An initial limiter to set. If none is given, will be set
	 * to the external storage directory.
	 */
	public FileSystemAdapter(LibraryActivity activity, Limiter limiter)
	{
		mActivity = activity;
		mLimiter = limiter;
		mFolderIcon = activity.getResources().getDrawable(R.drawable.folder);
		mAudioFileIcon = activity.getResources().getDrawable(R.drawable.eighth_note);
		mMiscFileIcon = activity.getResources().getDrawable(R.drawable.file);
		mInflater = (LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		if (limiter == null) {
			limiter = buildLimiter( activity.getFilesystemBrowseStart() );
		}
		setLimiter(limiter);
	}

	@Override
	public Object query()
	{
        if (!MediaUtils.hasPermissionToReadStorage(mActivity)) {
            MediaInfoHolder mih = new MediaInfoHolder();
            mih.permissionToReadStorageWasDenied = true;
            return mih;
        }

		File file = mLimiter == null ? new File("/") : (File)mLimiter.data;

		if (mFileObserver == null) {
			mFileObserver = new Observer(file.getPath());
		}

		File[] files = file.listFiles(mFileFilter);
		if (files != null)
			Arrays.sort(files, mFileComparator);
		return files;
	}

	@Override
	public void commitQuery(Object data)
	{
        if(queryResultIndicatesNoPermission(data)) {
            mPermissionToReadStorageWasDenied = true;
            mFiles = null;
        } else {
            mPermissionToReadStorageWasDenied = false;
            mFiles = (File[]) data;
        }
		notifyDataSetInvalidated();
	}

    private static boolean queryResultIndicatesNoPermission(Object queryResult) {
        if(!(queryResult instanceof MediaInfoHolder)) {
            return false;
        }
        if(((MediaInfoHolder) queryResult).permissionToReadStorageWasDenied) {
            return true;
        }
        throw new IllegalArgumentException("Incorrect data passed to file system adapter.");
    }

	@Override
	public void clear()
	{
		mFiles = null;
		notifyDataSetInvalidated();
	}

	@Override
	public int getCount()
	{
        if(mPermissionToReadStorageWasDenied) {
            return 1;
        }

		if (mFiles == null) {
            return 0;
        }
		
		Limiter limiter = getLimiter();
		
		if(getLimiter() == null) //we're already at the root, don't show a link to the parent
			return mFiles.length;
		
		return mFiles.length + 1; //show link to the parent
	}

	@Override
	public Object getItem(int pos)
	{
		return mFiles[pos];
	}

	@Override
	public long getItemId(int pos)
	{
		return pos;
	}

	private static class ViewHolder {
		public int id;
		public TextView text;
	}

	@Override
	public View getView(int pos, View convertView, ViewGroup parent)
	{

        if(mPermissionToReadStorageWasDenied) {
            return getNoPermissionView();
        }
		
		View view;
		ViewHolder holder;

		// always create a new view to get rid of touch feedback from the old one
		view = mInflater.inflate(R.layout.library_row_orchid, null);
		holder = new ViewHolder();
		holder.text = (TextView)view.findViewById(R.id.text);
		view.setTag(holder);
		
		if(getLimiter() == null) pos++; //skip the link to the parent dir

		if(pos == 0) { //create a link to the parent directory (..)

			holder.text.setCompoundDrawablesWithIntrinsicBounds(mFolderIcon, null, null, null);
			holder.text.setText(R.string.parent_directory);
			holder.id = ID_LINK_TO_PARENT_DIR;
			
		} else { //represent a file in the current directory
			File file = mFiles[pos - 1];
			String fileName = file.getName();
			holder.id = pos - 1;
			holder.text.setText(fileName);
			if(file.isDirectory()) {
				holder.text.setCompoundDrawablesWithIntrinsicBounds(mFolderIcon, null, null, null);
			} else if(fileName.endsWith(".mp3") || fileName.endsWith(".flac")) { //TODO: make this a comprehensive list of extensions
				holder.text.setCompoundDrawablesWithIntrinsicBounds(mAudioFileIcon, null, null, null);
			} else {
				holder.text.setCompoundDrawablesWithIntrinsicBounds(mMiscFileIcon, null, null, null);
				holder.text.setAlpha(0.8f);
			}
		}
		
		return view;
	}

    private View getNoPermissionView() {
        View view = mInflater.inflate(R.layout.library_row_storage_permission, null);
        TextView textView = (TextView) view.findViewById(R.id.text);
        textView.setText(R.string.grant_storage_access);
        return view;
    }

	@Override
	public void setFilter(String filter)
	{
		if (filter == null)
			mFilter = null;
		else
			mFilter = SPACE_SPLIT.split(filter.toLowerCase());
	}

	@Override
	public void setLimiter(Limiter limiter)
	{
		if (mFileObserver != null)
			mFileObserver.stopWatching();
		mFileObserver = null;
		mLimiter = limiter;
	}

	@Override
	public Limiter getLimiter()
	{
		return mLimiter;
	}

	/**
	 * Builds a limiter from the given folder. Only files contained in the
	 * given folder will be shown if the limiter is set on this adapter.
	 *
	 * @param file A File pointing to a folder.
	 * @return A limiter describing the given folder.
	 */
	public static Limiter buildLimiter(File file)
	{
		String[] fields = FILE_SEPARATOR.split(file.getPath().substring(1));
		return new Limiter(MediaUtils.TYPE_FILE, fields, file);
	}

	@Override
	public Limiter buildLimiter(int type, long id, String preGeneratedName)
	{
		return buildLimiter(mFiles[(int)id]);
	}

	@Override
	public int getMediaType()
	{
		return MediaUtils.TYPE_FILE;
	}

	/**
	 * FileObserver that reloads the files in this adapter.
	 */
	private class Observer extends FileObserver {
		public Observer(String path)
		{
			super(path, FileObserver.CREATE | FileObserver.DELETE | FileObserver.MOVED_TO | FileObserver.MOVED_FROM);
			startWatching();
		}

		@Override
		public void onEvent(int event, String path)
		{
			mActivity.mPagerAdapter.postRequestRequery(FileSystemAdapter.this);
		}
	}

	@Override
	public Intent createData(View view)
	{

		Intent intent = new Intent();

		if(mPermissionToReadStorageWasDenied) {
			intent.putExtra(LibraryAdapter.DATA_REQUEST_STORAGE_ACCESS, true);
			return intent;
		}

		ViewHolder holder = (ViewHolder)view.getTag();
		
		if(holder.id == ID_LINK_TO_PARENT_DIR)  { //the id for parent directory links
			intent.putExtra(LibraryAdapter.DATA_GO_UP, true);
			return intent;
		}
		
		File file = mFiles[holder.id];

		intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_FILE);
		intent.putExtra(LibraryAdapter.DATA_ID, (long)holder.id);
		intent.putExtra(LibraryAdapter.DATA_TITLE, holder.text.getText().toString());
		intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, file.isDirectory());

		String path;
		try {
			path = file.getCanonicalPath();
		} catch (IOException e) {
			path = file.getAbsolutePath();
			Log.e("OrchidMP", "Failed to canonicalize path", e);
		}
		intent.putExtra(LibraryAdapter.DATA_FILE, path);
		return intent;
	}

}
