package com.vibrdrome.app.network

enum class AlbumListType(val value: String) {
    RANDOM("random"),
    NEWEST("newest"),
    FREQUENT("frequent"),
    RECENT("recent"),
    STARRED("starred"),
    ALPHABETICAL_BY_NAME("alphabeticalByName"),
    ALPHABETICAL_BY_ARTIST("alphabeticalByArtist"),
    BY_YEAR("byYear"),
    BY_GENRE("byGenre"),
}

sealed class SubsonicEndpoint {
    abstract val path: String
    abstract val queryItems: Map<String, String>

    data object Ping : SubsonicEndpoint() {
        override val path = "/rest/ping"
        override val queryItems = emptyMap<String, String>()
    }

    data class GetArtists(val musicFolderId: String? = null) : SubsonicEndpoint() {
        override val path = "/rest/getArtists"
        override val queryItems = buildMap {
            musicFolderId?.let { put("musicFolderId", it) }
        }
    }

    data class GetArtist(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/getArtist"
        override val queryItems = mapOf("id" to id)
    }

    data class GetAlbum(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/getAlbum"
        override val queryItems = mapOf("id" to id)
    }

    data class GetSong(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/getSong"
        override val queryItems = mapOf("id" to id)
    }

    data class Search3(
        val query: String,
        val artistCount: Int = 20,
        val albumCount: Int = 20,
        val songCount: Int = 20,
        val artistOffset: Int = 0,
        val albumOffset: Int = 0,
        val songOffset: Int = 0,
        val musicFolderId: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/search3"
        override val queryItems = buildMap {
            put("query", query)
            put("artistCount", artistCount.toString())
            put("albumCount", albumCount.toString())
            put("songCount", songCount.toString())
            if (artistOffset > 0) put("artistOffset", artistOffset.toString())
            if (albumOffset > 0) put("albumOffset", albumOffset.toString())
            if (songOffset > 0) put("songOffset", songOffset.toString())
            musicFolderId?.let { put("musicFolderId", it) }
        }
    }

    data class GetAlbumList2(
        val type: AlbumListType,
        val pageSize: Int = 20,
        val offset: Int = 0,
        val fromYear: Int? = null,
        val toYear: Int? = null,
        val genre: String? = null,
        val musicFolderId: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/getAlbumList2"
        override val queryItems = buildMap {
            put("type", type.value)
            put("size", pageSize.toString())
            if (offset > 0) put("offset", offset.toString())
            fromYear?.let { put("fromYear", it.toString()) }
            toYear?.let { put("toYear", it.toString()) }
            genre?.let { put("genre", it) }
            musicFolderId?.let { put("musicFolderId", it) }
        }
    }

    data class GetRandomSongs(
        val pageSize: Int = 20,
        val genre: String? = null,
        val fromYear: Int? = null,
        val toYear: Int? = null,
        val musicFolderId: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/getRandomSongs"
        override val queryItems = buildMap {
            put("size", pageSize.toString())
            genre?.let { put("genre", it) }
            fromYear?.let { put("fromYear", it.toString()) }
            toYear?.let { put("toYear", it.toString()) }
            musicFolderId?.let { put("musicFolderId", it) }
        }
    }

    data class GetStarred2(val musicFolderId: String? = null) : SubsonicEndpoint() {
        override val path = "/rest/getStarred2"
        override val queryItems = buildMap {
            musicFolderId?.let { put("musicFolderId", it) }
        }
    }

    data object GetGenres : SubsonicEndpoint() {
        override val path = "/rest/getGenres"
        override val queryItems = emptyMap<String, String>()
    }

    data class Star(
        val id: String? = null,
        val albumId: String? = null,
        val artistId: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/star"
        override val queryItems = buildMap {
            id?.let { put("id", it) }
            albumId?.let { put("albumId", it) }
            artistId?.let { put("artistId", it) }
        }
    }

    data class Unstar(
        val id: String? = null,
        val albumId: String? = null,
        val artistId: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/unstar"
        override val queryItems = buildMap {
            id?.let { put("id", it) }
            albumId?.let { put("albumId", it) }
            artistId?.let { put("artistId", it) }
        }
    }

    data class SetRating(val id: String, val rating: Int) : SubsonicEndpoint() {
        override val path = "/rest/setRating"
        override val queryItems = mapOf("id" to id, "rating" to rating.toString())
    }

    data class Scrobble(
        val id: String,
        val time: Long? = null,
        val submission: Boolean = true,
    ) : SubsonicEndpoint() {
        override val path = "/rest/scrobble"
        override val queryItems = buildMap {
            put("id", id)
            put("submission", if (submission) "true" else "false")
            time?.let { put("time", it.toString()) }
        }
    }

    data object GetPlaylists : SubsonicEndpoint() {
        override val path = "/rest/getPlaylists"
        override val queryItems = emptyMap<String, String>()
    }

