package com.vibrdrome.app.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// MARK: - Top-level Response Wrapper

@Serializable
data class SubsonicResponse(
    @SerialName("subsonic-response") val subsonicResponse: SubsonicResponseBody
)

@Serializable
data class SubsonicResponseBody(
    val status: String,
    val version: String,
    val type: String? = null,
    val serverVersion: String? = null,
    val openSubsonic: Boolean? = null,
    val error: SubsonicAPIError? = null,
    // Payload keys — each endpoint uses a different one
    val artists: ArtistsResponse? = null,
    val artist: Artist? = null,
    val album: Album? = null,
    val song: Song? = null,
    val searchResult3: SearchResult3? = null,
    val playlists: PlaylistsWrapper? = null,
    val playlist: Playlist? = null,
    val genres: GenresWrapper? = null,
    val starred2: Starred2? = null,
    val albumList2: AlbumList2Response? = null,
    val randomSongs: RandomSongsResponse? = null,
    val internetRadioStations: InternetRadioStationsWrapper? = null,
    val lyricsList: LyricsList? = null,
    val nowPlaying: NowPlayingWrapper? = null,
    val playQueue: PlayQueue? = null,
    val bookmarks: BookmarksWrapper? = null,
    val similarSongs2: SimilarSongs2Response? = null,
    val topSongs: TopSongsResponse? = null,
    val musicFolders: MusicFoldersWrapper? = null,
    val directory: MusicDirectory? = null,
    val indexes: IndexesResponse? = null,
)

@Serializable
data class SubsonicAPIError(
    val code: Int,
    val message: String,
)

// MARK: - Artist Models

@Serializable
data class ArtistIndex(
    val name: String,
    val artist: List<Artist>? = null,
)

@Serializable
data class ArtistsResponse(
    val index: List<ArtistIndex>? = null,
    val ignoredArticles: String? = null,
)

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val coverArt: String? = null,
    val albumCount: Int? = null,
    val starred: String? = null,
    val album: List<Album>? = null,
)

// MARK: - Album Models

@Serializable
data class Album(
    val id: String,
    val name: String,
    val artist: String? = null,
    val artistId: String? = null,
    val coverArt: String? = null,
    val songCount: Int? = null,
    val duration: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val starred: String? = null,
    val created: String? = null,
    val song: List<Song>? = null,
    val replayGain: ReplayGain? = null,
)

@Serializable
data class AlbumList2Response(
    val album: List<Album>? = null,
)

@Serializable
data class RandomSongsResponse(
    val song: List<Song>? = null,
)

@Serializable
data class SimilarSongs2Response(
    val song: List<Song>? = null,
)

@Serializable
data class TopSongsResponse(
    val song: List<Song>? = null,
)

// MARK: - Song Model

@Serializable
data class Song(
    val id: String,
    val parent: String? = null,
    val title: String,
    val album: String? = null,
    val artist: String? = null,
    val albumId: String? = null,
    val artistId: String? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val coverArt: String? = null,
    val size: Int? = null,
    val contentType: String? = null,
    val suffix: String? = null,
    val duration: Int? = null,
    val bitRate: Int? = null,
    val path: String? = null,
    val discNumber: Int? = null,
    val created: String? = null,
    val starred: String? = null,
    val bpm: Int? = null,
    val replayGain: ReplayGain? = null,
    val musicBrainzId: String? = null,
)

@Serializable
data class ReplayGain(
    val trackGain: Double? = null,
    val albumGain: Double? = null,
    val trackPeak: Double? = null,
    val albumPeak: Double? = null,
    val baseGain: Double? = null,
)

// MARK: - Playlist Models

@Serializable
data class PlaylistsWrapper(
    val playlist: List<Playlist>? = null,
)

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songCount: Int? = null,
    val duration: Int? = null,
    val created: String? = null,
    val changed: String? = null,
    val coverArt: String? = null,
    val owner: String? = null,
    @SerialName("public") val isPublic: Boolean? = null,
    val entry: List<Song>? = null,
)

