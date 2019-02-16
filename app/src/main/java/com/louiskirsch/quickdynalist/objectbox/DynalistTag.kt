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
        fun find(tag: DynalistTag): DynalistTag {
            val box = DynalistApp.instance.boxStore.boxFor<DynalistTag>()
            return box.query {
                equal(DynalistTag_.type, tag.type.ordinal.toLong())
                and()
                equal(DynalistTag_.name, tag.name)
            }.findFirst() ?: tag
        }
    }
}
