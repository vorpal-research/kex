package org.jetbrains.research.kex.jacoco;

import org.junit.runner.JUnitCore;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.*;

public class TestsCompiler {

    private final URLClassLoader baseClassLoader;

    public TestsCompiler(URLClassLoader urlClassLoader) {
        this.baseClassLoader = urlClassLoader;
        /*System.setProperty("java.class.path",
                System.getProperty("java.class.path") + System.getProperty("path.separator") + classPath);*/
        System.out.println("TestsCompiler runs...\n" + System.getProperty("java.class.path"));
    }

    private List<ClassJavaFileObject> getGeneratedClasses(File javaFile) throws IOException {

        String name = javaFile.getName();
        if (!name.endsWith(".java")) throw new IllegalArgumentException();
        name = name.substring(0, name.length() - 5); // without ".java"

        String program = getProgramText(javaFile);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaFileObject compilationUnit =
                new StringJavaFileObject(name, program);

        SimpleJavaFileManager fileManager =
                new SimpleJavaFileManager(compiler.getStandardFileManager(null, null, null));

        JavaCompiler.CompilationTask compilationTask = compiler.getTask(
                null, fileManager, null, null, null, Collections.singletonList(compilationUnit));

        compilationTask.call();

        return fileManager.getGeneratedOutputFiles();
    }

    private final List<ClassJavaFileObject> generatedClassesList = new ArrayList<>();

    public void generateAll(String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) throw new IllegalArgumentException();
        File[] files = directory.listFiles();
        Objects.requireNonNull(files);
        for (File file : files) {
            if (!file.isDirectory()) {
                this.generatedClassesList.addAll(getGeneratedClasses(file));
            } else {
                generateAll(file.getAbsolutePath());
            }
        }
    }

    public List<ClassJavaFileObject> getGeneratedClassesList() {
        return generatedClassesList;
    }

    public List<String> getTestsNames() {
        List<String> result = new ArrayList<>();
        getGeneratedClassesList().forEach(s -> result.add(s.getClassName()));
        return result;
    }

    public CompiledClassLoader getCompiledClassLoader() {
        return new CompiledClassLoader(getGeneratedClassesList());
    }

    public void main() throws IOException, ClassNotFoundException {
        generateAll("tests");
        List<String> list = getTestsNames();
        System.out.println("TestNames:\n");
        list.forEach(System.out::println);
        CompiledClassLoader classLoader = getCompiledClassLoader();
        for (String name : list) {
            Class<?> klass = classLoader.loadClass(name);
            JUnitCore.runClasses(klass);
        }
    }

    public String getProgramText(File program) throws IOException {
        List<String> lines = Files.readAllLines(program.toPath());
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    public static class StringJavaFileObject extends SimpleJavaFileObject {

        private final String code;

        public StringJavaFileObject(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension),
                    Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static class ClassJavaFileObject extends SimpleJavaFileObject {

        private final ByteArrayOutputStream outputStream;

        private final String className;

        protected ClassJavaFileObject(String className, Kind kind) {
            super(URI.create("mem:///" + className.replace('.', '/') + kind.extension), kind);
            this.className = className;
            outputStream = new ByteArrayOutputStream();
        }

        @Override
        public OutputStream openOutputStream() {
            return outputStream;
        }

        public byte[] getBytes() {
            return outputStream.toByteArray();
        }

        public String getClassName() {
            return className;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static class SimpleJavaFileManager extends ForwardingJavaFileManager {

        private final List<ClassJavaFileObject> outputFiles;

        protected SimpleJavaFileManager(JavaFileManager fileManager) {
            super(fileManager);
            outputFiles = new ArrayList<>();
        }

        @Override
        public JavaFileObject getJavaFileForOutput(
                Location location, String className, JavaFileObject.Kind kind, FileObject sibling
        ) {
            ClassJavaFileObject file = new ClassJavaFileObject(className, kind);
            outputFiles.add(file);
            return file;
        }

        public List<ClassJavaFileObject> getGeneratedOutputFiles() {
            return outputFiles;
        }
    }

    public class CompiledClassLoader extends ClassLoader {

        private final List<ClassJavaFileObject> files;

        private CompiledClassLoader(List<ClassJavaFileObject> files) {
            this.files = files;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            byte[] bytes = getBytes(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return baseClassLoader.loadClass(name);
        }

        public byte[] getBytes(String name) {
            for (ClassJavaFileObject file : files) {
                if (file.getClassName().equals(name)) {
                    return file.getBytes();
                }
            }
            return null;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            return baseClassLoader.getResourceAsStream(name);
        }
    }

}
