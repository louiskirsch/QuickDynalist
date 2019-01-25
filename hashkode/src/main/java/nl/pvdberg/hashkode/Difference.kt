/*
 * MIT License
 *
 * Copyright (c) 2017 Pim van den Berg
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package nl.pvdberg.hashkode

data class FieldDifference<out T>(val field1: Pair<T, Any?>, val field2: Pair<T, Any?>)

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
class DifferenceContext<T>(val one: T, val two: T) : HashKodeContext<T>
{
    val differences = mutableListOf<FieldDifference<T>>()

    inline override infix fun Any.correspondsTo(other: Any?)
    {
        if (this != other)
        {
            differences.add(
                    FieldDifference(
                            field1 = one to this,
                            field2 = two to other
                    )
            )
        }
    }

    /**
     * Not supported when checking differences
     * @see differenceBy
     */
    inline override fun compareBy(comparison: () -> Boolean)
    {
        throw UnsupportedOperationException("compareBy is not supported when checking differences")
    }

    /**
     * Runs function
     * @param difference Function which should list all differences between two fields
     */
    inline fun differenceBy(difference: () -> List<FieldDifference<T>>)
    {
        differences.addAll(difference())
    }

    inline override fun compareField(getter: T.() -> Any?)
    {
        val field1 = one.getter()
        val field2 = two.getter()

        if (field1 != field2)
        {
            differences.add(
                    FieldDifference(
                            field1 = one to field1,
                            field2 = two to field2
                    )
            )
        }
    }
}

/**
 * Gets field differences between two objects.
 * @receiver Object to compare another object to
 * @param other Object to compare to receiver
 * @param requirements Lambda that compares fields
 * @return List of differences
 * @see Any.equals
 */
inline fun <reified T : Any> T.getDifferences(
        other: T,
        requirements: DifferenceContext<T>.() -> Unit
): List<FieldDifference<T>>
{
    if (other === this) return emptyList()
    return DifferenceContext(this, other)
            .apply { requirements() }
            .differences
}