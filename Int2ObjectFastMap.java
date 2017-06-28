import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

// Source: https://github.com/mikvor/hashmapTest/tree/master/src/main/java/map/intint
public class Int2ObjectFastMap <T> extends AbstractMap<Integer, T> {


    private static final int FREE_KEY = 0;

    public final T NO_VALUE = null;

    /** Keys */
    private int[] m_keys;
    /** Values */
    private T[] m_values;

    /** Do we have 'free' key in the map? */
    private boolean m_hasFreeKey;
    /** Value of 'free' key */
    private T m_freeValue;

    /** Fill factor, must be between (0 and 1) */
    private final float m_fillFactor;
    /** We will resize a map once it reaches this size */
    private int m_threshold;
    /** Current map size */
    private int m_size;
    /** Mask to calculate the original position */
    private int m_mask;

    public Int2ObjectFastMap(final int size, final float fillFactor )
    {
        if ( fillFactor <= 0 || fillFactor >= 1 )
            throw new IllegalArgumentException( "FillFactor must be in (0, 1)" );
        if ( size <= 0 )
            throw new IllegalArgumentException( "Size must be positive!" );
        final int capacity = arraySize( size, fillFactor );
        m_mask = capacity - 1;
        m_fillFactor = fillFactor;

        m_keys = new int[capacity];
        m_values = null;
        //noinspection unchecked
        m_values = (T[]) new Object[capacity];
        m_threshold = (int) (capacity * fillFactor);
    }

    public T get( final int key )
    {
        if ( key == FREE_KEY)
            return m_hasFreeKey ? m_freeValue : NO_VALUE;

        final int idx = getReadIndex( key );
        return idx != -1 ? m_values[ idx ] : NO_VALUE;
    }

    public T put( final int key, final T value )
    {
        if ( key == FREE_KEY )
        {
            final T ret = m_freeValue;
            if ( !m_hasFreeKey )
                ++m_size;
            m_hasFreeKey = true;
            m_freeValue = value;
            return ret;
        }

        int idx = getPutIndex(key);
        if ( idx < 0 )
        { //no insertion point? Should not happen...
            rehash( m_keys.length * 2 );
            idx = getPutIndex( key );
        }
        final T prev = m_values[ idx ];
        if ( m_keys[ idx ] != key )
        {
            m_keys[ idx ] = key;
            m_values[ idx ] = value;
            ++m_size;
            if ( m_size >= m_threshold )
                rehash( m_keys.length * 2 );
        }
        else //it means used cell with our key
        {
            assert m_keys[ idx ] == key;
            m_values[ idx ] = value;
        }
        return prev;
    }

    public T remove( final int key )
    {
        if ( key == FREE_KEY )
        {
            if ( !m_hasFreeKey )
                return NO_VALUE;
            m_hasFreeKey = false;
            final T ret = m_freeValue;
            m_freeValue = NO_VALUE;
            --m_size;
            return ret;
        }

        int idx = getReadIndex(key);
        if ( idx == -1 )
            return NO_VALUE;

        final T res = m_values[ idx ];
        m_values[ idx ] = NO_VALUE;
        shiftKeys(idx);
        --m_size;
        return res;
    }

    public int size()
    {
        return m_size;
    }

    @Override
    public Set<Entry<Integer, T>> entrySet() {
        Set<Entry<Integer, T>> set = new LinkedHashSet<>(); 
        return null;
    }

    private void rehash( final int newCapacity )
    {
        m_threshold = (int) (newCapacity * m_fillFactor);
        m_mask = newCapacity - 1;

        final int oldCapacity = m_keys.length;
        final int[] oldKeys = m_keys;
        final T[] oldValues = m_values;

        m_keys = new int[ newCapacity ];
        //noinspection unchecked
        m_values = (T[]) new Object[ newCapacity ];
        m_size = m_hasFreeKey ? 1 : 0;

        for ( int i = oldCapacity; i-- > 0; ) {
            if( oldKeys[ i ] != FREE_KEY  )
                put( oldKeys[ i ], oldValues[ i ] );
        }
    }

