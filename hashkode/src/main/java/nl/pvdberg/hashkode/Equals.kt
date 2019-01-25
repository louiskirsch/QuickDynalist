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

@Suppress("OVERRIDE_BY_INLINE", "NOTHING_TO_INLINE")
class EqualsContext<out T>(val one: T, val two: T) : HashKodeContext<T>
{
    var equal = true

    inline override infix fun Any.correspondsTo(other: Any?)
    {
        if (equal) equal = this == other
    }

    inline override fun compareBy(comparison: () -> Boolean)
    {
        if (equal) equal = comparison()
    }

    inline override fun compareField(getter: T.() -> Any?)
    {
        if (equal) equal = one.getter() == two.getter()
    }
}

/**
 * Tests equality of two objects
 * @receiver Object to compare another object to
 * @param other Object to compare to receiver
 * @param requirements Lambda that compares fields
 * @return True when objects are equal
 * @see Any.equals
 */
inline fun <reified T : Any> T.compareFields(
        other: Any?,
        requirements: EqualsContext<T>.() -> Unit
): Boolean
{
    if (other == null) return false
    if (other === this) return true
    if (other !is T) return false

    return EqualsContext(this, other)
            .apply { requirements() }
            .equal
}
