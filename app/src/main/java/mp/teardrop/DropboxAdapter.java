package mp.teardrop;

import java.io.File;
import java.util.ArrayList;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.dropbox.client2.DropboxAPI.Entry;

public class DropboxAdapter extends BaseAdapter implements LibraryAdapter {

    private static final Pattern FILE_SEPARATOR = Pattern.compile(File.separator);

    private LibraryActivity mActivity;
    private Limiter mLimiter;
    private LayoutInflater mInflater;
    private Drawable mFolderIcon;
    private Drawable mAudioFileIcon;
    private Drawable mMiscFileIcon;

    boolean mLinkedWithDropbox;
    private ArrayList<Entry> mDropboxFiles;


    /**
     * Create a DropboxAdapter.
     *
     * @param activity The LibraryActivity that will contain this adapter.
     *                 Called on to requery this adapter when the contents of the directory
     *                 change.
     * @param limiter  An initial limiter to set. If none is given, will be set
     *                 to the root of the user's Dropbox.
     */
    public DropboxAdapter(LibraryActivity activity, Limiter limiter) {
        mActivity = activity;
        mLimiter = limiter;
        mFolderIcon = activity.getResources().getDrawable(R.drawable.folder);
        mAudioFileIcon = activity.getResources().getDrawable(R.drawable.eighth_note);
        mMiscFileIcon = activity.getResources().getDrawable(R.drawable.file);
        mInflater = (LayoutInflater) activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        SharedPreferences prefs =
                mActivity.getSharedPreferences(LibraryActivity.ACCOUNT_PREFS_NAME, 0);
        String key = prefs.getString(LibraryActivity.ACCESS_KEY_NAME, null);
        String secret = prefs.getString(LibraryActivity.ACCESS_SECRET_NAME, null);
        if (key == null || secret == null || key.length() == 0 || secret.length() == 0) {
            mLinkedWithDropbox = false;
        } else {
            mLinkedWithDropbox = true;
        }

		/* if (limiter == null) {
            limiter = buildLimiter( activity.getFilesystemBrowseStart() );
		} */
        setLimiter(limiter);
    }


    @Override
    public int getCount() {
        if (!mLinkedWithDropbox) {
            return 1;
        } else {
            if (mDropboxFiles == null) {
                return 0;
            }

            if (getLimiter() == null) //we're already at the root, don't show a link to the parent
            {
                return mDropboxFiles.size();
            }

            return mDropboxFiles.size() + 1; //show link to the parent
        }
    }


    @Override
    public Object getItem(int pos) {
        return mLinkedWithDropbox ? mDropboxFiles.get(pos) : null;
    }


    @Override
    public long getItemId(int pos) {
        return pos;
    }


    private static class ViewHolder {

        public int id;
        public TextView text;
    }


    @Override
    public View getView(int pos, View convertView, ViewGroup parent) {
        if (!mLinkedWithDropbox) {
            View view = mInflater.inflate(R.layout.library_row_link_with_dropbox, null);
            TextView textView = (TextView) view.findViewById(R.id.text);
            textView.setText(R.string.link_with_dropbox);
            return view;
        } else {

            View view;
            ViewHolder holder;

            // always create a new view to get rid of touch feedback from the old one
            view = mInflater.inflate(R.layout.library_row_orchid, null);
            holder = new ViewHolder();
            holder.text = (TextView) view.findViewById(R.id.text);
            view.setTag(holder);

            if (mLimiter == null) {
                pos++; //skip the link to the parent dir
            }

            if (pos == 0) { //create a link to the parent directory (..)

                holder.text.setCompoundDrawablesWithIntrinsicBounds(mFolderIcon, null, null, null);
                holder.text.setText(R.string.parent_directory);
                holder.id = FileSystemAdapter.ID_LINK_TO_PARENT_DIR;

            } else { //represent a file in the current directory
                Entry dbFile = mDropboxFiles.get(pos - 1);
                String fileName = dbFile.fileName();
                holder.id = pos - 1;
                holder.text.setText(fileName);
                if (dbFile.isDir) {
                    holder.text
                            .setCompoundDrawablesWithIntrinsicBounds(mFolderIcon, null, null, null);
                } else if (fileName.endsWith(".mp3") || fileName.endsWith(
                        ".flac")) { //TODO: make this a comprehensive list of extensions
                    holder.text.setCompoundDrawablesWithIntrinsicBounds(mAudioFileIcon, null, null,
                            null);
                } else {
                    holder.text.setCompoundDrawablesWithIntrinsicBounds(mMiscFileIcon, null, null,
                            null);
                    holder.text.setAlpha(0.8f);
                }
            }

            return view;

        }
    }


    @Override
    public int getMediaType() {
        return MediaUtils.TYPE_DROPBOX;
    }


    @Override
    public void setLimiter(Limiter limiter) {
        mLimiter = limiter;
    }


    @Override
    public Limiter getLimiter() {
        return mLimiter;
    }


    public static Limiter buildLimiter(String path) {
        String[] fields = FILE_SEPARATOR.split(path.substring(1));
        return new Limiter(MediaUtils.TYPE_DROPBOX, fields, path);
    }


    @Override
    public Limiter buildLimiter(int type, long id, String preGeneratedName) {
        Entry dbFile = mDropboxFiles.get((int) id);
        String[] fields = FILE_SEPARATOR.split(dbFile.path.substring(1));
        return new Limiter(MediaUtils.TYPE_DROPBOX, fields, dbFile.path);
    }


    @Override
    public void setFilter(String filter) {
        // TODO Auto-generated method stub

    }


    @Override
    public Object query() {
        // TODO Auto-generated method stub
        return null;
    }


    @SuppressWarnings("unchecked")
    @Override
    public void commitQuery(Object data) {
        if (data == null) {
            return;
        }
        mDropboxFiles = (ArrayList<Entry>) data;
        notifyDataSetInvalidated();
    }


    @Override
    public void clear() {
        //not needed
    }


    void resetAfterDropboxUnlinked() {
        mLinkedWithDropbox = false;
        mDropboxFiles = null;
        notifyDataSetInvalidated();
    }


    @Override
    public Intent createData(View row) {
        Intent intent = new Intent();
        intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_DROPBOX);
        if (!mLinkedWithDropbox) {

            intent.putExtra(LibraryAdapter.DATA_LINK_WITH_DROPBOX, true);

            return intent;
        } else {

            ViewHolder holder = (ViewHolder) row.getTag();

            if (holder.id ==
                    FileSystemAdapter.ID_LINK_TO_PARENT_DIR) { //the id for parent directory links
                Intent intent1 = new Intent();
                intent1.putExtra(LibraryAdapter.DATA_GO_UP, true);

                return intent1;
            }

            Entry dbFile = mDropboxFiles.get(holder.id);

            intent.putExtra(LibraryAdapter.DATA_TYPE, MediaUtils.TYPE_DROPBOX);
            intent.putExtra(LibraryAdapter.DATA_ID, (long) holder.id);
            intent.putExtra(LibraryAdapter.DATA_TITLE, holder.text.getText().toString());
            intent.putExtra(LibraryAdapter.DATA_EXPANDABLE, dbFile.isDir);

            String path;
            path = dbFile.path;

            intent.putExtra(LibraryAdapter.DATA_FILE, path);
            return intent;

        }
    }

}
