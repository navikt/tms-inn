package no.nav.tms.brannslukning.common.gui

import io.ktor.http.*
import kotlinx.html.*

fun FIELDSET.labelAnDescribe(
    formInputField: FormInputField,
    inputBuilder: LABEL.() -> Unit
) {
    label {
        id = formInputField.labelId
        htmlFor = formInputField.elementId
        +formInputField.labelText

        formInputField.describe?.also {
            p(classes = "input-description") {
                id = formInputField.descriptionId!!
                +formInputField.describe
            }
        }
        inputBuilder()
    }

}

enum class FormInputField(val htmlName: String, val labelText: String, val describe: String? = null) {
    TITLE(htmlName = "title", labelText = "Tittel", describe = "Skriv en tittel på varselet (for intern bruk)"),
    DESCRIPTION(
        htmlName = "description",
        labelText = "Beskrivelse",
        describe = "Skriv inn hva som har skjedd i korte trekk (Kun til internt bruk)"
    ),
    SMS_EPOST_TEKST(
        htmlName = "ekstern-text",
        labelText = "Varsel på SMS og/eller e-post",
        describe = "Husk: ikke sensitive opplysninger som ytelse etc."
    ),
    LINK(
        htmlName = "url",
        labelText = "Lenke i beskjed på Min side/varselbjella (ikke obligatorisk)",
        describe = "Lim inn en lenke for mer informasjon (f.eks. en annen innlogget side eller nyhetsartikkel)."
    ),
    MIN_SIDE_TEXT(
        htmlName = "beskjed-text",
        labelText = "Beskjed på min side",
        describe = "Skriv en tekst som vises på Min side og i varselbjella (maks 500 tegn)"
    ),
    IDENT_FILE(
        htmlName = "ident",
        labelText = "Last opp en csv-fil med personnumre"
    );


    val elementId = "$htmlName-input"
    val labelId = "$elementId-label"
    val descriptionId = describe?.let { "$elementId-description" }
    val describedBy =
        describe?.let { "${descriptionId},${labelId}" } ?: labelId

    companion object {

        fun INPUT.setAttrs(field: FormInputField) {
            attributes["aria-describedby"] = field.describedBy
            id = field.elementId
            name = field.htmlName
        }

        fun TEXTAREA.setAttrs(field: FormInputField) {
            attributes["aria-describedby"] = field.describedBy
            id = field.elementId
            name = field.htmlName
        }

        fun Parameters.getFormFieldValue(field: FormInputField)=
            this[field.htmlName]?:throw MissingFormFieldException(field)
    }
}

//TODO Statuspages
class MissingFormFieldException(val formField: FormInputField): IllegalArgumentException()