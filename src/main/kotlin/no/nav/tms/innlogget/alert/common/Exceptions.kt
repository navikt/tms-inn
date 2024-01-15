package no.nav.personbruker.dittnav.varselbestiller.common

open class AbstractCustomException(message: String, cause: Throwable?, identifier: String?) :
    Exception(message, cause) {

    private val context = mutableMapOf<String, Any>()

    init {
        if (identifier != null) {
            context["identifier"] = identifier
        }
    }

    fun addContext(key: String, value: Any) {
        context[key] = value
    }

    override fun toString(): String {
        return when (context.isNotEmpty()) {
            true -> super.toString() + ", context: $context"
            false -> super.toString()
        }
    }

}

class FieldValidationException(message: String, cause: Throwable? = null) :
    AbstractCustomException(message, cause, null)

open class RetriableDatabaseException(message: String, cause: Throwable? = null, identifier: String?=null) :
    AbstractCustomException(message, cause, identifier)

class UnretriableDatabaseException(message: String, cause: Throwable?, identifier: String?) :
    AbstractCustomException(message, cause, identifier)

class UnretriableKafkaException(message: String, cause: Throwable? = null, identifier: String?=null) :
    AbstractCustomException(message, cause, identifier)

class RetriableKafkaException(message: String, cause: Throwable? = null, identifier: String?=null) :
    AbstractCustomException(message, cause, identifier)
