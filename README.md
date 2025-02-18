# sonatype-central-publish-maven-plugin

[![CI](https://github.com/nodece/sonatype-central-publish-maven-plugin/actions/workflows/main.yml/badge.svg)](https://github.com/nodece/sonatype-central-publish-maven-plugin/actions/workflows/main.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.nodece/sonatype-central-publish-maven-plugin?label=Maven%20Central)](https://search.maven.org/artifact/io.github.nodece/sonatype-central-publish-maven-plugin)
[![Release](https://img.shields.io/github/release/nodece/sonatype-central-publish-maven-plugin.svg?label=Release)](https://github.com/nodece/sonatype-central-publish-maven-plugin/releases/latest)

A maven plugin to publish the project artifacts to sonatype central.

## Usage

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.github.nodece</groupId>
      <artifactId>sonatype-central-publish-maven-plugin</artifactId>
      <version>${sonatype-central-publish-maven-plugin.version}</version>
      <extensions>true</extensions>
      <configuration>
        <skip>false</skip>
        <serverId>central</serverId>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### Authentication

To configure authentication, add your credentials to the `~/.m2/settings.xml` file:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- Username --></username>
      <password><!-- Password --></password>
    </server>
  </servers>
</settings>
```

Alternatively, you can specify your credentials directly in the `<configuration>` section of the `sonatype-central-publish-maven-plugin`:

```xml
<configuration>
  <username><!-- Username --></username>
  <password><!-- Password --></password>
</configuration>
```

### Skip Deployment

To skip the deployment process, set the `<skip>` configuration to `true` in the `<configuration>` section of the `sonatype-central-publish-maven-plugin`:

```xml
<configuration>
  <skip>true</skip>
</configuration>
```

Alternatively, the `sonatype-central-publish-maven-plugin` can also use the `<skip>true</skip>` configuration from the `maven-deploy-plugin` to skip deployment.

### Publish your project artifacts

```shell
mvn deploy
```
