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
package groovy.wire.macro

import org.codehaus.groovy.ast.ASTNode
import org.codehaus.groovy.ast.ClassHelper
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.Parameter
import org.codehaus.groovy.ast.VariableScope
import org.codehaus.groovy.ast.expr.BinaryExpression
import org.codehaus.groovy.ast.expr.ClassExpression
import org.codehaus.groovy.ast.expr.ClosureExpression
import org.codehaus.groovy.ast.expr.ConstantExpression
import org.codehaus.groovy.ast.expr.Expression
import org.codehaus.groovy.ast.expr.ListExpression
import org.codehaus.groovy.ast.expr.MapEntryExpression
import org.codehaus.groovy.ast.expr.MapExpression
import org.codehaus.groovy.ast.expr.MethodCallExpression
import org.codehaus.groovy.ast.expr.PropertyExpression
import org.codehaus.groovy.ast.expr.TupleExpression
import org.codehaus.groovy.ast.expr.VariableExpression
import org.codehaus.groovy.ast.stmt.BlockStatement
import org.codehaus.groovy.ast.stmt.ExpressionStatement
import org.codehaus.groovy.ast.stmt.Statement
import org.codehaus.groovy.macro.runtime.Macro
import org.codehaus.groovy.macro.runtime.MacroContext
import org.codehaus.groovy.syntax.SyntaxException
import org.codehaus.groovy.syntax.Token
import org.codehaus.groovy.syntax.Types

import static org.codehaus.groovy.ast.tools.GeneralUtils.args
import static org.codehaus.groovy.ast.tools.GeneralUtils.callX
import static org.codehaus.groovy.ast.tools.GeneralUtils.classX
import static org.codehaus.groovy.ast.tools.GeneralUtils.closureX
import static org.codehaus.groovy.ast.tools.GeneralUtils.constX
import static org.codehaus.groovy.ast.tools.GeneralUtils.ctorX
import static org.codehaus.groovy.ast.tools.GeneralUtils.declS
import static org.codehaus.groovy.ast.tools.GeneralUtils.listX
import static org.codehaus.groovy.ast.tools.GeneralUtils.propX
import static org.codehaus.groovy.ast.tools.GeneralUtils.returnS
import static org.codehaus.groovy.ast.tools.GeneralUtils.stmt
import static org.codehaus.groovy.ast.tools.GeneralUtils.varX

// Compile-time WIRE macro — << binding form.
//
// User syntax:
//
//   import static groovy.wire.macro.WireMacroMethods.WIRE
//
//   def wc = WIRE(WordCountPrimitives, 'WordCount', ['path']) {
//       text  << loadText(path)
//       words << countWords(text)
//       lines << countLines(text)
//       chars << countChars(text)
//       writeReport(path, words, lines, chars)
//   }
//   wc.run(path: '/tmp/file.txt')
//   wc.toPlantUml()
//
// Expands at compile time to real Groovy 6 dataflow code: each
// `name << call(refs)` becomes an `async { df.name = UsingClass.call(df.refs…) }`
// task; each bare `call(refs)` becomes a void `async { … }`. All
// scheduled tasks are awaited; the bindings are returned as a Map.
//
// `<<` is a deliberate choice. It's already Groovy 6's
// DataflowVariable.bind operator, so a reader who knows `var << val`
// from dataflow code reads a wire block correctly on first
// encounter. The `df.` prefixes and `async { }` wrapping are added
// by the macro — the macro is sugar over the standard dataflow
// idiom, not a competing DSL.
//
// Compile-time checks:
//   * every reference must be bound (graph-level input or earlier <<)
//   * the using class must contain a method matching each call name
//   * at least one overload must have the right arity
// All three report compile errors with source positions.
final class WireMacroMethods {

    private static final ClassNode WIRE_RESULT    = ClassHelper.make('groovy.wire.WireResult')
    private static final ClassNode WIRE_GRAPH     = ClassHelper.make('groovy.wire.WireGraph')
    private static final ClassNode WIRE_NODE      = ClassHelper.make('groovy.wire.WireNode')
    private static final ClassNode DATAFLOWS_TYPE = ClassHelper.make('groovy.concurrent.Dataflows')
    private static final ClassNode ASYNC_SUPPORT  = ClassHelper.make('org.apache.groovy.runtime.async.AsyncSupport')

