package ai.wakil.namozvaqti.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import ai.wakil.namozvaqti.R
import ai.wakil.namozvaqti.databinding.ItemPrayerBinding

class TodayAdapter : RecyclerView.Adapter<TodayAdapter.VH>() {

    data class Row(val name: String, val time: String, val isNext: Boolean, val iconRes: Int)

    private var items: List<Row> = emptyList()

    fun submit(list: List<Row>) {
        items = list
        notifyDataSetChanged()
    }

    inner class VH(val b: ItemPrayerBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemPrayerBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        val ctx = holder.b.root.context
        holder.b.tvName.text = row.name
        holder.b.tvTime.text = row.time
        holder.b.ivIcon.setImageResource(row.iconRes)

        val tint = ContextCompat.getColor(
            ctx, if (row.isNext) R.color.accent else R.color.text_secondary,
        )
        holder.b.ivIcon.setColorFilter(tint)

        if (row.isNext) {
            holder.b.row.setBackgroundResource(R.drawable.glass_hero)
            val accent = ContextCompat.getColor(ctx, R.color.accent)
            holder.b.tvName.setTextColor(accent)
            holder.b.tvTime.setTextColor(accent)
        } else {
            holder.b.row.setBackgroundResource(R.drawable.glass_row)
            val primary = ContextCompat.getColor(ctx, R.color.text_primary)
            holder.b.tvName.setTextColor(primary)
            holder.b.tvTime.setTextColor(primary)
        }
    }

    override fun getItemCount(): Int = items.size
}
