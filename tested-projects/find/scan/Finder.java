package scan;

import org.apache.tools.ant.DirectoryScanner;

public class Finder {

    public String[] find(String dirPath, String extension) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(dirPath);
        scanner.setIncludes(new String[]{ "**/*" + extension });
        scanner.scan();
        return scanner.getIncludedFiles();
    }

}
