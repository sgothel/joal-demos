Trivial example that does nothing beyond opening an AL context. This
should be sufficient to test package dependency resolution and whether
or not the binaries actually work.

Simply run the example with:

  $ mvn -C clean verify

This will cause maven to download the JOAL packages from the Central
Repository (http://search.maven.org), compile the example code, and
run the test suite.

