package uz.akbar.namozvaqti.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.GridLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import uz.akbar.namozvaqti.R
import uz.akbar.namozvaqti.databinding.ItemMonthCellBinding
import uz.akbar.namozvaqti.databinding.ItemMonthDayBinding

class MonthAdapter : RecyclerView.Adapter<MonthAdapter.VH>() {

    /** One prayer entry within a day card. */
    data class Cell(val iconRes: Int, val name: String, val time: String)

    /** title e.g. "Dush · 16-iyun"; cells are the day's prayers in order. */
    data class DayRow(
        val title: String,
        val cells: List<Cell>,
        val isToday: Boolean = false,
    )

    private var items: List<DayRow> = emptyList()

    fun submit(list: List<DayRow>) {
        items = list
        notifyDataSetChanged()
    }

    /** Adapter position of today's card, or -1 if not present. */
    fun indexOfToday(): Int = items.indexOfFirst { it.isToday }

    inner class VH(val b: ItemMonthDayBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemMonthDayBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val row = items[position]
        holder.b.tvDay.text = if (row.isToday) "${row.title} · Today" else row.title

        // Highlight today's card with the same accent glass as the next-prayer card.
        holder.b.card.setBackgroundResource(
            if (row.isToday) R.drawable.glass_hero else R.drawable.glass_row,
        )

        val grid = holder.b.grid
        grid.removeAllViews()
        val inflater = LayoutInflater.from(grid.context)
        val iconTint = ContextCompat.getColor(grid.context, R.color.text_secondary)

        for (c in row.cells) {
            val cell = ItemMonthCellBinding.inflate(inflater, grid, false)
            cell.cellIcon.setImageResource(c.iconRes)
            cell.cellIcon.setColorFilter(iconTint)
            cell.cellName.text = c.name
            cell.cellTime.text = c.time

            val lp = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
            }
            grid.addView(cell.root, lp)
        }
    }

    override fun getItemCount(): Int = items.size
}
