# Kex

Kex is a white-box fuzzer tool for Java bytecode.

# Dependencies

* [Authenticate to GitHub Packages](https://docs.github.com/en/packages/guides/configuring-apache-maven-for-use-with-github-packages#authenticating-to-github-packages)
  with following configuration:
  ```xml
  <server>
    <id>github-vorpal-research-kotlin-maven</id>
    <username>USERNAME</username>
    <password>TOKEN</password>
  </server>
  ``` 

* [z3-java](https://aur.archlinux.org/packages/z3-java/) v4.8.6

  you need to manually install jar package with java bindings to your local maven repository using
  following command:
  ```
  mvn install:install-file -Dfile=/usr/lib/com.microsoft.z3.jar -DgroupId=com.microsoft 
  -DartifactId=z3 -Dversion=4.8.6 -Dpackaging=jar
  ```
* [boolector-java](https://aur.archlinux.org/packages/boolector-java/) v3.2.1

# Build

* build jar with all the dependencies:
    ```
    mvn clean package
    ```

* build with only one SMT solver support:
    ```
    mvn clean package -Psolver
    ```
    where `solver` stand for required solver name (`boolector` or `z3`) 

Run all the tests:
```
mvn clean verify
```

# Usage

```
Usage: kex
    --config <arg>                  configuration file
 -cp,--classpath <arg[:arg]>        classpath for analysis, jar files and
                                    directories separated by `:`
 -h,--help                          print this help and quit
    --log <arg>                     log file name (`kex.log` by default)
 -m,--mode <arg>                    run mode: symbolic, concolic or debug
    --option <section:name:value>   set kex option through command line
    --output <arg>                  target directory for instrumented bytecode
                                    output
    --ps <arg>                      file with predicate state to debug; used
                                    only in debug mode
 -t,--target <arg>                  target to analyze: package, class or method
```

# Example

Consider an example class:
```kotlin
class TestClass {
    class Point(val x: Int, val y: Int)

    fun test(a: ArrayList<Point>) {
        if (a.size == 2) {
            if (a[0].x == 10) {
                if (a[1].y == 11) {
                    error("a")
                }
            }
        }
    }
}
```

Compile that class into the jar file and tun Kex on it using following command:
```bash
./kex.sh --classpath test.jar --target TestClass --output temp --log test.log
```

`test.log` file will contain tests generated by the Kex:
```kotlin
import java.util.ArrayList

fun <T> unknown(): T {
    TODO()
}

fun test(): Unit {
    val generatedTerm1197 = TestClass()
    val generatedTerm1198 = ArrayList<TestClass.Point>(2)
    val generatedTerm1503 = Test.Point(10, 0)
    generatedTerm1198.add(generatedTerm1503)
    val generatedTerm1279 = Test.Point(0, 11)
    generatedTerm1198.add(generatedTerm1279)
    generatedTerm1197.test(generatedTerm1198)
}
``` 