# Jawk - ReadMe

## Intro

JAWK is a pure Java distribution of [AWK](https://en.wikipedia.org/wiki/AWK).
It uses [Maven](http://maven.apache.org/),
and comes with [OSGi](https://en.wikipedia.org/wiki/OSGi) meta data
(it only exports package(s)).
Its is licensed under the GPL, and the location of the source code
can be found in the project documentation site (see below),
or the project file (`pom.xml`).


## How to build

Jawk uses Maven 2+ as build system, which you have to install first.
If you did so, you can:

compile & package:

	mvn package

execute:

	mvn exec:java

create project documentation (to be found under `target/site/index.html`):

	mvn site

Jawk relies on [BCEL](http://commons.apache.org/bcel/) for parsing AWK scripts.


## Release

### Prepare "target/" for the release process

	mvn release:clean

### Prepare the release
* asks for the release and new snapshot versions to use (for all modules)
* packages
* signs with GPG
* commits
* tags
* pushes to origin

		mvn release:prepare

### Perform the release
* checks-out the release tag
* builds
* deploy into Sonatype staging repository

		mvn release:perform

### Promote it on Maven
Moves it from the Sonatype staging to the main Sonatype repo.

1. using the Nexus staging plugin:

		mvn nexus:staging-close
		mvn nexus:staging-release

2. ... alternatively, using the web-interface:
	* firefox https://oss.sonatype.org
	* login
	* got to the "Staging Repositories" tab
	* select "org.jawk..."
	* "Close" it
	* select "org.jawk..." again
	* "Release" it

