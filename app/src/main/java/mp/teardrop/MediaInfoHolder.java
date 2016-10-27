package mp.teardrop;

import java.util.ArrayList;

import android.graphics.Bitmap;

/**
 * Contains media information retrieved from the MediaStore. Used to pass said
 * information from the worker thread, which queries the MediaStore, to the UI
 * thread, which displays the results.
 */
class MediaInfoHolder {
	class ArtistInfo {
		String name;
		long databaseId;

		public ArtistInfo(String name, long databaseId) {
			this.name = name;
			this.databaseId = databaseId;
		}
	}

	class AlbumInfo {
		String name;
		String artistName;
		long databaseId;
		Bitmap cover;

		public AlbumInfo(String name, String artistName, long databaseId, Bitmap cover) {
			this.name = name;
			this.artistName = artistName;
			this.databaseId = databaseId;
			this.cover = cover;
		}
	}

	class SongInfo {
		String name;
		String artistName;
		long databaseId;

		public SongInfo(String name, String artistName, long databaseId) {
			this.name = name;
			this.artistName = artistName;
			this.databaseId = databaseId;
		}
	}

	class PlaylistInfo {
		String name;
		long databaseId;

		public PlaylistInfo(String name, long databaseId) {
			this.name = name;
			this.databaseId = databaseId;
		}
	}

	ArrayList<ArtistInfo> artists = new ArrayList<ArtistInfo>();
	ArrayList<AlbumInfo> albums = new ArrayList<AlbumInfo>();
	ArrayList<SongInfo> songs = new ArrayList<SongInfo>();
	ArrayList<PlaylistInfo> playlists = new ArrayList<PlaylistInfo>();

	boolean permissionToReadStorageWasDenied = false;
}
