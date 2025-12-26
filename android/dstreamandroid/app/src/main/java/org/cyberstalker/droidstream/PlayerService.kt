package org.cyberstalker.droidstream

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import android.content.pm.ServiceInfo
import androidx.annotation.RequiresApi
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL


@Entity
data class DbTrack(
    @PrimaryKey(autoGenerate = true)  val id: Long = 0,
    @ColumnInfo(name = "uri") val onlineUri: String,
    @ColumnInfo(name = "offlineUri") val offlineUri: String,
    @ColumnInfo(name = "plays") val numPlays: Int = 1,
)

@Dao
interface TrackDao {
    @Query("SELECT * FROM dbtrack")
    fun getAll(): List<DbTrack>

    @Query("SELECT * FROM dbtrack WHERE uri = :uri")
    fun find(uri: String): DbTrack

    @Insert
    fun insert(dbTrack: DbTrack): Long

    @Query("UPDATE dbtrack SET plays = plays + 1 WHERE uri = :uri")
    fun play(uri: String)

    @Delete
    fun delete(track: DbTrack)
}

@Database(entities = [DbTrack::class], version = 6)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
}

public class PlayerService() : Service()  {
    private var player: MediaPlayer? = null
    private var playlist = ArrayList<Uri>(50);
    private var authHeader: HashMap<String, String> = HashMap(1)



    private lateinit var db: AppDatabase
    private lateinit var trackDao: TrackDao

    private var download = true;

    private val CHANNEL_ID: String
        get() = "dStreamServiceChannel"

    lateinit var mediaSession: MediaSession

