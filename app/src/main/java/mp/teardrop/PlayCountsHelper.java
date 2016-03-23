/*
 * Copyright (C) 2014 Adrian Ulrich <adrian@blinkenlights.ch>
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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;
import android.util.Log;
import java.util.ArrayList;

public class PlayCountsHelper extends SQLiteOpenHelper {

	/**
	 * SQL constants and CREATE TABLE statements used by 
	 * this java class
	 */
	private static final int DATABASE_VERSION = 1;
	private static final String DATABASE_NAME = "playcounts.db";
	private static final String TABLE_PLAYCOUNTS = "playcounts";
	private static final String DATABASE_CREATE = "CREATE TABLE "+TABLE_PLAYCOUNTS + " (" //TODO: normalize the DB if needed
	  + "type      INTEGER, "
	  + "type_id   BIGINT, "
	  + "playcount INTEGER);";
	private static final String INDEX_UNIQUE_CREATE = "CREATE UNIQUE INDEX idx_uniq ON "+TABLE_PLAYCOUNTS
	  + " (type, type_id);";
	private static final String INDEX_TYPE_CREATE = "CREATE INDEX idx_type ON "+TABLE_PLAYCOUNTS
	  + " (type);";

	private Context ctx;

	public PlayCountsHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
		ctx = context;
	}

	@Override
	public void onCreate(SQLiteDatabase dbh) {
		dbh.execSQL(DATABASE_CREATE);
		dbh.execSQL(INDEX_UNIQUE_CREATE);
		dbh.execSQL(INDEX_TYPE_CREATE);
	}

	@Override
	public void onUpgrade(SQLiteDatabase dbh, int oldVersion, int newVersion) {
		// first db -> nothing to upgrade
	}

	/**
	 * Increases this song's "popularity" (as well as that of
	 * its artist and album) for the purposes of displaying
	 * most played items in the library.
	 * 
	 * @param song
	 * @param weight How much to increase the "popularity" by.
	 */
	public void countSong(Song song, int weight) {
		long id = song.id;
		long albumId = song.albumId;
		long artistId = song.artistId;
		
		//TODO: remove the catch block and fix the root of the problem 
		//the below code occasionally raises
		//java.lang.IllegalStateException: attempt to re-open an already-closed object: SQLiteDatabase: /data/data/pl.orchidmp/databases/playcounts.db
		//Stack Overflow 23007747 looks like a good start for a fix
		//FIXME!!!
		
		try {
		
			SQLiteDatabase dbh = this.getWritableDatabase();
			
			//create the row if it doesn't exist, then increment
			dbh.execSQL("INSERT OR IGNORE INTO "+TABLE_PLAYCOUNTS+" (type, type_id, playcount) VALUES ("+UnifiedAdapter.ITEM_TYPE_SONG+", "+id+", 0);");
			dbh.execSQL("UPDATE "+TABLE_PLAYCOUNTS+" SET playcount=playcount+1 WHERE type="+UnifiedAdapter.ITEM_TYPE_SONG+" AND type_id="+id+";");
			
			dbh.execSQL("INSERT OR IGNORE INTO "+TABLE_PLAYCOUNTS+" (type, type_id, playcount) VALUES ("+UnifiedAdapter.ITEM_TYPE_ARTIST+", "+artistId+", 0);");
			dbh.execSQL("UPDATE "+TABLE_PLAYCOUNTS+" SET playcount=playcount+1 WHERE type="+UnifiedAdapter.ITEM_TYPE_ARTIST+" AND type_id="+artistId+";");
			
			dbh.execSQL("INSERT OR IGNORE INTO "+TABLE_PLAYCOUNTS+" (type, type_id, playcount) VALUES ("+UnifiedAdapter.ITEM_TYPE_ALBUM+", "+albumId+", 0);");
			dbh.execSQL("UPDATE "+TABLE_PLAYCOUNTS+" SET playcount=playcount+1 WHERE type="+UnifiedAdapter.ITEM_TYPE_ALBUM+" AND type_id="+albumId+";");
			
			performGC(dbh, UnifiedAdapter.ITEM_TYPE_SONG);
			
			dbh.close();
		
		} catch(IllegalStateException e) {
			
		}
	}

	/**
	 * Returns a sorted array list of most often listened to artist, album or song ids
	 * 
	 * @param type One of {@link UnifiedAdapter}.ITEM_TYPE_*
	 */
	public ArrayList<Long> getTopMedia(int type, int limit) {
		ArrayList<Long> payload = new ArrayList<Long>();
		SQLiteDatabase dbh = this.getReadableDatabase();
		
		Cursor cursor = dbh.rawQuery("SELECT type_id FROM "+TABLE_PLAYCOUNTS+" WHERE type="+type+" ORDER BY playcount DESC limit " + (limit < 0 ? 4096 : limit), null);

		while (cursor.moveToNext()) {
			payload.add(cursor.getLong(0));
		}

		cursor.close();
		return payload;
	}

	/**
	 * Picks a random amount of 'type' items from the provided DBH
	 * and checks them against Androids media database.
	 * Items not found in the media library are removed from the DBH's database
	 */
	private int performGC(SQLiteDatabase dbh, int type) {
		ArrayList<Long> toCheck = new ArrayList<Long>(); // List of songs we are going to check
		QueryTask query;                                 // Reused query object
		Cursor cursor;                                   // recycled cursor
		int removed = 0;                                 // Amount of removed items

		// We are just grabbing a bunch of random IDs
		cursor = dbh.rawQuery("SELECT type_id FROM "+TABLE_PLAYCOUNTS+" WHERE type="+type+" ORDER BY RANDOM() LIMIT 10", null);
		while (cursor.moveToNext()) {
			toCheck.add(cursor.getLong(0));
		}
		cursor.close();

		for (Long id : toCheck) {
			query = MediaUtils.buildQuery(type, id, null, null);
			cursor = query.runQuery(ctx.getContentResolver());
			if(cursor.getCount() == 0) {
				dbh.execSQL("DELETE FROM "+TABLE_PLAYCOUNTS+" WHERE type="+type+" AND type_id="+id);
				removed++;
			}
			cursor.close();
		}
		Log.v("OrchidMP", "performGC: items removed="+removed);
		return removed;
	}

}
