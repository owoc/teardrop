/*
 * Copyright (C) 2013 Adrian Ulrich <adrian@blinkenlights.ch>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>. 
 */

package mp.teardrop;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class ShowQueueAdapter extends ArrayAdapter<Song> {
	
	int resource;
	Context context;
	int hl_row;
	
	public ShowQueueAdapter(Context context, int resource) {
		super(context, resource);
		this.resource = resource;
		this.context = context;
		this.hl_row = -1;
	}
	
	/*
	** Tells the adapter to highlight a specific row id
	** Set this to -1 to disable the feature
	*/
	public void highlightRow(int pos) {
		this.hl_row = pos;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = ((Activity)context).getLayoutInflater();
		View row = inflater.inflate(resource, parent, false);
		Song song = getItem(position);
		TextView target = ((TextView)row.findViewById(R.id.text));
		SpannableStringBuilder sb = new SpannableStringBuilder(song.title);
		sb.append('\n');
		sb.append(song.artist+" | "+song.album);
		sb.setSpan(new ForegroundColorSpan(Color.GRAY), song.title.length() + 1, sb.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		
		target.setText(sb);
		
		Drawable drawable;
		if(position == this.hl_row && song.isCloudSong) {
			drawable = context.getResources().getDrawable(R.drawable.accented_eighth_note_cloud);
		} else if(position == this.hl_row) {
			drawable = context.getResources().getDrawable(R.drawable.accented_eighth_note_24);
		} else if(song.isCloudSong) {
			drawable = context.getResources().getDrawable(R.drawable.eighth_note_cloud);
		} else {
			drawable = context.getResources().getDrawable(R.drawable.eighth_note);
		}
		
		target.setCompoundDrawablesWithIntrinsicBounds(drawable, null, null, null);
		
		return row;
	}
	
}
