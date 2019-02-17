package com.louiskirsch.quickdynalist.objectbox

import com.louiskirsch.quickdynalist.DynalistApp
import io.objectbox.annotation.Convert
import io.objectbox.annotation.Entity
import io.objectbox.annotation.Id
import io.objectbox.annotation.Index
import io.objectbox.converter.PropertyConverter
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query

@Entity
class DynalistTag() {
    enum class Type { UNKNOWN, HASH_TAG, AT_TAG }
    class TypeConverter: PropertyConverter<Type, Int> {
        override fun convertToDatabaseValue(entityProperty: Type?): Int
                = entityProperty?.ordinal ?: 0
        override fun convertToEntityProperty(databaseValue: Int?): Type
                = databaseValue?.let { Type.values()[it] } ?: Type.UNKNOWN
    }

    @Id var id: Long = 0
    @Index
    @Convert(converter = TypeConverter::class, dbType = Integer::class)
    var type: Type = Type.HASH_TAG
    @Index var name: String = ""

    val fullName: String get() {
        val prefix = if (type == Type.AT_TAG) '@' else '#'
        return "$prefix$name"
    }

    override fun toString(): String = fullName
    override fun hashCode(): Int = (id % Int.MAX_VALUE).toInt()
    override fun equals(other: Any?): Boolean {
        return if (id > 0)
            return id == (other as? DynalistTag)?.id
        else
            false
    }

    private constructor(fromString: String) : this() {
        type = when(fromString[0]) {
            '#' -> Type.HASH_TAG
            '@' -> Type.AT_TAG
            else -> throw InvalidTagException()
        }
        name = fromString.substring(1)
    }

    companion object {
        fun find(fromString: String): DynalistTag = find(DynalistTag(fromString))
        private fun find(tag: DynalistTag): DynalistTag {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistTag>()
            var foundTag: DynalistTag? = null
            DynalistApp.instance.boxStore.runInTx {
                foundTag = box.query {
                    equal(DynalistTag_.type, tag.type.ordinal.toLong())
                    and()
                    equal(DynalistTag_.name, tag.name)
                }.findFirst() ?: tag.also { box.put(it) }
            }
            return foundTag!!
        }
    }
}
