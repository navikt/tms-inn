import default.DependencyGroup

object TmsVarselBuilder: DependencyGroup {
    override val groupId get() = "no.nav.tms.varsel"
    override val version get() = "1.0.0"

    val kotlinBuilder get() = dependency("kotlin-builder")
    val javabuilder get() = dependency("java-builder")
}
