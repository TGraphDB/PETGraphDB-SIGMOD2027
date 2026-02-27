package org.act.temporalProperty.impl;

import com.google.common.base.Preconditions;

import java.lang.ref.SoftReference;
import java.util.LinkedList;
import java.util.Map.Entry;

import org.act.temporalProperty.util.Slice;

import static com.google.common.base.Charsets.UTF_8;

public class InternalEntry
        implements Entry<InternalKey, Slice>
{
    private final InternalKey key;
    private final Slice value;

    public InternalEntry(InternalKey key, Slice value)
    {
        Preconditions.checkNotNull(key, "key is null");
        Preconditions.checkNotNull(value, "value is null");
        this.key = key;
        this.value = value;
    }

    @Override
    public InternalKey getKey()
    {
        return key;
    }

    @Override
    public Slice getValue()
    {
        return value;
    }

    /**
     * @throws UnsupportedOperationException always
     */
    @Override
    public final Slice setValue(Slice value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean equals(Object o)
    {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        InternalEntry entry = (InternalEntry) o;

        if (!key.equals(entry.key)) {
            return false;
        }
        if (!value.equals(entry.value)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode()
    {
        int result = key.hashCode();
        result = 31 * result + value.hashCode();
        return result;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("InternalEntry");
        sb.append("{key=").append(key);      // todo don't print the real value
        sb.append(", value=").append(value.toString(UTF_8));
        sb.append('}');
        return sb.toString();
    }

    public LinkedList<SearchableIterator> path = new LinkedList<>();
}
