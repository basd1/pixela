package dev.bastian.pixela.ui

import android.content.Context
import dev.bastian.pixela.R

/** Localized texts that ViewModels need to put into data (default names), kept out of the ViewModels' Android deps. */
interface Strings {
    fun layerName(number: Int): String
    fun newProjectName(number: Int): String
    fun copyOf(name: String): String
}

class AndroidStrings(private val context: Context) : Strings {
    override fun layerName(number: Int): String = context.getString(R.string.layer_default_name, number)
    override fun newProjectName(number: Int): String = context.getString(R.string.project_default_name, number)
    override fun copyOf(name: String): String = context.getString(R.string.copy_of, name)
}
