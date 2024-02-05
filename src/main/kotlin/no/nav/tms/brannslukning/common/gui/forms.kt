package no.nav.tms.brannslukning.common.gui

import kotlinx.html.*
import no.nav.tms.brannslukning.common.gui.FormInputField.Companion.setAttrs
import org.intellij.lang.annotations.Language

fun MAIN.bakgrunnForm(tmpHendelse: TmpHendelse?, postEndpoint: String) {
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

fun MAIN.varselForm(tmpHendelse: TmpHendelse, postEndpoint: String) {
    form {
        action = postEndpoint
        method = FormMethod.post
        encType = FormEncType.multipartFormData
        fieldSet {
            legend { +"Varseltekst" }
            labelAnDescribe(
                FormInputField.MIN_SIDE_TEXT
            ) {
                textArea {
                    setAttrs(FormInputField.MIN_SIDE_TEXT)
                    required = true
                    maxLength = "150"
                    minLength = "50"
                    tmpHendelse.varseltekst?.let {
                        text(it)
                    }
                }
            }

            labelAnDescribe(FormInputField.LINK) {
                input {
                    setAttrs(FormInputField.LINK)
                    type = InputType.url
                    required = true
                    minLength = "15"
                    tmpHendelse.url?.let {
                        value = it
                    }
                }

            }

            labelAnDescribe(FormInputField.SMS_EPOST_TEKST) {
                textArea {
                    setAttrs(FormInputField.SMS_EPOST_TEKST)
                    required = true
                    maxLength = "500"
                    minLength = "50"
                    tmpHendelse.eksternTekst?.let {
                        text(it)
                    }
                }
            }
            label {
                +"Bruk standardtekst"
                input {
                    type = InputType.checkBox
                    onChange = onChangedDefaultSmsText
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
                        required = tmpHendelse.affectedUsers.isEmpty() ?: true
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
            document.getElementsByName("${FormInputField.SMS_EPOST_TEKST.htmlName}")[0].setAttribute("disabled","true")      
    }else{
            document.getElementsByName("${FormInputField.SMS_EPOST_TEKST.htmlName}")[0].removeAttribute("disabled") 
    }"""