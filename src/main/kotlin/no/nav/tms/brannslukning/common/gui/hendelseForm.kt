package no.nav.tms.brannslukning.common.gui

import no.nav.tms.brannslukning.common.gui.FormInputField.Companion.setAttrs
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
            legend { +"Hvem skal motta varselet?" }
            labelAnDescribe(FormInputField.IDENT_FILE) {
                input {
                    setAttrs(FormInputField.IDENT_FILE)
                    accept = ".csv"
                    type = InputType.file
                    onChange="document.querySelector(\"#file-input-value\").textContent=this.files[0].name"
                    required = tmpHendelse?.affectedUsers?.isEmpty() ?: true
                }

                p {
                    attributes["aria-hidden"] = "true"
                    span(classes = "file-input") {
                        id="file-input-value"
                        +" Ingen fil valgt"
                    }
                    span(classes="file-input-button") {
                        +"Søk etter fil"
                    }
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
    }
}

