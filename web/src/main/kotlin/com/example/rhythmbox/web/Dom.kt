package com.example.rhythmbox.web

import kotlinx.browser.document
import org.khronos.webgl.Int8Array
import org.khronos.webgl.set
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLOptionElement
import org.w3c.dom.HTMLSelectElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/** [id] の要素を型付きで取る。無ければ例外（画面の組み立て漏れを早く気付けるように）。 */
inline fun <reified T : HTMLElement> byId(id: String): T =
    document.getElementById(id) as? T ?: error("要素が見つからない: $id")

fun HTMLSelectElement.setOptions(options: List<Pair<String, String>>, selectedValue: String? = null) {
    innerHTML = ""
    for ((value, label) in options) {
        val option = document.createElement("option") as HTMLOptionElement
        option.value = value
        option.textContent = label
        appendChild(option)
    }
    if (selectedValue != null) value = selectedValue
}

fun showScreen(name: String) {
    val screens = listOf("song", "appreciate", "help")
    for (screen in screens) {
        byId<HTMLElement>("screen-$screen").classList.toggle("active", screen == name)
    }
    val nav = byId<HTMLElement>("nav")
    val buttons = nav.querySelectorAll(".nav-btn")
    for (i in 0 until buttons.length) {
        val button = buttons.item(i) as HTMLElement
        button.classList.toggle("active", button.getAttribute("data-screen") == name)
    }
}

/** バイト列をブラウザで参照できる一時 URL にする（音声の再生・ダウンロード用）。 */
fun blobUrl(bytes: ByteArray, mimeType: String = "audio/mpeg"): String {
    val array = Int8Array(bytes.size)
    for (i in bytes.indices) array[i] = bytes[i]
    val blob = Blob(arrayOf(array), BlobPropertyBag(type = mimeType))
    return URL.createObjectURL(blob)
}
