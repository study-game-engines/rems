package me.anno.io

import me.anno.io.base.BaseWriter

/**
 * something that should be saveable, but also nameable by editors or users
 * */
open class NamedSaveable : Saveable() {

    open var name = ""
    open var description = ""

    override fun isDefaultValue(): Boolean = false
    override val approxSize get() = 10

    override fun save(writer: BaseWriter) {
        super.save(writer)
        writer.writeString("name", name)
        writer.writeString("desc", description)
    }

    override fun readString(name: String, value: String) {
        when (name) {
            "name" -> this.name = value
            "desc", "description" -> this.description = value
            else -> super.readString(name, value)
        }
    }

}