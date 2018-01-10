Reactivemongo Integration  [![](https://jitpack.io/v/jtjeferreira/kamon-mongo.svg)](https://jitpack.io/#jtjeferreira/kamon-mongo)
==========================
[![Build Status](https://travis-ci.org/jtjeferreira/kamon-mongo.svg?branch=master)](https://travis-ci.org/jtjeferreira/kamon-mongo)

The `kamon-mongo` module brings bytecode instrumentation to trace the [reactivemongo](https://github.com/ReactiveMongo/ReactiveMongo) library

The _kamon-mongo_ module requires you to start your application using the AspectJ Weaver Agent. Kamon will warn you
at startup if you failed to do so.

The bytecode instrumentation     provided by the `kamon-mongo` module hooks into the reactivemongo code to automatically
start and finish spans for requests. This translates into you having traces about how
the requests you are doing are behaving.

### Getting Started

Kamon scala module is currently available for Scala 2.11 and 2.12.

Supported releases and dependencies are shown below.

| kamon      | status | jdk  | scala            
|:----------:|:------:|:----:|------------------
|  0.0.1 |   RC   | 1.8+ | 2.11, 2.12


To get started with SBT, simply add the following to your `build.sbt` file:

```scala
resolvers += "jitpack" at "https://jitpack.io"
libraryDependencies += "com.github.jtjeferreira" % "kamon-mongo" % "0.0.1"
```

### Metrics ###

No metrics are recorded

### Naming Segments ###

By default, the name generator bundled with the `kamon-mongo` module will use the collection name as the name to 
the automatically generated segment. Currently, the only way to override that name would be to provide your own implementation 
of `kamon.mongo.NameGenerator` which is used to assign the segment name

### Configuration ###

```typesafeconfig
kamon {
  mongo {
    # Fully qualified name of the implementation of kamon.mongo.NameGenerator that will be used for assigning names to segments.
    name-generator = kamon.mongo.DefaultNameGenerator
  }
}
```
