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

    public String execute(String analyzeLevel) throws Exception {
        String canonicalName = analyzeLevel.replaceAll("[()]|(CLASS|METHOD)", "");
        CoverageBuilder coverageBuilder;
        String result;
        if (!canonicalName.equals(analyzeLevel)) {
            String[] pair = canonicalName.split(", ");
            String klass = pair[0].replace("klass=", "");
            coverageBuilder = getCoverageBuilder(Collections.singletonList(klass + ".class"));
            if (analyzeLevel.startsWith("CLASS")) {
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
        IClassCoverage cc = coverageBuilder.getClasses().iterator().next();
        return getCommonCounters("class", cc.getName(), cc) +
                getCounter("methods", cc.getMethodCounter());
    }

    private String getMethodCoverage(CoverageBuilder coverageBuilder, String method) {
        for (final IMethodCoverage mc : coverageBuilder.getClasses().iterator().next().getMethods()) {
            if (mc.getName().equals(method)) {
                return getCommonCounters("method", method, mc);
            }
        }
        return null;
    }

    private String getPackageCoverage(CoverageBuilder coverageBuilder) {
        IPackageCoverage pc = new PackageCoverageImpl(pkg, coverageBuilder.getClasses(), coverageBuilder.getSourceFiles());
        return getCommonCounters("package", pkg, pc) +
                getCounter("methods", pc.getMethodCounter()) +
                getCounter("classes", pc.getClassCounter());
    }

    private String getCounter(final String unit, final ICounter counter) {
        final int covered = counter.getCoveredCount();
        final int total = counter.getTotalCount();
        return String.format("%s of %s %s covered%n", covered, total, unit);
    }

    private String getCommonCounters(final String level, final String name, final ICoverageNode coverage) {
        return String.format("Coverage of %s %s:%n", level, name) +
                getCounter("instructions", coverage.getInstructionCounter()) +
                getCounter("branches", coverage.getBranchCounter()) +
                getCounter("lines", coverage.getLineCounter()) +
                getCounter("complexity", coverage.getComplexityCounter());
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