    private WireMacroMethods() { }

    @Macro
    static Expression WIRE(MacroContext ctx, Expression... exps) {
        if (exps == null || exps.length < 3 || exps.length > 4) {
            return error(ctx, ctx.call,
                "WIRE expects WIRE(UsingClass, 'name', [inputs]?) { … }")
        }
        Expression usingExpr  = exps[0]
        Expression nameExpr   = exps[1]
        Expression inputsExpr = (exps.length == 4) ? exps[2] : null
        Expression bodyExpr   = exps[exps.length - 1]

        if (!(nameExpr instanceof ConstantExpression) || !(nameExpr.value instanceof String)) {
            return error(ctx, nameExpr,
                "WIRE second arg must be a string literal naming the wire")
        }
        if (!(bodyExpr instanceof ClosureExpression)) {
            return error(ctx, bodyExpr,
                "WIRE third arg must be a closure body")
        }
        ClassExpression usingClass = toClassExpression(usingExpr)
        if (usingClass == null) {
            return error(ctx, usingExpr,
                "WIRE first arg must be a class reference")
        }
        List<String> declaredInputs = toStringList(inputsExpr)
        if (inputsExpr != null && declaredInputs == null) {
            return error(ctx, inputsExpr,
                "WIRE inputs arg must be a list of string literals")
        }
        if (declaredInputs == null) declaredInputs = []

        Class<?> usingRuntime = resolveRuntimeClass(usingClass, ctx)
        String wireName = (String) ((ConstantExpression) nameExpr).value
        ClosureExpression body = (ClosureExpression) bodyExpr

        List<Statement> oldStmts = (body.code instanceof BlockStatement)
            ? ((BlockStatement) body.code).statements
            : Collections.singletonList(body.code)

        Set<String> bound = new LinkedHashSet<>(declaredInputs)
        List<NodeSpec> specs = []

        for (Statement old : oldStmts) {
            NodeSpec ns = analyseStatement(old, bound, usingRuntime, ctx)
            if (ns != null) {
                specs << ns
                if (ns.output) bound << ns.output
            }
        }

        // Emit: new WireResult(graph: <graph>, executor: <closure>)
        Expression graphExpr = emitGraph(wireName, declaredInputs, specs)
        Expression executorExpr = emitExecutor(specs, bound, usingClass)
        Expression result = ctorX(WIRE_RESULT, args(namedArgs([
            graph:    graphExpr,
            executor: executorExpr
        ])))
        result.sourcePosition = ctx.call
        result
    }

    static Object WIRE(Object self, Object... runtimeArgs) {
        throw new IllegalStateException(
            "WIRE is a compile-time macro and should never run; check the classpath")
    }

    // ------------------------------------------------------------------
    // Statement analysis
    // ------------------------------------------------------------------

    private static NodeSpec analyseStatement(Statement s, Set<String> bound,
                                              Class<?> usingRuntime, MacroContext ctx) {
        if (!(s instanceof ExpressionStatement)) return null
        Expression expr = ((ExpressionStatement) s).expression

        // Case A: name << call(refs…)
        if (expr instanceof BinaryExpression
                && ((BinaryExpression) expr).operation.type == Types.LEFT_SHIFT) {
            BinaryExpression bin = (BinaryExpression) expr
            if (!(bin.leftExpression instanceof VariableExpression)) {
                error(ctx, bin.leftExpression,
                    "WIRE: << LHS must be a bare binding name (e.g. text << loadText(path))")
                return null
            }
            String outName = ((VariableExpression) bin.leftExpression).name
            MethodCallExpression rhs = asMethodCall(bin.rightExpression)
            if (rhs == null) {
                error(ctx, bin.rightExpression,
                    "WIRE: << RHS must be a primitive call (e.g. loadText(path))")
                return null
            }
            String methodName = rhs.methodAsString
            List<String> refs = collectArgNames(rhs, bound, ctx)
            validatePrimitive(usingRuntime, methodName, refs.size(), ctx, rhs)
            return new NodeSpec(label: methodName, inputs: refs, output: outName)
        }

        // Case B: bare call(refs…)
        if (expr instanceof MethodCallExpression) {
            MethodCallExpression mc = (MethodCallExpression) expr
            if (mc.implicitThis && mc.methodAsString != null) {
                String methodName = mc.methodAsString
                List<String> refs = collectArgNames(mc, bound, ctx)
                validatePrimitive(usingRuntime, methodName, refs.size(), ctx, mc)
                return new NodeSpec(label: methodName, inputs: refs, output: null)
            }
        }

        error(ctx, s,
            "WIRE: each statement must be either `name << call(refs)` or `call(refs)`")
        null
    }

