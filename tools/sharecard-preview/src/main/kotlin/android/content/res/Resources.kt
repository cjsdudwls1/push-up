package android.content.res

import java.util.Locale

/** The resource table the card reads, loaded from the real strings.xml. */
class Resources(private val strings: Map<Int, String>) {

    fun getString(id: Int): String =
        strings[id] ?: error("No string for id $id — regenerate the preview resource table.")

    fun getString(id: Int, vararg formatArgs: Any): String =
        String.format(Locale.KOREA, getString(id), *formatArgs)
}
