# pompom-sbt
"pompom-sbt" convert dependencies of pom.xml to sbt style.
XML parser use [jsoup](https://github.com/jhy/jsoup/) html parser, so that can parse incomplete xml format.

# build
```
$ sbt assembly
```

# usage
```
$ java -jar pompom-sbt-0.1.0.jar pom.xml
```
