package org.enso.interpreter.node.expression.builtin.number.integer;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;
import org.enso.interpreter.dsl.BuiltinMethod;
import org.enso.interpreter.node.expression.builtin.number.utils.BigIntegerOps;
import org.enso.interpreter.node.expression.builtin.number.utils.ToEnsoNumberNode;
import org.enso.interpreter.runtime.number.EnsoBigInteger;

@BuiltinMethod(type = "Integer", name = "+", description = "Addition of numbers.")
public abstract class AddNode extends Node {
  private @Child ToEnsoNumberNode toEnsoNumberNode = ToEnsoNumberNode.create();

  public abstract Object execute(Object self, Object that);

  public static AddNode build() {
    return AddNodeGen.create();
  }

  @Specialization(rewriteOn = ArithmeticException.class)
  long doLong(long self, long that) {
    return Math.addExact(self, that);
  }

  @Specialization(replaces = "doLong")
  Object doOverflow(long self, long that) {
    return toEnsoNumberNode.execute(BigIntegerOps.add(self, that));
  }

  @Specialization
  Object doDouble(long self, double that) {
    return self + that;
  }

  @TruffleBoundary
  @Specialization
  Object doBigIntegers(EnsoBigInteger self, EnsoBigInteger that) {
    return toEnsoNumberNode.execute(self.asBigInteger().add(that.asBigInteger()));
  }

  @Specialization
  Object doLongBigInteger(long self, EnsoBigInteger that) {
    return toEnsoNumberNode.execute(BigIntegerOps.add(that.getValue(), self));
  }

  @Specialization
  Object doBigIntegerLong(EnsoBigInteger self, long that) {
    return toEnsoNumberNode.execute(BigIntegerOps.add(self.getValue(), that));
  }

  @Specialization
  double doBigIntDouble(EnsoBigInteger self, double that) {
    return self.asDouble() + that;
  }

  @Fallback
  Object doOther(Object self, Object that) {
    throw IntegerUtils.throwTypeErrorIfNotInt(self, that, this);
  }
}