    private static MethodCallExpression asMethodCall(Expression e) {
        e instanceof MethodCallExpression ? (MethodCallExpression) e : null
    }

    private static List<String> collectArgNames(MethodCallExpression mc,
                                                Set<String> bound, MacroContext ctx) {
        List<String> out = []
        def argList = mc.arguments
        List<Expression> exprs = (argList instanceof TupleExpression)
            ? ((TupleExpression) argList).expressions
            : Collections.<Expression>emptyList()
        for (Expression a : exprs) {
            if (!(a instanceof VariableExpression)) {
                error(ctx, a,
                    "WIRE call argument must be a bare binding reference")
                continue
            }
            String n = ((VariableExpression) a).name
            if (!bound.contains(n)) {
                error(ctx, a,
                    "WIRE: reference '${n}' is not bound — declare it as a graph " +
                    "input or as the LHS of an earlier << binding")
            }
            out << n
        }
        out
    }

    // ------------------------------------------------------------------
    // AST emission
    // ------------------------------------------------------------------

    // new WireGraph(name: 'X', nodes: [WireNode(...)], declaredInputs: [...])
    private static Expression emitGraph(String wireName, List<String> declaredInputs,
                                         List<NodeSpec> specs) {
        Expression nodesList = listX(specs.collect { NodeSpec n -> emitNode(n) } as List<Expression>)
        Expression inputsList = listX(declaredInputs.collect { String s -> constX(s) } as List<Expression>)
        ctorX(WIRE_GRAPH, args(namedArgs([
            name:           constX(wireName),
            nodes:          nodesList,
            declaredInputs: inputsList
        ])))
    }

    // new WireNode(name: '…', inputs: [...], outputs: [...])
    private static Expression emitNode(NodeSpec spec) {
        Expression inputsList  = listX(spec.inputs.collect { String s -> constX(s) } as List<Expression>)
        Expression outputsList = listX(spec.output ? [constX(spec.output)] as List<Expression>
                                                   : [] as List<Expression>)
        ctorX(WIRE_NODE, args(namedArgs([
            name:    constX(spec.label),
            inputs:  inputsList,
            outputs: outputsList
        ])))
    }

    // { groovy.concurrent.Dataflows __df ->
    //     def __tasks = []
    //     __tasks << AsyncSupport.async { __df.text = UsingClass.loadText(__df.path) }
    //     ... etc.
    //     __tasks.each { AsyncSupport.await(it) }
    //     [path: __df.path, text: __df.text, …]
    // }
    private static Expression emitExecutor(List<NodeSpec> specs, Set<String> allBindings,
                                            ClassExpression usingClass) {
        Parameter dfParam = new Parameter(DATAFLOWS_TYPE, '__df')

        List<Statement> body = []
        body << declS(varX('__tasks'), new ListExpression(new ArrayList<Expression>()))

        for (NodeSpec n : specs) {
            Expression asyncCall = emitAsyncTask(n, usingClass)
            Expression leftShift = new BinaryExpression(
                varX('__tasks'),
                Token.newSymbol(Types.LEFT_SHIFT, -1, -1),
                asyncCall
            )
            body << stmt(leftShift)
        }

        // __tasks.each { AsyncSupport.await(it) }
        Parameter itParam = new Parameter(ClassHelper.OBJECT_TYPE, 'it')
        Expression awaitCall = callX(classX(ASYNC_SUPPORT), 'await', args(varX('it')))
        ClosureExpression itClosure = closureX(new Parameter[]{itParam}, stmt(awaitCall))
        itClosure.variableScope = new VariableScope()
        body << stmt(callX(varX('__tasks'), 'each', args(itClosure)))

        // Result map: [k: __df.k, …]
        List<MapEntryExpression> entries = allBindings.collect { String k ->
            new MapEntryExpression(constX(k), propX(varX('__df'), k))
        }
        body << returnS(new MapExpression(entries))

        BlockStatement blockBody = new BlockStatement(body, new VariableScope())
        ClosureExpression closure = closureX(new Parameter[]{dfParam}, blockBody)
        closure.variableScope = new VariableScope()
        closure
    }

