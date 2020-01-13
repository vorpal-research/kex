# Kex

Kex is a white-box fuzzer tool for Java bytecode.

# Dependencies

* [z3-java](https://aur.archlinux.org/packages/z3-java/) v4.8.6

  you need to manually install jar package with java bindings to your local maven repository using
  following command:
  ```
  mvn install:install-file -Dfile=/usr/lib/com.microsoft.z3.jar -DgroupId=com.microsoft 
  -DartifactId=z3 -Dversion=4.8.6 -Dpackaging=jar
  ```
* [boolector-java](https://aur.archlinux.org/packages/boolector-java/) v3.1.4

# Build

Build jar with all the dependencies:
```
mvn clean package
```

Run all the tests:
```
mvn clean verify
```

# Usage

```
Usage: kex
    --config <arg>                  configuration file
 -h,--help                          print help
 -j,--jar <arg>                     input jar file path
    --log <arg>                     log file name (`kex.log` by default)
    --option <section:name:value>   set kex option through command line
 -p,--package <arg>                 analyzed package
    --ps <arg>                      file with predicate state to debug; used
                                    only in debug mode
```
