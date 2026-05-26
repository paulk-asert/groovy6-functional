/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Compile-time field-flow check for `Wire.wire { ... }` blocks.
// Same contract as the runtime check in WireBuilder — moved earlier
// in the lifecycle so the diagnostic appears at compile time (and in
// IDEs that surface type-checking-extension errors).
//
// Demo scope: handles the common case (static `Class::method`
// references to @Wirable methods inside `Wire.wire('...') { ... }`).
// Production polish — overload disambiguation, helpful "did you
// mean" hints, nested wire blocks, conditional tasks — is left
// out on purpose; the goal is to show the SPI shape, not to ship
// a hardened checker.

import org.codehaus.groovy.ast.AnnotationNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.MethodPointerExpression
import org.codehaus.groovy.ast.expr.StaticMethodCallExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement

final String WIRABLE   = 'groovy.wire.Wirable'
final String WIRE_TYPE = 'groovy.wire.Wire'

beforeMethodCall { call ->
    if (isWireCall(call, WIRE_TYPE)) {
        def args = argExpressions(call)
        ClosureExpression body = args.reverse().find { it instanceof ClosureExpression } as ClosureExpression
        List<String> declared = extractDeclaredInputs(args)
        if (body != null) {
            walkWireBody(body, WIRABLE, declared)
        }
    }
    false  // never short-circuit normal type checking
}

// `task` calls inside a wire { … } block dispatch through the
// closure delegate (WireBuilder.task, which schedules via
// async/DataflowVariable) at runtime; @DelegatesTo resolution
// doesn't reach into @TypeChecked, so we tell the type checker
// to treat them as dynamic. The field-flow check above is what
// actually validates each task.
methodNotFound { receiver, name, argumentList, argTypes, call ->
    if (name == 'task') {
        return makeDynamic(call, ClassHelper.VOID_TYPE)
    }
    null
}

private static boolean isWireCall(call, String wireType) {
    if (call instanceof MethodCallExpression) {
        if (call.methodAsString != 'wire') return false
        def recv = call.objectExpression
        if (recv instanceof ClassExpression) return recv.type.name == wireType
        return recv?.text == 'Wire'
    }
    if (call instanceof StaticMethodCallExpression) {
        return call.method == 'wire' && call.ownerType?.name == wireType
    }
    false
}

private static List<Expression> argExpressions(call) {
    def args = call.arguments
    args instanceof TupleExpression ? args.expressions : []
}

private static List<String> extractDeclaredInputs(List<Expression> args) {
    // Wire.wire(name, inputs, body): the inputs list is a literal
    // List of strings between the name and the closure body.
    def listArg = args.find { it instanceof ListExpression }
    if (listArg instanceof ListExpression) {
        return listArg.expressions
                      .findAll { it instanceof ConstantExpression }
                      .collect { ((ConstantExpression) it).value as String }
    }
    []
}

private void walkWireBody(ClosureExpression closure, String wirableType, List<String> declaredInputs) {
    def code = closure.code
    def stmts = code instanceof BlockStatement ? code.statements : [code]
    Set<String> available = new LinkedHashSet<>()
    available.addAll(declaredInputs)
    for (stmt in stmts) {
        if (!(stmt instanceof ExpressionStatement)) continue
        def expr = stmt.expression
        if (!(expr instanceof MethodCallExpression)) continue
        if (expr.methodAsString != 'task') continue
        checkTaskCall((MethodCallExpression) expr, available, wirableType)
    }
}

private void checkTaskCall(MethodCallExpression taskCall, Set<String> available, String wirableType) {
    def args = argExpressions(taskCall)
    // Two task signatures handled:
    //   task(ref)                  — builder form, @Wirable-driven
    //   task(name, ref, [inputs])  — macro-emitted form, explicit
    if (args.size() == 1) {
        checkBuilderTask(taskCall, args[0], available, wirableType)
    } else if (args.size() == 3) {
        checkExplicitTask(taskCall, args, available)
    }
}

private void checkBuilderTask(MethodCallExpression taskCall, Expression refExpr,
                              Set<String> available, String wirableType) {
    MethodNode method = resolveReference(refExpr, wirableType)
    if (method == null) return

    AnnotationNode wirable = method.annotations.find { it.classNode?.name == wirableType }
    if (wirable == null) {
        addStaticTypeError(
            "wire task '${method.name}' is not annotated @Wirable", taskCall)
        return
    }

    List<String> inputs  = stringArrayMember(wirable, 'inputs')
    List<String> outputs = stringArrayMember(wirable, 'outputs')

    for (String field : inputs) {
        if (!available.contains(field)) {
            addStaticTypeError(
                "wire task '${method.name}' needs field '${field}' " +
                "but only ${available} have been produced so far",
                taskCall)
        }
    }
    available.addAll(outputs)
}

private void checkExplicitTask(MethodCallExpression taskCall, List<Expression> args,
                               Set<String> available) {
    String outputName = (args[0] instanceof ConstantExpression
                            && ((ConstantExpression) args[0]).value instanceof String)
        ? ((ConstantExpression) args[0]).value as String
        : null
    String taskName = describeReference(args[1]) ?: 'task'
    List<String> inputs = (args[2] instanceof ListExpression)
        ? ((ListExpression) args[2]).expressions
              .findAll { it instanceof ConstantExpression }
              .collect { ((ConstantExpression) it).value as String }
        : []
    for (String field : inputs) {
        if (!available.contains(field)) {
            addStaticTypeError(
                "wire task '${taskName}' needs field '${field}' " +
                "but only ${available} have been produced so far",
                taskCall)
        }
    }
    if (outputName != null) available << outputName
}

private static String describeReference(Expression ref) {
    if (ref instanceof MethodPointerExpression) {
        return ref.methodName instanceof ConstantExpression
            ? ((ConstantExpression) ref.methodName).value as String
            : ref.methodName.text
    }
    null
}

private static MethodNode resolveReference(Expression ref, String wirableType) {
    if (!(ref instanceof MethodPointerExpression)) return null
    def receiver = ref.expression
    if (!(receiver instanceof ClassExpression)) return null
    ClassNode owner = receiver.type
    String name = ref.methodName instanceof ConstantExpression
                    ? ref.methodName.value as String
                    : ref.methodName.text
    owner.methods.find {
        it.name == name &&
        it.annotations.any { ann -> ann.classNode?.name == wirableType }
    }
}

private static List<String> stringArrayMember(AnnotationNode ann, String memberName) {
    def expr = ann.getMember(memberName)
    if (expr == null) return []
    if (expr instanceof ListExpression) {
        return expr.expressions
                   .findAll { it instanceof ConstantExpression }
                   .collect { ((ConstantExpression) it).value as String }
    }
    if (expr instanceof ConstantExpression) {
        return [expr.value as String]
    }
    []
}
