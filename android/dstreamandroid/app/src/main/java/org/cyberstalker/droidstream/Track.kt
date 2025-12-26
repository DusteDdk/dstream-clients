package org.cyberstalker.droidstream

import android.net.Uri
import android.util.Log
import java.net.URI

class Track(
    val id: Int,
    val fileName: String,
    var artistName: String,
    var trackName: String,
    var albumName: String,
    var year: String,
    var duration: String,
    var codec: String,
    val baseUrl: String
        ) {
    val uri: Uri
    init {

        if(trackName.equals("Untitled") || trackName.equals("null")) {
            trackName = fileName.substringAfterLast('/').substringBeforeLast(".")
            artistName = fileName.substringAfter('/')
                .substringAfter('/')
                .substringBefore('/')

            albumName = "-"
            codec =  fileName.substringAfterLast('.')
            year = "-"
            duration = "-"
        }

        uri = Uri.parse("https://${baseUrl}$fileName")

        //Log.v("Track", "Name: $trackName Artist: $artistName Uri: $uri Codec: $codec")
    }
}