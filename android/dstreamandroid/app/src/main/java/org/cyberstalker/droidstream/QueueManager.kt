package org.cyberstalker.droidstream

class QueueManager ( private val onChange:(ArrayList<Track>, refresh: Boolean)->Unit ){

    var queue = ArrayList<Track>()

    private fun find(track: Track): Track? {
        return queue.find { it.id == track.id }
    }

    fun addTrack(track: Track) {
        if( find(track) == null) {
            queue.add(track)
        }
        onChange(queue, true)
    }

    fun removeTrack(track: Track) {
        queue.remove(find(track))
        onChange(queue, true)
    }

    fun clear() {
        queue.clear()
        onChange(queue, true)
    }

    fun updateNowPlaying(file: String?) {

        //Find it in current playlist
        val track = queue.find { it.uri.toString() == file }
        if(track != null) {
            queue.remove(track)
        }

        onChange(queue, false)
    }

}