// MARK: - Search Results

@Serializable
data class SearchResult3(
    val artist: List<Artist>? = null,
    val album: List<Album>? = null,
    val song: List<Song>? = null,
)

// MARK: - Starred

@Serializable
data class Starred2(
    val artist: List<Artist>? = null,
    val album: List<Album>? = null,
    val song: List<Song>? = null,
)

// MARK: - Internet Radio

@Serializable
data class InternetRadioStationsWrapper(
    val internetRadioStation: List<InternetRadioStation>? = null,
)

@Serializable
data class InternetRadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val homePageUrl: String? = null,
    val coverArt: String? = null,
) {
    /** Workaround for Navidrome bug #5293: coverArt returns raw filename instead of ra-{id} */
    fun fixedCoverArt(): String? {
        if (coverArt.isNullOrEmpty()) return null
        if (coverArt.startsWith("ra-")) return coverArt
        return "ra-$id"
    }
}

// MARK: - Lyrics (OpenSubsonic)

@Serializable
data class LyricsList(
    val structuredLyrics: List<StructuredLyrics>? = null,
)

@Serializable
data class StructuredLyrics(
    val displayArtist: String? = null,
    val displayTitle: String? = null,
    val lang: String,
    val synced: Boolean,
    val offset: Int? = null,
    val line: List<LyricLine>? = null,
)

@Serializable
data class LyricLine(
    val start: Int? = null,
    val value: String,
)

// MARK: - Play Queue

// MARK: - Now Playing

@Serializable
data class NowPlayingWrapper(
    val entry: List<NowPlayingEntry>? = null,
)

@Serializable
data class NowPlayingEntry(
    val username: String,
    val minutesAgo: Int? = null,
    val playerId: Int? = null,
    val playerName: String? = null,
    // Song fields (NowPlayingEntry extends Song in Subsonic API)
    val id: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val coverArt: String? = null,
)

// MARK: - Play Queue

@Serializable
data class PlayQueue(
    val current: String? = null,
    val position: Int? = null,
    val changed: String? = null,
    val changedBy: String? = null,
    val entry: List<Song>? = null,
)

// MARK: - Genres

@Serializable
data class GenresWrapper(
    val genre: List<Genre>? = null,
)

@Serializable
data class Genre(
    val songCount: Int? = null,
    val albumCount: Int? = null,
    val value: String,
)

// MARK: - Music Folders / Directory Browsing

@Serializable
data class MusicFoldersWrapper(
    val musicFolder: List<MusicFolder>? = null,
)

@Serializable
data class IndexesResponse(
    val lastModified: Long? = null,
    val ignoredArticles: String? = null,
    val index: List<FolderIndex>? = null,
    val child: List<DirectoryChild>? = null,
)

@Serializable
data class FolderIndex(
    val name: String,
    val artist: List<FolderArtist>? = null,
)

@Serializable
data class FolderArtist(
    val id: String,
    val name: String,
    val albumCount: Int? = null,
)

@Serializable
data class MusicFolder(
    val id: String,
    val name: String? = null,
)

@Serializable
data class MusicDirectory(
    val id: String,
    val name: String? = null,
    val parent: String? = null,
    val child: List<DirectoryChild>? = null,
)

@Serializable
data class DirectoryChild(
    val id: String,
    val title: String? = null,
    val isDir: Boolean = false,
    val artist: String? = null,
    val album: String? = null,
    val coverArt: String? = null,
    val duration: Int? = null,
    val track: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val size: Int? = null,
    val suffix: String? = null,
    val bitRate: Int? = null,
    val contentType: String? = null,
    val path: String? = null,
    val parent: String? = null,
    val starred: String? = null,
    val created: String? = null,
)

// MARK: - Bookmarks

@Serializable
data class BookmarksWrapper(
    val bookmark: List<Bookmark>? = null,
)

@Serializable
data class Bookmark(
    val position: Int,
    val username: String,
    val comment: String? = null,
    val created: String,
    val changed: String,
    val entry: Song? = null,
)
