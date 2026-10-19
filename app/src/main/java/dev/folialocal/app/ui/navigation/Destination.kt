package dev.folialocal.app.ui.navigation

sealed interface Destination {
    data object Songs : Destination
    data object Albums : Destination
    data object Artists : Destination
    data object Folders : Destination
    data object Search : Destination
    data object Settings : Destination
    data class AlbumDetail(val key: String) : Destination
    data class ArtistDetail(val name: String) : Destination
    data class FolderDetail(val path: String) : Destination
}
