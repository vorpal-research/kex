package org.jetbrains.research.kex.jacoco;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class TestsCompiler {

    private final List<ClassJavaFileObject> generatedClasses = new ArrayList<>();

    private List<ClassJavaFileObject> getGeneratedClasses(File javaFile) {

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        SimpleJavaFileManager fileManager =
                new SimpleJavaFileManager(compiler.getStandardFileManager(null, null, null));

        JavaFileObject compilationUnit = new TestJavaFileObject(javaFile);

        JavaCompiler.CompilationTask compilationTask = compiler.getTask(
                null, fileManager, null, null, null, Collections.singletonList(compilationUnit));

        compilationTask.call();

        return fileManager.getGeneratedOutputFiles();
    }

    public void generateAll(String directoryPath) {
        File directory = new File(directoryPath);
        if (!directory.isDirectory()) throw new IllegalArgumentException();
        File[] files = directory.listFiles();
        Objects.requireNonNull(files);
        for (File file : files) {
            if (!file.isDirectory()) {
                generatedClasses.addAll(getGeneratedClasses(file));
            }
            else {
                generateAll(file.getPath());
            }
        }
    }

    public List<String> getTestsNames() {
        List<String> result = new ArrayList<>();
        generatedClasses.forEach(c -> result.add(c.getClassName()));
        return result;
    }

    public CompiledClassLoader getCompiledClassLoader(URLClassLoader urlClassLoader) {
        return new CompiledClassLoader(generatedClasses, urlClassLoader);
    }

    public static class TestJavaFileObject extends SimpleJavaFileObject {

        private final File javaFile;

        public TestJavaFileObject(File javaFile) {
            super(URI.create(javaFile.getName()), Kind.SOURCE);
            this.javaFile = javaFile;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
            List<String> lines = Files.readAllLines(javaFile.toPath());
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                sb.append(line).append('\n');
            }
            return sb.toString();
        }
    }

    private static class ClassJavaFileObject extends SimpleJavaFileObject {

        private final ByteArrayOutputStream outputStream;

        private final String className;

        protected ClassJavaFileObject(String className) {
            super(URI.create(className.replace('.', '/') + ".class"), Kind.CLASS);
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
            ClassJavaFileObject file = new ClassJavaFileObject(className);
            outputFiles.add(file);
            return file;
        }

        public List<ClassJavaFileObject> getGeneratedOutputFiles() {
            return outputFiles;
        }
    }

    public static class CompiledClassLoader extends ClassLoader {

        private final List<ClassJavaFileObject> files;

        private final URLClassLoader parent;

        private CompiledClassLoader(List<ClassJavaFileObject> files, URLClassLoader parent) {
            this.files = files;
            this.parent = parent;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            byte[] bytes = getBytes(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return parent.loadClass(name);
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
            return parent.getResourceAsStream(name);
        }

        public URL[] getURLs() {
            return parent.getURLs();
        }
    }

}
