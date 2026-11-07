package com.example.rhythmbox.web

import kotlinx.browser.document
import kotlinx.browser.window
import org.w3c.dom.HTMLElement

fun main() {
    window.onload = {
        val nav = byId<HTMLElement>("nav")
        val buttons = nav.querySelectorAll(".nav-btn")
        for (i in 0 until buttons.length) {
            val button = buttons.item(i) as HTMLElement
            button.addEventListener("click", {
                val screen = button.getAttribute("data-screen") ?: return@addEventListener
                showScreen(screen)
            })
        }

        SongScreen.init()
        AppreciationScreen.init()
        HelpContent.render()
        Unit
    }
}
