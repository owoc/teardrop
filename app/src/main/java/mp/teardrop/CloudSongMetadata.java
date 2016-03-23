package mp.teardrop;

import java.util.Date;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.dropbox.client2.DropboxAPI.DropboxLink;

public class CloudSongMetadata {
	float rgAlbum; //TODO: change these into Float objects and use nulls when no data available
	float rgTrack;
	String path; //streaming URL
	String dbPath; //path to the file in Dropbox
	Date expires;
	String revision;
	String title;
	String artist;
	String album;
	long duration; //milliseconds
	int trackNumber;
	
	/**
	 * @param duration Length of the song in milliseconds.
	 */
	public CloudSongMetadata(
			DropboxLink streamingLink, String dbPath,
			String revision, float rgAlbum, float rgTrack,
			String title, String artist, String album, long duration, int trackNumber) {
		
		this.path = streamingLink == null ? null : streamingLink.url;
		this.dbPath = dbPath;
		this.revision = revision;
		this.expires = streamingLink == null ? null : streamingLink.expires;
		this.rgAlbum = rgAlbum;
		this.rgTrack = rgTrack;
		this.title = title;
		this.album = album;
		this.artist = artist;
		this.duration = duration;
		this.trackNumber = trackNumber;
		
	}
	
	JSONObject toJsonObject() {
		JSONObject jsonBourne = new JSONObject();
		
		try {
			
			jsonBourne.put("path", path);
			if(dbPath != null) jsonBourne.put("dbPath", dbPath);
			jsonBourne.put("revision", revision);
			jsonBourne.put("expires", expires.getTime());
			jsonBourne.put("rgAlbum", rgAlbum);
			jsonBourne.put("rgTrack", rgTrack);
			jsonBourne.put("title", title);
			jsonBourne.put("album", album);
			jsonBourne.put("artist", artist);
			jsonBourne.put("duration", duration);
			jsonBourne.put("trackNumber", trackNumber);
			
		} catch (JSONException e) {
			Log.w("OrchidMP", e.getMessage());
			jsonBourne = null;
		}
		
		return jsonBourne;
		
	}
	
	static CloudSongMetadata fromJsonObject(JSONObject jsonBourne) {
		
		try {
			
			CloudSongMetadata metadata = new CloudSongMetadata(
				null,
				null,
				jsonBourne.getString("revision"),
				Float.parseFloat(jsonBourne.getString("rgAlbum")),
				Float.parseFloat(jsonBourne.getString("rgTrack")),
				jsonBourne.getString("title"),
				jsonBourne.getString("artist"),
				jsonBourne.getString("album"),
				jsonBourne.getLong("duration"),
				jsonBourne.getInt("trackNumber"));
			
			metadata.path = jsonBourne.getString("path");
			metadata.dbPath = jsonBourne.optString("dbPath", null);
			metadata.expires = new Date(jsonBourne.getLong("expires"));
			
			return metadata;
			
		} catch (NumberFormatException e) {
			return null;
		} catch (JSONException e) {
			return null;
		}
		
	}
	
	static CloudSongMetadata fromJsonString(String json) {
		try {
			return fromJsonObject(new JSONObject(json));
		} catch (JSONException e) {
			return null;
		}
	}
	
}
