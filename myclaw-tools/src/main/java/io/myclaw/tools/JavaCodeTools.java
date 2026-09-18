package io.myclaw.tools;

import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import io.myclaw.core.tool.JsonSchema;
import io.myclaw.core.tool.Tool;
import io.myclaw.core.tool.ToolContext;
import io.myclaw.core.tool.ToolDefinition;
import tools.jackson.databind.JsonNode;

import javax.tools.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Java AST-powered structure, symbol, reference, and syntax diagnostic tools.
 */
public final class JavaCodeTools {
    private static final Set<String> SKIPPED = Set.of(".git", "target", "node_modules", "dist", ".idea");
    private static final int MAX_FILES = 2_000;
    private static final int MAX_RESULTS = 500;

    private JavaCodeTools() {
    }

    public static Tool findSymbol() {
        return tool("find_symbol", "Find Java declarations by AST node kind and symbol name, rather than raw text matching.",
                JsonSchema.object().string("query", "Class, interface, enum, record, method, or variable name")
                        .string("path", "File or directory; defaults to .", false)
                        .enumOf("kind", "Optional declaration kind", List.of("type", "method", "field", "variable"), false)
                        .bool("exact", "Require an exact name; defaults to true", false)
                        .integer("maxResults", "Maximum results, capped at 500", false).build(),
                (args, context) -> {
                    String query = ToolSupport.requiredString(args, "query");
                    boolean exact = ToolSupport.optionalBool(args, "exact", true);
                    String kind = ToolSupport.optionalString(args, "kind", "");
                    int max = bounded(args);
                    List<String> results = new ArrayList<>();
                    for (ParsedFile file : parseFiles(context, ToolSupport.optionalString(args, "path", "."))) {
                        new DeclarationScanner(file, declaration ->
                                (kind.isBlank() || kind.equals(declaration.kind())) &&
                                        (exact ? declaration.name().equals(query) : declaration.name().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))),
                                results, max).scan(file.unit(), null);
                        if (results.size() >= max) break;
                    }
                    return render(results, max);
                });
    }

    public static Tool findReferences() {
        return tool("find_references", "Find Java identifier/member references using parsed AST nodes. Results are syntactic; use LSP when type-resolved references are required.",
                JsonSchema.object().string("symbol", "Exact symbol name")
                        .string("path", "File or directory; defaults to .", false)
                        .bool("includeDeclarations", "Include matching declarations", false)
                        .integer("maxResults", "Maximum results, capped at 500", false).build(),
                (args, context) -> {
                    String symbol = ToolSupport.requiredString(args, "symbol");
                    boolean declarations = ToolSupport.optionalBool(args, "includeDeclarations", false);
                    int max = bounded(args);
                    List<String> results = new ArrayList<>();
                    for (ParsedFile file : parseFiles(context, ToolSupport.optionalString(args, "path", "."))) {
                        new ReferenceScanner(file, symbol, declarations, results, max).scan(file.unit(), null);
                        if (results.size() >= max) break;
                    }
                    return render(results, max);
                });
    }

    public static Tool outline() {
        return tool("code_outline", "Return the declaration hierarchy of one Java source file from its AST.",
                JsonSchema.object().string("path", "Java source file inside the workspace").build(),
                (args, context) -> {
                    Path path = context.resolve(ToolSupport.requiredString(args, "path"));
                    if (!Files.isRegularFile(path) || !path.toString().endsWith(".java"))
                        throw new IllegalArgumentException("path must be an existing .java file");
                    ParsedFile file = parse(context, path);
                    List<String> results = new ArrayList<>();
                    new OutlineScanner(file, results).scan(file.unit(), 0);
                    return results.isEmpty() ? "No declarations found." : String.join("\n", results);
                });
    }

    public static Tool diagnostics() {
        return tool("code_diagnostics", "Parse Java sources and report syntax diagnostics with file, line, and column. This does not require a language server.",
                JsonSchema.object().string("path", "Java file or directory; defaults to .", false)
                        .integer("maxResults", "Maximum diagnostics, capped at 500", false).build(),
                (args, context) -> {
                    int max = bounded(args);
                    List<String> results = new ArrayList<>();
                    for (Path file : javaFiles(context, ToolSupport.optionalString(args, "path", "."))) {
                        DiagnosticCollector<JavaFileObject> collector = new DiagnosticCollector<>();
                        parse(context, file, collector);
                        for (Diagnostic<? extends JavaFileObject> diagnostic : collector.getDiagnostics()) {
                            if (diagnostic.getKind() != Diagnostic.Kind.ERROR && diagnostic.getKind() != Diagnostic.Kind.WARNING && diagnostic.getKind() != Diagnostic.Kind.MANDATORY_WARNING)
                                continue;
                            results.add(relative(context, file) + ":" + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber() +
                                    " [" + diagnostic.getKind().name().toLowerCase(Locale.ROOT) + "] " + diagnostic.getMessage(Locale.ROOT));
                            if (results.size() >= max) return render(results, max);
                        }
                    }
                    return results.isEmpty() ? "No Java syntax diagnostics found." : String.join("\n", results);
                });
    }

    private static List<ParsedFile> parseFiles(ToolContext context, String rawPath) throws Exception {
        List<ParsedFile> result = new ArrayList<>();
        for (Path file : javaFiles(context, rawPath)) result.add(parse(context, file));
        return result;
    }

    private static List<Path> javaFiles(ToolContext context, String rawPath) throws Exception {
        Path root = context.resolve(rawPath);
        if (!Files.exists(root)) throw new java.io.FileNotFoundException("Path does not exist: " + root);
        try (Stream<Path> stream = Files.isDirectory(root) ? Files.walk(root) : Stream.of(root)) {
            List<Path> files = stream.filter(Files::isRegularFile).filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !skipped(root, path)).sorted().limit(MAX_FILES + 1L).toList();
            if (files.size() > MAX_FILES)
                throw new IllegalStateException("Java analysis is limited to " + MAX_FILES + " files; narrow path");
            return files;
        }
    }

    private static ParsedFile parse(ToolContext context, Path path) throws Exception {
        return parse(context, path, new DiagnosticCollector<>());
    }

    private static ParsedFile parse(ToolContext context, Path path, DiagnosticCollector<JavaFileObject> diagnostics) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("Java AST tools require a JDK, not a JRE");
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = manager.getJavaFileObjects(path.toFile());
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics, List.of("-proc:none"), null, sources);
            CompilationUnitTree unit = task.parse().iterator().next();
            Trees trees = Trees.instance(task);
            return new ParsedFile(context, path, unit, trees.getSourcePositions());
        }
    }

    private record ParsedFile(ToolContext context, Path path, CompilationUnitTree unit, SourcePositions positions) {
        String location(Tree tree) {
            long offset = positions.getStartPosition(unit, tree);
            long line = offset < 0 ? 0 : unit.getLineMap().getLineNumber(offset);
            long column = offset < 0 ? 0 : unit.getLineMap().getColumnNumber(offset);
            return relative(context, path) + ":" + line + ":" + column;
        }
    }

    private record Declaration(String kind, String name, Tree tree) {
    }

    private static final class DeclarationScanner extends TreePathScanner<Void, Void> {
        private final ParsedFile file;
        private final Predicate<Declaration> filter;
        private final List<String> output;
        private final int max;

        private DeclarationScanner(ParsedFile file, Predicate<Declaration> filter, List<String> output, int max) {
            this.file = file;
            this.filter = filter;
            this.output = output;
            this.max = max;
        }

        private void add(String kind, String name, Tree tree) {
            if (output.size() < max && filter.test(new Declaration(kind, name, tree)))
                output.add(file.location(tree) + " " + kind + " " + name);
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            add("type", node.getSimpleName().toString(), node);
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            add("method", node.getName().toString(), node);
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            add(getCurrentPath().getParentPath().getLeaf() instanceof ClassTree ? "field" : "variable", node.getName().toString(), node);
            return super.visitVariable(node, unused);
        }
    }

    private static final class ReferenceScanner extends TreePathScanner<Void, Void> {
        private final ParsedFile file;
        private final String symbol;
        private final boolean declarations;
        private final List<String> output;
        private final int max;

        private ReferenceScanner(ParsedFile file, String symbol, boolean declarations, List<String> output, int max) {
            this.file = file;
            this.symbol = symbol;
            this.declarations = declarations;
            this.output = output;
            this.max = max;
        }

        private void add(Tree tree, String kind) {
            if (output.size() < max) output.add(file.location(tree) + " " + kind + " " + symbol);
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            if (node.getName().contentEquals(symbol)) add(node, "reference");
            return super.visitIdentifier(node, unused);
        }

        @Override
        public Void visitMemberSelect(MemberSelectTree node, Void unused) {
            if (node.getIdentifier().contentEquals(symbol)) add(node, "reference");
            return super.visitMemberSelect(node, unused);
        }

        @Override
        public Void visitClass(ClassTree node, Void unused) {
            if (declarations && node.getSimpleName().contentEquals(symbol)) add(node, "declaration");
            return super.visitClass(node, unused);
        }

        @Override
        public Void visitMethod(MethodTree node, Void unused) {
            if (declarations && node.getName().contentEquals(symbol)) add(node, "declaration");
            return super.visitMethod(node, unused);
        }

        @Override
        public Void visitVariable(VariableTree node, Void unused) {
            if (declarations && node.getName().contentEquals(symbol)) add(node, "declaration");
            return super.visitVariable(node, unused);
        }
    }

    private static final class OutlineScanner extends TreePathScanner<Void, Integer> {
        private final ParsedFile file;
        private final List<String> output;

        private OutlineScanner(ParsedFile file, List<String> output) {
            this.file = file;
            this.output = output;
        }

        private void add(int depth, Tree tree, String label) {
            output.add("  ".repeat(Math.max(0, depth)) + file.location(tree) + " " + label);
        }

        @Override
        public Void visitClass(ClassTree node, Integer depth) {
            add(depth, node, node.getKind().name().toLowerCase(Locale.ROOT) + " " + node.getSimpleName());
            return super.visitClass(node, depth + 1);
        }

        @Override
        public Void visitMethod(MethodTree node, Integer depth) {
            add(depth, node, "method " + node.getName() + "(" + node.getParameters().stream().map(p -> p.getType() + " " + p.getName()).reduce((a, b) -> a + ", " + b).orElse("") + ")");
            return super.visitMethod(node, depth + 1);
        }

        @Override
        public Void visitVariable(VariableTree node, Integer depth) {
            add(depth, node, "variable " + node.getType() + " " + node.getName());
            return super.visitVariable(node, depth);
        }
    }

    private static Tool tool(String name, String description, JsonNode schema, Call call) {
        ToolDefinition definition = ToolDefinition.builder(name).description(description).parameters(schema).build();
        return new Tool() {
            public ToolDefinition definition() {
                return definition;
            }

            public String call(JsonNode args, ToolContext context) throws Exception {
                return call.run(args, context);
            }
        };
    }

    private static int bounded(JsonNode args) {
        return Math.min(MAX_RESULTS, Math.max(1, ToolSupport.optionalInt(args, "maxResults", 100)));
    }

    private static String render(List<String> results, int max) {
        return results.isEmpty() ? "No matches found." : String.join("\n", results) + (results.size() >= max ? "\n... results truncated at " + max : "");
    }

    private static String relative(ToolContext context, Path path) {
        return context.workingDirectory().relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static boolean skipped(Path root, Path path) {
        Path relative = Files.isDirectory(root) ? root.relativize(path) : path.getFileName();
        for (Path part : relative) if (SKIPPED.contains(part.toString())) return true;
        return false;
    }

    @FunctionalInterface
    private interface Call {
        String run(JsonNode args, ToolContext context) throws Exception;
    }
}
