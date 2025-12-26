package org.cyberstalker.droidstream

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class TrackAdapter(
    private val mList: List<Track>,
    private val onTrackSelected: (track: Track) -> Unit
) : RecyclerView.Adapter<TrackAdapter.ViewHolder>() {

    // create new views
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // inflates the card_view_design view
        // that is used to hold list item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.trackitem, parent, false)

        return ViewHolder(view)
    }



    // binds the list items to a view
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        val trackInfo = mList[position]

        holder.txtTrackName.text = trackInfo.trackName
        holder.txtArtistName.text = trackInfo.artistName
        holder.txtAlbumName.text = trackInfo.albumName
        holder.txtCodec.text = trackInfo.codec
        holder.txtYear.text = trackInfo.year
        holder.txtDuration.text = trackInfo.duration

        holder.card.isClickable = true
        holder.card.setOnClickListener {onTrackSelected(trackInfo)}

    }

    // return the number of the items in the list
    override fun getItemCount(): Int {
        return mList.size
    }

    // Holds the views for adding it to image and text
    class ViewHolder(ItemView: View) : RecyclerView.ViewHolder(ItemView) {

        val txtTrackName: TextView = itemView.findViewById(R.id.txtTrackTitle)
        val txtArtistName: TextView = itemView.findViewById(R.id.txtArtist)
        val txtAlbumName: TextView = itemView.findViewById(R.id.txtAlbum)
        val txtCodec: TextView = itemView.findViewById(R.id.txtCodec)
        val txtYear: TextView = itemView.findViewById(R.id.txtYear)
        val txtDuration: TextView = itemView.findViewById(R.id.txtDuration)

        val card: CardView = itemView.findViewById(R.id.trackCard)


    }
}