    private var pbs = PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0L, 1F)

    private lateinit var notification: Notification
    init {

    }

    private var currentFilename = ""
    override fun onCreate() {
        super.onCreate()
        db = Room.databaseBuilder(
            this,
            AppDatabase::class.java, "trackDb"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
        trackDao = db.trackDao()
    }

    fun setList(extras: Bundle) {
        val auth = extras.getString("auth")
        download = extras.getBoolean("download")

        authHeader.clear()
        if (auth != null) {
            authHeader.put("Authorization", auth)
        }

        playlist.clear()

        val numTracks = extras.getInt("numTracks")

        var uriStr: String?

        for(i in 0..(numTracks!!-1)) {
            uriStr = extras.getString("track_${i}")
            if(uriStr != null) {
                playlist.add( Uri.parse(uriStr))
            }
        }


        if(player==null && !locked) {
            playNextItem()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if(!this::notification.isInitialized) {
            doStartForeground()
        }

        val extras = intent?.extras


        val command = extras?.getString("command")

        if(command!=null) {
            when(command) {
                "setList" -> setList(extras)
                "pause" -> pause()
                "resume"-> resume()
                "next" -> playNextItem()
                "stop" -> stopService()
            }
        }

        return START_STICKY
    }

    private fun pause() {
        player?.pause()
        state("paused")
        var actions = PlaybackState.ACTION_PLAY or PlaybackState.ACTION_STOP
        if(playlist.size > 0) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        pbs.setState(PlaybackState.STATE_PAUSED, 0L, 1.0F)
            .setActions(actions)

        mediaSession.setPlaybackState(pbs.build())


    }

    private fun resume() {
        player?.start()

        state("playing") { add ->
            add("file", currentFilename)
        }
        var actions = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP
        if(playlist.size > 0) {
            actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
        }
        var pos = 0L;
        if(player != null) {
            pos = player?.currentPosition!!.toLong()
        }
        pbs.setState(PlaybackState.STATE_PLAYING, pos, 1.0F)
            .setActions(actions)

        mediaSession.setPlaybackState(pbs.build())
    }

    private fun stopPlayback() {

        if(player != null) {
            player!!.stop()
            player!!.reset()
            player!!.release()
            player = null
        }

        currentFilename = ""
        state("stopped")
        pbs.setState(PlaybackState.STATE_STOPPED, 0L, 1.0F)
            .setActions(0)
        mediaSession.setPlaybackState(pbs.build())
        mediaSession.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, "").build())


    }

    private fun stopService() {
        stopPlayback()
        stopForeground(STOP_FOREGROUND_DETACH)
//        stopForeground(false)
        stopSelf()
    }

    private fun doStartForeground() {

        // Create media session token
        mediaSession = MediaSession(this, "dsStream")

        mediaSession.setCallback( object: MediaSession.Callback() {
            override fun onPlay() {
                super.onPlay()
                Log.v("PlayerService", "onPlay")
            }

            override fun onPause() {
                super.onPause()
                pause()
                Log.v("PlayerService", "onPause")
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                playNextItem()
                Log.v("PlayerService", "onSkipTonext")

            }
        })


        // Create notification channel
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(CHANNEL_ID, "dStream player service",
                NotificationManager.IMPORTANCE_NONE)
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }

        // Create notification
        notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("dStream")
            .setSmallIcon(R.mipmap.ic_launcher_round)
            .setLargeIcon(BitmapFactory.decodeResource(resources, R.drawable.ic_launcher))
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken))
            .setOngoing(true)
            .build()

            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

    }

    private var locked=false;

    private fun state(
        state: String,
        more: ( (String, String)->Unit ) -> Unit = {},
    ) {


        val intent = Intent("DSTreamUpdate")
        intent.putExtra("state", state)

        val addMore: (String, String) -> Unit = { key: String, value: String ->  intent.putExtra(key, value) };


        more(addMore)
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)

    }



    fun downloadFileToDStreamMusic(fileName: String, fileUri: String): Uri? {
        try {
            // Define the directory and file details
            val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/dStreamMusic"
            val resolver = this.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName) // File name
                put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream") // File type
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath) // Directory path
            }

            // Insert into MediaStore
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create file in MediaStore")

            // Write the file content to the output stream
            resolver.openOutputStream(uri).use { outputStream ->
                val url = URL(fileUri)
                val connection = url.openConnection() as HttpURLConnection
                authHeader.forEach { (key, value) ->
                    connection.setRequestProperty(key, value)
                }

                connection.getInputStream().use { inputStream ->
                    inputStream.copyTo(outputStream!!)
                }
            }

            Log.d("Download", "File downloaded $fileName")
            return uri
        } catch (e: Exception) {
            Log.e("Download", "Error downloading file", e)
            return null
        }
    }

    private fun weHaveFile(context: Context, uri: Uri?): Boolean {
        if(uri==null) {
            return false;
        }

        var haveFile = try {
            // Use ContentResolver to query the Uri
            context.contentResolver.openInputStream(uri)?.use {
                true
            } ?: false
        } catch (e: Exception) {
            false
        }
        return haveFile;
    }

    private fun getRealUri(uri: Uri,
                           onReady: ( String ) -> Unit = {},
                           ) {

        Log.v("PlayerService", "Should play: $uri")

        val track = trackDao.find(uri.toString());

        if(track == null) {

            var fn = uri.lastPathSegment;

            if(fn !== null && download) {
                CoroutineScope(Dispatchers.IO).launch {
                    Log.d("PlayerService", "Downloading...")
                    state("downloading") { add ->
                        add("file", uri.toString())
                    }
                    val savedFileName = downloadFileToDStreamMusic(fn, uri.toString())
                    withContext(Dispatchers.Main) {
                        if (savedFileName != null) {
                            Log.v(
                                "PlayerService",
                                "Download successful, URI: $savedFileName"
                            )

                                //Toast.makeText(context, "Download complete: $fileName", Toast.LENGTH_SHORT).show()
                                trackDao.insert(
                                    DbTrack(
                                        onlineUri = uri.toString(),
                                        offlineUri = savedFileName.toString()
                                    )
                                );
                                Log.v("PlayerService", "Added $uri as $savedFileName")

                                onReady(savedFileName.toString());
                        } else {
                            Log.e("PlayerService", "Download failed")
                            onReady(uri.toString());
                        }
                    }
                }
            } else {
                Log.v("PlayerService", "Download disabled, no local file, streaming.")
                onReady(uri.toString());
            }

        } else {
            Log.v("PlayerService", "Found in database")
            val attrCtx = createAttributionContext("audioPlayback")
            if(weHaveFile(attrCtx, Uri.parse(track.offlineUri))) {
                Log.v("PlayerService", "File exists, start playing offline URI: ${track.offlineUri}")
                onReady(track.offlineUri);
            } else {
                Log.v("PlayerService", "${track.offlineUri} was deleted")
                trackDao.delete(track);
                getRealUri(uri, onReady);
            }
        }

    }

    private fun playNextItem() {

        stopPlayback()

        if(locked) {
            Log.v("PlayerService", "playNextItem: Not doing anything, is locked.")
            return;
        }


        if(playlist.size != 0) {
            locked=true;

            val onlineUri = playlist[0]
            playlist.removeAt(0)
            Log.v("PlayerService", "PlayNextItem: Fetch begin.")

            getRealUri(onlineUri) { fileName ->
                stopPlayback()

                currentFilename = onlineUri.toString()
                state("playing") { add ->
                    add("file", onlineUri.toString())
                }

                mediaSession.setMetadata(MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, onlineUri.toString()).build())
                val attrCtxA = createAttributionContext("audioPlayback")
                player = MediaPlayer(attrCtxA).apply {
                    val attrCtxB = createAttributionContext("audioPlayback")
                    setDataSource(attrCtxB, Uri.parse(fileName), authHeader)
                    prepare() // Use prepareAsync() for large files

                    Log.v("PlayerService", "Player prepared with $fileName")
                    var actions = PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_STOP
                    if (playlist.size > 0) {
                        actions = actions or PlaybackState.ACTION_SKIP_TO_NEXT
                    }
                    pbs.setState(PlaybackState.STATE_PLAYING, 0L, 1.0F)
                        .setActions(actions)
                    mediaSession.setPlaybackState(pbs.build())

                    locked = false;

                    setOnCompletionListener {
                        Log.v("PlayerService", "onCompletionListener")
                        playNextItem()
                    }

                    start()
                }


            }

        }


    }

    override fun onDestroy() {
        super.onDestroy()
        stopService()
    }

    override fun onBind(p0: Intent?): IBinder? {
        return null
    }
}
