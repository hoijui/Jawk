# Contributing

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

