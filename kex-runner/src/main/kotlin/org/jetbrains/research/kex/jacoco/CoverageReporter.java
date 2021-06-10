package org.jetbrains.research.kex.jacoco;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.junit.runner.JUnitCore;

import java.io.IOException;
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

    public CoverageReporter(URLClassLoader urlClassLoader) throws IOException {

        TestsCompiler testsCompiler = new TestsCompiler(urlClassLoader);
        testsCompiler.generateAll("tests");

        this.compiledClassLoader = testsCompiler.getCompiledClassLoader();
        this.instrAndTestsClassLoader = new MemoryClassLoader(compiledClassLoader);
        this.tests = testsCompiler.getTestsNames();
    }

    public String execute(String analyzeLevel) throws Exception {
        CoverageBuilder coverageBuilder;
        String canonicalName = analyzeLevel.replaceAll("[()]|(CLASS|METHOD|PACKAGE)", "");
        String result;
        if (analyzeLevel.startsWith("CLASS")) {
            String klass = canonicalName.replace("klass=", "");
            coverageBuilder = getCoverageBuilder(Collections.singletonList(klass + ".class"));
            result = getClassCoverage(coverageBuilder);
        } else if (analyzeLevel.startsWith("METHOD")) {
            String[] pair = canonicalName.split(", ");
            String klass = pair[0].replace("klass=", "");
            String method = pair[1].replace("method=", "");
            coverageBuilder = getCoverageBuilder(Collections.singletonList(klass + ".class"));
            result = getMethodCoverage(coverageBuilder, method);
        } else {
            String pkg = canonicalName.replace("pkg=", "");
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
            result = String.format("Coverage of package %s:%n%n", pkg) + getClassCoverage(coverageBuilder);
        }
        return result;
    }

    public CoverageBuilder getCoverageBuilder(List<String> classes) throws Exception {

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
        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            String className = cc.getName();
            sb.append(String.format("Coverage of class %s:%n", className));
            sb.append(getCounter("instructions", cc.getInstructionCounter()));
            sb.append(getCounter("branches", cc.getBranchCounter()));
            sb.append(getCounter("lines", cc.getLineCounter()));
            sb.append(getCounter("methods", cc.getMethodCounter()));
            sb.append(getCounter("complexity", cc.getComplexityCounter()));
            sb.append(getCounter("class", cc.getClassCounter()));
        }
        return sb.append("\n").toString();
    }

    private String getMethodCoverage(CoverageBuilder coverageBuilder, String method) {
        StringBuilder sb = new StringBuilder();
        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            for (final IMethodCoverage mc : cc.getMethods()) {
                String methodName = mc.getName();
                if (methodName.equals(method)) {
                    sb.append(String.format("Coverage of method %s:%n", method));
                    sb.append(getCounter("instructions", mc.getInstructionCounter()));
                    sb.append(getCounter("branches", mc.getBranchCounter()));
                    sb.append(getCounter("lines", mc.getLineCounter()));
                    sb.append(getCounter("complexity", mc.getComplexityCounter()));
                }
            }
        }
        return sb.toString();
    }

    private String getFullyQualifiedName(String name) {
        return name.substring(0, name.length() - 6).replace('/', '.');
    }

    private String getCounter(final String unit, final ICounter counter) {
        final int covered = counter.getCoveredCount();
        final int total = counter.getTotalCount();
        return String.format("%s of %s %s covered%n", covered, total, unit);
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