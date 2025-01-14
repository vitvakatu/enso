package org.enso.table.data.column.builder;

import java.util.BitSet;
import org.enso.base.polyglot.NumericConverter;
import org.enso.table.data.column.storage.type.IntegerType;
import org.enso.table.error.ValueTypeMismatchException;

/**
 * A LongBuilder that does not need to check value ranges, because it stores 64-bit integers and no
 * larger integers will come to appendLong anyway.
 */
public class LongBuilderUnchecked extends LongBuilder {
  protected LongBuilderUnchecked(BitSet isMissing, long[] data, int currentSize) {
    super(isMissing, data, currentSize);
  }

  @Override
  public void appendNoGrow(Object o) {
    if (o == null) {
      isMissing.set(currentSize++);
    } else {
      try {
        long x = NumericConverter.coerceToLong(o);
        data[currentSize++] = x;
      } catch (UnsupportedOperationException e) {
        throw new ValueTypeMismatchException(getType(), o);
      }
    }
  }

  @Override
  public void appendLongNoGrow(long data) {
    appendRawNoGrow(data);
  }

  @Override
  public IntegerType getType() {
    return IntegerType.INT_64;
  }
}
