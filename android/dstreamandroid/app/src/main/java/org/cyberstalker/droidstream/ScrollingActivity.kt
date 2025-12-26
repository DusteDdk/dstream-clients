package org.cyberstalker.droidstream

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.cyberstalker.droidstream.databinding.ActivityScrollingBinding
import org.json.JSONArray
import java.io.BufferedReader
import java.lang.Math.floor
import java.net.URL
import java.util.*
import kotlin.collections.ArrayList


enum class MainState {
    SEARCH, QUEUE, CFG
}

enum class Command {
    PLAY_LIST, PAUSE, RESUME, NEXT, STOP
}

class ScrollingActivity : AppCompatActivity() {
    private lateinit var binding: ActivityScrollingBinding
    private var searchResult = ArrayList<Track>()
    private lateinit var basicAuth: String
    private lateinit var userPass: String
    private lateinit var baseUrl: String

    private lateinit var prefs :SharedPreferences

    fun clearSearch(view: View) {
        var edit = findViewById(R.id.editTxt) as EditText
        if(state == MainState.SEARCH) {
            edit.text.clear()

            edit.requestFocus()
            val imm  = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);


        } else if(state == MainState.QUEUE) {
            if(queueManager.queue.size == 0) {
                control(Command.STOP)
            }
            queueManager.clear()
        }
    }

    var state = MainState.SEARCH

    var playbackState: String = "stopped"

    var download: Boolean = false;

    fun modeChange(view: View) {
        val modeBtn: Button = findViewById(R.id.btnMode)
        val editText: EditText = findViewById(R.id.editTxt)
        val clearBtn: Button = findViewById(R.id.btnClear)
        val addAllBtn: Button = findViewById(R.id.btnAddAll)

        val txtBaseUrl: EditText = findViewById(R.id.txtBaseUrl)
        val txtAuthString: EditText = findViewById(R.id.txtAuthString)
        val chkDownload: CheckBox = findViewById(R.id.chkDownload)
        val txtNowPlaying: TextView = findViewById(R.id.txtNowPlaying)


        when(state) {
            MainState.QUEUE -> {
                state = MainState.SEARCH
                modeBtn.text = "LIST"
                editText.visibility = View.VISIBLE
                clearBtn.text = "X"
                setTracks(searchResult)
                if(searchResult.size > 0) {
                    addAllBtn.text="+All"
                }

            }
            MainState.SEARCH -> {
                if(editText.text.toString() == "cfg" && queueManager.queue.isEmpty()) {
                    state = MainState.CFG
                    clearBtn.visibility = View.INVISIBLE
                    editText.visibility = View.INVISIBLE
                    modeBtn.text = "SAVE"
                    txtBaseUrl.visibility = View.VISIBLE
                    txtBaseUrl.setText(baseUrl, TextView.BufferType.EDITABLE);
                    txtAuthString.visibility = View.VISIBLE
                    txtAuthString.setText(userPass, TextView.BufferType.EDITABLE);
                    chkDownload.visibility = View.VISIBLE
                    chkDownload.setChecked(download)
                } else {
                    state = MainState.QUEUE
                    modeBtn.text = "FIND"
                    editText.visibility = View.INVISIBLE
                    setTracks(queueManager.queue)
                    addAllBtn.text = ""
                }
            }
            MainState.CFG -> {
                // Save the config
                clearBtn.visibility = View.VISIBLE
                editText.visibility = View.VISIBLE

                prefs.edit()
                    .putString("url", txtBaseUrl.text.toString())
                    .putString("auth", txtAuthString.text.toString())
                    .putBoolean("download", chkDownload.isChecked)
                    .commit()

                txtBaseUrl.visibility = View.GONE
                txtAuthString.visibility = View.GONE
                chkDownload.visibility = View.GONE

                readPrefs()
                // return to normal
                state = MainState.QUEUE
                modeChange(view)
                clearSearch(view)
                txtNowPlaying.text = "Config Saved."
            }
        }
    }
    fun btnPause(view: View) {
        if(playbackState == "playing") {
            control(Command.PAUSE)
        } else if(playbackState == "paused" ) {
            control(Command.RESUME)
        }
    }

    fun btnNext(view: View) {
        if(playbackState !== "fetching") {
            control(Command.NEXT)
        }
    }

    fun btnAddAll(view: View) {
        if(state == MainState.SEARCH) {
            if(searchResult.size > 0) {
                searchResult.forEach { track->
                    queueManager.addTrack(track)
                }
            }
        }
    }


    val queueManager = QueueManager() {newList, refresh ->
        val btnNext: Button = findViewById(R.id.btnNext)
        if(state==MainState.QUEUE) {
            setTracks(newList)
        }
        if(refresh) {
            control(Command.PLAY_LIST)
        }
        if(newList.size > 0) {
            btnNext.text = "next"
        } else {
            if(playbackState == "playing") {
                btnNext.text = "Stop"
            } else {
                btnNext.text = ""
            }
        }
    }
    lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityScrollingBinding.inflate(layoutInflater)
        setContentView(binding.root)


        val txtNowPlaying: TextView = findViewById(R.id.txtNowPlaying)

        prefs = getPreferences(Context.MODE_PRIVATE)

        readPrefs()

        // Get the recyclerView
        val recView = findViewById<RecyclerView>(R.id.recView)

        recView.layoutManager = LinearLayoutManager(this)

        val addAllBtn: Button = findViewById(R.id.btnAddAll)
        val edit = findViewById(R.id.editTxt) as EditText

        fun fetch(url: String) {
            Thread {

                try {
                    val api = URL(url)
                    val uc = api.openConnection()
                    uc.setRequestProperty("Authorization", basicAuth)
                    uc.connect()
                    val ins = uc.getInputStream()
                    val apiResponse = ins.bufferedReader().use(BufferedReader::readText)
                    ins.close()

                    val json = JSONArray(apiResponse)
                    val max = if (json.length() > 100) 100 else json.length()

                    searchResult.clear()
                    for (i in 0 until max) {
                        val track = json.getJSONObject(i)

                        val yearInt = track.optInt("year", -1)
                        val durationDouble = track.optDouble("duration", -1.0)

                        val yearString: String = if (yearInt > -1) yearInt.toString(10) else ""

                        var durationString = ""

                        if (durationDouble > 0.0) {
                            val hours = floor(durationDouble / 3600.0).toInt()
                            val minutes = ((durationDouble % 3600) / 60).toInt()
                            val seconds = (durationDouble % 60).toInt()

                            val hoursString = if (hours > 0) "$hours:" else ""
                            val minutesString =
                                if (hours > 0 && minutes < 10) "0$minutes" else "$minutes"
                            val secondsString =
                                if (minutes > 0 && seconds < 10) "0$seconds" else "$seconds"
                            durationString = "$hoursString$minutesString:$secondsString"
                        }

                        searchResult.add(
                            Track(
                                track.getInt("id"),
                                track.getString("file"),
                                track.getString("artistName"),
                                track.getString("title"),
                                track.getString("albumName"),
                                yearString,
                                durationString,
                                track.getString("codec"),
                                baseUrl)
                        )
                    }

                    runOnUiThread {
                        setTracks(searchResult)
                        if (searchResult.size > 0) {
                            addAllBtn.text = "+All"
                        } else {
                            addAllBtn.text = ""
                        }
                    }

                } catch(e: Exception) {
                    txtNowPlaying.text = "Error connecting, configured? (type cfg, click LIST)"
                }
            }.start()
        }


        edit.doOnTextChanged { text, _, _, _ ->
                val newUrl = "https://${baseUrl}/tracks.json?q=" + text
                fetch(newUrl)
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            mMessageReceiver, IntentFilter("DSTreamUpdate")
        );
    }

    private fun readPrefs() {
        baseUrl = prefs.getString("url", "DOMAIN_NAME_NOT_SET").toString()
        userPass = prefs.getString("auth", "AUTH_NOT_SET").toString()
        download = prefs.getBoolean("download", false)

        if (userPass.isNotEmpty() && userPass != "AUTH_NOT_SET") {
            basicAuth = "Basic " + Base64.getEncoder().encodeToString(userPass.toByteArray())
        } else {
            basicAuth = ""

            val txtNowPlaying: TextView = findViewById(R.id.txtNowPlaying)
            txtNowPlaying.text = "Not configured: Type cfg and press LIST"
        }

    }

    private fun setTracks(tracks: ArrayList<Track>) {
        val copy = ArrayList(tracks)
        val recView = findViewById<RecyclerView>(R.id.recView)
        recView.adapter = TrackAdapter(copy) { track ->
            selectedTrack(track)
        }
    }

    private fun selectedTrack(track: Track) {

        when(state) {
            MainState.SEARCH -> queueManager.addTrack(track)
            MainState.QUEUE -> queueManager.removeTrack(track)
            MainState.CFG-> Unit
        }

    }

    private fun control(command: Command) {
        val intent = Intent(this, PlayerService::class.java)

        when(command) {
            Command.PLAY_LIST -> {
                intent.putExtra("command", "setList")
                intent.putExtra("auth", basicAuth)
                intent.putExtra("numTracks", queueManager.queue.size)
                intent.putExtra("download", download)
                var idx = 0
                queueManager.queue.forEach { track ->
                    intent.putExtra("track_${idx}", track.uri.toString())
                    idx += 1
                }

            }
            Command.PAUSE -> intent.putExtra("command", "pause")
            Command.RESUME -> intent.putExtra("command", "resume")
            Command.NEXT -> intent.putExtra("command", "next")
            Command.STOP -> intent.putExtra("command", "stop")

        }

        applicationContext.startForegroundService(intent)
    }

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context?, intent: Intent) {

            val txtNowPlaying: TextView = findViewById(R.id.txtNowPlaying)
            val btnPause: Button = findViewById(R.id.btnPause)
            val btnNext: Button = findViewById(R.id.btnNext)

            val state = intent.getStringExtra("state")
            when(state) {
                "stopped"-> {
                    btnPause.text=""
                    btnNext.text = ""
                    playbackState = "stopped"
                    txtNowPlaying.text="[not playing]"

                }
                "paused"-> {
                    btnPause.text="play"
                    playbackState = "paused"
                    txtNowPlaying.text="[paused] ${txtNowPlaying.text}"
                }
                "downloading"->{
                    btnPause.text=""
                    playbackState = "downloading"
                    val file = intent.getStringExtra("file")
                    txtNowPlaying.text=">${file} (downloading)"
                }

                "playing"->{
                    btnPause.text="pause"
                    playbackState = "playing"
                    val file = intent.getStringExtra("file")
                    queueManager.updateNowPlaying(file)
                    txtNowPlaying.text=">${file}"
                }

            }

        }
    }
}