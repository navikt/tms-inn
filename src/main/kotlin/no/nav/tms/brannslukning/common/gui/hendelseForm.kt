package no.nav.tms.brannslukning.common.gui

import no.nav.tms.brannslukning.common.gui.FormInputField.Companion.setAttrs
import io.ktor.http.*
import kotlinx.html.*

fun MAIN.hendelseForm(tmpHendelse: TmpHendelse?, postEndpoint: String) {
    form {
        action = postEndpoint
        method = FormMethod.post
        encType = FormEncType.multipartFormData
        fieldSet {
            legend {
                +"Varsel"
            }
            labelAnDescribe(FormInputField.TITLE) { ->
                input {
                    setAttrs(FormInputField.TITLE)
                    required = true
                    maxLength = "50"
                    tmpHendelse?.let {
                        value = tmpHendelse.title
                    }
                }
            }
            labelAnDescribe(FormInputField.DESCRIPTION) {
                textArea {
                    setAttrs(FormInputField.DESCRIPTION)
                    maxLength = "300"
                    tmpHendelse?.let {
                        text(it.description)
                    }
                }
            }

            labelAnDescribe(
                FormInputField.MIN_SIDE_TEXT
            ) {
                textArea {
                    setAttrs(FormInputField.MIN_SIDE_TEXT)
                    required = true
                    maxLength = "150"
                    minLength = "50"
                    tmpHendelse?.let {
                        text(it.varseltekst)
                    }
                }
            }

            labelAnDescribe(FormInputField.LINK) {
                input {
                    setAttrs(FormInputField.LINK)
                    type = InputType.url
                    required = true
                    minLength = "15"
                    tmpHendelse?.let {
                        value = tmpHendelse.url
                    }
                }

            }

            labelAnDescribe(FormInputField.SMS_EPOST_TEKST) {
                textArea {
                    setAttrs(FormInputField.SMS_EPOST_TEKST)
                    required = true
                    maxLength = "150"
                    minLength = "50"
                    tmpHendelse?.let {
                        text(it.eksternTekst)
                    }
                }
            }
        }
        fieldSet {
            legend { +"Mottakere" }
            labelAnDescribe(FormInputField.IDENT_FILE) {
                input {
                    setAttrs(FormInputField.IDENT_FILE)
                    accept = ".csv"
                    type = InputType.file
                    required = tmpHendelse?.affectedUsers?.isEmpty() ?: true
                }
            }
        }
        button {
            type = ButtonType.submit
            text("Neste")
        }
    }
    cancelAndGoBackButtons()
}

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
    TITLE(htmlName = "title", labelText = "Tittel", describe = "Kun til internt bruk"),
    DESCRIPTION(htmlName = "description", labelText = "Beskrivelse", describe = "Kun til internt bruk"),
    SMS_EPOST_TEKST(
        htmlName = "ekstern-text",
        labelText = "Varseltekst som blir sendt på SMS/e-post",
        describe = "Husk: ikke sensitive opplysninger som ytelse etc."
    ),
    LINK(
        htmlName = "url",
        labelText = "Link",
        describe = "Til en side med mer informasjon. Eksempelvis en nyhetssak."
    ),
    MIN_SIDE_TEXT(
        htmlName = "beskjed-text",
        labelText = "Beskjed på min side",
        describe = "Teksten som vises i varsler på Min side og i varselbjella i dekoratøren"
    ),
    IDENT_FILE(
        htmlName = "ident",
        labelText = "Last opp csv-fil med personnumre"
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
    }
}