    data class GetPlaylist(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/getPlaylist"
        override val queryItems = mapOf("id" to id)
    }

    data class CreatePlaylist(
        val name: String,
        val songIds: List<String>,
    ) : SubsonicEndpoint() {
        override val path = "/rest/createPlaylist"
        override val queryItems = buildMap {
            put("name", name)
            // Note: multiple songId params are handled in SubsonicClient.buildUrl()
        }
        val songIdParams: List<Pair<String, String>>
            get() = songIds.map { "songId" to it }
    }

    data class UpdatePlaylist(
        val id: String,
        val name: String? = null,
        val comment: String? = null,
        val isPublic: Boolean? = null,
        val songIdsToAdd: List<String> = emptyList(),
        val songIndexesToRemove: List<Int> = emptyList(),
    ) : SubsonicEndpoint() {
        override val path = "/rest/updatePlaylist"
        override val queryItems = buildMap {
            put("playlistId", id)
            name?.let { put("name", it) }
            comment?.let { put("comment", it) }
            isPublic?.let { put("public", if (it) "true" else "false") }
        }
        val extraParams: List<Pair<String, String>>
            get() = songIdsToAdd.map { "songIdToAdd" to it } +
                    songIndexesToRemove.map { "songIndexToRemove" to it.toString() }
    }

    data class DeletePlaylist(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/deletePlaylist"
        override val queryItems = mapOf("id" to id)
    }

    data class Stream(
        val id: String,
        val maxBitRate: Int? = null,
        val format: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/stream"
        override val queryItems = buildMap {
            put("id", id)
            maxBitRate?.let { put("maxBitRate", it.toString()) }
            format?.let { put("format", it) }
        }
    }

    data class Download(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/download"
        override val queryItems = mapOf("id" to id)
    }

    data class GetCoverArt(val id: String, val imageSize: Int? = null) : SubsonicEndpoint() {
        override val path = "/rest/getCoverArt"
        override val queryItems = buildMap {
            put("id", id)
            imageSize?.let { put("size", it.toString()) }
        }
    }

    data class GetLyricsBySongId(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/getLyricsBySongId"
        override val queryItems = mapOf("id" to id)
    }

    data object GetInternetRadioStations : SubsonicEndpoint() {
        override val path = "/rest/getInternetRadioStations"
        override val queryItems = emptyMap<String, String>()
    }

    data class CreateInternetRadioStation(
        val streamUrl: String,
        val name: String,
        val homepageUrl: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/createInternetRadioStation"
        override val queryItems = buildMap {
            put("streamUrl", streamUrl)
            put("name", name)
            homepageUrl?.let { put("homepageUrl", it) }
        }
    }

    data class DeleteInternetRadioStation(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/deleteInternetRadioStation"
        override val queryItems = mapOf("id" to id)
    }

    data object GetPlayQueue : SubsonicEndpoint() {
        override val path = "/rest/getPlayQueue"
        override val queryItems = emptyMap<String, String>()
    }

    data class SavePlayQueue(
        val ids: List<String>,
        val current: String? = null,
        val position: Int? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/savePlayQueue"
        override val queryItems = buildMap {
            current?.let { put("current", it) }
            position?.let { put("position", it.toString()) }
        }
        val idParams: List<Pair<String, String>>
            get() = ids.map { "id" to it }
    }

    data object GetBookmarks : SubsonicEndpoint() {
        override val path = "/rest/getBookmarks"
        override val queryItems = emptyMap<String, String>()
    }

    data class CreateBookmark(
        val id: String,
        val position: Int,
        val comment: String? = null,
    ) : SubsonicEndpoint() {
        override val path = "/rest/createBookmark"
        override val queryItems = buildMap {
            put("id", id)
            put("position", position.toString())
            comment?.let { put("comment", it) }
        }
    }

    data class DeleteBookmark(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/deleteBookmark"
        override val queryItems = mapOf("id" to id)
    }

    data class GetSimilarSongs2(val id: String, val count: Int = 50) : SubsonicEndpoint() {
        override val path = "/rest/getSimilarSongs2"
        override val queryItems = mapOf("id" to id, "count" to count.toString())
    }

    data class GetTopSongs(val artist: String, val count: Int = 50) : SubsonicEndpoint() {
        override val path = "/rest/getTopSongs"
        override val queryItems = mapOf("artist" to artist, "count" to count.toString())
    }

    data object GetMusicFolders : SubsonicEndpoint() {
        override val path = "/rest/getMusicFolders"
        override val queryItems = emptyMap<String, String>()
    }

    data class GetIndexes(val musicFolderId: String? = null) : SubsonicEndpoint() {
        override val path = "/rest/getIndexes"
        override val queryItems = buildMap {
            musicFolderId?.let { put("musicFolderId", it) }
        }
    }

    data class GetMusicDirectory(val id: String) : SubsonicEndpoint() {
        override val path = "/rest/getMusicDirectory"
        override val queryItems = mapOf("id" to id)
    }
}