    // AsyncSupport.async { __df.<out> = UsingClass.<call>(__df.<in1>, …) }
    //   or, for void/terminal nodes:
    // AsyncSupport.async { UsingClass.<call>(__df.<in1>, …) }
    private static Expression emitAsyncTask(NodeSpec n, ClassExpression usingClass) {
        List<Expression> callArgs = n.inputs.collect { String f -> propX(varX('__df'), f) }
        Expression innerCall = callX(usingClass, n.label, args(callArgs))

        Expression closureBody = (n.output != null)
            ? new BinaryExpression(
                propX(varX('__df'), n.output),
                Token.newSymbol(Types.ASSIGN, -1, -1),
                innerCall
            )
            : innerCall

        ClosureExpression body = closureX(Parameter.EMPTY_ARRAY, stmt(closureBody))
        body.variableScope = new VariableScope()
        callX(classX(ASYNC_SUPPORT), 'async', args(body))
    }

    // ------------------------------------------------------------------
    // Header-arg parsers + utilities
    // ------------------------------------------------------------------

    private static ClassExpression toClassExpression(Expression e) {
        if (e instanceof ClassExpression) return (ClassExpression) e
        if (e instanceof VariableExpression || e instanceof PropertyExpression) {
            ClassNode cn = ClassHelper.make(e.text)
            ClassExpression ce = new ClassExpression(cn)
            ce.sourcePosition = e
            return ce
        }
        null
    }

    private static List<String> toStringList(Expression e) {
        if (e == null) return Collections.emptyList()
        if (!(e instanceof ListExpression)) return null
        List<String> out = []
        for (Expression el : ((ListExpression) e).expressions) {
            if (!(el instanceof ConstantExpression) || !(((ConstantExpression) el).value instanceof String)) {
                return null
            }
            out << (((ConstantExpression) el).value as String)
        }
        out
    }

    private static MapExpression namedArgs(Map<String, Expression> pairs) {
        List<MapEntryExpression> entries = pairs.collect { String k, Expression v ->
            new MapEntryExpression(constX(k), v)
        }
        new MapExpression(entries)
    }

    private static Class<?> resolveRuntimeClass(ClassExpression ce, MacroContext ctx) {
        String text = ce.type.name
        ClassLoader cl = ctx.sourceUnit.classLoader
        try { return cl.loadClass(text) } catch (Throwable ignored) { /* fall through */ }
        def ast = ctx.sourceUnit.AST
        for (imp in (ast.imports ?: [])) {
            if (imp.alias == text || imp.className?.endsWith('.' + text)) {
                try { return cl.loadClass(imp.className) } catch (Throwable ignored) { }
            }
        }
        for (star in (ast.starImports ?: [])) {
            try { return cl.loadClass(star.packageName + text) } catch (Throwable ignored) { }
        }
        null
    }

    private static void validatePrimitive(Class<?> usingClazz, String methodName,
                                           int sourceArity, MacroContext ctx, ASTNode at) {
        if (usingClazz == null) return
        def named = usingClazz.methods.findAll { it.name == methodName }
        if (named.isEmpty()) {
            error(ctx, at,
                "WIRE: ${usingClazz.simpleName} has no method named '${methodName}'")
            return
        }
        if (!named.any { it.parameterCount == sourceArity }) {
            def arities = named.collect { it.parameterCount }.unique().sort()
            error(ctx, at,
                "WIRE: ${usingClazz.simpleName}.${methodName} takes " +
                "${arities.size() == 1 ? arities[0] : arities} arg(s), " +
                "but called with ${sourceArity}")
        }
    }

    private static Expression error(MacroContext ctx, ASTNode node, String message) {
        ctx.sourceUnit.addError(new SyntaxException(message + '\n', node))
        constX(null)
    }

    // Small compile-time tuple carrying the per-statement info collected
    // from the user's source — used twice, once to build the graph node
    // and once to emit the async task.
    private static final class NodeSpec {
        String label
        List<String> inputs
        String output    // null for terminal/void tasks
    }
}
