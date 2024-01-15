package no.nav.tms.brannslukning

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.tms.brannslukning.common.gui.gui

fun main() {
    embeddedServer(
        Netty,
        port = System.getenv("PORT")?.toInt() ?: 8081,
        module = { this.gui() }).start(
        wait = true
    )
}
