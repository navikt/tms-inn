import kotlinx.html.DIV
import kotlinx.html.table
import kotlinx.html.thead

class VarselStatus(
    val antallFerdigstilteVarsler: Int,
    val antallLesteVarsler: Int,
    val eksterneVarslerStatus: EksterneVarslerStatus
)

class EksterneVarslerStatus(
    val antallBestilt: Int,
    val antallSendt: Int,
    val antallFeilet: Int
)

enum class EksternStatus(val priority: Int) {
    bestilt(1), feilet(2), sendt(3);

    companion object {
        fun resolve(old: String?, new: String): String =
            when {
                old == null -> new
                valueOf(old).priority > valueOf(new).priority -> old
                else -> new
            }
    }
}