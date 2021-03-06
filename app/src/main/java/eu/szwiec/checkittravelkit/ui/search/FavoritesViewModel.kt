package eu.szwiec.checkittravelkit.ui.search

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.databinding.ObservableArrayList
import androidx.lifecycle.Transformations
import androidx.lifecycle.ViewModel
import eu.szwiec.checkittravelkit.BR
import eu.szwiec.checkittravelkit.R
import eu.szwiec.checkittravelkit.prefs.Preferences
import me.tatarka.bindingcollectionadapter2.collections.DiffObservableList
import me.tatarka.bindingcollectionadapter2.itembindings.OnItemBindClass

class FavoritesViewModel(context: Context, preferences: Preferences) : ViewModel() {

    private val header: Drawable = context.getDrawable(R.drawable.ic_favorite_border_white_24dp)
    private val footer: Drawable = context.getDrawable(android.R.color.transparent)

    val itemBind: OnItemBindClass<Any> = OnItemBindClass<Any>()
            .map(FavoriteCountry::class.java, BR.item, R.layout.row_fave_country)
            .map(Drawable::class.java, BR.item, R.layout.row_fave_icon)

    val items: DiffObservableList<Any> = DiffObservableList(object : DiffObservableList.Callback<Any> {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return areContentsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return if (oldItem is FavoriteCountry && newItem is FavoriteCountry) {
                oldItem.name == newItem.name
            } else {
                true
            }
        }
    })

    val favorites = Transformations.map(preferences.getFavoritesLD()) { favorites ->
        val newItems = getNewItems(favorites, header, footer)
        items.update(newItems)
    }!!

    fun getNewItems(favorites: Set<String>, header: Any, footer: Any): List<Any> {
        val items = ObservableArrayList<Any>()
        val favorites = favorites.map { FavoriteCountry(it) }.sortedBy { it.name }

        if (favorites.isNotEmpty()) {
            items.add(header)
            items.addAll(favorites)
            items.add(footer)
        }

        return items
    }
}
