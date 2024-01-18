import kotlinx.html.*
import no.nav.tms.brannslukning.common.gui.TmpHendelse
import no.nav.tms.brannslukning.common.gui.cancelAndGoBackButtons


fun BODY.textForm(tmpHendelse: TmpHendelse?, postEndpoint:String) {
    form {
        action = postEndpoint
        method = FormMethod.post
        fieldSet {
            legend {
                +"Hendelsedetaljer"
            }
            label {
                htmlFor = "title-input"
                +"Tittel"
            }
            input {
                id = "title-input"
                name = "title"
                required = true
                maxLength = "50"
                tmpHendelse?.let {
                    value = tmpHendelse.title
                }
            }
            label {
                htmlFor = "descript-input"
                +"Beskrivelse"
            }
            textArea(classes = "text-input") {
                id = "descript-input"
                name = "description"
                maxLength = "300"
                tmpHendelse?.let {
                    text(it.description)
                }
            }


        }
        fieldSet {
            legend {
                +"Beskjed på min side"
            }
            label {
                htmlFor = "beskjed-input"
                +"Tekst"
            }
            textArea(classes = "text-input") {
                id = "beskjed-input"
                name = "beskjed-text"
                required = true
                maxLength = "150"
                minLength = "50"
                tmpHendelse?.let {
                    text(it.varseltekst)
                }
            }
            label {
                htmlFor = "url-input"
                +"Link til mer informasjon"
            }
            input {
                id = "url-input"
                name = "url"
                type = InputType.url
                required = true
                minLength = "15"
                tmpHendelse?.let {
                    value = tmpHendelse.url
                }
            }
        }
        fieldSet {
            id = "ekstern-tekst-fieldset"
            legend {
                +"Varsel på sms/epost"
            }
            label {
                htmlFor = "ekstern-tekst-input"
                +"Tekst"
            }
            textArea(classes = "text-input") {
                id = "ekstern-tekst-input"
                name = "ekstern-text"
                required = true
                maxLength = "150"
                minLength = "50"
                tmpHendelse?.let {
                    text(it.eksternTekst)
                }
            }
        }
        cancelAndGoBackButtons()
        button {
            type = ButtonType.submit
            text("Neste")
        }
    }
}