    private int shiftKeys(int pos)
    {
        // Shift entries with the same hash.
        int last, slot;
        int k;
        final int[] keys = this.m_keys;
        while ( true )
        {
            last = pos;
            pos = getNextIndex(pos);
            while ( true )
            {
                if ((k = keys[pos]) == FREE_KEY)
                {
                    keys[last] = FREE_KEY;
                    m_values[ last ] = NO_VALUE;
                    return last;
                }
                slot = getStartIndex(k); //calculate the starting slot for the current key
                if (last <= pos ? last >= slot || slot > pos : last >= slot && slot > pos) break;
                pos = getNextIndex(pos);
            }
            keys[last] = k;
            m_values[last] = m_values[pos];
        }
    }

    /**
     * Find key position in the map.
     * @param key Key to look for
     * @return Key position or -1 if not found
     */
    private int getReadIndex( final int key )
    {
        int idx = getStartIndex( key );
        if ( m_keys[ idx ] == key ) //we check FREE prior to this call
            return idx;
        if ( m_keys[ idx ] == FREE_KEY ) //end of chain already
            return -1;
        final int startIdx = idx;
        while (( idx = getNextIndex( idx ) ) != startIdx )
        {
            if ( m_keys[ idx ] == FREE_KEY )
                return -1;
            if ( m_keys[ idx ] == key )
                return idx;
        }
        return -1;
    }

    /**
     * Find an index of a cell which should be updated by 'put' operation.
     * It can be:
     * 1) a cell with a given key
     * 2) first free cell in the chain
     * @param key Key to look for
     * @return Index of a cell to be updated by a 'put' operation
     */
    private int getPutIndex( final int key )
    {
        final int readIdx = getReadIndex( key );
        if ( readIdx >= 0 )
            return readIdx;
        //key not found, find insertion point
        final int startIdx = getStartIndex( key );
        if ( m_keys[ startIdx ] == FREE_KEY )
            return startIdx;
        int idx = startIdx;
        while ( m_keys[ idx ] != FREE_KEY )
        {
            idx = getNextIndex( idx );
            if ( idx == startIdx )
                return -1;
        }
        return idx;
    }


    private int getStartIndex( final int key )
    {
        return phiMix( key ) & m_mask;
    }

    private int getNextIndex( final int currentIndex )
    {
        return ( currentIndex + 1 ) & m_mask;
    }
    // https://github.com/mikvor/hashmapTest/blob/master/src/main/java/map/intint/java
    private static final int INT_PHI = 0x9E3779B9;

    /** Taken from FastUtil implementation */

    /** Return the least power of two greater than or equal to the specified value.
     *
     * <p>Note that this function will return 1 when the argument is 0.
     *
     * @param x a long integer smaller than or equal to 2<sup>62</sup>.
     * @return the least power of two greater than or equal to the specified value.
     */
    private static long nextPowerOfTwo( long x ) {
        if ( x == 0 ) return 1;
        x--;
        x |= x >> 1;
        x |= x >> 2;
        x |= x >> 4;
        x |= x >> 8;
        x |= x >> 16;
        return ( x | x >> 32 ) + 1;
    }

    /** Returns the least power of two smaller than or equal to 2<sup>30</sup> and larger than or equal to <code>Math.ceil( expected / f )</code>.
     *
     * @param expected the expected number of elements in a hash table.
     * @param f the load factor.
     * @return the minimum possible size for a backing array.
     * @throws IllegalArgumentException if the necessary size is larger than 2<sup>30</sup>.
     */
    private static int arraySize( final int expected, final float f ) {
        final long s = Math.max( 2, nextPowerOfTwo( (long)Math.ceil( expected / f ) ) );
        if ( s > (1 << 30) ) throw new IllegalArgumentException( "Too large (" + expected + " expected elements with load factor " + f + ")" );
        return (int)s;
    }
    private static int phiMix( final int x ) {
        final int h = x * INT_PHI;
        return h ^ (h >> 16);
    }

}
