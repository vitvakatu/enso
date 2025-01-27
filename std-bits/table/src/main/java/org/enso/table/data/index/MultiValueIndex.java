package org.enso.table.data.index;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.enso.base.text.TextFoldingStrategy;
import org.enso.table.aggregations.Aggregator;
import org.enso.table.data.column.builder.Builder;
import org.enso.table.data.column.storage.Storage;
import org.enso.table.data.table.Column;
import org.enso.table.data.table.Table;
import org.enso.table.data.table.problems.FloatingPointGrouping;
import org.enso.table.problems.AggregatedProblems;
import org.enso.table.util.ConstantList;
import org.graalvm.polyglot.Context;

public class MultiValueIndex<KeyType extends MultiValueKeyBase> {
  private final int keyColumnsLength;
  private final Map<KeyType, List<Integer>> locs;
  private final AggregatedProblems problems;

  public static MultiValueIndex<OrderedMultiValueKey> makeOrderedIndex(
      Column[] keyColumns, int tableSize, int[] ordering, Comparator<Object> objectComparator) {
    TreeMap<OrderedMultiValueKey, List<Integer>> locs = new TreeMap<>();
    final Storage<?>[] storage =
        Arrays.stream(keyColumns).map(Column::getStorage).toArray(Storage[]::new);
    IntFunction<OrderedMultiValueKey> keyFactory =
        i -> new OrderedMultiValueKey(storage, i, ordering, objectComparator);
    return new MultiValueIndex<>(keyColumns, tableSize, locs, keyFactory);
  }

  public static MultiValueIndex<UnorderedMultiValueKey> makeUnorderedIndex(
      Column[] keyColumns, int tableSize, List<TextFoldingStrategy> textFoldingStrategies) {
    HashMap<UnorderedMultiValueKey, List<Integer>> locs = new HashMap<>();
    final Storage<?>[] storage =
        Arrays.stream(keyColumns).map(Column::getStorage).toArray(Storage[]::new);
    IntFunction<UnorderedMultiValueKey> keyFactory =
        i -> new UnorderedMultiValueKey(storage, i, textFoldingStrategies);
    return new MultiValueIndex<>(keyColumns, tableSize, locs, keyFactory);
  }

  public static MultiValueIndex<UnorderedMultiValueKey> makeUnorderedIndex(
      Column[] keyColumns, int tableSize, TextFoldingStrategy commonTextFoldingStrategy) {
    List<TextFoldingStrategy> strategies =
        ConstantList.make(commonTextFoldingStrategy, keyColumns.length);
    return makeUnorderedIndex(keyColumns, tableSize, strategies);
  }

  private MultiValueIndex(
      Column[] keyColumns,
      int tableSize,
      Map<KeyType, List<Integer>> initialLocs,
      IntFunction<KeyType> keyFactory) {
    this.keyColumnsLength = keyColumns.length;
    this.problems = new AggregatedProblems();
    this.locs = initialLocs;

    if (keyColumns.length != 0) {
      int size = keyColumns[0].getSize();

      Context context = Context.getCurrent();
      for (int i = 0; i < size; i++) {
        KeyType key = keyFactory.apply(i);

        if (key.hasFloatValues()) {
          final int row = i;
          key.floatColumnPositions()
              .forEach(
                  columnIx ->
                      problems.add(new FloatingPointGrouping(keyColumns[columnIx].getName(), row)));
        }

        List<Integer> ids = this.locs.computeIfAbsent(key, x -> new ArrayList<>());
        ids.add(i);

        context.safepoint();
      }
    } else {
      this.locs.put(
          keyFactory.apply(0), IntStream.range(0, tableSize).boxed().collect(Collectors.toList()));
    }
  }

  public Table makeTable(Aggregator[] columns) {
    Context context = Context.getCurrent();
    final int length = columns.length;
    final int size = locs.size();

    boolean emptyScenario = size == 0 && keyColumnsLength == 0;
    Builder[] storage =
        Arrays.stream(columns)
            .map(c -> Builder.getForType(c.getType(), emptyScenario ? 1 : size))
            .toArray(Builder[]::new);

    if (emptyScenario) {
      // No grouping and no data
      List<Integer> empty = new ArrayList<>();
      for (int i = 0; i < length; i++) {
        storage[i].appendNoGrow(columns[i].aggregate(empty));
        context.safepoint();
      }
    } else {
      for (List<Integer> group_locs : this.locs.values()) {
        for (int i = 0; i < length; i++) {
          Object value = columns[i].aggregate(group_locs);
          storage[i].appendNoGrow(value);
          context.safepoint();
        }
      }
    }

    // Merge Problems
    AggregatedProblems[] problems = new AggregatedProblems[1 + length];
    problems[0] = this.problems;
    IntStream.range(0, length).forEach(i -> problems[i + 1] = columns[i].getProblems());
    AggregatedProblems merged = AggregatedProblems.merge(problems);

    return new Table(
        IntStream.range(0, length)
            .mapToObj(i -> new Column(columns[i].getName(), storage[i].seal()))
            .toArray(Column[]::new),
        merged);
  }

  public AggregatedProblems getProblems() {
    return problems;
  }

  public int[] makeOrderMap(int rowCount) {
    if (this.locs.size() == 0) {
      return new int[0];
    }

    int[] output = new int[rowCount];

    int idx = 0;
    Context context = Context.getCurrent();
    for (List<Integer> rowIndexes : this.locs.values()) {
      for (Integer rowIndex : rowIndexes) {
        output[idx++] = rowIndex;
        context.safepoint();
      }
    }

    return output;
  }

  public Set<KeyType> keys() {
    return locs.keySet();
  }

  public boolean contains(KeyType key) {
    return this.locs.containsKey(key);
  }

  public List<Integer> get(KeyType key) {
    return this.locs.get(key);
  }

  public int size() {
    return this.locs.size();
  }
}
