import default.DependencyGroup

object TmsVarselBuilder: DependencyGroup {
    override val groupId get() = "no.nav.tms.varsel"
    override val version get() = "1.0.2"

    val kotlinBuilder get() = dependency("kotlin-builder")
}
