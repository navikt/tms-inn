package no.nav.tms.brannslukning.gui

import kotlinx.html.*
import no.nav.tms.brannslukning.gui.FormInputField.Companion.setAttrs
import org.intellij.lang.annotations.Language

fun MAIN.bakgrunnForm(tmpHendelse: TmpBeredskapsvarsel?, postEndpoint: String) {
    form {
        action = postEndpoint
        method = FormMethod.post
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

        }
        button {
            type = ButtonType.submit
            text("Neste")
        }
    }
    cancelAndGoBackButtons()
}

fun MAIN.varselForm(tmpHendelse: TmpBeredskapsvarsel, postEndpoint: String) {
    form {
        action = postEndpoint
        method = FormMethod.post
        encType = FormEncType.multipartFormData
        fieldSet {
            legend { +"Varseltekst" }
            labelAnDescribe(
                FormInputField.SMS_EPOST_TEKST,
                standardTextBuilder = {
                    div {
                        input {
                            id = "standardtekst-sms"
                            type = InputType.checkBox
                            onChange = onChangedDefaultSmsText
                        }
                        label {
                            htmlFor = "standardtekst-sms"
                            +"Bruk standardtekst"
                        }
                    }
                },
                inputBuilder = {
                    textArea {
                        setAttrs(FormInputField.SMS_EPOST_TEKST)
                        required = true
                        maxLength = "140"
                        minLength = "30"
                        tmpHendelse.eksternTekst?.let {
                            text(it)
                        }
                    }
                }
            )
            labelAnDescribe(FormInputField.LINK) {
                input {
                    setAttrs(FormInputField.LINK)
                    type = InputType.url
                    required = true
                    minLength = "10"
                    tmpHendelse.link?.let {
                        value = it
                    }
                }

            }

            labelAnDescribe(
                FormInputField.MIN_SIDE_TEXT
            ) {
                textArea {
                    setAttrs(FormInputField.MIN_SIDE_TEXT)
                    required = true
                    maxLength = "300"
                    minLength = "30"
                    tmpHendelse.eksternTekst?.let {
                        text(it)
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
                        onChange = "document.querySelector(\"#file-input-value\").textContent=this.files[0].name"
                        required = tmpHendelse.countUsersAffected() == 0
                    }

                    p {
                        attributes["aria-hidden"] = "true"
                        span(classes = "file-input") {
                            id = "file-input-value"
                            +" Ingen fil valgt"
                        }
                        span(classes = "file-input-button") {
                            +"Søk etter fil"
                        }
                    }
                }
            }
        }
        button {
            type = ButtonType.submit
            text("Neste")
        }
    }
}


@Language("JavaScript")
private val onChangedDefaultSmsText = """
    if(this.checked == true){
        document.getElementsByName("${FormInputField.SMS_EPOST_TEKST.htmlName}")[0].value = "${FormInputField.SMS_EPOST_TEKST.default}";
    }"""
