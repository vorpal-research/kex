package org.jetbrains.research.kex.jacoco;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.internal.analysis.PackageCoverageImpl;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.junit.runner.JUnitCore;

import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.jetbrains.research.kex.jacoco.TestsCompiler.CompiledClassLoader;

public class CoverageReporter {

    private final CompiledClassLoader compiledClassLoader;

    private final MemoryClassLoader instrAndTestsClassLoader;

    private final List<String> tests;

    private final String pkg;

    public CoverageReporter(String testsPackage, URLClassLoader urlClassLoader) {
        TestsCompiler testsCompiler = new TestsCompiler();
        testsCompiler.generateAll("tests/" + testsPackage);

        compiledClassLoader = testsCompiler.getCompiledClassLoader(urlClassLoader);
        instrAndTestsClassLoader = new MemoryClassLoader(compiledClassLoader);
        tests = testsCompiler.getTestsNames();
        pkg = testsPackage;
    }

    public String execute(String analysisLevel) throws Exception {
        String canonicalName = analysisLevel.replaceAll("[()]|(CLASS|METHOD)", "");
        CoverageBuilder coverageBuilder;
        String result;
        if (!canonicalName.equals(analysisLevel)) {
            String[] pair = canonicalName.split(", ");
            String klass = pair[0].replace("klass=", "");
            coverageBuilder = getCoverageBuilder(Collections.singletonList(klass + ".class"));
            if (analysisLevel.startsWith("CLASS")) {
                result = getClassCoverage(coverageBuilder);
            }
            else {
                String method = pair[1].replace("method=", "");
                result = getMethodCoverage(coverageBuilder, method);
            }
        } else {
            URL[] urls = compiledClassLoader.getURLs();
            String jarPath = urls[urls.length - 1].toString().replace("file:", "");
            JarFile jarFile = new JarFile(jarPath);
            Enumeration<JarEntry> jarEntries = jarFile.entries();
            List<String> classes = new ArrayList<>();
            while(jarEntries.hasMoreElements()) {
                JarEntry jarEntry = jarEntries.nextElement();
                String name = jarEntry.getName();
                if (name.startsWith(pkg + '/') && name.endsWith(".class")) {
                    classes.add(name);
                }
            }
            coverageBuilder = getCoverageBuilder(classes);
            result = getPackageCoverage(coverageBuilder);
        }
        return result;
    }

    private CoverageBuilder getCoverageBuilder(List<String> classes) throws Exception {

        final IRuntime runtime = new LoggerRuntime();

        InputStream original;

        for (String className: classes) {
            original = compiledClassLoader.getResourceAsStream(className);
            Objects.requireNonNull(original);
            String fullyQualifiedName = getFullyQualifiedName(className);
            final Instrumenter instr = new Instrumenter(runtime);
            final byte[] instrumented = instr.instrument(original, fullyQualifiedName);
            original.close();
            instrAndTestsClassLoader.addDefinition(fullyQualifiedName, instrumented);
        }

        final RuntimeData data = new RuntimeData();
        runtime.startup(data);

        System.out.println("\nRunning tests...\n");

        for (String testName : tests) {
            instrAndTestsClassLoader.addDefinition(testName, compiledClassLoader.getBytes(testName));
            final Class<?> testClass = instrAndTestsClassLoader.loadClass(testName);
            JUnitCore.runClasses(testClass);
        }

        System.out.println("\nAnalyzing Coverage...\n");

        final ExecutionDataStore executionData = new ExecutionDataStore();
        final SessionInfoStore sessionInfos = new SessionInfoStore();
        data.collect(executionData, sessionInfos, false);
        runtime.shutdown();

        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(executionData, coverageBuilder);

        for (String className : classes) {
            original = compiledClassLoader.getResourceAsStream(className);
            Objects.requireNonNull(original);
            analyzer.analyzeClass(original, getFullyQualifiedName(className));
            original.close();
        }

        return coverageBuilder;
    }

    private String getClassCoverage(CoverageBuilder coverageBuilder) {
        StringBuilder sb = new StringBuilder();
        for (IClassCoverage cc : coverageBuilder.getClasses()) {
            sb.append(getCoverage("class", cc.getName(), cc));
            for (IMethodCoverage mc : cc.getMethods()) {
                sb.append(getCoverage("method", mc.getName(), mc));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private String getMethodCoverage(CoverageBuilder coverageBuilder, String method) {
        for (final IMethodCoverage mc : coverageBuilder.getClasses().iterator().next().getMethods()) {
            if (mc.getName().equals(method)) {
                return getCoverage("method", method, mc);
            }
        }
        return null;
    }

    private String getPackageCoverage(CoverageBuilder coverageBuilder) {
        IPackageCoverage pc = new PackageCoverageImpl(pkg, coverageBuilder.getClasses(), coverageBuilder.getSourceFiles());
        return getCoverage("package", pkg, pc) + "\n" + getClassCoverage(coverageBuilder);
    }

    private String getCoverage(final String level, final String name, final ICoverageNode coverage) {
        final List<String> names = new ArrayList<>(Arrays.asList("instructions", "branches", "lines", "complexity"));
        final List<ICounter> counters = new ArrayList<>(Arrays.asList(
                coverage.getInstructionCounter(), coverage.getBranchCounter(),
                coverage.getLineCounter(), coverage.getComplexityCounter()
        ));
        if (!level.equals("method")) {
            names.add("methods");
            counters.add(coverage.getMethodCounter());
            if (level.equals("package")) {
                names.add("classes");
                counters.add(coverage.getClassCounter());
            }
        }
        float ratio = 0;
        int count = 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < names.size(); i++) {
            ICounter counter = counters.get(i);
            int covered = counter.getCoveredCount();
            int total = counter.getTotalCount();
            if (total != 0) {
                ratio += (float) covered / total;
                count++;
            }
            sb.append(String.format("%s of %s %s covered%n", covered, total, names.get(i)));
        }
        String percent = String.format("%.02f", ratio / count * 100) + '%';
        return String.format("Coverage of %s %s = %s :%n", level, name, percent) + sb + "\n";
    }

    private String getFullyQualifiedName(String name) {
        return name.substring(0, name.length() - 6).replace('/', '.');
    }

    private static class MemoryClassLoader extends ClassLoader {

        private final ClassLoader parent;

        public MemoryClassLoader(ClassLoader parent) {
            this.parent = parent;
        }

        private final Map<String, byte[]> definitions = new HashMap<>();

        public void addDefinition(final String name, final byte[] bytes) {
            definitions.put(name, bytes);
        }

        @Override
        public Class<?> loadClass(final String name) throws ClassNotFoundException {
            final byte[] bytes = definitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return parent.loadClass(name);
        }
    }

}